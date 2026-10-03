package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class CombatShieldMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$keepBlockingShield(int slot, int button, ContainerInput input,
            Player player, CallbackInfo ci) {
        if (!CombatRules.shieldLocked(player)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (input == ContainerInput.SWAP && button == Inventory.SLOT_OFFHAND
                || slot >= 0 && slot < menu.slots.size() && CombatRules.shieldSlotLocked(menu.slots.get(slot))) {
            menu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
