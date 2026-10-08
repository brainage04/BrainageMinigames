package io.github.brainage04.brainage_minigames.event;

import io.github.brainage04.brainage_minigames.dimension.DiscardedWrites;
import io.github.brainage04.brainage_minigames.feedback.FeedbackReminders;
import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.hub.Hub;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcNether;
import io.github.brainage04.brainage_minigames.game.uhc.UhcWorldCleanup;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios;
import io.github.brainage04.brainage_minigames.game.uhc.UhcClock;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.scoreboard.EloRatings;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Loader-neutral handlers the Fabric and NeoForge entrypoints forward their events to. */
public final class ModServerEvents {
    private ModServerEvents() {}

    public static void serverStarting(MinecraftServer server) {
        DiscardedWrites.clear();
        UhcWorldCleanup.deletePendingWorld(server);
    }

    public static void serverStarted(MinecraftServer server) {
        ModScoreboard.registerGamesWon(server.getScoreboard());
        MatchManager.removeLeftoverTeams(server.getScoreboard());
        UhcClock.tick(server);
        Hub.serverStarted(server);
        MapArena.loadMaps(server);
    }

    public static void serverStopping(MinecraftServer server) {
        DuelRequests.clear();
        MatchManager.stopAll();
        UhcNether.clear();
        PlayerSnapshotStorage.restoreOnlinePlayers(server);
        DiscardedWrites.serverStopping(server, server instanceof GameTestServer, UhcWorldCleanup.resetPending(server));
    }

    public static void serverStopped(MinecraftServer server) {
        DiscardedWrites.clear();
        UhcWorldCleanup.deletePendingWorld(server);
        UhcClock.clear();
        FeedbackReminders.clear();
        Hub.serverStopped();
    }

    public static void tick(MinecraftServer server) {
        UhcClock.tick(server);
        MatchManager.tick();
        DuelRequests.tick(server);
        UhcNether.tick(server);
        UhcResourceScenarios.tick(server);
        FeedbackReminders.tick(server);
        Hub.tick(server);
    }

    public static void playerJoined(ServerPlayer player) {
        EloRatings.publish(player);
        MatchManager.handleConnect(player);
        FeedbackReminders.playerJoined(player);
    }

    public static void playerLeft(ServerPlayer player) {
        DuelRequests.handleDisconnect(player);
        MatchManager.handleDisconnect(player);
        FeedbackReminders.playerLeft(player);
        Hub.playerLeft(player);
    }

    public static boolean allowDamage(ServerPlayer player, DamageSource source) {
        return MatchManager.allowDamage(player, source) && Hub.allowDamage(player, source);
    }

    public static boolean allowDeath(ServerPlayer player) {
        return MatchManager.allowDeath(player);
    }
}
