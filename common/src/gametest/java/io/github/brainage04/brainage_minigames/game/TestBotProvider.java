package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link MatchBots} provider for GameTests, registered the way a bot mod registers: its bots are
 * players connected through the real player list, parked in the Overworld.
 */
final class TestBotProvider {
    static final String ID = "brainage_minigames_test";
    private static final AtomicInteger NAMES = new AtomicInteger();

    private final MinecraftServer server;
    final List<Map<String, Object>> requests = new ArrayList<>();
    final List<ServerPlayer> spawned = new ArrayList<>();
    final Set<UUID> removed = new HashSet<>();

    /** Most bots spawned per request, to stand in for a provider that runs out of bots. */
    int limit = Integer.MAX_VALUE;

    private TestBotProvider(MinecraftServer server) {
        this.server = server;
    }

    static TestBotProvider register(MinecraftServer server) {
        TestBotProvider provider = new TestBotProvider(server);
        MatchBots.register(ID, provider::spawn, provider::remove);
        return provider;
    }

    private List<ServerPlayer> spawn(Map<String, Object> request) {
        requests.add(new HashMap<>(request));
        int count = Math.min(limit, (Integer) request.get("count"));
        List<ServerPlayer> bots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            var cookie = TestPlayers.cookie("TBot" + NAMES.incrementAndGet());
            var bot = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
            TestPlayers.connect(bot, cookie);
            spawned.add(bot);
            bots.add(bot);
        }
        return bots;
    }

    private void remove(ServerPlayer bot) {
        removed.add(bot.getUUID());
        if (server.getPlayerList().getPlayer(bot.getUUID()) == bot) {
            server.getPlayerList().remove(bot);
        }
    }

    /** Unregisters and disconnects every bot still online. */
    void close() {
        MatchBots.unregister(ID);
        for (ServerPlayer bot : spawned) {
            if (server.getPlayerList().getPlayer(bot.getUUID()) == bot) server.getPlayerList().remove(bot);
        }
    }
}
