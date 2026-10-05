package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UseCooldown.class)
abstract class CombatBalanceCooldownMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noPearlCooldown(ItemStack stack, LivingEntity user, CallbackInfo ci) {
        if (CombatRules.classic(user) && stack.is(Items.ENDER_PEARL)) ci.cancel();
    }
}
