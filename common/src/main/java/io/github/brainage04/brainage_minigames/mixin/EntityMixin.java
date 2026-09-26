package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.uhc.UhcNether;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps spectators watching a player who goes between the UHC dimension and its nether; see {@link
 * UhcNether#followAfterTravel}.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @WrapOperation(
            method = "teleportSpectators",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;setCamera(Lnet/minecraft/world/entity/Entity;)V"))
    private void brainage_minigames$keepWatching(
            ServerPlayer spectator,
            Entity camera,
            Operation<Void> original,
            @Local(argsOnly = true) ServerLevel oldLevel) {
        original.call(spectator, camera);
        if ((Object) this instanceof ServerPlayer target) {
            UhcNether.followAfterTravel(spectator, target, oldLevel);
        }
    }
}
