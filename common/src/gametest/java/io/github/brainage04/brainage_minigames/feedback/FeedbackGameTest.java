package io.github.brainage04.brainage_minigames.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.TestPlayers.ChatPlayer;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** {@code /feedback} storage and logging, and the welcome and hourly reminders. */
public final class FeedbackGameTest {
    /**
     * Feedback sent with the command becomes one JSON line in the world folder with the player,
     * place, match and message, and an INFO log line with the {@code [Feedback]} prefix.
     */
    public void feedbackIsStoredAndLogged(GameTestHelper context) throws Exception {
        MinecraftServer server = context.getLevel().getServer();
        ChatPlayer player = TestPlayers.chat(context, "feedback_fay");
        List<String> logged = new CopyOnWriteArrayList<>();
        var logger = (Logger) LogManager.getLogger(BrainageMinigames.MOD_NAME);
        var appender = new AbstractAppender("feedback-gametest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.INFO) logged.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        Match match = null;
        try {
            String outside = "The hub needs more signs " + UUID.randomUUID();
            run(player, "feedback   " + outside + "  ");
            JsonObject entry = entry(server, outside);
            check(entry.get("uuid").getAsString().equals(player.getUUID().toString()), "uuid " + entry);
            check(entry.get("name").getAsString().equals("feedback_fay"), "name " + entry);
            check(entry.get("dimension").getAsString().equals(player.level().dimension().identifier().toString()), "dimension " + entry);
            check(Math.abs(entry.get("x").getAsDouble() - player.getX()) < 0.01
                    && Math.abs(entry.get("y").getAsDouble() - player.getY()) < 0.01
                    && Math.abs(entry.get("z").getAsDouble() - player.getZ()) < 0.01, "position " + entry);
            check(entry.has("time") && !entry.has("match"), "time without a match " + entry);
            check(logged.stream().anyMatch(line -> line.startsWith(FeedbackLog.LOG_PREFIX + " feedback_fay")
                    && line.endsWith(outside)), "Expected an INFO [Feedback] log line, got " + logged);
            check(player.messages.stream().anyMatch(message -> message.getString().contains("feedback was recorded")),
                    "Expected the player to be thanked.");

            match = MatchManager.open(server, Minigames.GAPPLE, TeamLayout.FREE_FOR_ALL, null);
            MatchManager.join(player, match, 0);
            String inside = "Lobby is too dark " + UUID.randomUUID();
            run(player, "feedback " + inside);
            JsonObject matchEntry = entry(server, inside).getAsJsonObject("match");
            check(matchEntry != null && matchEntry.get("id").getAsInt() == match.id()
                    && matchEntry.get("game").getAsString().equals("gapple")
                    && matchEntry.get("phase").getAsString().equals("lobby"), "match " + matchEntry);
        } finally {
            logger.removeAppender(appender);
            appender.stop();
            if (match != null) MatchManager.stop(match);
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /**
     * A first join is welcomed once; reminders come every interval with a working
     * {@code [Turn off reminders]} button, stop for that player once it is used, and resume with
     * {@code /feedback reminders on}.
     */
    public void remindersWelcomeRemindAndTurnOff(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ChatPlayer player = TestPlayers.chat(context, "feedback_gus");
        try {
            check(count(player, "Welcome to the Brainage Minigames playtest") == 1, "Expected one welcome on the first join.");
            check(FeedbackReminders.enabled(server, player.getUUID()), "Expected reminders on by default.");
            Integer due = FeedbackReminders.dueTick(player.getUUID());
            check(due != null && due == server.getTickCount() + 60 * 60 * 20,
                    "Expected the first reminder an hour after joining, found " + due + " at " + server.getTickCount());
            FeedbackReminders.playerJoined(player);
            check(count(player, "Welcome to the Brainage Minigames playtest") == 1, "Expected no second welcome.");

            player.messages.clear();
            FeedbackReminders.dueAt(player, server.getTickCount());
            FeedbackReminders.tick(server);
            check(count(player, "/feedback <message>") == 1, "Expected one reminder once it is due.");
            String off = runCommand(player.messages.getLast(), "[Turn off reminders]");
            check(off.equals("/feedback reminders off"), "turn-off button runs " + off);
            run(player, off.substring(1));
            check(!FeedbackReminders.enabled(server, player.getUUID()), "Expected the button to turn reminders off.");

            player.messages.clear();
            FeedbackReminders.dueAt(player, server.getTickCount());
            FeedbackReminders.tick(server);
            check(count(player, "/feedback <message>") == 0, "Expected no reminder once turned off, got " + player.messages);

            run(player, "feedback reminders on");
            check(FeedbackReminders.enabled(server, player.getUUID()), "Expected /feedback reminders on to turn them back on.");
            player.messages.clear();
            FeedbackReminders.dueAt(player, server.getTickCount());
            FeedbackReminders.tick(server);
            check(count(player, "/feedback <message>") == 1, "Expected reminders again once turned back on.");
        } finally {
            FeedbackReminders.setEnabled(server, player.getUUID(), true);
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    private static JsonObject entry(MinecraftServer server, String message) throws Exception {
        Path file = FeedbackLog.file(server);
        check(Files.exists(file), "Expected " + file + " to exist.");
        return Files.readAllLines(file).stream()
                .map(line -> JsonParser.parseString(line).getAsJsonObject())
                .filter(entry -> entry.get("message").getAsString().equals(message))
                .findFirst()
                .orElseThrow(() -> failure("Expected a feedback line with message " + message));
    }

    private static long count(ChatPlayer player, String text) {
        return player.messages.stream().filter(message -> message.getString().contains(text)).count();
    }

    /** The command a clickable part labelled {@code label} runs. */
    private static String runCommand(Component message, String label) {
        for (Component part : message.toFlatList()) {
            if (part.getString().equals(label) && part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand(String command)) {
                return command;
            }
        }
        throw failure("Expected a " + label + " button in " + message.getString());
    }

    private static void run(ChatPlayer player, String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
