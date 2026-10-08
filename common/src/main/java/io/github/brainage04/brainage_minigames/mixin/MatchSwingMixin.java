package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells a player's match when they swing their arm: a left-click, at a block, an entity or the air. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class MatchSwingMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void brainage_minigames$swing(ServerboundSwingPacket packet, CallbackInfo ci) {
        MatchManager.swing(player);
    }
}
