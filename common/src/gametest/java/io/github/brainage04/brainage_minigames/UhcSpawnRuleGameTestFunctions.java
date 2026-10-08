package io.github.brainage04.brainage_minigames;

import io.netty.channel.embedded.EmbeddedChannel;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcMobDrops;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules.Group;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules.SpawningMob;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Sugar cane and mob spawn rules on both loaders, in disposable worlds and the real UHC dimension. */
public final class UhcSpawnRuleGameTestFunctions {
    /** Every cane spot of the drop test is within this position's chunk, where drops are visible. */
    private static final BlockPos CANE = new BlockPos(8, 100, 8);
    private static final AABB CANE_DROPS = new AABB(CANE).inflate(5);

    private UhcSpawnRuleGameTestFunctions() {}

    /** Natural cane drops by the rule; placed cane and cane that grows during play never multiply. */
    public static void sugarCaneDrops(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int previous = server.getGameRules().get(UhcResourceRules.SUGAR_CANE_DROP_PERCENT);
        try {
            for (var dimension : List.of(ModDimensions.UHC, Level.OVERWORLD)) {
                UhcResourceGameTestFunctions.isolated(server, dimension, context.getLevel().getChunkSource().getGenerator(), level -> {
                    boolean uhc = dimension.equals(ModDimensions.UHC);
                    level.getChunkAt(CANE);
                    UhcResourceGameTestFunctions.field(level, "entityManager", PersistentEntitySectionManager.class)
                            .updateChunkStatus(new ChunkPos(CANE.getX() >> 4, CANE.getZ() >> 4), Visibility.TRACKED);
                    var cookie = TestPlayers.cookie("canecutter");
                    var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
                    var connection = new Connection(PacketFlow.SERVERBOUND);
                    var channel = new EmbeddedChannel(connection);
                    try {
                        player.connection = new ServerGamePacketListenerImpl(server, connection, player, cookie);
                        player.setGameMode(GameType.SURVIVAL);
                        player.snapTo(CANE.getX() + 0.5, CANE.getY() + 4, CANE.getZ() + 0.5);
                        server.getGameRules().set(UhcResourceRules.SUGAR_CANE_DROP_PERCENT, 200, server);
                        BlockPos natural = CANE;
                        BlockPos placed = CANE.west(3);
                        BlockPos stalk = CANE.east(3);
                        BlockPos none = CANE.north(3);
                        naturalCane(level, natural, 0);
                        int naturalCount = cut(player, natural);
                        check(naturalCount == (uhc ? 2 : 1), dimension + " natural cane at 200%: " + naturalCount);
                        placeCane(player, placed);
                        int placedCount = cut(player, placed);
                        check(placedCount == 1, dimension + " placed cane at 200%: " + placedCount);
                        // Natural cane that grows a block during play: the stalk stays natural, the growth does not.
                        naturalCane(level, stalk, 15);
                        level.getBlockState(stalk).randomTick(level, stalk, RandomSource.create(0));
                        check(level.getBlockState(stalk.above()).is(Blocks.SUGAR_CANE), "the cane did not grow");
                        int grownCount = cut(player, stalk.above());
                        check(grownCount == 1, dimension + " grown cane at 200%: " + grownCount);
                        int stalkCount = cut(player, stalk);
                        check(stalkCount == (uhc ? 2 : 1), dimension + " natural stalk at 200%: " + stalkCount);
                        server.getGameRules().set(UhcResourceRules.SUGAR_CANE_DROP_PERCENT, 0, server);
                        naturalCane(level, none, 0);
                        int noneCount = cut(player, none);
                        check(noneCount == (uhc ? 0 : 1), dimension + " natural cane at 0%: " + noneCount);
                    } finally {
                        channel.finishAndReleaseAll();
                    }
                    return null;
                });
            }
        } finally {
            server.getGameRules().set(UhcResourceRules.SUGAR_CANE_DROP_PERCENT, previous, server);
        }
        context.succeed();
    }

