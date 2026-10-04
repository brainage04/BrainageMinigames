package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.ContainerProtection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also blocks an already-open menu when protection is enabled during the match. */
@Mixin(Slot.class)
abstract class ContainerSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$ownedContents(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!ContainerProtection.canTake(((Slot) (Object) this).container, player)) {
            cir.setReturnValue(false);
        }
    }
}
