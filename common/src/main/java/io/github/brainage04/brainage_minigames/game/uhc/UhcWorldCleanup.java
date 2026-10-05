package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.DiscardedWrites;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Regenerates the UHC, Meetup and FinalUHC dimension pairs: opening a match in any of them marks
 * the pairs for deletion, which happens when the server stops or, failing that, when it next starts.
 */
public final class UhcWorldCleanup {
    private static final String RESET_MARKER = ".brainage_minigames-uhc-reset";

    private UhcWorldCleanup() {}

    static boolean markForReset(MinecraftServer server) {
        Path marker = markerPath(server);
        try {
            Files.writeString(
                    marker,
                    "The dedicated UHC, Meetup and FinalUHC dimension pairs will be regenerated.\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException exception) {
            BrainageMinigames.LOGGER.error(
                    "Could not schedule the UHC-style dimensions for regeneration", exception);
            return false;
        }
    }

    /** Whether the dimension pairs are marked for deletion when the server stops or next starts. */
    public static boolean resetPending(MinecraftServer server) {
        return Files.exists(markerPath(server));
    }

    public static void deletePendingWorld(MinecraftServer server) {
        if (NaturalTerrain.inUse(server)) return;
        deletePendingWorld(server.getWorldPath(LevelResource.ROOT));
    }

    static void deletePendingWorld(Path root) {
        Path marker = root.resolve(RESET_MARKER);
        if (!Files.exists(marker)) {
            return;
        }

        try {
            // The nether goes with the overworld-like dimension: its portals lead back to it.
            for (ResourceKey<Level> key : DiscardedWrites.resetDimensions()) {
                Path dimension = root.resolve("dimensions")
                        .resolve(key.identifier().getNamespace())
                        .resolve(key.identifier().getPath());
                if (Files.exists(dimension)) {
                    try (Stream<Path> paths = Files.walk(dimension)) {
                        paths.sorted(Comparator.reverseOrder()).forEach(UhcWorldCleanup::delete);
                    }
                }
            }
            Files.deleteIfExists(marker);
            BrainageMinigames.LOGGER.info("Regenerated the UHC, Meetup and FinalUHC dimension pairs");
        } catch (IOException | UncheckedIOException exception) {
            BrainageMinigames.LOGGER.error(
                    "Could not regenerate the UHC dimensions in {}; they will be retried on the next server start",
                    root.resolve("dimensions").resolve(BrainageMinigames.MOD_ID),
                    exception);
        }
    }

    private static Path markerPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(RESET_MARKER);
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
