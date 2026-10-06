package io.github.brainage04.brainage_minigames.feedback;

import com.google.gson.JsonObject;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Player feedback: one JSON object per line in {@code <world>/brainage_minigames/feedback.jsonl},
 * each also logged at INFO with the {@value #LOG_PREFIX} prefix.
 */
public final class FeedbackLog {
    public static final String LOG_PREFIX = "[Feedback]";

    private FeedbackLog() {}

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(BrainageMinigames.MOD_ID)
                .resolve("feedback.jsonl");
    }

    /**
     * Logs and stores {@code message} from {@code player} with where they are and the match they
     * are in; throws when the file cannot be written, after the log line has been written.
     */
    public static JsonObject record(ServerPlayer player, String message) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("time", Instant.now().toString());
        entry.addProperty("uuid", player.getUUID().toString());
        entry.addProperty("name", player.getScoreboardName());
        entry.addProperty("dimension", player.level().dimension().identifier().toString());
        entry.addProperty("x", round(player.getX()));
        entry.addProperty("y", round(player.getY()));
        entry.addProperty("z", round(player.getZ()));
        Match match = MatchManager.matchOf(player.getUUID()).orElse(null);
        if (match != null) {
            JsonObject matchEntry = new JsonObject();
            matchEntry.addProperty("id", match.id());
            matchEntry.addProperty("game", match.game().id());
            matchEntry.addProperty("layout", match.layout().displayName());
            matchEntry.addProperty("phase", match.phase().name().toLowerCase(Locale.ROOT));
            entry.add("match", matchEntry);
        }
        entry.addProperty("message", message);

        BrainageMinigames.LOGGER.info(
                "{} {} ({}) in {} at {} {} {}{}: {}",
                LOG_PREFIX,
                player.getScoreboardName(),
                player.getUUID(),
                entry.get("dimension").getAsString(),
                entry.get("x").getAsDouble(),
                entry.get("y").getAsDouble(),
                entry.get("z").getAsDouble(),
                match == null ? "" : ", match #" + match.id() + " (" + match.game().id() + " " + match.layout().displayName() + ")",
                message);

        Path file = file(player.level().getServer());
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                entry + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE,
                StandardOpenOption.DSYNC);
        return entry;
    }

    private static double round(double coordinate) {
        return Math.round(coordinate * 100.0) / 100.0;
    }
}
