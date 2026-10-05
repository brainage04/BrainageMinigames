package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * Creates each natural game dimension pair from the flat test Overworld and vanilla Nether,
 * using the real per-game dimension types and clocks.
 */
final class UhcTestDimensions {
    private UhcTestDimensions() {}

    static void ensure(MinecraftServer server) {
        for (ResourceKey<Level> dimension : List.of(ModDimensions.UHC, ModDimensions.MEETUP, ModDimensions.FINAL_UHC)) {
            if (server.getLevel(dimension) == null) create(server, dimension, server.overworld());
            ResourceKey<Level> nether = ModDimensions.paired(dimension);
            if (server.getLevel(nether) == null) create(server, nether, server.getLevel(Level.NETHER));
        }
        if (server.getLevel(ModDimensions.MINIGAMES) == null) {
            create(server, ModDimensions.MINIGAMES, server.overworld());
        }
    }

    private static void create(
            MinecraftServer server, ResourceKey<Level> dimension, ServerLevel template) {
        LevelStem stem =
                new LevelStem(
                        server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, dimension.identifier())),
                        template.getChunkSource().getGenerator());
        ServerLevel level =
                new ServerLevel(
                        server,
                        field(server, "executor", Executor.class),
                        field(server, "storageSource", LevelStorageSource.LevelStorageAccess.class),
                        new DerivedLevelData(
                                server.getWorldData(), server.getWorldData().overworldData()),
                        dimension,
                        stem,
                        false,
                        BiomeManager.obfuscateSeed(server.overworld().getSeed()),
                        List.of(),
                        false);
        @SuppressWarnings("unchecked")
        Map<ResourceKey<Level>, ServerLevel> levels = field(server, "levels", Map.class);
        levels.put(dimension, level);
        server.getPlayerList().addWorldborderListener(level);
    }

    private static <T> T field(MinecraftServer server, String name, Class<T> type) {
        try {
            Field field = MinecraftServer.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(server));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "MinecraftServer." + name + " is not accessible", exception);
        }
    }
}
