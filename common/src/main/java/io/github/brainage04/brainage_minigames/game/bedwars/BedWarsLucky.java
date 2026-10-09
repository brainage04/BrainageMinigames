package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.TeamState;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Trap;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.menu.Icon;
import io.github.brainage04.brainage_minigames.menu.Menu;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bed Wars Lucky Blocks (v2): generators also drop lucky blocks, and each one placed and broken opens
 * one effect of its own table. The tables are the effects Hypixel's Lucky Blocks v2 announcement lists
 * for each block, with their rarity (Common, Rare, Epic, Legendary), as far as this mod has them; how
 * often each rarity comes up, and the numbers the announcement leaves open, are this mod's choice.
 *
 * <p>Among them: five lucky traps (Slowness, damage, Arrow Rain, freeze and Poison) that lie as a
 * carpet of the opener's team colour and go off on the first enemy to step on them unless an arrow
 * clears them first; Jerry, who trades Miracle Lucky Blocks for emeralds; and the Placeable Wither,
 * an old wither that fights for its team for a minute.
 */
public final class BedWarsLucky {
    private BedWarsLucky() {}

    /** The five lucky blocks: which generator drops each and its block. */
    enum Lucky {
        NORMAL("Lucky Block", Blocks.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW), ChatFormatting.YELLOW),
        PROMISING("Promising Lucky Block", Blocks.GLAZED_TERRACOTTA.pick(DyeColor.ORANGE), ChatFormatting.GOLD),
        FORTUNATE("Fortunate Lucky Block", Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE), ChatFormatting.AQUA),
        OFFENSIVE("Offensive Lucky Block", Blocks.GLAZED_TERRACOTTA.pick(DyeColor.RED), ChatFormatting.RED),
        MIRACLE("Miracle Lucky Block", Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME), ChatFormatting.GREEN);

        final String displayName;
        final Block block;
        final ChatFormatting color;

        Lucky(String displayName, Block block, ChatFormatting color) {
            this.displayName = displayName;
            this.block = block;
            this.color = color;
        }

        ItemStack item() {
            ItemStack stack = new ItemStack(block);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(style -> style.withColor(color).withItalic(false)));
            return stack;
        }

        static @Nullable Lucky of(BlockState state) {
            for (Lucky lucky : values()) if (state.is(lucky.block)) return lucky;
            return null;
        }
    }

    /** How often an effect of each rarity comes up, relative to the others; this mod's choice. */
    enum Rarity {
        COMMON(50),
        RARE(30),
        EPIC(15),
        LEGENDARY(5);

        final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }
    }

    /** Where an effect opens: the block's place, its opener and their team. */
    record Opening(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, int team, DyeColor dye) {
        ServerLevel level() {
            return match.arena().level();
        }

        void give(ItemStack stack) {
            ItemEntity item = new ItemEntity(level(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
            level().addFreshEntity(item);
        }
    }

    @FunctionalInterface
    interface Opener {
        void open(Opening opening);
    }

    /** One effect of a lucky block's table, named as the announcement names it. */
    record Effect(String name, Lucky block, Rarity rarity, Opener opener) {}

    /** A lucky trap lying at a block: the team that laid it and what it does. */
    record LuckyTrap(int team, String flavour) {}

    /** Who Jerry is, and how many emeralds a Miracle Lucky Block costs him; this mod's choice. */
    static final String JERRY = "Jerry";
    static final int JERRY_PRICE = 3;
    private static final int JERRY_TICKS = 2 * 60 * 20;
    private static final int HELPER_TICKS = 60 * 20;
    private static final int WITHER_TICKS = 60 * 20;

    /** Every effect this mod has, by block. */
    static final List<Effect> EFFECTS = List.of(
            // Lucky Block (with iron).
            effect("Bridge Eggs", Lucky.NORMAL, Rarity.COMMON, opening -> opening.give(withCount(shop(opening, "bridge_egg"), 2))),
            effect("Rainbow Wool", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::rainbowWool),
            effect("Flint and Steel", Lucky.NORMAL, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.FLINT_AND_STEEL))),
            effect("Wither Skeleton", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::witherSkeleton),
            effect("Trap > Slowness", Lucky.NORMAL, Rarity.COMMON, opening -> trap(opening, "Slowness")),
            effect("Trap > damage", Lucky.NORMAL, Rarity.COMMON, opening -> trap(opening, "damage")),
            effect("Instant Tool Upgrade", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::toolUpgrade),
            effect("Trap > Arrow Rain", Lucky.NORMAL, Rarity.RARE, opening -> trap(opening, "Arrow Rain")),
            effect("Bed Compass", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::bedCompass),
            effect("Random Team Upgrade", Lucky.NORMAL, Rarity.EPIC, BedWarsLucky::teamUpgrade),
            // Promising (with gold).
            effect("Sugar Cookie Rotation Effect", Lucky.PROMISING, Rarity.COMMON, opening -> opening.give(shop(opening, "sugar_cookie"))),
            effect("Instant Barrier", Lucky.PROMISING, Rarity.COMMON, BedWarsLucky::barrier),
            effect("Cute Pants", Lucky.PROMISING, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_LEGGINGS, DyeColor.PINK), "Cute Pants", Enchantments.PROTECTION, 2))),
            effect("Squid boots", Lucky.PROMISING, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_BOOTS, DyeColor.BLACK), "Squid Boots", Enchantments.DEPTH_STRIDER, 3))),
            effect("Spiky Suit", Lucky.PROMISING, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_CHESTPLATE, DyeColor.GREEN), "Spiky Suit", Enchantments.THORNS, 2))),
            // Fortunate (with diamonds).
            effect("Protective Bed Cover", Lucky.FORTUNATE, Rarity.COMMON, BedWarsLucky::bedCover),
            effect("heat-resistant boots", Lucky.FORTUNATE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_BOOTS, DyeColor.ORANGE), "Heat-Resistant Boots",
                            Enchantments.FIRE_PROTECTION, 4))),
            effect("4x Obsidian", Lucky.FORTUNATE, Rarity.RARE, opening -> opening.give(new ItemStack(Items.OBSIDIAN, 4))),
            effect("Sharp Spoon", Lucky.FORTUNATE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.WOODEN_SHOVEL)), "Sharp Spoon",
                            Enchantments.SHARPNESS, 3))),
            effect("Endstone Drop", Lucky.FORTUNATE, Rarity.RARE, opening -> opening.give(new ItemStack(Items.END_STONE, 32))),
            effect("Trap > freeze", Lucky.FORTUNATE, Rarity.RARE, opening -> trap(opening, "freeze")),
            // Offensive (with diamonds).
            effect("5x Cobweb", Lucky.OFFENSIVE, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.COBWEB, 5))),
            effect("Rush Pearl", Lucky.OFFENSIVE, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.ENDER_PEARL))),
            effect("Trap > Poison", Lucky.OFFENSIVE, Rarity.COMMON, opening -> trap(opening, "Poison")),
            effect("Transform block > Fire", Lucky.OFFENSIVE, Rarity.COMMON, BedWarsLucky::fire),
            effect("Battle Axe", Lucky.OFFENSIVE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.IRON_AXE)), "Battle Axe",
                            Enchantments.SHARPNESS, 2))),
            effect("Instant Trap Queue", Lucky.OFFENSIVE, Rarity.RARE, BedWarsLucky::trapQueue),
            effect("Instant Weapon Upgrade", Lucky.OFFENSIVE, Rarity.RARE, BedWarsLucky::weaponUpgrade),
            effect("Sword Of Justice", Lucky.OFFENSIVE, Rarity.RARE,
                    opening -> opening.give(BedWarsShop.unbreakable(BedWarsShop.tagged(Items.GOLDEN_SWORD, "sword_of_justice", "Sword of Justice")))),
            effect("Baby Zombie Helper", Lucky.OFFENSIVE, Rarity.EPIC, BedWarsLucky::babyZombie),
            effect("Knockback Slimeball", Lucky.OFFENSIVE, Rarity.EPIC,
                    opening -> opening.give(enchanted(opening, new ItemStack(Items.SLIME_BALL), "Knockback Slimeball", Enchantments.KNOCKBACK, 2))),
            // Miracle (with emeralds).
            effect("Jerry", Lucky.MIRACLE, Rarity.COMMON, BedWarsLucky::jerry),
            effect("Spicy Sword", Lucky.MIRACLE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.STONE_SWORD)), "Spicy Sword",
                            Enchantments.FIRE_ASPECT, 2))),
            effect("OP chainmail helmet", Lucky.MIRACLE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.CHAINMAIL_HELMET)), "OP Chainmail Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("OP iron helmet", Lucky.MIRACLE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.IRON_HELMET)), "OP Iron Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("OP diamond helmet", Lucky.MIRACLE, Rarity.EPIC,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_HELMET)), "OP Diamond Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("Placeable Wither", Lucky.MIRACLE, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.tagged(Items.WITHER_SKELETON_SKULL, "placeable_wither", "Placeable Wither (Right Click)"))),
            effect("Obsidian Drop", Lucky.MIRACLE, Rarity.EPIC, opening -> opening.give(new ItemStack(Items.OBSIDIAN, 8))),
            effect("Super Star", Lucky.MIRACLE, Rarity.LEGENDARY, BedWarsLucky::superStar));

    private static Effect effect(String name, Lucky block, Rarity rarity, Opener opener) {
        return new Effect(name, block, rarity, opener);
    }

    /**
     * The lucky block a generator drops alongside its resource, if any: island generators a Lucky Block
     * with one iron in 20 and a Promising one with one gold in 6, diamond generators a Fortunate or
     * Offensive one with one diamond in 3, emerald generators a Miracle one with one emerald in 3 (these
     * odds are this mod's choice).
     */
    static @Nullable Lucky drop(Currency currency) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (currency) {
            case IRON -> random.nextInt(20) == 0 ? Lucky.NORMAL : null;
            case GOLD -> random.nextInt(6) == 0 ? Lucky.PROMISING : null;
            case DIAMOND -> random.nextInt(3) == 0 ? (random.nextBoolean() ? Lucky.FORTUNATE : Lucky.OFFENSIVE) : null;
            case EMERALD -> random.nextInt(3) == 0 ? Lucky.MIRACLE : null;
        };
    }

    /** Opens a lucky block a player broke: it is gone, and one effect of its table, by rarity, happens. */
    static void open(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, Lucky lucky) {
        List<Effect> table = EFFECTS.stream().filter(effect -> effect.block() == lucky).toList();
        int total = table.stream().mapToInt(effect -> effect.rarity().weight).sum();
        int pick = ThreadLocalRandom.current().nextInt(total);
        Effect chosen = table.getLast();
        for (Effect effect : table) {
            pick -= effect.rarity().weight;
            if (pick < 0) {
                chosen = effect;
                break;
            }
        }
        apply(game, match, state, player, pos, chosen);
    }

    private static void apply(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, Effect effect) {
        ServerLevel level = match.arena().level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.6F);
        int team = BedWarsGame.teamOf(match, player);
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        effect.opener().open(new Opening(game, match, state, player, pos, team, dye));
        player.sendSystemMessage(Component.literal(effect.block().displayName + ": ").withStyle(effect.block().color)
                .append(Component.literal(effect.name()).withStyle(ChatFormatting.WHITE)));
    }

    /**
     * Opens the effect named {@code effect} for {@code player} at {@code pos}, as if a lucky block of its
     * table had been broken there; returns whether the player is in a Lucky Blocks match and the effect
     * exists. The GameTests open named effects through this.
     */
    public static boolean open(ServerPlayer player, BlockPos pos, String effect) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof BedWarsGame game) || game.mode() != BedWarsMode.LUCKY_BLOCKS) return false;
        State state = game.states.get(match);
        Optional<Effect> found = EFFECTS.stream().filter(candidate -> candidate.name().equals(effect)).findFirst();
        if (state == null || found.isEmpty()) return false;
        apply(game, match, state, player, pos, found.get());
        return true;
    }

    // ---------------------------------------------------------------- effects

    private static ItemStack shop(Opening opening, String id) {
        return BedWarsShop.stack(BedWarsShop.find(id).orElseThrow(), opening.dye(), opening.player().registryAccess());
    }

    private static ItemStack withCount(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }

    private static ItemStack dyed(Item item, DyeColor dye) {
        ItemStack stack = BedWarsShop.unbreakable(new ItemStack(item));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor()));
        return stack;
    }

    private static ItemStack enchanted(Opening opening, ItemStack stack, String name, ResourceKey<Enchantment> enchantment, int level) {
        BedWarsShop.enchant(opening.player().registryAccess(), stack, enchantment, level);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** 32 wool, in eight random colours. */
    private static void rainbowWool(Opening opening) {
        DyeColor[] colours = DyeColor.values();
        for (int index = 0; index < 8; index++) {
            opening.give(new ItemStack(Items.WOOL.pick(colours[ThreadLocalRandom.current().nextInt(colours.length)]), 4));
        }
    }

    /** A hostile wither skeleton, for the unlucky. */
    private static void witherSkeleton(Opening opening) {
        WitherSkeleton skeleton = EntityTypes.WITHER_SKELETON.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (skeleton == null) return;
        skeleton.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        skeleton.addTag(BedWarsGame.ENTITY_TAG);
        skeleton.setTarget(opening.player());
        opening.level().addFreshEntity(skeleton);
        opening.state().spawned.add(skeleton);
    }

    /** The opener's pickaxe and axe both go up a tier, free. */
    private static void toolUpgrade(Opening opening) {
        BedWarsGame.PlayerState bought = opening.state().player(opening.player().getUUID());
        var registries = opening.player().registryAccess();
        bought.pickaxe = Math.min(4, bought.pickaxe + 1);
        bought.axe = Math.min(4, bought.axe + 1);
        BedWarsGame.replace(opening.player(), stack -> stack.is(ItemTags.PICKAXES), BedWarsShop.pickaxe(registries, bought.pickaxe),
                Optional.of(BedWarsHotbar.Category.TOOLS));
        BedWarsGame.replace(opening.player(), stack -> stack.is(ItemTags.AXES), BedWarsShop.axe(registries, bought.axe),
                Optional.of(BedWarsHotbar.Category.TOOLS));
    }

    /** A compass pointing at the nearest enemy bed still standing. */
    private static void bedCompass(Opening opening) {
        Bed nearest = null;
        double best = Double.MAX_VALUE;
        for (TeamState team : opening.state().teams.values()) {
            if (team.number == opening.team()) continue;
            for (Bed bed : team.standing) {
                double distance = bed.foot().distSqr(opening.pos());
                if (distance < best) {
                    best = distance;
                    nearest = bed;
                }
            }
        }
        ItemStack compass = BedWarsShop.tagged(Items.COMPASS, "bed_compass", "Bed Compass");
        if (nearest != null) {
            compass.set(DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(Optional.of(GlobalPos.of(opening.level().dimension(), nearest.foot())), false));
        }
        opening.give(compass);
    }

    /** One more tier of a random team upgrade the team can still buy, free. */
    private static void teamUpgrade(Opening opening) {
        TeamState team = opening.state().teams.get(opening.team());
        if (team == null) return;
        List<Upgrade> open = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            if (team.level(upgrade) < upgrade.tiers() && opening.game().offers(upgrade)) open.add(upgrade);
        }
        if (open.isEmpty()) return;
        Upgrade upgrade = open.get(ThreadLocalRandom.current().nextInt(open.size()));
        team.upgrades.put(upgrade, team.level(upgrade) + 1);
        for (ServerPlayer member : BedWarsGame.members(opening.match(), opening.team())) {
            opening.game().applyUpgrades(opening.match(), opening.state(), member);
            member.sendSystemMessage(Component.literal("Lucky! Your team got " + upgrade.nameAt(team.level(upgrade)) + "!")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** A ring of the team's wool three high around the opener. */
    private static void barrier(Opening opening) {
        BlockPos feet = opening.player().blockPosition();
        BlockState wool = Blocks.WOOL.pick(opening.dye()).defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy < 3; dy++) put(opening, feet.offset(dx, dy, dz), wool);
            }
        }
    }

    /** A cover of the team's wool over each of its standing beds. */
    private static void bedCover(Opening opening) {
        TeamState team = opening.state().teams.get(opening.team());
        if (team == null) return;
        BlockState wool = Blocks.WOOL.pick(opening.dye()).defaultBlockState();
        for (Bed bed : List.copyOf(team.standing)) BedWarsDreams.defend(opening.match(), opening.state(), bed, opening.dye(), List.of(wool));
    }

    /** The lucky block turns into fire where it stood. */
    private static void fire(Opening opening) {
        put(opening, opening.pos(), Blocks.FIRE.defaultBlockState());
    }

    private static void put(Opening opening, BlockPos pos, BlockState block) {
        if (!opening.level().getBlockState(pos).isAir() || !opening.match().arena().canBuild(pos) || BedWarsGame.isBed(opening.state(), pos)) return;
        opening.level().setBlock(pos, block, Block.UPDATE_ALL);
        opening.match().markPlaced(pos);
    }

    /** The team's trap queue fills up with random traps, while its bed stands. */
    private static void trapQueue(Opening opening) {
        TeamState team = opening.state().teams.get(opening.team());
        if (team == null || !team.bedStanding()) return;
        Trap[] traps = Trap.values();
        while (team.traps.size() < BedWarsUpgrades.TRAP_QUEUE) team.traps.add(traps[ThreadLocalRandom.current().nextInt(traps.length)]);
    }

    /** The opener's best sword goes up a tier: wooden to stone, stone to iron, iron to diamond. */
    private static void weaponUpgrade(Opening opening) {
        Item[] tiers = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD};
        var inventory = opening.player().getInventory();
        int best = -1;
        int slot = -1;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            for (int tier = 0; tier < tiers.length; tier++) {
                if (inventory.getItem(index).is(tiers[tier]) && tier > best) {
                    best = tier;
                    slot = index;
                }
            }
        }
        ItemStack sword = BedWarsShop.unbreakable(new ItemStack(tiers[Math.min(tiers.length - 1, best + 1)]));
        if (slot >= 0) inventory.setItem(slot, sword);
        else PlayerUtils.giveOrDrop(opening.player(), sword);
        opening.game().applyUpgrades(opening.match(), opening.state(), opening.player());
    }

    /** A baby zombie that fights for the opener's team for a minute. */
    private static void babyZombie(Opening opening) {
        Zombie zombie = EntityTypes.ZOMBIE.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (zombie == null) return;
        zombie.setBaby(true);
        zombie.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        zombie.setCustomName(BedWarsGame.teamName(opening.match(), opening.team()).append(Component.literal(" Helper").withStyle(ChatFormatting.GRAY)));
        BedWarsGame.own(zombie, opening.team());
        opening.level().addFreshEntity(zombie);
        opening.state().tracked.add(new BedWarsGame.Tracked(zombie, "silverfish", opening.team(), opening.player().getUUID(),
                opening.match().activeTicks() + HELPER_TICKS));
    }

    /** Super Star: ten seconds of Resistance IV, Speed II and Regeneration II. */
    private static void superStar(Opening opening) {
        ServerPlayer player = opening.player();
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 3));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
    }

    // ---------------------------------------------------------------- lucky traps

    /** Lays a lucky trap where the block stood: a carpet of the opener's team colour. */
    private static void trap(Opening opening, String flavour) {
        BlockPos pos = opening.pos();
        if (!opening.level().getBlockState(pos).isAir()) return;
        opening.level().setBlock(pos, Blocks.CARPET.pick(opening.dye()).defaultBlockState(), Block.UPDATE_ALL);
        opening.match().markPlaced(pos);
        opening.state().luckyTraps.put(pos.immutable(), new LuckyTrap(opening.team(), flavour));
    }

    /** Sets off a lucky trap under the first enemy standing on it, and forgets traps whose carpet is gone. */
    static void tickTraps(Match match, State state) {
        ServerLevel level = match.arena().level();
        for (Iterator<Map.Entry<BlockPos, LuckyTrap>> entries = state.luckyTraps.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<BlockPos, LuckyTrap> entry = entries.next();
            BlockPos pos = entry.getKey();
            if (!level.getBlockState(pos).is(net.minecraft.tags.BlockTags.WOOL_CARPETS)) {
                entries.remove();
                continue;
            }
            for (ServerPlayer player : match.alivePlayers()) {
                if (player.isSpectator() || BedWarsGame.teamOf(match, player) == entry.getValue().team() || !player.blockPosition().equals(pos)) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                entries.remove();
                spring(level, player, entry.getValue().flavour());
                break;
            }
        }
    }

    /** What each lucky trap does (strengths and lengths this mod's choice). */
    private static void spring(ServerLevel level, ServerPlayer victim, String flavour) {
        switch (flavour) {
            case "Slowness" -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5 * 20, 2));
            case "damage" -> victim.hurtServer(level, victim.damageSources().magic(), 6.0F);
            case "Arrow Rain" -> {
                for (int index = 0; index < 8; index++) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    Arrow arrow = new Arrow(level, victim.getX() + random.nextDouble(-1.5, 1.5), victim.getY() + 8,
                            victim.getZ() + random.nextDouble(-1.5, 1.5), new ItemStack(Items.ARROW), null);
                    arrow.setDeltaMovement(0, -1.5, 0);
                    arrow.addTag(BedWarsGame.ENTITY_TAG);
                    level.addFreshEntity(arrow);
                }
            }
            case "freeze" -> {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 3 * 20, 6));
                victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 3 * 20, 2));
            }
            default -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 5 * 20, 1));
        }
        victim.sendSystemMessage(Component.literal("You stepped on a lucky trap! (" + flavour + ")").withStyle(ChatFormatting.RED));
        level.playSound(null, victim.blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** An arrow hitting a lucky trap clears it safely. */
    static void arrowHit(Match match, State state, BlockPos pos) {
        if (state.luckyTraps.remove(pos) == null) return;
        ServerLevel level = match.arena().level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 6, 0.2, 0.0, 0.2, 0.0);
    }

    // ---------------------------------------------------------------- Jerry

    /** Jerry comes to trade for two minutes. */
    private static void jerry(Opening opening) {
        MapArena.Point spot = new MapArena.Point("jerry", Vec3.atBottomCenterOf(opening.pos()), opening.player().getYRot() + 180.0F);
        Villager jerry = opening.game().spawnShopkeeper(opening.match(), opening.state(), spot, opening.team(), BedWarsGame.Shopkeeper.JERRY);
        if (jerry == null) return;
        opening.state().tracked.add(new BedWarsGame.Tracked(jerry, "jerry", opening.team(), null, opening.match().activeTicks() + JERRY_TICKS));
    }

    /** Jerry's trades: a Miracle Lucky Block for emeralds. */
    static void openJerry(BedWarsGame game, Match match, ServerPlayer player) {
        Menu menu = new Menu(JERRY, 3);
        boolean affordable = game.available(match, player, Currency.EMERALD) >= JERRY_PRICE;
        Icon icon = Icon.of(Lucky.MIRACLE.block).name(Lucky.MIRACLE.displayName, affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                .text("Jerry trades you a Miracle Lucky Block.").blank()
                .line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(Currency.EMERALD.count(JERRY_PRICE)).withStyle(Currency.EMERALD.color)))
                .blank();
        if (affordable) icon.action("Click to trade!");
        else icon.refusal("You don't have enough Emeralds!");
        menu.set(Menu.slot(1, 4), icon, (clicker, click) -> {
            BedWarsGame.State state = game.states.get(match);
            if (state == null) throw new MatchException("This match has no Jerry.");
            game.spend(match, state, clicker, new BedWarsShop.Cost(Currency.EMERALD, JERRY_PRICE));
            PlayerUtils.giveOrDrop(clicker, Lucky.MIRACLE.item());
            clicker.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
            openJerry(game, match, clicker);
        });
        menu.close();
        menu.open(player);
    }

    // ---------------------------------------------------------------- the Placeable Wither

    /** The Placeable Wither, used: an old wither of the opener's team where they look, for a minute. */
    static boolean placeWither(Match match, State state, ServerPlayer player, BlockPos at) {
        ServerLevel level = player.level();
        WitherBoss wither = EntityTypes.WITHER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (wither == null) return false;
        int team = BedWarsGame.teamOf(match, player);
        wither.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, player.getYRot(), 0.0F);
        wither.setNoAi(true);
        wither.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20.0);
        wither.setHealth(20.0F);
        wither.setCustomName(BedWarsGame.teamName(match, team).append(Component.literal(" Wither").withStyle(ChatFormatting.GRAY)));
        BedWarsGame.own(wither, team);
        level.addFreshEntity(wither);
        state.tracked.add(new BedWarsGame.Tracked(wither, "wither_ally", team, player.getUUID(), match.activeTicks() + WITHER_TICKS));
        return true;
    }

    /**
     * The wither ally floats after its owner and every second and a half strikes the nearest enemy
     * within 12 blocks for two hearts and a moment of Wither (this mod's choice of strength).
     */
    static void tickWither(Match match, Entity entity, int team, @Nullable ServerPlayer owner, int now) {
        if (!(entity instanceof WitherBoss wither)) return;
        if (owner != null && owner.level() == wither.level() && wither.distanceToSqr(owner) > 9.0) {
            Vec3 toward = owner.position().add(0, 2.5, 0).subtract(wither.position()).normalize().scale(0.4);
            wither.setPos(wither.getX() + toward.x, wither.getY() + toward.y, wither.getZ() + toward.z);
        }
        if (now % 30 != 0) return;
        ServerPlayer target = null;
        double best = 12.0 * 12.0;
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator() || BedWarsGame.teamOf(match, player) == team) continue;
            double distance = player.distanceToSqr(wither);
            if (distance < best) {
                best = distance;
                target = player;
            }
        }
        if (target == null) return;
        ServerLevel level = (ServerLevel) wither.level();
        Vec3 from = wither.getEyePosition();
        Vec3 to = target.getEyePosition();
        for (int step = 0; step <= 10; step++) {
            Vec3 at = from.lerp(to, step / 10.0);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        target.invulnerableTime = 0;
        target.hurtServer(level, level.damageSources().mobAttack(wither), 4.0F);
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        level.playSound(null, wither.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.6F, 1.0F);
    }
}
