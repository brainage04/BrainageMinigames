package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class CombatShieldSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$keepBlockingShield(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (CombatRules.shieldSlotLocked((Slot) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$reserveOffhand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CombatRules.shieldSlotLocked((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
