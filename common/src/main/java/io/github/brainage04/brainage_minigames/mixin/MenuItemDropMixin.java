package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.menu.MenuItems;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Menu items stay in their slot when dropped from the hotbar and are never thrown anywhere. */
@Mixin(ServerPlayer.class)
abstract class MenuItemDropMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$keepSelectedMenuItem(boolean all, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (MenuItems.isMenuItem(player.getInventory().getSelectedItem())) {
            PlayerUtils.resyncInventory(player);
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void brainage_minigames$refuseMenuItemDrop(
            ItemStack stack,
            boolean randomly,
            boolean thrownFromHand,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (MenuItems.refuseDrop((ServerPlayer) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
