package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class UhcExtraArrowMixin {
    @Shadow protected abstract boolean isInGround();
    @Inject(method = "tick", at = @At("HEAD"))
    private void brainage_minigames$home(CallbackInfo ci) {
        if (!isInGround()) UhcExtraRecipes.arrowTick((AbstractArrow) (Object) this);
    }
}
