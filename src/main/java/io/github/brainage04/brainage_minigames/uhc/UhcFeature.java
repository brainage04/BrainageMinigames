package io.github.brainage04.brainage_minigames.uhc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class UhcFeature {
    private UhcFeature() {
    }

    public static void initialize() {
    }

    public static boolean allowDamage(ServerPlayer player, DamageSource source) {
        return UhcManager.allowDamage(player, source);
    }

    public static boolean allowDeath(ServerPlayer player) {
        return !UhcManager.eliminate(player);
    }

    public static void playerJoined(ServerPlayer player) {
        UhcManager.handleConnect(player);
    }

    public static void playerLeft(ServerPlayer player) {
        UhcManager.handleDisconnect(player);
    }

    public static void serverStarting(MinecraftServer server) {
        UhcWorldCleanup.deletePendingWorld(server);
    }

    public static void serverStopped(MinecraftServer server) {
        UhcWorldCleanup.deletePendingWorld(server);
    }
}
