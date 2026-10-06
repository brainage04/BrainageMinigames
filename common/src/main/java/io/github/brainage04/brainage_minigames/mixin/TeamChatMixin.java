package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sends a player's chat to their team alone while their team match is being played, instead of
 * to everyone; see {@link MatchManager#teamChat}. Chat spam detection still counts it.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class TeamChatMixin {
    @WrapWithCondition(
            method = "broadcastChatMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    private boolean brainage_minigames$teamChat(
            PlayerList players, PlayerChatMessage message, ServerPlayer sender, ChatType.Bound chatType) {
        return !MatchManager.teamChat(sender, message);
    }
}
