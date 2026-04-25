package io.github.brainage04.brainage_minigames.event.custom;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.event.custom.core.IBaseMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigamePhase;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.uhc.UhcManager;
import io.github.brainage04.brainage_minigames.util.ChatUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

public final class CustomEventManager {
    private CustomEventManager() {
    }

    public static boolean start(MinecraftServer server, IBaseMinigame desiredEvent) {
        if (MinigameState.event != null || UhcManager.isRunning() || desiredEvent == null) {
            return false;
        }
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES_LEVEL_KEY);
        if (level == null) {
            BrainageMinigames.LOGGER.error("Cannot start a custom event: the minigames dimension is unavailable");
            return false;
        }

        MinigameState.reset();
        MinigameState.event = desiredEvent;
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "A revival event is starting: %s, %d-%d players per team."
                        .formatted(desiredEvent.getName(), desiredEvent.getMinPlayersPerTeam(), desiredEvent.getMaxPlayersPerTeam())), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChatUtils.sendJoinEventUsage(player);
            ChatUtils.sendWatchEventUsage(player);
            ChatUtils.sendLeaveEventUsage(player);
            if (player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                ChatUtils.sendStopCountdownUsage(player, true);
                ChatUtils.sendNextPhaseUsage(player);
                ChatUtils.sendStopEventUsage(player);
                if (desiredEvent.getMaxPlayersPerTeam() > 1) {
                    ChatUtils.sendMatchTeamsUsage(player);
                }
            }
        }
        return true;
    }

    public static boolean join(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        TeleportTransition spawn = ModDimensions.minigamesSpawn(server);
        if (MinigameState.event == null || spawn == null || !MinigameState.acceptingParticipants) {
            return false;
        }
        if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
            return false;
        }
        if (player.gameMode() == GameType.SPECTATOR) {
            player.sendSystemMessage(Component.literal("Spectators cannot join as participants.")
                    .withStyle(ChatFormatting.RED));
            if (player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                ChatUtils.sendCreativeModeUsage(player);
            }
            return false;
        }
        if (!PlayerSnapshotStorage.save(player, PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE)) {
            return false;
        }

        MinigameState.addParticipant(player);
        player.setGameMode(GameType.ADVENTURE);
        player.getInventory().clearContent();
        player.removeAllEffects();
        PlayerUtils.makeInvulnerable(player);
        ServerPlayer teleported = player.teleport(spawn);
        if (teleported == null) {
            MinigameState.removeParticipant(player);
            PlayerSnapshotStorage.restore(player);
            return false;
        }
        teleported.sendSystemMessage(Component.literal("You joined the event.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean watch(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        TeleportTransition spawn = ModDimensions.minigamesSpawn(server);
        if (MinigameState.event == null || spawn == null || PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
            return false;
        }
        if (!PlayerSnapshotStorage.save(player, PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE)) {
            return false;
        }

        player.setGameMode(GameType.SPECTATOR);
        player.getInventory().clearContent();
        player.removeAllEffects();
        ServerPlayer teleported = player.teleport(spawn);
        if (teleported == null) {
            PlayerSnapshotStorage.restore(player);
            return false;
        }
        teleported.sendSystemMessage(Component.literal("You are now watching the event.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (!PlayerSnapshotStorage.hasSnapshot(server, player.getUUID(), PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE)) {
            return false;
        }
        MinigameState.removeParticipant(player);
        boolean restored = PlayerSnapshotStorage.restore(player);
        if (restored) {
            player.sendSystemMessage(Component.literal("You left the event.").withStyle(ChatFormatting.GREEN));
        }
        return restored;
    }

    public static boolean nextPhase(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES_LEVEL_KEY);
        IBaseMinigame activeEvent = MinigameState.event;
        if (level == null || activeEvent == null) {
            return false;
        }
        if (MinigameState.phase == MinigamePhase.WAITING && !activeEvent.isReadyToStart(level)) {
            String message = activeEvent.getRequirementsToStart();
            BrainageMinigames.LOGGER.error(message);
            if (MinigameState.waitPhaseTicking) {
                stop(server);
            }
            return false;
        }

        int nextOrdinal = MinigameState.phase.ordinal() + 1;
        if (nextOrdinal >= MinigamePhase.values().length) {
            stop(server);
            return false;
        }
        if (MinigameState.phase == MinigamePhase.WAITING) {
            MinigameState.acceptingParticipants = false;
        }
        MinigameState.phase = MinigamePhase.values()[nextOrdinal];
        MinigameState.eventTimer = 0;
        return true;
    }

    public static boolean stopCountdown() {
        if (MinigameState.event == null) {
            return false;
        }
        return switch (MinigameState.phase) {
            case WAITING -> {
                if (!MinigameState.waitPhaseTicking) {
                    yield false;
                }
                MinigameState.waitPhaseTicking = false;
                yield true;
            }
            case POST_WIN -> {
                if (!MinigameState.postWinPhaseTicking) {
                    yield false;
                }
                MinigameState.postWinPhaseTicking = false;
                yield true;
            }
            default -> false;
        };
    }

    public static boolean stop(MinecraftServer server) {
        if (MinigameState.event == null) {
            return false;
        }
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES_LEVEL_KEY);
        if (level != null) {
            MinigameState.cleanUp(level);
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("The revival event has finished. Thanks for playing!"), false);
        PlayerSnapshotStorage.restoreOnlinePlayers(server, PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE);
        MinigameState.reset();
        return true;
    }

    public static boolean eliminate(ServerPlayer player) {
        if (MinigameState.event == null
                || MinigameState.phase != MinigamePhase.PVP
                || !MinigameState.isParticipant(player.getUUID())) {
            return false;
        }
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("You were eliminated from the event.")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    public static boolean allowDamage(ServerPlayer player) {
        return MinigameState.event == null
                || !MinigameState.isParticipant(player.getUUID())
                || MinigameState.phase == MinigamePhase.PVP;
    }

    public static void handleConnect(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID(), PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE)
                && !MinigameState.isParticipant(player.getUUID())) {
            PlayerSnapshotStorage.restore(player);
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID(), PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE)) {
            MinigameState.removeParticipant(player);
            BrainageMinigames.LOGGER.info(
                    "{} left an active custom minigame session",
                    player.getGameProfile().name()
            );
        }
    }

    public static void tick(ServerLevel level) {
        if (level.dimension().equals(ModDimensions.MINIGAMES_LEVEL_KEY)) {
            MinigameState.tick(level);
        }
    }
}
