package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lucky Block SkyWars: yellow glazed terracotta blocks stand on every island and around the mid;
 * breaking one rolls an {@link Outcome} from a weighted table (good items, bad events and harmless
 * fun) instead of dropping the block. Each refill puts back the lucky blocks that were broken.
 */
public final class SkyWarsLuckyBlocks {
    public static final Block BLOCK = Blocks.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW);

    /** Lucky blocks on each team's island, and around the mid. */
    static final int PER_ISLAND = 2;
    static final int AT_MID = 4;
    /** Where around an island's spawn (or the mid's lobby point) lucky blocks may stand, in order of preference. */
    private static final List<int[]> ISLAND_OFFSETS = List.of(new int[] {3, 0}, new int[] {-3, 0}, new int[] {0, 3},
            new int[] {0, -3}, new int[] {2, 2}, new int[] {-2, -2}, new int[] {2, -2}, new int[] {-2, 2});
    private static final List<int[]> MID_OFFSETS = List.of(new int[] {4, 0}, new int[] {-4, 0}, new int[] {0, 4},
            new int[] {0, -4}, new int[] {3, 3}, new int[] {-3, -3}, new int[] {3, -3}, new int[] {-3, 3});

    public enum Kind {
        GOOD("Lucky! ", ChatFormatting.GREEN),
        BAD("Unlucky! ", ChatFormatting.RED),
        FUN("Lucky Block: ", ChatFormatting.GOLD);

        final String prefix;
        final ChatFormatting color;

        Kind(String prefix, ChatFormatting color) {
            this.prefix = prefix;
            this.color = color;
        }
    }

    /** What breaking a lucky block does; weights add up to 100, so each is a percentage. */
    public enum Outcome {
        DIAMOND_ARMOR(8, Kind.GOOD, "A Protection I diamond armour piece."),
        GOLDEN_APPLES(7, Kind.GOOD, "Three golden apples."),
        SHARP_SWORD(6, Kind.GOOD, "A Sharpness II diamond sword."),
        ENDER_PEARLS(6, Kind.GOOD, "Two ender pearls."),
        BUILDING_BLOCKS(6, Kind.GOOD, "32 oak planks."),
        POWER_BOW(5, Kind.GOOD, "A Power II bow and 16 arrows."),
        HEALING_POTIONS(5, Kind.GOOD, "Two splash potions of Instant Health II."),
        ENCHANTING(4, Kind.GOOD, "16 bottles o' enchanting and an enchanted book."),
        TNT_KIT(4, Kind.GOOD, "Four TNT and a flint and steel."),
        LUCKY_SWORD(2, Kind.GOOD, "The Lucky Sword: a Sharpness V, Knockback II golden sword."),
        ENCHANTED_GOLDEN_APPLE(1, Kind.GOOD, "An enchanted golden apple."),
        TOTEM(1, Kind.GOOD, "A totem of undying."),
        LIGHTNING(6, Kind.BAD, "Lightning strikes you."),
        ZOMBIES(6, Kind.BAD, "Three zombies climb out."),
        PRIMED_TNT(5, Kind.BAD, "It was lit TNT. Run!"),
        BLINDNESS(5, Kind.BAD, "Blindness and Slowness II for 8 seconds."),
        LEVITATION(4, Kind.BAD, "Levitation II for 3 seconds."),
        COBWEBS(4, Kind.BAD, "Cobwebs wrap around you."),
        FIREWORKS(4, Kind.FUN, "A firework show and a cake."),
        WOLF_PACK(4, Kind.FUN, "Two wolves join your side."),
        CHICKENS(4, Kind.FUN, "Six chickens and 16 eggs."),
        ANVIL(3, Kind.FUN, "Look up!");

        public final int weight;
        public final Kind kind;
        public final String description;

        Outcome(int weight, Kind kind, String description) {
            this.weight = weight;
            this.kind = kind;
            this.description = description;
        }
    }

    private static final int TOTAL_WEIGHT = java.util.Arrays.stream(Outcome.values()).mapToInt(outcome -> outcome.weight).sum();

    private SkyWarsLuckyBlocks() {}

    /** Picks an outcome by weight. */
    public static Outcome roll(RandomSource random) {
        int pick = random.nextInt(TOTAL_WEIGHT);
        for (Outcome outcome : Outcome.values()) {
            pick -= outcome.weight;
            if (pick < 0) return outcome;
        }
        throw new IllegalStateException("Lucky block weights changed while rolling");
    }

    /**
     * Places the map's lucky blocks once it is pasted: {@link #PER_ISLAND} on each team's island
     * beside its first spawn and {@link #AT_MID} around the lobby point, each on the first solid
     * ground of its column with room above.
     */
    static void place(SkyWarsMatch state, MapArena arena) {
        ServerLevel level = arena.level();
        for (int team = 1; team <= arena.teamSlots(); team++) {
            List<Arena.Spawn> spawns = arena.spawnsOf(team);
            if (!spawns.isEmpty()) {
                findSpots(state, arena, BlockPos.containing(spawns.getFirst().position()), ISLAND_OFFSETS, PER_ISLAND);
            }
        }
        findSpots(state, arena, BlockPos.containing(arena.lobbyPosition()), MID_OFFSETS, AT_MID);
        for (BlockPos pos : state.luckySpots) {
            level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            state.luckyBlocks.add(pos);
        }
    }

    private static void findSpots(SkyWarsMatch state, MapArena arena, BlockPos centre, List<int[]> offsets, int wanted) {
        ServerLevel level = arena.level();
        int found = 0;
        for (int[] offset : offsets) {
            if (found == wanted) return;
            BlockPos.MutableBlockPos pos = centre.offset(offset[0], 2, offset[1]).mutable();
            for (int depth = 0; depth < 12; depth++, pos.move(Direction.DOWN)) {
                BlockPos below = pos.below();
                BlockState ground = level.getBlockState(below);
                if (ground.isAir()) continue;
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && ground.isFaceSturdy(level, below, Direction.UP) && !ground.hasBlockEntity()
                        && !state.cage.contains(pos.immutable()) && arena.canBuild(pos)) {
                    state.luckySpots.add(pos.immutable());
                    found++;
                }
                break;
            }
        }
    }

    /** Puts back every broken lucky block whose spot is empty and free of entities; refills call it. */
    static void restore(SkyWarsMatch state, ServerLevel level) {
        for (BlockPos pos : state.luckySpots) {
            if (!state.luckyBlocks.contains(pos) && level.getBlockState(pos).isAir()
                    && level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos)).isEmpty()) {
                level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                state.luckyBlocks.add(pos);
            }
        }
    }

    /**
     * When {@code pos} is a lucky block, removes it without drops and runs a rolled outcome for the
     * player who broke it; returns whether it was one.
     */
    static boolean broken(SkyWarsMatch state, Match match, ServerPlayer player, BlockPos pos) {
        if (!state.luckyBlocks.remove(pos)) return false;
        ServerLevel level = player.level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        apply(roll(player.getRandom()), state, match, player, pos);
        return true;
    }

    /** Runs one outcome for the player who broke the lucky block at {@code pos}. */
    public static void apply(Outcome outcome, SkyWarsMatch state, Match match, ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        var registries = player.registryAccess();
        level.playSound(null, pos, outcome.kind == Kind.BAD ? SoundEvents.WITHER_AMBIENT : SoundEvents.PLAYER_LEVELUP,
                SoundSource.BLOCKS, 0.6F, 1.2F);
        player.sendSystemMessage(Component.literal(outcome.kind.prefix).withStyle(outcome.kind.color, ChatFormatting.BOLD)
                .append(Component.literal(outcome.description).withStyle(outcome.kind.color)));
        switch (outcome) {
            case DIAMOND_ARMOR -> {
                List<Item> pieces = List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                ItemStack piece = new ItemStack(pieces.get(random.nextInt(pieces.size())));
                piece.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 1);
                pop(level, pos, piece);
            }
            case GOLDEN_APPLES -> pop(level, pos, new ItemStack(Items.GOLDEN_APPLE, 3));
            case SHARP_SWORD -> {
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
                pop(level, pos, sword);
            }
            case ENDER_PEARLS -> pop(level, pos, new ItemStack(Items.ENDER_PEARL, 2));
            case BUILDING_BLOCKS -> pop(level, pos, new ItemStack(Items.OAK_PLANKS, 32));
            case POWER_BOW -> {
                ItemStack bow = new ItemStack(Items.BOW);
                bow.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 2);
                pop(level, pos, bow);
                pop(level, pos, new ItemStack(Items.ARROW, 16));
            }
            case HEALING_POTIONS -> pop(level, pos, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING).copyWithCount(2));
            case ENCHANTING -> {
                pop(level, pos, new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
                pop(level, pos, SkyWarsKits.randomBook(registries, random, 3));
            }
            case TNT_KIT -> {
                pop(level, pos, new ItemStack(Items.TNT, 4));
                pop(level, pos, new ItemStack(Items.FLINT_AND_STEEL));
            }
            case LUCKY_SWORD -> {
                ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
                var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
                sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5);
                sword.enchant(enchantments.getOrThrow(Enchantments.KNOCKBACK), 2);
                sword.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Sword").withStyle(style -> style.withItalic(false)
                        .withColor(ChatFormatting.YELLOW)));
                pop(level, pos, sword);
            }
            case ENCHANTED_GOLDEN_APPLE -> pop(level, pos, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            case TOTEM -> pop(level, pos, new ItemStack(Items.TOTEM_OF_UNDYING));
            case LIGHTNING -> {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.snapTo(player.getX(), player.getY(), player.getZ());
                    level.addFreshEntity(bolt);
                }
            }
            case ZOMBIES -> {
                for (int count = 0; count < 3; count++) {
                    spawn(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(pos).add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5));
                }
            }
            case PRIMED_TNT -> {
                PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, null);
                tnt.setFuse(40);
                level.addFreshEntity(tnt);
            }
            case BLINDNESS -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8 * 20, 1));
            }
            case LEVITATION -> player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 3 * 20, 1));
            case COBWEBS -> {
                BlockPos feet = player.blockPosition();
                for (BlockPos web : List.of(feet, feet.above())) {
                    if (level.getBlockState(web).isAir() && match.arena().canBuild(web)) {
                        level.setBlockAndUpdate(web, Blocks.COBWEB.defaultBlockState());
                    }
                }
            }
            case FIREWORKS -> {
                for (int count = 0; count < 3; count++) {
                    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                    rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                            FireworkExplosion.Shape.values()[random.nextInt(FireworkExplosion.Shape.values().length)],
                            IntList.of(DyeColor.values()[random.nextInt(DyeColor.values().length)].getFireworkColor()),
                            IntList.of(), true, true))));
                    level.addFreshEntity(new FireworkRocketEntity(level, pos.getX() + random.nextDouble(), pos.getY() + 1.0,
                            pos.getZ() + random.nextDouble(), rocket));
                }
                pop(level, pos, new ItemStack(Items.CAKE));
            }
            case WOLF_PACK -> {
                for (int count = 0; count < 2; count++) {
                    if (SkyWarsItems.spawnFriendly(state, level, EntityTypes.WOLF, Vec3.atBottomCenterOf(pos), state.team(player))
                            instanceof Wolf wolf) {
                        wolf.tame(player);
                    }
                }
            }
            case CHICKENS -> {
                for (int count = 0; count < 6; count++) spawn(level, EntityTypes.CHICKEN, Vec3.atBottomCenterOf(pos));
                pop(level, pos, new ItemStack(Items.EGG, 16));
            }
            case ANVIL -> {
                BlockPos above = player.blockPosition().above(6);
                if (level.getBlockState(above).isAir() && match.arena().canBuild(above)) {
                    FallingBlockEntity.fall(level, above, Blocks.ANVIL.defaultBlockState());
                }
            }
        }
    }

    private static void pop(ServerLevel level, BlockPos pos, ItemStack stack) {
        Block.popResource(level, pos, stack);
    }

    private static void spawn(ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(level, EntitySpawnReason.TRIGGERED);
        if (mob != null) {
            mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }
}
