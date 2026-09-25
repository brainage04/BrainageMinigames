package io.github.brainage04.brainage_minigames.game;

import java.util.List;

/**
 * An integer setting a minigame exposes through {@code /minigames settings}. Booleans use 0 and 1.
 */
public record GameSetting(String key, int defaultValue, int min, int max, String description) {
    public static final String COUNTDOWN_SECONDS = "countdown_seconds";
    public static final String TIME_LIMIT_MINUTES = "time_limit_minutes";
    public static final String NATURAL_REGENERATION = "natural_regeneration";

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
}
