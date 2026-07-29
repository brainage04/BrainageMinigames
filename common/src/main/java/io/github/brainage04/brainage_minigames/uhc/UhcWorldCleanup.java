package io.github.brainage04.brainage_minigames.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

final class UhcWorldCleanup {
    private static final String RESET_MARKER = ".brainage_minigames-uhc-reset";

    private UhcWorldCleanup() {
    }

    static void initialize() {
    }

    static boolean markForReset(MinecraftServer server) {
        Path marker = markerPath(server);
        try {
            Files.writeString(
                    marker,
                    "The dedicated UHC dimension will be regenerated.\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return true;
        } catch (IOException exception) {
            BrainageMinigames.LOGGER.error("Could not schedule the UHC dimension for regeneration", exception);
            return false;
        }
    }

    static void deletePendingWorld(MinecraftServer server) {
        Path marker = markerPath(server);
        if (!Files.exists(marker)) {
            return;
        }

        Path dimension = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(BrainageMinigames.MOD_ID)
                .resolve("uhc");
        try {
            if (Files.exists(dimension)) {
                try (Stream<Path> paths = Files.walk(dimension)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(UhcWorldCleanup::delete);
                }
            }
            Files.deleteIfExists(marker);
            BrainageMinigames.LOGGER.info("Regenerated the dedicated UHC dimension");
        } catch (IOException | UncheckedIOException exception) {
            BrainageMinigames.LOGGER.error(
                    "Could not regenerate the UHC dimension at {}; it will be retried on the next server start",
                    dimension,
                    exception
            );
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
