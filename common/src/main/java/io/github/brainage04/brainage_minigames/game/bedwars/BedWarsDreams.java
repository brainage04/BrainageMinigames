package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.Generator;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.TeamState;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the Bed Wars Dream modes that change the core game rather than add to it: Rush,
 * Voidless, Swappage, One Block and Lucky Blocks. Numbers the sources do not give are this mod's
 * choice and say so.
 */
final class BedWarsDreams {
    private BedWarsDreams() {}

    // ---------------------------------------------------------------- bed defences (Rush, Voidless)

    /**
     * Covers {@code bed} with layers of blocks, innermost first, as Rush's and Voidless's ready-made
     * defences: each layer is every free block one step further out from the bed (sideways or up).
     */
    static void defend(Match match, State state, Bed bed, DyeColor dye, List<BlockState> layers) {
        ServerLevel level = match.arena().level();
        Set<BlockPos> inner = new HashSet<>(List.of(bed.foot(), bed.head()));
        Set<BlockPos> covered = new HashSet<>(inner);
        for (BlockState layer : layers) {
            Set<BlockPos> next = new HashSet<>();
            for (BlockPos pos : inner) {
                for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
                    BlockPos out = pos.relative(side);
                    if (covered.contains(out) || out.getY() < bed.foot().getY()) continue;
                    next.add(out);
                }
            }
            for (BlockPos pos : next) {
                covered.add(pos);
                if (!level.getBlockState(pos).isAir() || !match.arena().canBuild(pos)) continue;
                level.setBlock(pos, layer, Block.UPDATE_CLIENTS);
                match.markPlaced(pos);
            }
            inner = next;
        }
    }

    /** Wood, then the team's wool, then blast-proof glass: Rush's and Voidless's defence. */
    static List<BlockState> defenceLayers(DyeColor dye) {
        return List.of(Blocks.OAK_PLANKS.defaultBlockState(), Blocks.WOOL.pick(dye).defaultBlockState(),
                Blocks.STAINED_GLASS.pick(dye).defaultBlockState());
    }

    static void defendAll(Match match, State state) {
        for (TeamState team : state.teams.values()) {
            DyeColor dye = match.teamNumbered(team.number).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
            for (Bed bed : team.standing) defend(match, state, bed, dye, defenceLayers(dye));
        }
    }

    // ---------------------------------------------------------------- Rush

    /** Rush: every generator at its highest tier from the start, and the beds already defended. */
    static void rushStart(Match match, State state) {
        for (Generator generator : state.generators) {
            generator.tier = 3;
            generator.timer = Math.min(generator.timer, generator.interval());
        }
        for (TeamState team : state.teams.values()) team.upgrades.put(Upgrade.DRAGON_BUFF, 1);
        defendAll(match, state);
    }

    /** Rush's permanent Speed I (this mod's choice of level). */
    static void rushSpeed(ServerPlayer player) {
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        if (speed == null || speed.getDuration() < 40 && speed.getAmplifier() == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, true, false));
        }
    }

    /** Blocks a Rush bridge reaches out from placed wool, in the direction the player faces. */
    static final int RUSH_BRIDGE = 5;

    /**
     * Rush's expanding bridges: wool placed while bridging is on grows five blocks further in the
     * direction the player faces.
     */
    static void rushBridge(Match match, State state, ServerPlayer player, BlockPos pos) {
        ServerLevel level = match.arena().level();
        BlockState placed = level.getBlockState(pos);
        if (!placed.is(net.minecraft.tags.BlockTags.WOOL) || state.rushBridgingOff.contains(player.getUUID())) return;
        Direction facing = player.getDirection();
        for (int step = 1; step <= RUSH_BRIDGE; step++) {
            BlockPos next = pos.relative(facing, step);
            if (!level.getBlockState(next).isAir() || !match.arena().canBuild(next)) break;
            level.setBlock(next, placed, Block.UPDATE_ALL);
            match.markPlaced(next);
        }
    }

    /** A left-click with wool in hand turns Rush's bridging on or off. */
    static void rushToggle(State state, ServerPlayer player) {
        if (!player.getMainHandItem().is(net.minecraft.tags.ItemTags.WOOL)) return;
        UUID id = player.getUUID();
        boolean off = !state.rushBridgingOff.remove(id);
        if (off) state.rushBridgingOff.add(id);
        player.sendSystemMessage(Component.literal("Bridge building " + (off ? "disabled" : "enabled") + "!")
                .withStyle(off ? ChatFormatting.RED : ChatFormatting.GREEN), true);
    }

    // ---------------------------------------------------------------- Swappage

    /** Ticks until the next swap: a random time between the match's {@code swap_min_seconds} and {@code swap_max_seconds}. */
    static int nextSwap(Match match) {
        int min = match.settings().get(BedWarsGame.SWAP_MIN_SECONDS) * 20;
        int max = Math.max(min, match.settings().get(BedWarsGame.SWAP_MAX_SECONDS) * 20);
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Swappage: teams swap places in pairs, each player with one of the other team's, teams that still
     * have a bed only with each other and the others among themselves; a team left over stays.
     */
    static void swap(Match match, State state) {
        List<Integer> withBeds = new ArrayList<>();
        List<Integer> without = new ArrayList<>();
        for (TeamState team : state.teams.values()) {
            if (team.eliminated || players(match, team.number).isEmpty()) continue;
            (team.bedStanding() ? withBeds : without).add(team.number);
        }
        for (List<Integer> group : List.of(withBeds, without)) {
            Collections.shuffle(group);
            for (int index = 0; index + 1 < group.size(); index += 2) swapTeams(match, group.get(index), group.get(index + 1));
        }
    }

    private static void swapTeams(Match match, int first, int second) {
        List<ServerPlayer> ones = players(match, first);
        List<ServerPlayer> others = players(match, second);
        for (int index = 0; index < Math.min(ones.size(), others.size()); index++) {
            ServerPlayer one = ones.get(index);
            ServerPlayer other = others.get(index);
            Vec3 at = one.position();
            float yaw = one.getYRot();
            PlayerUtils.teleport(one, match.arena().level(), other.position(), other.getYRot());
            PlayerUtils.teleport(other, match.arena().level(), at, yaw);
            one.resetFallDistance();
            other.resetFallDistance();
        }
        for (ServerPlayer player : ones) announceSwap(match, player, second);
        for (ServerPlayer player : others) announceSwap(match, player, first);
    }

    private static void announceSwap(Match match, ServerPlayer player, int with) {
        BedWarsGame.title(List.of(player), Component.literal("SWAP!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.literal("You swapped with ").withStyle(ChatFormatting.WHITE).append(BedWarsGame.teamName(match, with)));
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
    }

    private static List<ServerPlayer> players(Match match, int team) {
        return match.alivePlayers().stream()
                .filter(player -> !player.isSpectator() && BedWarsGame.teamOf(match, player) == team).toList();
    }

    // ---------------------------------------------------------------- One Block

    /** Ticks between One Block's random items: every 3 seconds, then every 4 after ten minutes. */
    static int oneBlockInterval(int now) {
        return now < 10 * 60 * 20 ? 60 : 80;
    }

    private record Weighted(int weight, ItemStack stack) {}

    private static final List<Weighted> ONE_BLOCK = List.of(
            new Weighted(14, new ItemStack(Items.OAK_PLANKS, 4)),
            new Weighted(14, new ItemStack(Items.WOOL.white(), 4)),
            new Weighted(8, new ItemStack(Items.END_STONE, 2)),
            new Weighted(6, new ItemStack(Items.COBBLESTONE, 4)),
            new Weighted(5, new ItemStack(Items.LADDER, 2)),
            new Weighted(5, new ItemStack(Items.IRON_INGOT, 4)),
            new Weighted(4, new ItemStack(Items.GOLD_INGOT, 2)),
            new Weighted(3, new ItemStack(Items.STONE_SWORD)),
            new Weighted(2, new ItemStack(Items.IRON_SWORD)),
            new Weighted(1, new ItemStack(Items.DIAMOND_SWORD)),
            new Weighted(3, new ItemStack(Items.STONE_PICKAXE)),
            new Weighted(2, new ItemStack(Items.IRON_AXE)),
            new Weighted(2, new ItemStack(Items.SHEARS)),
            new Weighted(4, new ItemStack(Items.BREAD, 2)),
            new Weighted(3, new ItemStack(Items.GOLDEN_APPLE)),
            new Weighted(2, new ItemStack(Items.BOW)),
            new Weighted(3, new ItemStack(Items.ARROW, 4)),
            new Weighted(2, new ItemStack(Items.ENDER_PEARL)),
            new Weighted(2, new ItemStack(Items.TNT)),
            new Weighted(2, new ItemStack(Items.WATER_BUCKET)),
            new Weighted(2, new ItemStack(Items.SNOWBALL, 4)),
            new Weighted(2, new ItemStack(Items.CHAINMAIL_CHESTPLATE)),
            new Weighted(1, new ItemStack(Items.IRON_CHESTPLATE)),
            new Weighted(1, new ItemStack(Items.OBSIDIAN)),
            new Weighted(2, new ItemStack(Items.FISHING_ROD)),
            new Weighted(1, new ItemStack(Items.SLIME_BLOCK, 2)));

    /** Gives every player still in the game one random item or block, as One Block's islands do. */
    static void oneBlockItems(Match match, State state) {
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator() || state.respawning.containsKey(player.getUUID())) continue;
            ItemStack stack = roll(ONE_BLOCK).copy();
            if (stack.is(Items.WOOL.white())) {
                DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
                stack = new ItemStack(Items.WOOL.pick(dye), stack.getCount());
            }
            if (stack.isDamageableItem()) BedWarsShop.unbreakable(stack);
            PlayerUtils.giveOrDrop(player, stack);
            player.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
        }
    }

    private static ItemStack roll(List<Weighted> table) {
        int total = table.stream().mapToInt(Weighted::weight).sum();
        int pick = ThreadLocalRandom.current().nextInt(total);
        for (Weighted entry : table) {
            pick -= entry.weight();
            if (pick < 0) return entry.stack();
        }
        return table.getLast().stack();
    }

    // ---------------------------------------------------------------- Lucky Blocks

    /** The five lucky blocks, after Hypixel's Lucky Blocks v2: which generator drops each and its block. */
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
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal(displayName).withStyle(style -> style.withColor(color).withItalic(false)));
            return stack;
        }

        static Lucky of(BlockState state) {
            for (Lucky lucky : values()) if (state.is(lucky.block)) return lucky;
            return null;
        }
    }

    /**
     * The lucky block a generator drops alongside its resource, if any: island generators a Lucky Block
     * with one iron in 20 and a Promising one with one gold in 6, diamond generators a Fortunate or
     * Offensive one with one diamond in 3, emerald generators a Miracle one with one emerald in 3 (these
     * odds are this mod's choice).
     */
    static Lucky luckyDrop(Currency currency) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (currency) {
            case IRON -> random.nextInt(20) == 0 ? Lucky.NORMAL : null;
            case GOLD -> random.nextInt(6) == 0 ? Lucky.PROMISING : null;
            case DIAMOND -> random.nextInt(3) == 0 ? (random.nextBoolean() ? Lucky.FORTUNATE : Lucky.OFFENSIVE) : null;
            case EMERALD -> random.nextInt(3) == 0 ? Lucky.MIRACLE : null;
        };
    }

    private static List<Weighted> table(Lucky lucky, net.minecraft.core.HolderLookup.Provider registries) {
        return switch (lucky) {
            case NORMAL -> List.of(
                    new Weighted(10, new ItemStack(Items.WOOL.white(), 32)), new Weighted(8, new ItemStack(Items.IRON_INGOT, 16)),
                    new Weighted(6, BedWarsShop.unbreakable(new ItemStack(Items.STONE_SWORD))), new Weighted(6, new ItemStack(Items.GOLDEN_APPLE, 2)),
                    new Weighted(5, new ItemStack(Items.END_STONE, 16)), new Weighted(4, shop("speed_potion", registries)),
                    new Weighted(4, shop("fireball", registries)), new Weighted(3, new ItemStack(Items.ARROW, 8)),
                    new Weighted(3, BedWarsShop.unbreakable(new ItemStack(Items.BOW))), new Weighted(4, ItemStack.EMPTY));
            case PROMISING -> List.of(
                    new Weighted(8, BedWarsShop.unbreakable(new ItemStack(Items.IRON_SWORD))), new Weighted(6, new ItemStack(Items.TNT, 2)),
                    new Weighted(6, shop("bridge_egg", registries)), new Weighted(6, new ItemStack(Items.GOLDEN_APPLE, 3)),
                    new Weighted(5, shop("jump_potion", registries)), new Weighted(5, new ItemStack(Items.GOLD_INGOT, 12)),
                    new Weighted(4, shop("popup_tower", registries)), new Weighted(4, shop("knockback_stick", registries)),
                    new Weighted(3, ItemStack.EMPTY));
            case FORTUNATE -> List.of(
                    new Weighted(6, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_SWORD))), new Weighted(6, new ItemStack(Items.ENDER_PEARL, 2)),
                    new Weighted(5, shop("invisibility_potion", registries)), new Weighted(5, shop("bow_power", registries)),
                    new Weighted(5, new ItemStack(Items.DIAMOND, 6)), new Weighted(4, new ItemStack(Items.OBSIDIAN, 4)),
                    new Weighted(4, new ItemStack(Items.EMERALD, 3)));
            case OFFENSIVE -> List.of(
                    new Weighted(6, new ItemStack(Items.TNT, 4)), new Weighted(6, withCount(shop("fireball", registries), 3)),
                    new Weighted(5, withCount(shop("bedbug", registries), 2)), new Weighted(4, shop("dream_defender", registries)),
                    new Weighted(4, shop("bow_power_punch", registries)), new Weighted(4, new ItemStack(Items.ARROW, 16)));
            case MIRACLE -> List.of(
                    new Weighted(5, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_SWORD))), new Weighted(5, new ItemStack(Items.EMERALD, 8)),
                    new Weighted(5, new ItemStack(Items.ENDER_PEARL, 4)), new Weighted(4, new ItemStack(Items.DIAMOND, 16)),
                    new Weighted(4, withCount(shop("invisibility_potion", registries), 2)), new Weighted(3, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        };
    }

    private static ItemStack shop(String id, net.minecraft.core.HolderLookup.Provider registries) {
        return BedWarsShop.stack(BedWarsShop.find(id).orElseThrow(), DyeColor.WHITE, registries);
    }

    private static ItemStack withCount(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }

    /**
     * Opens a lucky block a player broke: its contents drop where it stood, or one of its events
     * happens instead (the empty entries): a Normal or Promising one may strike lightning or let out
     * two zombies; better ones always give something.
     */
    static void open(Match match, State state, ServerPlayer player, BlockPos pos, Lucky lucky) {
        ServerLevel level = match.arena().level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.6F);
        ItemStack stack = roll(table(lucky, player.registryAccess())).copy();
        if (stack.is(Items.WOOL.white())) {
            DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
            stack = new ItemStack(Items.WOOL.pick(dye), stack.getCount());
        }
        if (!stack.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
            level.addFreshEntity(drop);
            player.sendSystemMessage(Component.literal(lucky.displayName + ": ").withStyle(lucky.color)
                    .append(Component.literal(stack.getCount() + "x " + stack.getHoverName().getString()).withStyle(ChatFormatting.WHITE)));
            return;
        }
        if (ThreadLocalRandom.current().nextBoolean()) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(Vec3.atBottomCenterOf(pos));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            player.hurtServer(level, player.damageSources().lightningBolt(), 4.0F);
            player.sendSystemMessage(Component.literal(lucky.displayName + ": Struck by lightning!").withStyle(ChatFormatting.RED));
        } else {
            for (int index = 0; index < 2; index++) {
                Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                if (zombie == null) continue;
                zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
                zombie.addTag(BedWarsGame.ENTITY_TAG);
                zombie.setTarget(player);
                level.addFreshEntity(zombie);
                state.spawned.add(zombie);
            }
            player.sendSystemMessage(Component.literal(lucky.displayName + ": Zombies!").withStyle(ChatFormatting.RED));
        }
    }

    static Item luckyItem(Lucky lucky) {
        return lucky.block.asItem();
    }

    /** Whether {@code team} has any player in it at all, for modes that skip empty teams. */
    static boolean manned(Match match, MatchTeam team) {
        return !team.members().isEmpty();
    }
}
