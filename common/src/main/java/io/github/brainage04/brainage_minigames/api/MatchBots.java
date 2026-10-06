package io.github.brainage04.brainage_minigames.api;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bot providers: mods that spawn player bots to fill match slots. A provider registers at server
 * start, without a compile dependency, through reflection on {@link #register}; the public
 * signatures use only JDK and Minecraft types.
 *
 * <p>The spawner receives a map with every key present: {@code "server"} ({@link
 * MinecraftServer}), {@code "game"} (game id, e.g. {@code "uhc"}), {@code "match"} (match id as a
 * string), {@code "count"} ({@link Integer}), {@code "names"} ({@code List<String>}, may be empty,
 * when the provider chooses) and {@code "difficulty"} ({@code easy}, {@code normal}, {@code hard} or
 * {@code mixed}). It returns the spawned, connected players; fewer than requested is allowed. The
 * match adds them like human participants and calls the remover for each once it no longer needs
 * the bot (eliminated, or the match ended), always after the bot has left the match.
 */
public final class MatchBots {
    public static final String MIXED = "mixed";
    public static final List<String> DIFFICULTIES = List.of("easy", "normal", "hard", MIXED);

    private static final LinkedHashMap<String, Provider> PROVIDERS = new LinkedHashMap<>();

    /** The provider that spawned each live bot, which also removes it. */
    private static final Map<UUID, Provider> OWNERS = new HashMap<>();

    private record Provider(
            String id,
            Function<Map<String, Object>, List<ServerPlayer>> spawner,
            Consumer<ServerPlayer> remover) {}

    private MatchBots() {}

    /**
     * Registers a provider, replacing any earlier one with the same id. The most recently
     * registered provider spawns every bot.
     */
    public static void register(
            String providerId,
            Function<Map<String, Object>, List<ServerPlayer>> spawner,
            Consumer<ServerPlayer> remover) {
        PROVIDERS.remove(providerId);
        PROVIDERS.put(providerId, new Provider(providerId, spawner, remover));
    }

    public static void unregister(String providerId) {
        PROVIDERS.remove(providerId);
    }

    /** Whether a provider is registered, so menus and commands can offer bot slots. */
    public static boolean available() {
        return !PROVIDERS.isEmpty();
    }

    /** Asks the provider for {@code count} bots; empty without a provider or when it fails. */
    public static List<ServerPlayer> spawn(
            MinecraftServer server, String game, String match, int count, String difficulty) {
        if (count <= 0 || PROVIDERS.isEmpty()) return List.of();
        Provider provider = PROVIDERS.sequencedValues().getLast();
        Map<String, Object> request = new HashMap<>();
        request.put("server", server);
        request.put("game", game);
        request.put("match", match);
        request.put("count", count);
        request.put("names", List.<String>of());
        request.put("difficulty", difficulty);
        List<ServerPlayer> spawned;
        try {
            spawned = provider.spawner().apply(request);
        } catch (RuntimeException exception) {
            BrainageMinigames.LOGGER.error(
                    "Bot provider {} failed to spawn {} bots", provider.id(), count, exception);
            return List.of();
        }
        if (spawned == null) return List.of();
        List<ServerPlayer> bots = new ArrayList<>(Math.min(count, spawned.size()));
        for (ServerPlayer bot : spawned) {
            if (bot == null) continue;
            if (bots.size() < count) {
                bots.add(bot);
                OWNERS.put(bot.getUUID(), provider);
            } else {
                remove(provider, bot);
            }
        }
        return bots;
    }

    /** Hands a bot back to the provider that spawned it. */
    public static void remove(ServerPlayer bot) {
        Provider provider = OWNERS.remove(bot.getUUID());
        if (provider != null) remove(provider, bot);
    }

    private static void remove(Provider provider, ServerPlayer bot) {
        try {
            provider.remover().accept(bot);
        } catch (RuntimeException exception) {
            BrainageMinigames.LOGGER.error(
                    "Bot provider {} failed to remove {}", provider.id(), bot.getScoreboardName(), exception);
        }
    }
}
