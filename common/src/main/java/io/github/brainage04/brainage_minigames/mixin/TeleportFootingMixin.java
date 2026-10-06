package io.github.brainage04.brainage_minigames.mixin;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A teleported player finds their footing where they land. The block a player last stood on stays
 * recorded across a teleport, and the next tick's movement reads it again in the player's new
 * level: after a dimension change, at the same coordinates in the new dimension, which the server
 * then loads, or even generates, on the server thread while every player waits. Moving players
 * into and out of UHC, Meetup and FinalUHC regions, and through nether portals, would stall the
 * server for seconds each time.
 */
@Mixin(ServerPlayer.class)
abstract class TeleportFootingMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"))
    private void brainage_minigames$forgetFooting(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer moved = cir.getReturnValue();
        if (moved != null) moved.mainSupportingBlockPos = Optional.empty();
    }
}
