package io.github.brainage04.brainage_minigames.game;

import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/** Per-world setting overrides for each minigame, stored in command storage. */
public final class SettingsStorage {
    private static final Identifier STORAGE_ID = BrainageMinigames.id("settings");
    private static final Codec<Map<String, Integer>> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    private SettingsStorage() {}

    public static GameSettings resolve(MinecraftServer server, Minigame game) {
        return new GameSettings(game, overrides(server, game));
    }

    public static void set(MinecraftServer server, Minigame game, GameSetting setting, int value) {
        Map<String, Integer> overrides = overrides(server, game);
        overrides.put(setting.key(), value);
        write(server, game, overrides);
    }

    public static void reset(MinecraftServer server, Minigame game, GameSetting setting) {
        Map<String, Integer> overrides = overrides(server, game);
        overrides.remove(setting.key());
        write(server, game, overrides);
    }

    private static Map<String, Integer> overrides(MinecraftServer server, Minigame game) {
        return new HashMap<>(root(server).read(game.id(), CODEC).orElse(Map.of()));
    }

    private static void write(
            MinecraftServer server, Minigame game, Map<String, Integer> overrides) {
        CompoundTag root = root(server);
        if (overrides.isEmpty()) {
            root.remove(game.id());
        } else {
            root.store(game.id(), CODEC, overrides);
        }
        server.getCommandStorage().set(STORAGE_ID, root);
    }

    private static CompoundTag root(MinecraftServer server) {
        return server.getCommandStorage().get(STORAGE_ID);
    }
}
