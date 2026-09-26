package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * Creates the UHC dimension and the UHC nether on the GameTest server, which loads no datapack
 * dimensions: the UHC dimension as a copy of the flat test Overworld and the UHC nether as a copy
 * of the vanilla Nether, each created the way the server creates its secondary levels.
 */
final class UhcTestDimensions {
    private UhcTestDimensions() {}

    static void ensure(MinecraftServer server) {
        if (server.getLevel(ModDimensions.UHC) == null) {
            create(server, ModDimensions.UHC, server.overworld());
        }
        if (server.getLevel(ModDimensions.UHC_NETHER) == null) {
            create(server, ModDimensions.UHC_NETHER, server.getLevel(Level.NETHER));
        }
    }

    private static void create(
            MinecraftServer server, ResourceKey<Level> dimension, ServerLevel template) {
        LevelStem stem =
                new LevelStem(
                        template.dimensionTypeRegistration(),
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
