package io.github.brainage04.brainage_minigames.event;

import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.uhc.UhcManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class ModServerEvents {
    private ModServerEvents() {
    }

    public static void serverStarted(MinecraftServer server) {
        ModScoreboard.registerEventsWon(server.getScoreboard());
        MinigameState.clearEventTeams(server.getScoreboard());
        PlayerSnapshotStorage.restoreOnlinePlayers(server);
    }

    public static void serverStopping(MinecraftServer server) {
        CustomEventManager.stop(server);
        UhcManager.stop(server);
        PlayerSnapshotStorage.restoreOnlinePlayers(server);
    }

    public static void playerJoined(ServerPlayer player) {
        CustomEventManager.handleConnect(player);
        UhcManager.handleConnect(player);
    }

    public static void playerLeft(ServerPlayer player) {
        CustomEventManager.handleDisconnect(player);
        UhcManager.handleDisconnect(player);
    }

    public static boolean allowDamage(ServerPlayer player, DamageSource source) {
        return CustomEventManager.allowDamage(player) && UhcManager.allowDamage(player, source);
    }

    public static boolean allowDeath(ServerPlayer player) {
        return !CustomEventManager.eliminate(player) && !UhcManager.eliminate(player);
    }
}
