package io.github.brainage04.brainage_minigames.game;

import java.util.List;

/**
 * An integer setting a minigame exposes through {@code /minigames settings}. Booleans use 0 and 1.
 */
public record GameSetting(String key, int defaultValue, int min, int max, String description) {
    public static final String COUNTDOWN_SECONDS = "countdown_seconds";
    public static final String TIME_LIMIT_MINUTES = "time_limit_minutes";
    public static final String NATURAL_REGENERATION = "natural_regeneration";
    public static final String LOBBY_SECONDS = "lobby_seconds";
    public static final String LOBBY_SIZE = "lobby_size";

    public GameSetting {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(
                    "Default for " + key + " is outside " + min + ".." + max);
        }
    }

    /** Settings every minigame has; {@link Match} reads them directly. */
    public static List<GameSetting> common(
            int countdownSeconds, int timeLimitMinutes, boolean naturalRegeneration) {
        return List.of(
                new GameSetting(
                        COUNTDOWN_SECONDS,
                        countdownSeconds,
                        0,
                        60,
                        "Seconds between the match filling up and players being released"),
                new GameSetting(
                        TIME_LIMIT_MINUTES,
                        timeLimitMinutes,
                        0,
                        600,
                        "Minutes before the match ends in a draw between the remaining teams (0 disables)"),
                new GameSetting(
                        NATURAL_REGENERATION,
                        naturalRegeneration ? 1 : 0,
                        0,
                        1,
                        "Whether players regenerate health from a full hunger bar (1) or not (0)"));
    }

    /**
     * Settings of games whose lobbies start on their own: {@link Match} starts a lobby that is not
     * full {@code seconds} after its first player began waiting; a free-for-all that starts early
     * fills up to {@code size} participants with bots while {@link
     * MatchService#FILL_BOTS_ON_EARLY_START} is on.
     */
    public static List<GameSetting> lobby(int seconds, int size) {
        return List.of(
                new GameSetting(
                        LOBBY_SECONDS,
                        seconds,
                        0,
                        600,
                        "Seconds after the first player starts waiting before a lobby that is not full starts (0: only a full lobby, a vote or a start command starts it)"),
                new GameSetting(
                        LOBBY_SIZE,
                        size,
                        2,
                        100,
                        "Participants a free-for-all lobby is filled up to with bots when it starts early (while fill_bots_on_early_start is on)"));
    }
}
