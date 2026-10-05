package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class UhcWorldCleanup {
    private static final String RESET_MARKER = ".brainage_minigames-uhc-reset";

    /** Folders under {@code dimensions/brainage_minigames} that are deleted together. */
    private static final String[] DIMENSIONS = {
            "uhc", "uhc_nether", "meetup", "meetup_nether", "final_uhc", "final_uhc_nether"};

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

    public static void deletePendingWorld(MinecraftServer server) {
        if (NaturalTerrain.inUse(server)) return;
        deletePendingWorld(server.getWorldPath(LevelResource.ROOT));
    }

    static void deletePendingWorld(Path root) {
        Path marker = root.resolve(RESET_MARKER);
        if (!Files.exists(marker)) {
            return;
        }

        Path dimensions =
                root
                        .resolve("dimensions")
                        .resolve(BrainageMinigames.MOD_ID);
        try {
            // The nether goes with the overworld-like dimension: its portals lead back to it.
            for (String name : DIMENSIONS) {
                Path dimension = dimensions.resolve(name);
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
                    dimensions,
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
