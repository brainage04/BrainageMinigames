package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules.Ore;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Identical consumer-facing proof on both loaders, in isolated disposable worlds. */
public final class UhcResourceGameTestFunctions {
    private static final BlockPos DROP_POS = new BlockPos(2, 100, 2);
    private static final AABB DROP_BOX = new AABB(DROP_POS).inflate(2);

    private UhcResourceGameTestFunctions() {}

    public static void drops(GameTestHelper context) {
        var server = context.getLevel().getServer();
        int oldIron = server.getGameRules().get(Ore.IRON.dropPercent);
        int oldApple = server.getGameRules().get(UhcResourceRules.APPLE_DROP_PERCENT);
        try {
            for (var dimension : List.of(ModDimensions.UHC, ModDimensions.UHC_NETHER, Level.OVERWORLD)) {
                isolated(server, dimension, context.getLevel().getChunkSource().getGenerator(), level -> {
                    boolean uhc = !dimension.equals(Level.OVERWORLD);
                    level.getChunkAt(DROP_POS);
                    field(level, "entityManager", PersistentEntitySectionManager.class)
                            .updateChunkStatus(new ChunkPos(DROP_POS.getX() >> 4, DROP_POS.getZ() >> 4), Visibility.TRACKED);
                    checkOreTypes(level, uhc);
                    server.getGameRules().set(Ore.IRON.dropPercent, 200, server);
                    for (Block iron : List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)) {
                        int count = breakAndCount(level, iron.defaultBlockState(), Items.RAW_IRON, false);
                        check(count == (uhc ? 2 : 1), dimension + " iron at 200%: " + count);
                    }
                    server.getGameRules().set(Ore.IRON.dropPercent, 150, server);
                    level.getRandom().setSeed(42);
                    int one = 0;
                    int two = 0;
                    for (int i = 0; i < 128; i++) {
                        int count = breakAndCount(level, Blocks.IRON_ORE.defaultBlockState(), Items.RAW_IRON, false);
                        check(count == 1 || (uhc && count == 2), dimension + " fractional iron: " + count);
                        if (count == 1) one++;
                        if (count == 2) two++;
                    }
                    check(one > 0 && (uhc ? two > 0 : two == 0), dimension + " iron fractional outcomes " + one + "/" + two);
                    server.getGameRules().set(Ore.IRON.dropPercent, 0, server);
                    check(breakAndCount(level, Blocks.IRON_ORE.defaultBlockState(), Items.RAW_IRON, false) == (uhc ? 0 : 1), "zero iron multiplier");
                    server.getGameRules().set(Ore.IRON.dropPercent, 10_000, server);
                    check(breakAndCount(level, Blocks.IRON_ORE.defaultBlockState(), Items.RAW_IRON, false) == (uhc ? 100 : 1), "ore drops crossing the stack limit lost items");
                    for (Block leaf : List.of(Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES)) {
                        for (boolean decay : List.of(false, true)) {
                            server.getGameRules().set(UhcResourceRules.APPLE_DROP_PERCENT, 200, server);
                            checkApples(level, leaf, decay, uhc, false);
                            server.getGameRules().set(UhcResourceRules.APPLE_DROP_PERCENT, 150, server);
                            checkApples(level, leaf, decay, uhc, true);
                        }
                    }
                    checkReplanting(level);
                    return null;
                });
            }
        } finally {
            server.getGameRules().set(Ore.IRON.dropPercent, oldIron, server);
            server.getGameRules().set(UhcResourceRules.APPLE_DROP_PERCENT, oldApple, server);
        }
        context.succeed();
    }

    private static void checkOreTypes(ServerLevel level, boolean uhc) {
        var server = level.getServer();
        for (Block block : List.of(
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS)) {
            var state = block.defaultBlockState();
            var rule = UhcResourceRules.ore(state).dropPercent;
            int previous = server.getGameRules().get(rule);
            try {
                server.getGameRules().set(rule, 100, server);
                server.getRandomSequence(block.getLootTable().orElseThrow().identifier()).setSeed(382);
                int vanilla = breakAndCount(level, state, null, false);
                server.getGameRules().set(rule, 200, server);
                server.getRandomSequence(block.getLootTable().orElseThrow().identifier()).setSeed(382);
                int multiplied = breakAndCount(level, state, null, false);
                check(vanilla > 0 && multiplied == vanilla * (uhc ? 2 : 1), level.dimension() + " " + block + " drops: " + vanilla + " -> " + multiplied);
            } finally {
                server.getGameRules().set(rule, previous, server);
            }
        }
    }

    private static void checkApples(ServerLevel level, Block leaf, boolean decay, boolean uhc, boolean fractional) {
        // Actual leaf destruction/decay and item entities, not an artificial loot table.
        level.getRandom().setSeed(771);
        level.getServer().getRandomSequence(leaf.getLootTable().orElseThrow().identifier()).setSeed(991);
        int one = 0;
        int two = 0;
        for (int i = 0; i < 20_000 && (one + two < 32 || (uhc && fractional && (one == 0 || two == 0))); i++) {
            int count = breakAndCount(level, leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE, 7), Items.APPLE, decay);
            check(count == 0 || count == (uhc && !fractional ? 2 : 1) || (uhc && fractional && count == 2), level.dimension() + " " + leaf + " decay=" + decay + " apples=" + count);
            if (count == 1) one++;
            if (count == 2) two++;
        }
        check(one + two >= 32, "leaf sample produced fewer than 32 apple drops");
        check(!uhc ? two == 0 : fractional ? one > 0 && two > 0 : one == 0 && two >= 32, "apple outcomes " + one + "/" + two);
    }

    private static int breakAndCount(ServerLevel level, BlockState state, Item item, boolean decay) {
        level.getEntitiesOfClass(ItemEntity.class, DROP_BOX).forEach(ItemEntity::discard);
        level.setBlock(DROP_POS, state, Block.UPDATE_CLIENTS);
        if (decay) {
            state.randomTick(level, DROP_POS, RandomSource.create(0));
            check(level.getBlockState(DROP_POS).isAir(), "leaves did not decay");
        } else {
            Block.dropResources(state, level, DROP_POS, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            level.setBlock(DROP_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return level.getEntitiesOfClass(ItemEntity.class, DROP_BOX).stream()
                .filter(entity -> item == null || entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void checkReplanting(ServerLevel level) {
        var server = level.getServer();
        int oldIron = server.getGameRules().get(Ore.IRON.dropPercent);
        int oldDebris = server.getGameRules().get(Ore.ANCIENT_DEBRIS.dropPercent);
        int oldApple = server.getGameRules().get(UhcResourceRules.APPLE_DROP_PERCENT);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "orecycler"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        // No match membership: placements by outsiders must have the same protection.
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            player.connection = new ServerGamePacketListenerImpl(server, connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(DROP_POS.getX() + 2.5, DROP_POS.getY(), DROP_POS.getZ() + 0.5);
            server.getGameRules().set(Ore.IRON.dropPercent, 200, server);
            server.getGameRules().set(Ore.ANCIENT_DEBRIS.dropPercent, 200, server);
            server.getGameRules().set(UhcResourceRules.APPLE_DROP_PERCENT, 200, server);
            var pick = new ItemStack(Items.DIAMOND_PICKAXE);
            var silk = new ItemStack(Items.DIAMOND_PICKAXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            // Silk Touch must not multiply even the first break of a natural ore.
            for (Block block : List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.ANCIENT_DEBRIS)) {
                level.setBlock(DROP_POS, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                check(mineAndCount(player, silk, block.asItem()) == 1, "natural Silk Touch multiplied " + block);
            }
            level.setBlock(DROP_POS, Blocks.ANCIENT_DEBRIS.defaultBlockState(), Block.UPDATE_CLIENTS);
            int firstDebris = mineAndCount(player, pick, Items.ANCIENT_DEBRIS);
            check(firstDebris == (UhcResourceRules.applies(level) ? 2 : 1), "natural first debris break: " + firstDebris);
            checkCycle(player, Blocks.ANCIENT_DEBRIS, pick, firstDebris);
            checkCycle(player, Blocks.IRON_ORE, silk, 1);
            checkCycle(player, Blocks.DEEPSLATE_IRON_ORE, silk, 1);
            for (Block leaf : List.of(Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES)) {
                checkCycle(player, leaf, new ItemStack(Items.SHEARS), 1);
                checkCycle(player, leaf, silk, 1);
            }
            // Placed resources remain vanilla, not merely immune to the self-item case.
            for (int percent : List.of(0, 200)) {
                server.getGameRules().set(Ore.IRON.dropPercent, percent, server);
                place(player, new ItemStack(Items.IRON_ORE));
                check(mineAndCount(player, pick, Items.RAW_IRON) == 1, "placed raw iron at " + percent + "%");
            }
            for (boolean decay : List.of(false, true)) {
                int apples = 0;
                server.getRandomSequence(Blocks.OAK_LEAVES.getLootTable().orElseThrow().identifier()).setSeed(991);
                for (int attempt = 0; attempt < 4096 && apples < 8; attempt++) {
                    place(player, new ItemStack(Items.OAK_LEAVES));
                    int count;
                    if (decay) {
                        clearDrops(level);
                        var state = level.getBlockState(DROP_POS).setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 7);
                        level.setBlock(DROP_POS, state, Block.UPDATE_CLIENTS);
                        state.randomTick(level, DROP_POS, RandomSource.create(0));
                        check(level.getBlockState(DROP_POS).isAir(), "placed leaves did not decay");
                        count = countDrops(level, Items.APPLE);
                    } else {
                        count = mineAndCount(player, pick, Items.APPLE);
                    }
                    check(count <= 1, "placed leaf apples multiplied: " + count + ", decay=" + decay);
                    apples += count;
                }
                check(apples == 8, "placed leaf sample did not produce eight apples, decay=" + decay);
            }
        } finally {
            channel.finishAndReleaseAll();
            server.getGameRules().set(Ore.IRON.dropPercent, oldIron, server);
            server.getGameRules().set(Ore.ANCIENT_DEBRIS.dropPercent, oldDebris, server);
            server.getGameRules().set(UhcResourceRules.APPLE_DROP_PERCENT, oldApple, server);
        }
    }

    private static void checkCycle(ServerPlayer player, Block block, ItemStack tool, int initial) {
        var stock = new ItemStack(block, initial);
        for (int cycle = 0; cycle < 4; cycle++) {
            place(player, stock);
            stock.grow(mineAndCount(player, tool, block.asItem()));
            check(stock.getCount() == initial, block + " place/mine cycle " + cycle + " grew " + initial + " to " + stock.getCount());
        }
    }

    private static void place(ServerPlayer player, ItemStack stock) {
        var level = player.level();
        level.setBlock(DROP_POS.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        player.setItemInHand(InteractionHand.MAIN_HAND, stock);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(DROP_POS), Direction.UP, DROP_POS.below(), false);
        var result = ((BlockItem) stock.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stock, hit));
        check(result.consumesAction() && !level.getBlockState(DROP_POS).isAir(), "resource placement failed");
    }

    private static int mineAndCount(ServerPlayer player, ItemStack tool, Item item) {
        clearDrops(player.level());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        check(player.gameMode.destroyBlock(DROP_POS), "resource mining failed");
        return countDrops(player.level(), item);
    }

    private static void clearDrops(ServerLevel level) {
        level.getEntitiesOfClass(ItemEntity.class, DROP_BOX).forEach(ItemEntity::discard);
    }

    private static int countDrops(ServerLevel level, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, DROP_BOX).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    public static void generation(GameTestHelper context) {
        var server = context.getLevel().getServer();
        int[] previous = Arrays.stream(Ore.values()).mapToInt(ore -> server.getGameRules().get(ore.generationPercent)).toArray();
        try {
            int[] uhcOne = sample(server, ModDimensions.UHC, false, 100);
            int[] uhcTwo = sample(server, ModDimensions.UHC, false, 200);
            check(uhcTwo[Ore.IRON.ordinal()] > uhcOne[Ore.IRON.ordinal()] * 1.5, "2x UHC iron did not substantially increase");
            check(total(uhcTwo) > total(uhcOne) * 1.5, "2x UHC total ore did not substantially increase");
            int[] netherOne = sample(server, ModDimensions.UHC_NETHER, true, 100);
            int[] netherTwo = sample(server, ModDimensions.UHC_NETHER, true, 200);
            check(netherTwo[Ore.NETHER_QUARTZ.ordinal()] > netherOne[Ore.NETHER_QUARTZ.ordinal()] * 1.5, "2x UHC nether quartz did not substantially increase");
            sample(server, Level.OVERWORLD, false, 100);
            sample(server, Level.OVERWORLD, false, 200);
            checkOverworldPlacement(server, context.getLevel().getChunkSource().getGenerator());
            // A fractional chance is also exercised against actual fresh chunk decoration.
            int[] uhcHalf = sample(server, ModDimensions.UHC, false, 50);
            check(total(uhcHalf) > 0 && total(uhcHalf) < total(uhcOne), "0.5x generation did not reduce ore");
        } finally {
            for (Ore ore : Ore.values()) server.getGameRules().set(ore.generationPercent, previous[ore.ordinal()], server);
        }
        context.succeed();
    }

    private static int[] sample(MinecraftServer server, ResourceKey<Level> dimension, boolean nether, int percent) {
        for (Ore ore : Ore.values()) server.getGameRules().set(ore.generationPercent, percent, server);
        var registries = server.registryAccess();
        ChunkGenerator generator = new NoiseBasedChunkGenerator(
                new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(nether ? Biomes.NETHER_WASTES : Biomes.PLAINS)),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD));
        int[] counts = isolated(server, dimension, generator, level -> {
            int[] result = new int[Ore.values().length];
            // Same seed, coordinates and natural noise/decoration in every fresh world.
            // Finish a one-chunk halo before counting: neighboring veins can cross the sample edge.
            for (int x = 99; x < 105; x++) {
                for (int z = 99; z < 105; z++) level.getChunk(x, z);
            }
            for (int x = 100; x < 104; x++) {
                for (int z = 100; z < 104; z++) {
                    var chunk = level.getChunk(x, z);
                    chunk.findBlocks(state -> UhcResourceRules.ore(state) != null,
                            (pos, state) -> result[UhcResourceRules.ore(state).ordinal()]++);
                }
            }
            return result;
        });
        return counts;
    }

    private static void checkOverworldPlacement(MinecraftServer server, ChunkGenerator generator) {
        var feature = new PlacedFeature(
                server.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(OreFeatures.ORE_IRON),
                List.of(CountPlacement.of(8), InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(28), VerticalAnchor.absolute(36))));
        isolated(server, Level.OVERWORLD, generator, level -> {
            int[] counts = new int[2];
            var heightmaps = Set.of(Heightmap.Types.OCEAN_FLOOR_WG);
            for (int index = 0; index < counts.length; index++) {
                server.getGameRules().set(Ore.IRON.generationPercent, index == 0 ? 100 : 200, server);
                // Compare actual vanilla placement, without asynchronous natural decoration races.
                for (BlockPos pos : BlockPos.betweenClosed(-8, 20, -8, 23, 44, 23)) {
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // Live chunks retain runtime heightmaps, but OreFeature reads the worldgen map.
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Heightmap.primeHeightmaps(level.getChunk(x, z), heightmaps);
                    }
                }
                feature.place(level, generator, RandomSource.create(771), BlockPos.ZERO);
                for (BlockPos pos : BlockPos.betweenClosed(-8, 20, -8, 23, 44, 23)) {
                    if (level.getBlockState(pos).is(Blocks.IRON_ORE)) counts[index]++;
                }
            }
            check(counts[0] > 0 && counts[0] == counts[1],
                    "Overworld vanilla iron feature changed at 2x: " + Arrays.toString(counts));
            return null;
        });
    }

    private static int total(int[] counts) {
        return Arrays.stream(counts).sum();
    }

    private static <T> T isolated(MinecraftServer server, ResourceKey<Level> dimension, ChunkGenerator generator, Function<ServerLevel, T> action) {
        Path root = null;
        try {
            root = Files.createTempDirectory("brainage-resource-gametest-");
            try (var access = LevelStorageSource.createDefault(root).createAccess("world")) {
                var type = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(
                        dimension.equals(ModDimensions.UHC_NETHER) ? net.minecraft.world.level.dimension.BuiltinDimensionTypes.NETHER : net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD);
                try (var level = new ServerLevel(server, field(server, "executor", Executor.class), access,
                        new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), dimension,
                        new LevelStem(type, generator), false, BiomeManager.obfuscateSeed(server.overworld().getSeed()), List.of(), false)) {
                    level.noSave = true;
                    return action.apply(level);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create disposable resource test world", exception);
        } finally {
            if (root != null) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not remove disposable resource test world", exception);
                }
            }
        }
    }

    private static <T> T field(Object instance, String name, Class<T> type) {
        try {
            Field field = (instance instanceof MinecraftServer ? MinecraftServer.class : ServerLevel.class).getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(instance));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(instance.getClass().getSimpleName() + "." + name + " is inaccessible", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