    /** Sand with water beside it under {@code pos}, where cane can stand. */
    private static void caneGround(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(pos.below().south(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Cane put there by the world, as generation does, not by a player. */
    private static void naturalCane(ServerLevel level, BlockPos pos, int age) {
        caneGround(level, pos);
        level.setBlock(pos, Blocks.SUGAR_CANE.defaultBlockState().setValue(SugarCaneBlock.AGE, age), Block.UPDATE_CLIENTS);
    }

    private static void placeCane(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        caneGround(level, pos);
        ItemStack stack = new ItemStack(Items.SUGAR_CANE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        check(result.consumesAction() && level.getBlockState(pos).is(Blocks.SUGAR_CANE), "placing cane failed");
    }

    /** Breaks the cane at {@code pos} as the player and counts the cane it drops. */
    private static int cut(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        level.getEntitiesOfClass(ItemEntity.class, CANE_DROPS).forEach(ItemEntity::discard);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(level.getBlockState(pos).is(Blocks.SUGAR_CANE) && player.gameMode.destroyBlock(pos), "cutting cane failed");
        return level.getEntitiesOfClass(ItemEntity.class, CANE_DROPS).stream()
                .filter(entity -> entity.getItem().is(Items.SUGAR_CANE))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /**
     * The animals new chunks bring and the vanilla sugar cane patches scale with their rules in the
     * UHC dimension, and stay as they are in the overworld. Every mob's own rule is held at 100%, so
     * the passive rule alone sets the sample. Both samples are seeded and placed on prepared ground,
     * so every count is exactly reproducible: 200% makes exactly twice the animals of 100%.
     */
    public static void generation(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int cane = server.getGameRules().get(UhcResourceRules.SUGAR_CANE_GENERATION_PERCENT);
        int passive = server.getGameRules().get(Group.PASSIVE.percent);
        boolean spawning = server.getGameRules().get(GameRules.SPAWN_MOBS);
        java.util.Map<SpawningMob, Integer> own = new java.util.EnumMap<>(SpawningMob.class);
        for (SpawningMob mob : SpawningMob.values()) own.put(mob, server.getGameRules().get(mob.percent));
        try {
            own.keySet().forEach(mob -> server.getGameRules().set(mob.percent, 100, server));
            server.getGameRules().set(GameRules.SPAWN_MOBS, true, server);
            int[] uhc = {animals(server, ModDimensions.UHC, 100), animals(server, ModDimensions.UHC, 200),
                    animals(server, ModDimensions.UHC, 0)};
            int[] overworld = {animals(server, Level.OVERWORLD, 100), animals(server, Level.OVERWORLD, 200)};
            String counts = "animals in new chunks, UHC at 100/200/0%: " + uhc[0] + "/" + uhc[1] + "/" + uhc[2]
                    + "; overworld at 100/200%: " + overworld[0] + "/" + overworld[1];
            BrainageMinigames.LOGGER.info("uhc_spawn_generation: {}", counts);
            check(uhc[0] >= 40, "too few animals to compare: " + counts);
            check(uhc[1] == uhc[0] * 2, "200% did not double the animals of new UHC chunks: " + counts);
            check(uhc[2] == 0, "0% left animals in new UHC chunks: " + counts);
            check(overworld[0] == uhc[0] && overworld[1] == overworld[0], "the overworld changed with the UHC rule: " + counts);
            int[] uhcCane = {cane(server, ModDimensions.UHC, 100), cane(server, ModDimensions.UHC, 200),
                    cane(server, ModDimensions.UHC, 0)};
            int[] overworldCane = {cane(server, Level.OVERWORLD, 100), cane(server, Level.OVERWORLD, 200)};
            String caneCounts = "cane from desert patches, UHC at 100/200/0%: " + uhcCane[0] + "/" + uhcCane[1] + "/" + uhcCane[2]
                    + "; overworld at 100/200%: " + overworldCane[0] + "/" + overworldCane[1];
            check(uhcCane[0] >= 100, "too little cane to compare: " + caneCounts);
            check(uhcCane[1] >= uhcCane[0] * 1.6, "200% did not substantially increase UHC cane: " + caneCounts);
            check(uhcCane[2] == 0, "0% left cane: " + caneCounts);
            check(overworldCane[0] == uhcCane[0] && overworldCane[1] == overworldCane[0],
                    "the overworld changed with the UHC rule: " + caneCounts);
        } finally {
            server.getGameRules().set(UhcResourceRules.SUGAR_CANE_GENERATION_PERCENT, cane, server);
            server.getGameRules().set(Group.PASSIVE.percent, passive, server);
            server.getGameRules().set(GameRules.SPAWN_MOBS, spawning, server);
            own.forEach((mob, percent) -> server.getGameRules().set(mob.percent, percent, server));
        }
        context.succeed();
    }

    /**
     * The animals that vanilla's new-chunk spawning places on a swamp chunk of grass high above the
     * terrain, with the passive rule at {@code percent}, over 400 runs drawing from one seeded
     * random. Each run is what generating a chunk does ({@link
     * NaturalSpawner#spawnMobsForChunkGeneration}); copies draw only from the level's random, so the
     * seeded sequence is the same at every percentage. Each run's animals are counted and removed
     * before the next, so nothing depends on generation timing such as lighting. In the UHC
     * dimension every animal is marked as naturally spawned, in the overworld none is.
     */
    private static int animals(MinecraftServer server, ResourceKey<Level> dimension, int percent) {
        server.getGameRules().set(Group.PASSIVE.percent, percent, server);
        var swamp = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SWAMP);
        return UhcResourceGameTestFunctions.isolated(server, dimension, fixedBiome(server, Biomes.SWAMP), level -> {
            ChunkPos chunk = new ChunkPos(0, 0);
            level.getChunk(chunk.x(), chunk.z());
            UhcResourceGameTestFunctions.field(level, "entityManager", PersistentEntitySectionManager.class)
                    .updateChunkStatus(chunk, Visibility.TRACKED);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    level.setBlock(new BlockPos(x, ANIMAL_GROUND, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            AABB above = new AABB(0, ANIMAL_GROUND, 0, 16, ANIMAL_GROUND + 8, 16);
            boolean uhc = dimension.equals(ModDimensions.UHC);
            RandomSource random = RandomSource.create(42);
            int total = 0;
            for (int run = 0; run < 400; run++) {
                NaturalSpawner.spawnMobsForChunkGeneration(level, swamp, chunk, random);
                List<Animal> animals = level.getEntitiesOfClass(Animal.class, above);
                check(animals.stream().allMatch(animal -> animal.entityTags().contains(UhcMobDrops.NATURAL_TAG) == uhc),
                        dimension + ": new chunks' animals " + (uhc ? "not all" : "") + " marked as naturally spawned");
                total += animals.size();
                animals.forEach(Entity::discard);
            }
            return total;
        });
    }

    /** The height of the grass the generation sample's animals stand on, above any terrain. */
    private static final int ANIMAL_GROUND = 250;

    /**
     * Cane that the vanilla desert patch places on ten prepared shores, sand with a water channel in
     * every third column, with the cane rule at {@code percent}: each patch is placed directly, with
     * the same seeds, so the counts are exactly reproducible.
     */
    private static int cane(MinecraftServer server, ResourceKey<Level> dimension, int percent) {
        server.getGameRules().set(UhcResourceRules.SUGAR_CANE_GENERATION_PERCENT, percent, server);
        var patch = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(
                ResourceKey.create(Registries.PLACED_FEATURE, net.minecraft.resources.Identifier.withDefaultNamespace("patch_sugar_cane_desert")));
        var generator = fixedBiome(server, Biomes.DESERT);
        return UhcResourceGameTestFunctions.isolated(server, dimension, generator, level -> {
            int count = 0;
            for (int shore = 0; shore < 10; shore++) {
                BlockPos origin = new BlockPos(shore * 32, 200, 0);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -2, -5), origin.offset(20, 4, 20))) {
                    int y = pos.getY() - origin.getY();
                    var state = y == -2 ? Blocks.STONE : y == -1 ? (Math.floorMod(pos.getX(), 3) == 0 ? Blocks.WATER : Blocks.SAND) : Blocks.AIR;
                    level.setBlock(pos, state.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                patch.value().placeWithBiomeCheck(level, generator, RandomSource.create(4711 + shore), origin);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, 0, -5), origin.offset(20, 4, 20))) {
                    if (level.getBlockState(pos).is(Blocks.SUGAR_CANE)) count++;
                }
            }
            return count;
        });
    }

    private static NoiseBasedChunkGenerator fixedBiome(MinecraftServer server, ResourceKey<net.minecraft.world.level.biome.Biome> biome) {
        var registries = server.registryAccess();
        return new NoiseBasedChunkGenerator(
                new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(biome)),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
    }

    /**
     * Every mob the UHC dimensions spawn naturally has a rule; mob caps count each mob by its rule;
     * and natural spawning in a dark UHC cave makes no hostile mobs at 0% and twice as many per pack
     * at 200% as at 100%, while the overworld keeps vanilla counts.
     */
    public static void naturalSpawning(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerLevel uhc = server.getLevel(ModDimensions.UHC);
        checkCoverage(server);
        int hostile = server.getGameRules().get(Group.HOSTILE.percent);
        int zombie = server.getGameRules().get(SpawningMob.ZOMBIE.percent);
        Difficulty difficulty = server.getWorldData().getDifficulty();
        List<Runnable> cleanup = new ArrayList<>();
        GameTestLifecycle.afterTest(context, () -> {
            cleanup.forEach(Runnable::run);
            server.getGameRules().set(Group.HOSTILE.percent, hostile, server);
            server.getGameRules().set(SpawningMob.ZOMBIE.percent, zombie, server);
            server.setDifficulty(difficulty, true);
        });
        server.setDifficulty(Difficulty.NORMAL, true);
        // A plains cave far from any match, where the usual zombies, skeletons, spiders and creepers spawn.
        var source = uhc.getChunkSource().getGenerator().getBiomeSource();
        var found = source.findBiomeHorizontal(1_200_000, -40, 1_200_000, 6400, 32,
                biome -> biome.is(Biomes.PLAINS), RandomSource.create(5), true, uhc.getChunkSource().randomState().sampler());
        check(found != null, "no plains near the test cave");
        BlockPos room = new BlockPos(found.getFirst().getX(), -40, found.getFirst().getZ());
        // Hostile mobs spawn only between 24 and 32 blocks from the nearest player: watch from 28 above.
        BlockPos pocket = room.above(28);
        for (BlockPos pos : List.of(room, pocket)) {
            ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = chunk.x() + dx;
                    int z = chunk.z() + dz;
                    if (uhc.setChunkForced(x, z, true)) cleanup.add(() -> uhc.setChunkForced(x, z, false));
                }
            }
        }
        ServerPlayer player = TestPlayers.connect(context, "spawnwatcher");
        cleanup.add(() -> TestPlayers.disconnect(player));
        AABB inside = new AABB(room).inflate(6, 0, 6).expandTowards(0, 2, 0);
        GameTestLifecycle.awaitPreparation(context,
                () -> uhc.isPositionEntityTicking(room) && uhc.isPositionEntityTicking(pocket),
                () -> {
                    carve(uhc, room, 6);
                    carve(uhc, pocket, 1);
                    PlayerUtils.teleport(player, uhc, Vec3.atBottomCenterOf(pocket), 0);
                    player.setGameMode(GameType.SURVIVAL);
                    checkCaps(uhc, room, true);
                    checkCaps(context.getLevel(), context.absolutePos(BlockPos.ZERO), false);
                    // Carved rock reads as open sky until the light engine has caught up.
                    GameTestLifecycle.awaitPreparation(context,
                            () -> uhc.getBrightness(LightLayer.SKY, room) == 0 && uhc.getBrightness(LightLayer.SKY, room.east(5)) == 0,
                            () -> {
                                server.getGameRules().set(SpawningMob.ZOMBIE.percent, 100, server);
                                int[] none = spawnHostiles(uhc, room, inside, 0, 500);
                                int[] usual = spawnHostiles(uhc, room, inside, 100, HOSTILE_PACKS);
                                int[] twice = spawnHostiles(uhc, room, inside, 200, HOSTILE_PACKS);
                                checkDoubled(java.util.Arrays.stream(none).sum(), usual, twice,
                                        " (biome " + uhc.getBiome(room).getRegisteredName() + ")");
                                context.succeed();
                            });
                });
    }

