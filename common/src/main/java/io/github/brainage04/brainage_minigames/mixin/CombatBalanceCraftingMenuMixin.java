package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatCrafting;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CraftingMenu.class, InventoryMenu.class})
abstract class CombatBalanceCraftingMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noStaleShiftClick(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index != 0 || !(player instanceof ServerPlayer serverPlayer) || CombatRules.legacyBalance(serverPlayer.level())) return;
        var result = ((AbstractContainerMenu) (Object) this).getSlot(0).container;
        if (result instanceof ResultContainer container && container.getRecipeUsed() != null
                && container.getRecipeUsed().id().equals(CombatCrafting.APPLE_ID)) {
            CombatCrafting.refresh(serverPlayer);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
