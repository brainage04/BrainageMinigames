package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
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
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
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
import net.minecraft.world.phys.BlockHitResult;
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
    static final String TRADER = "Resource Trader";
    static final int JERRY_PRICE = 3;
    private static final int JERRY_TICKS = 2 * 60 * 20;
    private static final int HELPER_TICKS = 60 * 20;
    private static final int WITHER_TICKS = 60 * 20;
    private static final int BLITZ_TICKS = 30 * 20;
    private static final Identifier LITTLE = BrainageMinigames.id("lucky_little_big_problem");
    /** The Blitz effects Roulette Blitz picks from. */
    private static final List<String> ROULETTE = List.of("vampire", "gremlin", "invoker", "imprison", "jedi_force", "wither_warrior");

    /** Every effect this mod has, by block, in the order of the announcement's list. */
    static final List<Effect> EFFECTS = List.of(
            // Lucky Block (with iron).
            effect("Wither Skeleton", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::witherSkeleton),
            effect("Explosive Chicken", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::explosiveChicken),
            effect("Flint and Steel", Lucky.NORMAL, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.FLINT_AND_STEEL))),
            effect("Hot Head", Lucky.NORMAL, Rarity.COMMON, opening -> opening.give(tag(dyed(Items.LEATHER_HELMET, DyeColor.RED), "hot_head", "Hot Head"))),
            effect("Ice Bridge Rotation Effect", Lucky.NORMAL, Rarity.COMMON,
                    opening -> opening.give(BedWarsShop.tagged(Items.ICE, "ice_bridge", "Ice Bridge (Right Click)"))),
            effect("Rainbow Wool", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::rainbowWool),
            effect("Splitter Slime", Lucky.NORMAL, Rarity.COMMON, BedWarsLucky::splitterSlime),
            effect("Water Balloon", Lucky.NORMAL, Rarity.COMMON,
                    opening -> opening.give(withCount(BedWarsShop.tagged(Items.SNOWBALL, "water_balloon", "Water Balloon"), 3))),
            effect("Boombox", Lucky.NORMAL, Rarity.RARE, opening -> opening.give(BedWarsShop.tagged(Items.JUKEBOX, "boombox", "Boombox (Right Click)"))),
            effect("Lava Rune", Lucky.NORMAL, Rarity.RARE, opening -> opening.give(BedWarsShop.tagged(Items.MAGMA_CREAM, "lava_rune", "Lava Rune (Right Click)"))),
            effect("Sleepinator", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::sleepinator),
            effect("Fling Bow", Lucky.NORMAL, Rarity.EPIC,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.BOW)), "Fling Bow", Enchantments.PUNCH, 3))),
            effect("Random Team Upgrade", Lucky.NORMAL, Rarity.EPIC, BedWarsLucky::teamUpgrade),
            effect("Resource Trader", Lucky.NORMAL, Rarity.EPIC, opening -> trader(opening, BedWarsGame.Shopkeeper.TRADER)),
            effect("Bridge Eggs", Lucky.NORMAL, Rarity.COMMON, opening -> opening.give(withCount(shop(opening, "bridge_egg"), 2))),
            effect("Fist Upgrade", Lucky.NORMAL, Rarity.COMMON, opening -> opening.player().addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60 * 20, 0))),
            effect("Slime Boots", Lucky.NORMAL, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_BOOTS, DyeColor.LIME), "Slime Boots", Enchantments.FEATHER_FALLING, 4))),
            effect("Trap > Slowness", Lucky.NORMAL, Rarity.COMMON, opening -> trap(opening, "Slowness")),
            effect("Trap > damage", Lucky.NORMAL, Rarity.COMMON, opening -> trap(opening, "damage")),
            effect("Transform block > Gold", Lucky.NORMAL, Rarity.COMMON, opening -> transform(opening, Blocks.GOLD_BLOCK)),
            effect("Transform block > Emerald", Lucky.NORMAL, Rarity.COMMON, opening -> transform(opening, Blocks.EMERALD_BLOCK)),
            effect("Chicken Hat", Lucky.NORMAL, Rarity.RARE, opening -> opening.give(tag(dyed(Items.LEATHER_HELMET, DyeColor.WHITE), "chicken_hat", "Chicken Hat"))),
            effect("Instant Tool Upgrade", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::toolUpgrade),
            effect("Hot Potato", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::hotPotato),
            effect("Trap > Arrow Rain", Lucky.NORMAL, Rarity.RARE, opening -> trap(opening, "Arrow Rain")),
            effect("Chicken Bomb", Lucky.NORMAL, Rarity.RARE,
                    opening -> opening.give(withCount(BedWarsShop.tagged(Items.EGG, "chicken_bomb", "Chicken Bomb"), 2))),
            effect("Bed Compass", Lucky.NORMAL, Rarity.RARE, BedWarsLucky::bedCompass),
            // Promising (with gold).
            effect("Disco Armor", Lucky.PROMISING, Rarity.COMMON, BedWarsLucky::discoArmor),
            effect("Ghast", Lucky.PROMISING, Rarity.COMMON, opening -> ally(opening, EntityTypes.GHAST, "ghast_ally", 45 * 20)),
            effect("Cute Pants", Lucky.PROMISING, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_LEGGINGS, DyeColor.PINK), "Cute Pants", Enchantments.PROTECTION, 2))),
            effect("Instant Barrier", Lucky.PROMISING, Rarity.COMMON, BedWarsLucky::barrier),
            effect("Sugar Cookie Rotation Effect", Lucky.PROMISING, Rarity.COMMON, opening -> opening.give(shop(opening, "sugar_cookie"))),
            effect("Blaze Rider", Lucky.PROMISING, Rarity.RARE, BedWarsLucky::blazeRider),
            effect("Exodus", Lucky.PROMISING, Rarity.RARE, opening -> opening.give(BedWarsShop.tagged(Items.PAPER, "exodus", "Exodus (Right Click)"))),
            effect("Scythe", Lucky.PROMISING, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_HOE)), "Scythe", Enchantments.SHARPNESS, 4))),
            effect("Time Warp Pearl", Lucky.PROMISING, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.tagged(Items.ENDER_EYE, "time_warp_pearl", "Time Warp Pearl (Right Click)"))),
            effect("James Bond Armor", Lucky.PROMISING, Rarity.COMMON, BedWarsLucky::jamesBond),
            effect("Vampire Blitz", Lucky.PROMISING, Rarity.COMMON, opening -> blitz(opening, "vampire")),
            effect("Companion Picker", Lucky.PROMISING, Rarity.COMMON, BedWarsLucky::companion),
            effect("Gremlin Blitz", Lucky.PROMISING, Rarity.RARE, opening -> blitz(opening, "gremlin")),
            effect("Invoker Blitz", Lucky.PROMISING, Rarity.RARE, opening -> blitz(opening, "invoker")),
            effect("Squid boots", Lucky.PROMISING, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_BOOTS, DyeColor.BLACK), "Squid Boots", Enchantments.DEPTH_STRIDER, 3))),
            effect("Spiky Suit", Lucky.PROMISING, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_CHESTPLATE, DyeColor.GREEN), "Spiky Suit", Enchantments.THORNS, 2))),
            effect("Imprison Blitz", Lucky.PROMISING, Rarity.EPIC, opening -> blitz(opening, "imprison")),
            // Fortunate (with diamonds).
            effect("Frog Helmet", Lucky.FORTUNATE, Rarity.COMMON, opening -> opening.give(tag(dyed(Items.LEATHER_HELMET, DyeColor.GREEN), "frog_helmet", "Frog Helmet"))),
            effect("Mystery Meat", Lucky.FORTUNATE, Rarity.COMMON,
                    opening -> opening.give(BedWarsShop.tagged(Items.COOKED_PORKCHOP, "mystery_meat", "Mystery Meat (Right Click)"))),
            effect("Protective Bed Cover", Lucky.FORTUNATE, Rarity.COMMON, BedWarsLucky::bedCover),
            effect("heat-resistant boots", Lucky.FORTUNATE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, dyed(Items.LEATHER_BOOTS, DyeColor.ORANGE), "Heat-Resistant Boots",
                            Enchantments.FIRE_PROTECTION, 4))),
            effect("4x Obsidian", Lucky.FORTUNATE, Rarity.RARE, opening -> opening.give(new ItemStack(Items.OBSIDIAN, 4))),
            effect("Sharp Spoon", Lucky.FORTUNATE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.WOODEN_SHOVEL)), "Sharp Spoon",
                            Enchantments.SHARPNESS, 3))),
            effect("Devil Chicken Bow", Lucky.FORTUNATE, Rarity.EPIC, BedWarsLucky::devilBow),
            effect("Gravity Gun", Lucky.FORTUNATE, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.tagged(Items.ECHO_SHARD, "gravity_gun", "Gravity Gun (Right Click)"))),
            effect("Little Big Problem", Lucky.FORTUNATE, Rarity.COMMON, BedWarsLucky::littleBigProblem),
            effect("Endstone Drop", Lucky.FORTUNATE, Rarity.RARE, opening -> opening.give(new ItemStack(Items.END_STONE, 32))),
            effect("Trap > freeze", Lucky.FORTUNATE, Rarity.RARE, opening -> trap(opening, "freeze")),
            effect("Beam me up scotty!", Lucky.FORTUNATE, Rarity.RARE,
                    opening -> opening.give(BedWarsShop.tagged(Items.AMETHYST_SHARD, "beam_me_up", "Beam Me Up Scotty! (Right Click)"))),
            effect("Roulette Blitz", Lucky.FORTUNATE, Rarity.EPIC, opening -> blitz(opening, ROULETTE.get(ThreadLocalRandom.current().nextInt(ROULETTE.size())))),
            effect("Grappling hook", Lucky.FORTUNATE, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.unbreakable(BedWarsShop.tagged(Items.FISHING_ROD, "grappling_hook", "Grappling Hook")))),
            effect("Wildcard", Lucky.FORTUNATE, Rarity.LEGENDARY, BedWarsLucky::wildcard),
            effect("Shotgun Blitz", Lucky.FORTUNATE, Rarity.LEGENDARY, opening -> opening.give(BedWarsGuns.stack(BedWarsGuns.Gun.SHOTGUN))),
            // Offensive (with diamonds).
            effect("5x Cobweb", Lucky.OFFENSIVE, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.COBWEB, 5))),
            effect("Rush Pearl", Lucky.OFFENSIVE, Rarity.COMMON, opening -> opening.give(new ItemStack(Items.ENDER_PEARL))),
            effect("Snowman Rotation Effect", Lucky.OFFENSIVE, Rarity.COMMON, opening -> ally(opening, EntityTypes.SNOW_GOLEM, "snowman_ally", 60 * 20)),
            effect("Battle Axe", Lucky.OFFENSIVE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.IRON_AXE)), "Battle Axe",
                            Enchantments.SHARPNESS, 2))),
            effect("Instant Trap Queue", Lucky.OFFENSIVE, Rarity.RARE, BedWarsLucky::trapQueue),
            effect("Instant Weapon Upgrade", Lucky.OFFENSIVE, Rarity.RARE, BedWarsLucky::weaponUpgrade),
            effect("Sword Of Justice", Lucky.OFFENSIVE, Rarity.RARE, opening -> opening.give(swordOfJustice())),
            effect("Telebow", Lucky.OFFENSIVE, Rarity.RARE,
                    opening -> opening.give(withCount(BedWarsShop.unbreakable(BedWarsShop.tagged(Items.BOW, "telebow", "Telebow")), 1))),
            effect("Baby Zombie Helper", Lucky.OFFENSIVE, Rarity.EPIC, BedWarsLucky::babyZombie),
            effect("Knockback Slimeball", Lucky.OFFENSIVE, Rarity.EPIC,
                    opening -> opening.give(enchanted(opening, new ItemStack(Items.SLIME_BALL), "Knockback Slimeball", Enchantments.KNOCKBACK, 2))),
            effect("Trap > Poison", Lucky.OFFENSIVE, Rarity.COMMON, opening -> trap(opening, "Poison")),
            effect("Transform block > Fire", Lucky.OFFENSIVE, Rarity.COMMON, BedWarsLucky::fire),
            effect("Transform block > Lava", Lucky.OFFENSIVE, Rarity.COMMON, BedWarsLucky::lava),
            effect("Jedi Force Blitz", Lucky.OFFENSIVE, Rarity.RARE, opening -> blitz(opening, "jedi_force")),
            effect("Wither Warrior Blitz", Lucky.OFFENSIVE, Rarity.RARE, opening -> blitz(opening, "wither_warrior")),
            effect("Dreadlord Skull", Lucky.OFFENSIVE, Rarity.RARE,
                    opening -> opening.give(withCount(BedWarsShop.tagged(Items.WITHER_SKELETON_SKULL, "dreadlord_skull", "Dreadlord Skull (Right Click)"), 2))),
            effect("Axe Of Perun", Lucky.OFFENSIVE, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.unbreakable(BedWarsShop.tagged(Items.GOLDEN_AXE, "axe_of_perun", "Axe of Perun")))),
            effect("Golden Knight (from castle)", Lucky.OFFENSIVE, Rarity.EPIC, BedWarsLucky::goldenKnight),
            effect("Apocalypse Blitz", Lucky.OFFENSIVE, Rarity.LEGENDARY, opening -> blitz(opening, "apocalypse")),
            // Miracle (with emeralds).
            effect("Magic Toy Stick", Lucky.MIRACLE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, new ItemStack(Items.STICK), "Magic Toy Stick", Enchantments.KNOCKBACK, 2))),
            effect("Spicy Sword", Lucky.MIRACLE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.STONE_SWORD)), "Spicy Sword",
                            Enchantments.FIRE_ASPECT, 2))),
            effect("OP chainmail helmet", Lucky.MIRACLE, Rarity.COMMON,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.CHAINMAIL_HELMET)), "OP Chainmail Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("OP iron helmet", Lucky.MIRACLE, Rarity.RARE,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.IRON_HELMET)), "OP Iron Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("Diamond Sheep", Lucky.MIRACLE, Rarity.RARE, opening -> ally(opening, EntityTypes.SHEEP, "diamond_sheep", 60 * 20)),
            effect("OP diamond helmet", Lucky.MIRACLE, Rarity.EPIC,
                    opening -> opening.give(enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_HELMET)), "OP Diamond Helmet",
                            Enchantments.PROTECTION, 4))),
            effect("Super Star", Lucky.MIRACLE, Rarity.LEGENDARY, BedWarsLucky::superStar),
            effect("Jerry", Lucky.MIRACLE, Rarity.COMMON, opening -> trader(opening, BedWarsGame.Shopkeeper.JERRY)),
            effect("Vault Hunter Blitz", Lucky.MIRACLE, Rarity.RARE, BedWarsLucky::vaultHunter),
            effect("Voidmaker", Lucky.MIRACLE, Rarity.EPIC, opening -> opening.give(BedWarsShop.tagged(Items.ENDER_EYE, "voidmaker", "Voidmaker (Right Click)"))),
            effect("Placeable Wither", Lucky.MIRACLE, Rarity.EPIC,
                    opening -> opening.give(BedWarsShop.tagged(Items.WITHER_SKELETON_SKULL, "placeable_wither", "Placeable Wither (Right Click)"))),
            effect("Obsidian Drop", Lucky.MIRACLE, Rarity.EPIC, opening -> opening.give(new ItemStack(Items.OBSIDIAN, 8))),
            effect("Transform Block > Bedrock", Lucky.MIRACLE, Rarity.LEGENDARY,
                    opening -> opening.level().setBlock(opening.pos(), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL)),
            effect("Nuke Blitz", Lucky.MIRACLE, Rarity.LEGENDARY,
                    opening -> opening.give(BedWarsShop.tagged(Items.RECOVERY_COMPASS, "nuke", "Nuke Targeting Device (Right Click)"))),
            effect("Placeable bed", Lucky.MIRACLE, Rarity.LEGENDARY,
                    opening -> opening.give(BedWarsShop.tagged(Items.BED.pick(opening.dye()), "placeable_bed", "Placeable Bed (Right Click)"))));

    private static Effect effect(String name, Lucky block, Rarity rarity, Opener opener) {
        return new Effect(name, block, rarity, opener);
    }

    /** The names of every effect, as the README's table lists them; the GameTests open each one. */
    public static List<String> effectNames() {
        return EFFECTS.stream().map(Effect::name).toList();
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

    // ---------------------------------------------------------------- Jerry and the Resource Trader

    /** Jerry or the Resource Trader comes to trade where the block stood, for two minutes. */
    private static void trader(Opening opening, BedWarsGame.Shopkeeper kind) {
        MapArena.Point spot = new MapArena.Point(kind.title, Vec3.atBottomCenterOf(opening.pos()), opening.player().getYRot() + 180.0F);
        Villager trader = opening.game().spawnShopkeeper(opening.match(), opening.state(), spot, opening.team(), kind);
        if (trader == null) return;
        opening.state().tracked.add(new BedWarsGame.Tracked(trader, "jerry", opening.team(), null, opening.match().activeTicks() + JERRY_TICKS));
    }

    /** One trade: what it costs and what it gives. */
    private record Trade(BedWarsShop.Cost cost, ItemStack goods) {}

    /**
     * Jerry trades a Miracle Lucky Block for 3 emeralds; the Resource Trader 32 iron for 4 gold, 16 gold
     * for an emerald and 4 diamonds for an emerald (all this mod's choice).
     */
    private static List<Trade> trades(BedWarsGame.Shopkeeper kind) {
        if (kind == BedWarsGame.Shopkeeper.JERRY) return List.of(new Trade(new BedWarsShop.Cost(Currency.EMERALD, JERRY_PRICE), Lucky.MIRACLE.item()));
        return List.of(new Trade(new BedWarsShop.Cost(Currency.IRON, 32), new ItemStack(Items.GOLD_INGOT, 4)),
                new Trade(new BedWarsShop.Cost(Currency.GOLD, 16), new ItemStack(Items.EMERALD)),
                new Trade(new BedWarsShop.Cost(Currency.DIAMOND, 4), new ItemStack(Items.EMERALD)));
    }

    static void openTrader(BedWarsGame game, Match match, ServerPlayer player, BedWarsGame.Shopkeeper kind) {
        Menu menu = new Menu(kind.title, 3);
        List<Trade> trades = trades(kind);
        int first = 4 - (trades.size() - 1);
        for (int index = 0; index < trades.size(); index++) {
            Trade trade = trades.get(index);
            boolean affordable = game.available(match, player, trade.cost().currency()) >= trade.cost().amount();
            Icon icon = Icon.of(trade.goods().copy()).name(trade.goods().getHoverName().getString(), affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .count(trade.goods().getCount())
                    .line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(trade.cost().describe()).withStyle(trade.cost().currency().color)))
                    .blank();
            if (affordable) icon.action("Click to trade!");
            else icon.refusal("You don't have enough " + trade.cost().currency().displayName + "!");
            menu.set(Menu.slot(1, first + 2 * index), icon, (clicker, click) -> {
                BedWarsGame.State state = game.states.get(match);
                if (state == null) throw new MatchException("This match has no traders.");
                game.spend(match, state, clicker, trade.cost());
                PlayerUtils.giveOrDrop(clicker, trade.goods().copy());
                clicker.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
                openTrader(game, match, clicker, kind);
            });
        }
        menu.close();
        menu.open(player);
    }

    // ---------------------------------------------------------------- allies

    /** A mob of the opener's team: the wither, Ghast, Snowman and Diamond Sheep, gone after {@code ticks}. */
    private static <T extends net.minecraft.world.entity.Mob> @Nullable T ally(Opening opening, net.minecraft.world.entity.EntityType<T> type,
            String kind, int ticks) {
        return ally(opening.match(), opening.state(), opening.player(), Vec3.atBottomCenterOf(opening.pos()), type, kind, ticks);
    }

    private static <T extends net.minecraft.world.entity.Mob> @Nullable T ally(Match match, State state, ServerPlayer owner, Vec3 at,
            net.minecraft.world.entity.EntityType<T> type, String kind, int ticks) {
        ServerLevel level = match.arena().level();
        T mob = type.create(level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return null;
        int team = BedWarsGame.teamOf(match, owner);
        boolean flies = mob instanceof WitherBoss || mob instanceof net.minecraft.world.entity.monster.Ghast;
        mob.snapTo(at.x, at.y + (flies ? 2.0 : 0.0), at.z, owner.getYRot(), 0.0F);
        mob.setNoAi(true);
        mob.setNoGravity(flies);
        mob.setPersistenceRequired();
        if (mob instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) sheep.setColor(DyeColor.LIGHT_BLUE);
        String name = switch (kind) {
            case "wither_ally" -> "Wither";
            case "ghast_ally" -> "Ghast";
            case "snowman_ally" -> "Snowman";
            default -> "Diamond Sheep";
        };
        mob.setCustomName(BedWarsGame.teamName(match, team).append(Component.literal(" " + name).withStyle(ChatFormatting.GRAY)));
        mob.setCustomNameVisible(true);
        BedWarsGame.own(mob, team);
        level.addFreshEntity(mob);
        state.tracked.add(new BedWarsGame.Tracked(mob, kind, team, owner.getUUID(), match.activeTicks() + ticks));
        return mob;
    }

    /** The Placeable Wither, used: an old wither of the opener's team (10 hearts) where they look, for a minute. */
    private static boolean placeWither(Match match, State state, ServerPlayer player, BlockPos at) {
        WitherBoss wither = ally(match, state, player, Vec3.atBottomCenterOf(at), EntityTypes.WITHER, "wither_ally", WITHER_TICKS);
        if (wither == null) return false;
        wither.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        wither.setHealth(20.0F);
        return true;
    }

    /** The nearest player of another team than {@code team} within {@code range} of {@code from}, or null. */
    private static @Nullable ServerPlayer nearestEnemy(Match match, int team, Vec3 from, double range) {
        ServerPlayer nearest = null;
        double best = range * range;
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator() || BedWarsGame.teamOf(match, player) == team) continue;
            double distance = player.position().distanceToSqr(from);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void follow(Entity mob, @Nullable ServerPlayer owner, double height) {
        if (owner == null || owner.level() != mob.level() || mob.position().distanceToSqr(owner.position()) < 9.0) return;
        Vec3 toward = owner.position().add(0, height, 0).subtract(mob.position()).normalize().scale(0.4);
        mob.setPos(mob.getX() + toward.x, mob.getY() + toward.y, mob.getZ() + toward.z);
    }

    /**
     * Runs what Lucky Blocks summoned and threw, as the game follows them; returns whether {@code tracked}
     * is finished, or null for kinds that are not Lucky Blocks'. Allies' strengths are this mod's choice:
     * the wither strikes the nearest enemy within 12 blocks for two hearts and Wither every 1.5 seconds,
     * the Ghast shoots a fireball at one within 20 every 3 seconds, the Snowman a snowball at one within
     * 15 every 0.75 seconds, and the Diamond Sheep drops a diamond every 10 seconds.
     */
    static @Nullable Boolean tickTracked(BedWarsGame game, Match match, State state, BedWarsGame.Tracked tracked, Vec3 last, int now,
            List<BedWarsGame.Tracked> summoned) {
        Entity entity = tracked.entity();
        boolean gone = !entity.isAlive();
        ServerPlayer owner = tracked.owner() == null ? null : match.server().getPlayerList().getPlayer(tracked.owner());
        ServerLevel level = match.arena().level();
        switch (tracked.kind()) {
            case "wither_ally", "ghast_ally", "snowman_ally", "diamond_sheep", "companion" -> {
                if (gone || now >= tracked.expires()) {
                    entity.discard();
                    return true;
                }
                switch (tracked.kind()) {
                    case "wither_ally" -> {
                        follow(entity, owner, 2.5);
                        ServerPlayer target = now % 30 == 0 ? nearestEnemy(match, tracked.team(), entity.position(), 12.0) : null;
                        if (target != null) {
                            trail(level, ParticleTypes.SMOKE, entity.position().add(0, 2.5, 0), target.getEyePosition());
                            target.invulnerableTime = 0;
                            target.hurtServer(level, level.damageSources().mobAttack((WitherBoss) entity), 4.0F);
                            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                            level.playSound(null, entity.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.6F, 1.0F);
                        }
                    }
                    case "ghast_ally" -> {
                        follow(entity, owner, 4.0);
                        ServerPlayer target = now % 60 == 0 ? nearestEnemy(match, tracked.team(), entity.position(), 20.0) : null;
                        if (target != null) {
                            Vec3 from = entity.position().add(0, 2.0, 0);
                            Vec3 aim = target.getEyePosition().subtract(from).normalize();
                            LargeFireball fireball = new LargeFireball(level, (net.minecraft.world.entity.LivingEntity) entity, aim, 0);
                            fireball.setPos(from.x + aim.x * 3, from.y + aim.y * 3, from.z + aim.z * 3);
                            fireball.setDeltaMovement(aim.scale(1.0));
                            fireball.addTag(BedWarsGame.ENTITY_TAG);
                            BedWarsGame.own(fireball, tracked.team());
                            level.addFreshEntity(fireball);
                            summoned.add(new BedWarsGame.Tracked(fireball, "fireball", tracked.team(), tracked.owner(), now + 200));
                        }
                    }
                    case "snowman_ally" -> {
                        ServerPlayer target = now % 15 == 0 ? nearestEnemy(match, tracked.team(), entity.position(), 15.0) : null;
                        if (target != null && entity instanceof net.minecraft.world.entity.LivingEntity golem) {
                            Snowball snowball = new Snowball(level, golem, new ItemStack(Items.SNOWBALL));
                            Vec3 aim = target.getEyePosition().subtract(golem.getEyePosition());
                            snowball.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.1, aim.z, 1.6F, 2.0F);
                            level.addFreshEntity(snowball);
                        }
                    }
                    case "diamond_sheep" -> {
                        if (now % 200 == 0) {
                            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(Items.DIAMOND)));
                        }
                    }
                    default -> {}
                }
                return false;
            }
            case "blaze_rider" -> {
                if (gone || now >= tracked.expires() || owner == null || owner.getVehicle() != entity) {
                    entity.discard();
                    return true;
                }
                Vec3 next = entity.position().add(owner.getLookAngle().scale(0.5));
                entity.setYRot(owner.getYRot());
                entity.setPos(next.x, next.y, next.z);
                return false;
            }
            case "explosive_chicken" -> {
                if (now >= tracked.expires()) {
                    if (!gone) game.explode(match, state, entity, owner, entity.position(), 2.0F);
                    entity.discard();
                    return true;
                }
                return gone;
            }
            case "chicken_bomb", "dreadlord", "water_balloon" -> {
                if (!gone && now < tracked.expires()) return false;
                entity.discard();
                if (gone) landed(game, match, state, tracked, last, owner);
                return true;
            }
            default -> {
                return null;
            }
        }
    }

    /** A thrown Chicken Bomb, Dreadlord Skull or Water Balloon lands at {@code at}. */
    private static void landed(BedWarsGame game, Match match, State state, BedWarsGame.Tracked tracked, Vec3 at, @Nullable ServerPlayer owner) {
        ServerLevel level = match.arena().level();
        switch (tracked.kind()) {
            case "chicken_bomb" -> game.explode(match, state, tracked.entity(), owner, at, 2.5F);
            case "dreadlord" -> {
                game.explode(match, state, tracked.entity(), owner, at, 2.0F);
                for (ServerPlayer player : match.alivePlayers()) {
                    if (!player.isSpectator() && BedWarsGame.teamOf(match, player) != tracked.team() && player.position().distanceTo(at) < 4.0) {
                        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 5 * 20, 1));
                    }
                }
            }
            default -> {
                BlockPos pos = BlockPos.containing(at);
                if (level.getBlockState(pos).isAir() && match.arena().canBuild(pos) && !BedWarsGame.isBed(state, pos)) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                    later(state, match.activeTicks() + 60, () -> {
                        if (level.getBlockState(pos).is(Blocks.WATER)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    });
                }
            }
        }
    }

    private static void trail(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 from, Vec3 to) {
        for (int step = 0; step <= 10; step++) {
            Vec3 at = from.lerp(to, step / 10.0);
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---------------------------------------------------------------- timers and buffs

    /** Something Lucky Blocks does later: at match tick {@code at}. */
    record Timer(int at, Runnable action) {}

    private static void later(State state, int at, Runnable action) {
        state.luckyTimers.add(new Timer(at, action));
    }

    private static void buff(State state, ServerPlayer player, String name, int until) {
        state.luckyBuffs.computeIfAbsent(player.getUUID(), ignored -> new java.util.HashMap<>()).put(name, until);
    }

    private static boolean has(State state, ServerPlayer player, String name, int now) {
        Map<String, Integer> buffs = state.luckyBuffs.get(player.getUUID());
        return buffs != null && buffs.getOrDefault(name, 0) > now;
    }

    /** Lucky Blocks' own every-tick work: lucky traps, things that happen later, and what worn lucky items do. */
    static void tick(BedWarsGame game, Match match, State state, int now) {
        if (now % 5 == 0 && !state.luckyTraps.isEmpty()) tickTraps(match, state);
        if (!state.luckyTimers.isEmpty()) {
            List<Timer> due = state.luckyTimers.stream().filter(timer -> timer.at() <= now).toList();
            state.luckyTimers.removeAll(due);
            due.forEach(timer -> timer.action().run());
        }
        if (now % 20 != 0) return;
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator()) continue;
            String head = BedWarsShop.ability(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            if (head.equals("frog_helmet")) player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 1, true, false));
            if (head.equals("chicken_hat") && now % 400 == 0 && player.getInventory().countItem(Items.EGG) < 2) {
                PlayerUtils.giveOrDrop(player, BedWarsShop.stack(BedWarsShop.find("bridge_egg").orElseThrow(), DyeColor.WHITE, player.registryAccess()));
                player.playSound(SoundEvents.CHICKEN_EGG, 1.0F, 1.0F);
            }
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                ItemStack worn = player.getItemBySlot(slot);
                if (BedWarsShop.ability(worn).equals("disco_armor")) {
                    worn.set(DataComponents.DYED_COLOR, new DyedItemColor(DyeColor.values()[ThreadLocalRandom.current().nextInt(16)].getTextureDiffuseColor()));
                }
            }
        }
    }

    /** A player leaving the match keeps no Lucky Blocks size change. */
    static void release(ServerPlayer player) {
        var scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(LITTLE);
    }

    /** Hits with or against lucky items: Vampire and Wither Warrior Blitz, the Sleepinator, the Axe of Perun and Hot Head. */
    static void damaged(Match match, State state, ServerPlayer victim, ServerPlayer attacker) {
        int now = match.activeTicks();
        if (has(state, attacker, "vampire", now)) attacker.heal(2.0F);
        if (has(state, attacker, "wither_warrior", now)) victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        ItemStack held = attacker.getMainHandItem();
        switch (BedWarsShop.ability(held)) {
            case "sleepinator" -> {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                held.setDamageValue(held.getDamageValue() + 1);
                if (held.getDamageValue() >= held.getMaxDamage()) held.shrink(1);
            }
            case "axe_of_perun" -> {
                if (!attacker.getCooldowns().isOnCooldown(held)) {
                    lightning(victim);
                    victim.invulnerableTime = 0;
                    victim.hurtServer(victim.level(), victim.damageSources().lightningBolt(), 3.0F);
                    attacker.getCooldowns().addCooldown(held, 100);
                }
            }
            default -> {}
        }
        if (BedWarsShop.ability(victim.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)).equals("hot_head")) {
            attacker.igniteForSeconds(3.0F);
        }
    }

    private static void lightning(Entity at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(at.level(), EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(at.position());
        bolt.setVisualOnly(true);
        at.level().addFreshEntity(bolt);
    }

    // ---------------------------------------------------------------- lucky items in use

    /** Lucky items that would otherwise be placed or used on the block clicked; they are used instead. */
    static final java.util.Set<String> USED_ON_BLOCKS = java.util.Set.of("ice_bridge", "boombox", "placeable_wither", "dreadlord_skull",
            "placeable_bed", "time_warp_pearl", "voidmaker", "nuke", "chicken_bomb");

    /**
     * Right-clicks with lucky items: used-up items are spent when they work; the Gravity Gun and the
     * Grappling Hook stay, and guns (the Shotgun Blitz) fire as in Armed. {@link InteractionResult#PASS}
     * for anything else.
     */
    static InteractionResult use(BedWarsGame game, Match match, State state, ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.level();
        int team = BedWarsGame.teamOf(match, player);
        int now = match.activeTicks();
        String ability = BedWarsShop.ability(stack);
        if (BedWarsGuns.isGun(ability)) return BedWarsGuns.fire(game, match, state, player, stack);
        if (ability.equals("grappling_hook")) {
            // Reeling in while the hook is out pulls the player towards it; vanilla then reels the line in.
            if (player.fishing != null) {
                Vec3 pull = player.fishing.position().subtract(player.position());
                player.setDeltaMovement(pull.scale(0.25).add(0, 0.4, 0));
                player.hurtMarked = true;
                player.resetFallDistance();
            }
            return InteractionResult.PASS;
        }
        if (ability.equals("gravity_gun")) {
            ServerPlayer target = player.getCooldowns().isOnCooldown(stack) ? null : lookedAt(match, player, team, 15.0);
            if (target == null) return InteractionResult.FAIL;
            Vec3 pull = player.position().subtract(target.position()).normalize().scale(1.4).add(0, 0.4, 0);
            target.push(pull.x, pull.y, pull.z);
            target.hurtMarked = true;
            player.getCooldowns().addCooldown(stack, 60);
            return InteractionResult.SUCCESS;
        }
        Boolean used = switch (ability) {
            case "ice_bridge" -> iceBridge(match, state, player);
            case "water_balloon", "chicken_bomb", "dreadlord_skull" -> {
                Item shown = ability.equals("water_balloon") ? Items.SNOWBALL : ability.equals("chicken_bomb") ? Items.EGG : Items.WITHER_SKELETON_SKULL;
                Snowball thrown = new Snowball(level, player, new ItemStack(shown));
                thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.5F);
                thrown.addTag(BedWarsGame.ENTITY_TAG);
                level.addFreshEntity(thrown);
                String kind = ability.equals("dreadlord_skull") ? "dreadlord" : ability;
                state.tracked.add(new BedWarsGame.Tracked(thrown, kind, team, player.getUUID(), now + 20 * 20));
                yield true;
            }
            case "boombox" -> {
                for (ServerPlayer mate : match.alivePlayers()) {
                    if (!mate.isSpectator() && BedWarsGame.teamOf(match, mate) == team && mate.distanceTo(player) <= 8.0) {
                        mate.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 0));
                        mate.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 15 * 20, 0));
                    }
                }
                level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.0, player.getZ(), 12, 2.0, 0.5, 2.0, 1.0);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                yield true;
            }
            case "lava_rune" -> {
                for (ServerPlayer enemy : match.alivePlayers()) {
                    if (!enemy.isSpectator() && BedWarsGame.teamOf(match, enemy) != team && enemy.distanceTo(player) <= 6.0) enemy.igniteForSeconds(4.0F);
                }
                level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 20, 3.0, 0.3, 3.0, 0.0);
                yield true;
            }
            case "exodus" -> {
                PlayerUtils.teleport(player, level, ((MapArena) match.arena()).spawnsOf(team).getFirst().position(), player.getYRot());
                yield true;
            }
            case "beam_me_up" -> {
                List<Vec3> middle = state.layout.emeralds();
                if (middle.isEmpty()) yield false;
                PlayerUtils.teleport(player, level, middle.getFirst().add(1.5, 0, 0), player.getYRot());
                yield true;
            }
            case "time_warp_pearl" -> {
                Vec3 from = player.position();
                java.util.UUID id = player.getUUID();
                later(state, now + 100, () -> {
                    ServerPlayer warped = match.server().getPlayerList().getPlayer(id);
                    if (warped != null && match.isAlive(id) && !warped.isSpectator()) {
                        PlayerUtils.teleport(warped, level, from, warped.getYRot());
                        warped.resetFallDistance();
                    }
                });
                player.sendSystemMessage(Component.literal("You will warp back here in 5 seconds!").withStyle(ChatFormatting.LIGHT_PURPLE));
                yield true;
            }
            case "mystery_meat" -> {
                List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> effects = List.of(MobEffects.SPEED, MobEffects.REGENERATION,
                        MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.POISON, MobEffects.SLOWNESS, MobEffects.NAUSEA);
                player.addEffect(new MobEffectInstance(effects.get(ThreadLocalRandom.current().nextInt(effects.size())), 10 * 20, 1));
                player.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
                yield true;
            }
            case "voidmaker" -> {
                ServerPlayer target = nearestEnemy(match, team, player.position(), 15.0);
                if (target == null) yield false;
                BlockPos feet = target.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -3, -2), feet.offset(2, -1, 2))) {
                    if (match.isPlacedBlock(pos) && !BedWarsGame.isBed(state, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY(), target.getZ(), 40, 2.0, 0.5, 2.0, 0.5);
                yield true;
            }
            case "nuke" -> {
                BlockHitResult hit = target(player, 64.0);
                if (hit == null) yield false;
                Vec3 at = Vec3.atCenterOf(hit.getBlockPos());
                match.broadcast(Component.literal("A nuke is coming down!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                java.util.UUID id = player.getUUID();
                later(state, now + 100, () -> {
                    ServerPlayer nuker = match.server().getPlayerList().getPlayer(id);
                    game.explode(match, state, nuker == null ? player : nuker, nuker, at, 8.0F);
                });
                yield true;
            }
            case "placeable_wither" -> {
                BlockHitResult hit = target(player, player.blockInteractionRange());
                yield hit != null && placeWither(match, state, player, hit.getBlockPos().relative(hit.getDirection()));
            }
            case "placeable_bed" -> placeBed(match, state, player);
            default -> null;
        };
        if (used == null) return InteractionResult.PASS;
        if (!used) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    private static @Nullable BlockHitResult target(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getLookAngle().scale(range)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit : null;
    }

    /** The enemy the player looks at within {@code range}, or null. */
    private static @Nullable ServerPlayer lookedAt(Match match, ServerPlayer player, int team, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        ServerPlayer best = null;
        double bestDot = 0.97;
        for (ServerPlayer other : match.alivePlayers()) {
            if (other.isSpectator() || BedWarsGame.teamOf(match, other) == team) continue;
            Vec3 to = other.getEyePosition().subtract(eye);
            if (to.length() > range) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = other;
            }
        }
        return best;
    }

    /** Ice Bridge: a three-wide bridge of ice ten blocks ahead at the feet, melting five seconds later. */
    private static boolean iceBridge(Match match, State state, ServerPlayer player) {
        ServerLevel level = player.level();
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        BlockPos feet = player.blockPosition().below();
        List<BlockPos> built = new ArrayList<>();
        for (int ahead = 1; ahead <= 10; ahead++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos pos = feet.relative(facing, ahead).relative(side, across);
                if (!level.getBlockState(pos).isAir() || !match.arena().canBuild(pos) || BedWarsGame.isBed(state, pos)) continue;
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
                match.markPlaced(pos);
                built.add(pos.immutable());
            }
        }
        if (built.isEmpty()) return false;
        later(state, match.activeTicks() + 100, () -> {
            for (BlockPos pos : built) if (level.getBlockState(pos).is(Blocks.ICE)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        return true;
    }

    /**
     * Placeable Bed: a new bed of the team where the player looks, inside its base and before Bed
     * Destruction; while it stands the team respawns again.
     */
    private static boolean placeBed(Match match, State state, ServerPlayer player) {
        BlockHitResult hit = target(player, player.blockInteractionRange());
        TeamState team = state.teams.get(BedWarsGame.teamOf(match, player));
        if (hit == null || team == null || state.bedsGone) return false;
        BlockPos foot = hit.getBlockPos().relative(hit.getDirection());
        Direction facing = player.getDirection();
        BlockPos head = foot.relative(facing);
        ServerLevel level = player.level();
        if (!state.layout.inBase(team.number, Vec3.atCenterOf(foot)) || !level.getBlockState(foot).isAir() || !level.getBlockState(head).isAir()
                || !match.arena().canBuild(foot) || !match.arena().canBuild(head)) {
            player.sendSystemMessage(Component.literal("Place the bed on free ground in your base!").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Bed bed = new Bed(team.number, "", foot, facing);
        team.beds.add(bed);
        team.standing.add(bed);
        BedWarsGame.placeBed(level, bed, match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE));
        for (ServerPlayer member : BedWarsGame.members(match, team.number)) {
            member.sendSystemMessage(Component.literal("Your team has a bed again!").withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    /** A Telebow arrow that lands on a block takes its shooter there. */
    static void arrowLanded(Match match, net.minecraft.world.entity.projectile.Projectile projectile, BlockHitResult hit) {
        if (!(projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow)
                || arrow.getWeaponItem() == null || !BedWarsShop.ability(arrow.getWeaponItem()).equals("telebow")
                || !(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        PlayerUtils.teleport(shooter, shooter.level(), at, shooter.getYRot());
        shooter.resetFallDistance();
        arrow.discard();
    }

    /** A Transform block (gold or emerald) breaks into resources: 3 gold, or an emerald. */
    static boolean breakTransform(Match match, State state, BlockPos pos, BlockState block) {
        if (!state.luckyTransforms.remove(pos)) return false;
        ServerLevel level = match.arena().level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack drop = block.is(Blocks.GOLD_BLOCK) ? new ItemStack(Items.GOLD_INGOT, 3) : new ItemStack(Items.EMERALD);
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, drop));
        return true;
    }

    // ---------------------------------------------------------------- the rest of the openers

    private static ItemStack tag(ItemStack stack, String ability, String name) {
        return BedWarsShop.tag(stack, ability, name);
    }

    private static ItemStack swordOfJustice() {
        return BedWarsShop.unbreakable(BedWarsShop.tagged(Items.GOLDEN_SWORD, "sword_of_justice", "Sword of Justice"));
    }

    /** A chicken of the opener's team that blows up three seconds later. */
    private static void explosiveChicken(Opening opening) {
        net.minecraft.world.entity.animal.chicken.Chicken chicken = EntityTypes.CHICKEN.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (chicken == null) return;
        chicken.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        BedWarsGame.own(chicken, opening.team());
        opening.level().addFreshEntity(chicken);
        opening.state().tracked.add(new BedWarsGame.Tracked(chicken, "explosive_chicken", opening.team(), opening.player().getUUID(),
                opening.match().activeTicks() + 60));
    }

    /** A big hostile slime, for the unlucky. */
    private static void splitterSlime(Opening opening) {
        net.minecraft.world.entity.monster.cubemob.Slime slime = EntityTypes.SLIME.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (slime == null) return;
        slime.setSize(3, true);
        slime.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        slime.addTag(BedWarsGame.ENTITY_TAG);
        slime.setTarget(opening.player());
        opening.level().addFreshEntity(slime);
        opening.state().spawned.add(slime);
    }

    /** A stick that blinds and slows whoever it hits, three times. */
    private static void sleepinator(Opening opening) {
        ItemStack stick = BedWarsShop.tagged(Items.STICK, "sleepinator", "Sleepinator");
        stick.set(DataComponents.MAX_STACK_SIZE, 1);
        stick.set(DataComponents.MAX_DAMAGE, 3);
        stick.set(DataComponents.DAMAGE, 0);
        opening.give(stick);
    }

    /** The lucky block becomes a block of gold or emerald, which breaks into resources. */
    private static void transform(Opening opening, Block block) {
        opening.level().setBlock(opening.pos(), block.defaultBlockState(), Block.UPDATE_ALL);
        opening.match().markPlaced(opening.pos());
        opening.state().luckyTransforms.add(opening.pos().immutable());
    }

    /** A potato that goes off in the opener's pocket eight seconds later, unless they got rid of it. */
    private static void hotPotato(Opening opening) {
        ServerPlayer player = opening.player();
        PlayerUtils.giveOrDrop(player, BedWarsShop.tagged(Items.BAKED_POTATO, "hot_potato", "Hot Potato"));
        java.util.UUID id = player.getUUID();
        later(opening.state(), opening.match().activeTicks() + 160, () -> {
            ServerPlayer holder = opening.match().server().getPlayerList().getPlayer(id);
            if (holder == null || !holder.getInventory().contains(stack -> BedWarsShop.ability(stack).equals("hot_potato"))) return;
            for (int slot = 0; slot < holder.getInventory().getContainerSize(); slot++) {
                if (BedWarsShop.ability(holder.getInventory().getItem(slot)).equals("hot_potato")) holder.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            opening.game().explode(opening.match(), opening.state(), holder, null, holder.position(), 2.0F);
        });
    }

    /** Leather armour in random colours that keep changing. */
    private static void discoArmor(Opening opening) {
        for (Item item : List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)) {
            opening.give(enchanted(opening, tag(dyed(item, DyeColor.PINK), "disco_armor", "Disco Armor"), "Disco Armor", Enchantments.PROTECTION, 1));
        }
    }

    /** Black leather armour with Protection II. */
    private static void jamesBond(Opening opening) {
        for (Item item : List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)) {
            opening.give(enchanted(opening, dyed(item, DyeColor.BLACK), "James Bond Armor", Enchantments.PROTECTION, 2));
        }
    }

    /** A bow with Flame and Power II. */
    private static void devilBow(Opening opening) {
        ItemStack bow = enchanted(opening, BedWarsShop.unbreakable(new ItemStack(Items.BOW)), "Devil Chicken Bow", Enchantments.FLAME, 1);
        BedWarsShop.enchant(opening.player().registryAccess(), bow, Enchantments.POWER, 2);
        opening.give(bow);
    }

    /** A blaze the opener rides for 20 seconds, flying where they look. */
    private static void blazeRider(Opening opening) {
        net.minecraft.world.entity.monster.Blaze blaze = EntityTypes.BLAZE.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (blaze == null) return;
        ServerPlayer player = opening.player();
        blaze.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        blaze.setNoAi(true);
        blaze.setNoGravity(true);
        blaze.setInvulnerable(true);
        BedWarsGame.own(blaze, opening.team());
        opening.level().addFreshEntity(blaze);
        player.startRiding(blaze, true, true);
        opening.state().tracked.add(new BedWarsGame.Tracked(blaze, "blaze_rider", opening.team(), player.getUUID(), opening.match().activeTicks() + 20 * 20));
    }

    /** The opener is half their size for 30 seconds. */
    private static void littleBigProblem(Opening opening) {
        var scale = opening.player().getAttribute(Attributes.SCALE);
        if (scale == null) return;
        scale.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LITTLE, -0.5,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        later(opening.state(), opening.match().activeTicks() + 30 * 20, () -> scale.removeModifier(LITTLE));
    }

    /** A tamed wolf of the opener's team, for two minutes. */
    private static void companion(Opening opening) {
        net.minecraft.world.entity.animal.wolf.Wolf wolf = EntityTypes.WOLF.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (wolf == null) return;
        wolf.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        wolf.tame(opening.player());
        BedWarsGame.own(wolf, opening.team());
        opening.level().addFreshEntity(wolf);
        opening.state().tracked.add(new BedWarsGame.Tracked(wolf, "companion", opening.team(), opening.player().getUUID(),
                opening.match().activeTicks() + 2 * 60 * 20));
    }

    /** The Golden Knight: the Sword of Justice and a horse in golden armour. */
    private static void goldenKnight(Opening opening) {
        opening.give(swordOfJustice());
        net.minecraft.world.entity.animal.equine.Horse horse = EntityTypes.HORSE.create(opening.level(), EntitySpawnReason.TRIGGERED);
        if (horse == null) return;
        horse.snapTo(opening.pos().getX() + 0.5, opening.pos().getY(), opening.pos().getZ() + 0.5, 0.0F, 0.0F);
        horse.setTamed(true);
        horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
        horse.addTag(BedWarsGame.ENTITY_TAG);
        opening.level().addFreshEntity(horse);
        opening.state().spawned.add(horse);
    }

    /** Lava where the block stood, gone five seconds later. */
    private static void lava(Opening opening) {
        BlockPos pos = opening.pos();
        if (!opening.level().getBlockState(pos).isAir()) return;
        opening.level().setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        later(opening.state(), opening.match().activeTicks() + 100, () -> {
            if (opening.level().getBlockState(pos).is(Blocks.LAVA)) opening.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
    }

    /** Vault Hunter: a wooden sword, 32 iron and 8 gold. */
    private static void vaultHunter(Opening opening) {
        opening.give(BedWarsShop.unbreakable(new ItemStack(Items.WOODEN_SWORD)));
        opening.give(new ItemStack(Items.IRON_INGOT, 32));
        opening.give(new ItemStack(Items.GOLD_INGOT, 8));
    }

    /** Wildcard: the effect of any other lucky block. */
    private static void wildcard(Opening opening) {
        List<Effect> others = EFFECTS.stream().filter(effect -> !effect.name().equals("Wildcard")).toList();
        Effect effect = others.get(ThreadLocalRandom.current().nextInt(others.size()));
        effect.opener().open(opening);
        opening.player().sendSystemMessage(Component.literal("Wildcard: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(effect.name()).withStyle(ChatFormatting.WHITE)));
    }

    /**
     * The Blitz effects, after Blitz Survival Games' kits (strengths this mod's choice): Vampire (30 s
     * of hits healing a heart), Wither Warrior (30 s of hits giving Wither), Gremlin (four silverfish of
     * the team for 15 s), Invoker (lightning on every enemy within 8 blocks), Imprison (cobwebs round the
     * nearest enemy within 10), Jedi Force (enemies within 6 thrown away) and Apocalypse (a fireball
     * falling on every enemy within 25).
     */
    private static void blitz(Opening opening, String kind) {
        Match match = opening.match();
        ServerPlayer player = opening.player();
        ServerLevel level = opening.level();
        int now = match.activeTicks();
        switch (kind) {
            case "vampire", "wither_warrior" -> buff(opening.state(), player, kind, now + BLITZ_TICKS);
            case "gremlin" -> {
                for (int index = 0; index < 4; index++) {
                    net.minecraft.world.entity.monster.Silverfish silverfish = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);
                    if (silverfish == null) continue;
                    silverfish.snapTo(player.getX(), player.getY(), player.getZ(), ThreadLocalRandom.current().nextFloat() * 360.0F, 0.0F);
                    BedWarsGame.own(silverfish, opening.team());
                    level.addFreshEntity(silverfish);
                    opening.state().tracked.add(new BedWarsGame.Tracked(silverfish, "silverfish", opening.team(), player.getUUID(), now + 15 * 20));
                }
            }
            case "invoker" -> {
                for (ServerPlayer enemy : match.alivePlayers()) {
                    if (enemy.isSpectator() || BedWarsGame.teamOf(match, enemy) == opening.team() || enemy.distanceTo(player) > 8.0) continue;
                    lightning(enemy);
                    enemy.invulnerableTime = 0;
                    enemy.hurtServer(level, level.damageSources().lightningBolt(), 3.0F);
                }
            }
            case "imprison" -> {
                ServerPlayer enemy = nearestEnemy(match, opening.team(), player.position(), 10.0);
                if (enemy == null) return;
                BlockPos feet = enemy.blockPosition();
                for (BlockPos pos : List.of(feet, feet.above(), feet.north(), feet.south(), feet.east(), feet.west())) {
                    put(opening, pos, Blocks.COBWEB.defaultBlockState());
                }
            }
            case "jedi_force" -> {
                for (ServerPlayer enemy : match.alivePlayers()) {
                    if (enemy.isSpectator() || BedWarsGame.teamOf(match, enemy) == opening.team() || enemy.distanceTo(player) > 6.0) continue;
                    Vec3 away = enemy.position().subtract(player.position()).normalize().scale(1.6).add(0, 0.5, 0);
                    enemy.push(away.x, away.y, away.z);
                    enemy.hurtMarked = true;
                }
            }
            default -> {
                for (ServerPlayer enemy : match.alivePlayers()) {
                    if (enemy.isSpectator() || BedWarsGame.teamOf(match, enemy) == opening.team() || enemy.distanceTo(player) > 25.0) continue;
                    LargeFireball fireball = new LargeFireball(level, player, new Vec3(0, -1, 0), 0);
                    fireball.setPos(enemy.getX(), enemy.getY() + 12, enemy.getZ());
                    fireball.setDeltaMovement(0, -1.0, 0);
                    fireball.addTag(BedWarsGame.ENTITY_TAG);
                    level.addFreshEntity(fireball);
                    opening.state().tracked.add(new BedWarsGame.Tracked(fireball, "fireball", opening.team(), player.getUUID(), now + 100));
                }
            }
        }
        player.sendSystemMessage(Component.literal("Blitz!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }
}