    /** An air room of radius {@code radius} and 3 high in solid stone, its floor at {@code floor}. */
    private static void carve(ServerLevel level, BlockPos floor, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-radius - 1, -1, -radius - 1), floor.offset(radius + 1, 3, radius + 1))) {
            boolean shell = Math.abs(pos.getX() - floor.getX()) > radius || Math.abs(pos.getZ() - floor.getZ()) > radius
                    || pos.getY() < floor.getY() || pos.getY() > floor.getY() + 2;
            level.setBlock(pos, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Packs spawned at 100% and at 200%; enough that the ratio's standard error is about 0.02. */
    private static final int HOSTILE_PACKS = 8000;

    /**
     * Whether {@code twice} has twice as many mobs per pack as {@code usual}. Packs are independent,
     * so the ratio of the mean counts is normal with a standard error {@code se} (delta method).
     * The check fails only when the ratio is more than 5 {@code se} from 2: by chance, about once in
     * 1.7 million runs. It needs {@code se <= 0.1}, so a ratio of 1 (no doubling) always fails.
     */
    private static void checkDoubled(int none, int[] usual, int[] twice, String where) {
        double[] u = meanAndVariance(usual);
        double[] t = meanAndVariance(twice);
        double ratio = t[0] / u[0];
        double se = ratio * Math.sqrt(u[1] / (usual.length * u[0] * u[0]) + t[1] / (twice.length * t[0] * t[0]));
        String counts = "hostile mobs at 0%%: %d; per pack at 100/200%%: %.3f/%.3f (variance %.3f/%.3f, %d packs each); ratio %.3f, se %.3f%s"
                .formatted(none, u[0], t[0], u[1], t[1], usual.length, ratio, se, where);
        BrainageMinigames.LOGGER.info("uhc_spawn_natural: {}", counts);
        check(none == 0, "hostile spawns at 0%: " + counts);
        check(u[0] > 0 && se <= 0.1, "too few hostile spawns to compare: " + counts);
        check(Math.abs(ratio - 2) <= 5 * se, "200% did not double hostile spawns: " + counts);
    }

    private static double[] meanAndVariance(int[] samples) {
        double mean = java.util.Arrays.stream(samples).average().orElse(0);
        double variance = java.util.Arrays.stream(samples).mapToDouble(x -> (x - mean) * (x - mean)).sum()
                / Math.max(1, samples.length - 1);
        return new double[] {mean, variance};
    }

    /**
     * Spawns {@code packs} packs of hostile mobs as the natural spawner does, one at a time, and
     * returns how many mobs each made, removing them before the next so the packs are independent.
     * Every one is marked as naturally spawned.
     */
    private static int[] spawnHostiles(ServerLevel level, BlockPos room, AABB inside, int percent, int packs) {
        level.getServer().getGameRules().set(Group.HOSTILE.percent, percent, level.getServer());
        level.getEntitiesOfClass(Mob.class, inside).forEach(Entity::discard);
        int[] counts = new int[packs];
        for (int pack = 0; pack < packs; pack++) {
            NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, room);
            // Every mob goes, endermen and jockeys' chickens included: one left behind would block later packs.
            List<Mob> all = level.getEntitiesOfClass(Mob.class, inside);
            List<Mob> spawned = all.stream()
                    .filter(mob -> UhcSpawnRules.mob(mob.getType()) != null && UhcSpawnRules.mob(mob.getType()).group == Group.HOSTILE)
                    .toList();
            check(spawned.stream().allMatch(mob -> mob.entityTags().contains(UhcMobDrops.NATURAL_TAG)),
                    "a naturally spawned hostile mob is not marked as natural");
            counts[pack] = spawned.size();
            all.forEach(Entity::discard);
        }
        return counts;
    }

    /**
     * 2000 zombies count as 2000 towards the monster caps at 100% and 4000 at 50% in a UHC dimension,
     * and 2000 elsewhere whatever the rule. At 200% each counts once or not at all, at even odds by
     * its UUID: 1000 on average with a standard deviation of 22, so 850 to 1150 fails by chance about
     * once in 50 billion runs.
     */
    private static void checkCaps(ServerLevel level, BlockPos at, boolean uhc) {
        MinecraftServer server = level.getServer();
        List<Entity> zombies = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            zombies.add(zombie);
        }
        int[] counted = new int[3];
        int[] percents = {100, 200, 50};
        for (int i = 0; i < percents.length; i++) {
            server.getGameRules().set(Group.HOSTILE.percent, percents[i], server);
            var state = NaturalSpawner.createState(1, zombies,
                    (chunk, count) -> count.accept(level.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))),
                    new LocalMobCapCalculator(level.getChunkSource().chunkMap));
            counted[i] = state.getMobCategoryCounts().getInt(MobCategory.MONSTER);
        }
        String counts = level.dimension() + " monster cap counts at 100/200/50%: " + counted[0] + "/" + counted[1] + "/" + counted[2];
        if (uhc) {
            check(counted[0] == 2000 && counted[1] >= 850 && counted[1] <= 1150 && counted[2] == 4000, counts);
        } else {
            check(counted[0] == 2000 && counted[1] == 2000 && counted[2] == 2000, counts);
        }
    }

    /** Every naturally spawning mob of the UHC dimensions' biomes and structures has a rule named after it. */
    private static void checkCoverage(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        Consumer<MobSpawnSettings.SpawnerData> check = spawner -> {
            if (UhcSpawnRules.mob(spawner.type()) == null) missing.add(BuiltInRegistries.ENTITY_TYPE.getKey(spawner.type()).toString());
        };
        for (var dimension : List.of(ModDimensions.UHC, ModDimensions.UHC_NETHER)) {
            for (var biome : server.getLevel(dimension).getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                for (MobCategory category : MobCategory.values()) {
                    biome.value().getMobSettings().getMobs(category).unwrap().forEach(weighted -> check.accept(weighted.value()));
                }
            }
        }
        for (var structure : server.registryAccess().lookupOrThrow(Registries.STRUCTURE)) {
            for (StructureSpawnOverride override : structure.spawnOverrides().values()) {
                override.spawns().unwrap().forEach(weighted -> check.accept(weighted.value()));
            }
        }
        check(missing.isEmpty(), "naturally spawning mobs without a rule: " + missing.stream().distinct().sorted().toList());
        for (SpawningMob mob : SpawningMob.values()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).getPath();
            check(id.equals(mob.id()), mob + " is named " + mob.id() + " but spawns " + id);
            check(server.getGameRules().get(mob.percent) != null && server.getGameRules().get(mob.dropPercent) != null,
                    mob + " has no registered spawn or drop rule");
        }
    }

    /**
     * Spawning defaults to vanilla, except twice the cows, horses and chickens (leather, feathers,
     * transport); mob loot defaults to vanilla, and meat stays as it is.
     */
    public static void defaults(GameTestHelper context) {
        for (Group group : Group.values()) {
            check(group.percent.defaultValue() == 100, group + " spawning defaults to " + group.percent.defaultValue());
        }
        var doubled = java.util.EnumSet.of(SpawningMob.COW, SpawningMob.HORSE, SpawningMob.CHICKEN);
        for (SpawningMob mob : SpawningMob.values()) {
            GameRule<Integer> spawn = mob.percent;
            int expected = doubled.contains(mob) ? 200 : 100;
            check(spawn.defaultValue() == expected, mob + " spawning defaults to " + spawn.defaultValue() + ", not " + expected);
            check(mob.dropPercent.defaultValue() == 100, mob + " drops default to " + mob.dropPercent.defaultValue());
        }
        check(!UhcMobDrops.ALL_MEAT_IS_BEEF.defaultValue(), "all meat is beef by default");
        context.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
