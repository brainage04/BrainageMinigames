package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class CombatBalanceArrowMixin {
    @ModifyExpressionValue(method = "doKnockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double brainage_minigames$unscaledPunch(double resistance) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        return arrow.getWeaponItem() != null && arrow.getWeaponItem().is(Items.BOW) && CombatRules.classic(arrow.getOwner()) ? 0 : resistance;
    }
}
