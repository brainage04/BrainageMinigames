package io.github.brainage04.brainage_minigames.event;

import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.uhc.UhcWorldCleanup;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Loader-neutral handlers the Fabric and NeoForge entrypoints forward their events to. */
public final class ModServerEvents {
    private ModServerEvents() {}

    public static void serverStarting(MinecraftServer server) {
        UhcWorldCleanup.deletePendingWorld(server);
    }

    public static void serverStarted(MinecraftServer server) {
        ModScoreboard.registerGamesWon(server.getScoreboard());
        MatchManager.removeLeftoverTeams(server.getScoreboard());
    }

    public static void serverStopping(MinecraftServer server) {
        DuelRequests.clear();
        MatchManager.stopAll();
        PlayerSnapshotStorage.restoreOnlinePlayers(server);
    }

    public static void serverStopped(MinecraftServer server) {
        UhcWorldCleanup.deletePendingWorld(server);
    }

    public static void tick(MinecraftServer server) {
        MatchManager.tick();
        DuelRequests.tick(server);
    }

    public static void playerJoined(ServerPlayer player) {
        MatchManager.handleConnect(player);
    }

    public static void playerLeft(ServerPlayer player) {
        DuelRequests.handleDisconnect(player);
        MatchManager.handleDisconnect(player);
    }

    public static boolean allowDamage(ServerPlayer player, DamageSource source) {
        return MatchManager.allowDamage(player, source);
    }

    public static boolean allowDeath(ServerPlayer player) {
        return MatchManager.allowDeath(player);
    }
}
