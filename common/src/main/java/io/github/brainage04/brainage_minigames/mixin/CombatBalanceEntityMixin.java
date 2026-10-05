package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class CombatBalanceEntityMixin {
    @ModifyExpressionValue(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInLava()Z", ordinal = 0))
    private boolean brainage_minigames$burnWhileInLava(boolean inLava) {
        return inLava && !CombatRules.classic((Entity) (Object) this);
    }
}
