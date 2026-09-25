package io.github.brainage04.brainage_minigames.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Setting values resolved for one match: stored overrides layered over the minigame's defaults. */
public final class GameSettings {
    private final Map<String, Integer> values;

    GameSettings(Minigame game, Map<String, Integer> overrides) {
        Map<String, Integer> resolved = new LinkedHashMap<>();
        for (GameSetting setting : game.settings()) {
            Integer override = overrides.get(setting.key());
            int value = override == null ? setting.defaultValue() : override;
            resolved.put(setting.key(), Math.clamp(value, setting.min(), setting.max()));
        }
        this.values = Map.copyOf(resolved);
    }

    public int get(String key) {
        Integer value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
        return value;
    }

    public int get(GameSetting setting) {
        return get(setting.key());
    }

    public boolean enabled(String key) {
        return get(key) != 0;
    }

    public int minutesInTicks(GameSetting setting) {
        return get(setting) * 60 * 20;
    }
}
