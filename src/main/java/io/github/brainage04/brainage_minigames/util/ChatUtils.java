package io.github.brainage04.brainage_minigames.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

public final class ChatUtils {
    private ChatUtils() {
    }

    public static String seconds(String prefix, int seconds, String suffix) {
        return prefix + seconds + (seconds == 1 ? " second" : " seconds") + suffix;
    }

    public static void sendCountdown(List<ServerPlayer> players, String prefix, int seconds, String suffix) {
        PlayerUtils.broadcastWithSound(players, seconds(prefix, seconds, suffix), SoundEvents.UI_BUTTON_CLICK.value());
    }

    public static void sendCreativeModeUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Switch to Creative mode to skip this check.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendNextPhaseUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/nextphase to start the event.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendStopEventUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/stopevent to terminate the event.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendJoinEventUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/joinevent to play in the event.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendWatchEventUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/watchevent to spectate the event.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendLeaveEventUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/leaveevent to leave the event.").withStyle(ChatFormatting.GRAY));
    }

    public static void sendMatchTeamsUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("/matchteams add <team1> <team2> to choose the next match.")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void sendStopCountdownUsage(ServerPlayer player, boolean starting) {
        String action = starting ? "start" : "end";
        player.sendSystemMessage(Component.literal(
                "The event will automatically %s in 60 seconds. Run /stopcountdown to %s it manually instead."
                        .formatted(action, action)));
    }

    public static void broadcastServerEvent(
            MinecraftServer server,
            Component title,
            Component subtitle,
            boolean broadcastToChat,
            SoundEvent sound
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.playSound(sound, 1.0F, 1.0F);
        }
        if (broadcastToChat) {
            server.getPlayerList().broadcastSystemMessage(Component.empty().append(title).append(" ").append(subtitle), false);
        }
    }
}
