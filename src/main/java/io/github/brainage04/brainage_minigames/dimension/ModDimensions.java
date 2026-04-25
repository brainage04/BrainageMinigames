package io.github.brainage04.brainage_minigames.dimension;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class ModDimensions {
    public static final ResourceKey<Level> MINIGAMES_LEVEL_KEY =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("minigames"));

    private ModDimensions() {
    }

    public static boolean isInMinigamesDimension(ServerPlayer player) {
        return player.level().dimension().equals(MINIGAMES_LEVEL_KEY);
    }

    public static TeleportTransition minigamesSpawn(MinecraftServer server) {
        ServerLevel level = server.getLevel(MINIGAMES_LEVEL_KEY);
        if (level == null) {
            return null;
        }
        return new TeleportTransition(
                level,
                Vec3.atBottomCenterOf(level.getServer().getRespawnData().pos()),
                Vec3.ZERO,
                90.0F,
                0.0F,
                TeleportTransition.DO_NOTHING
        );
    }
}
