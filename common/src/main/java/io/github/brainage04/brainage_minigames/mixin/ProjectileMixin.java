package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells matches when their participants' projectiles hit blocks, before vanilla handles the hit;
 * arrows reach this through {@code super.onHitBlock}.
 */
@Mixin(Projectile.class)
abstract class ProjectileMixin {
    @Inject(method = "onHitBlock", at = @At("HEAD"))
    private void brainage_minigames$matchHitBlock(BlockHitResult hit, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (!projectile.level().isClientSide()) {
            MatchManager.projectileHitBlock(projectile, hit);
        }
    }
}
