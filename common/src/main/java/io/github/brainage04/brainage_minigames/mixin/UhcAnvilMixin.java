package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
abstract class UhcAnvilMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;
    @Shadow private int repairItemCountCost;
    @Unique private ItemStack brainage_minigames$input = ItemStack.EMPTY;
    @Unique private ItemStack brainage_minigames$book = ItemStack.EMPTY;
    @Unique private ItemStack brainage_minigames$result = ItemStack.EMPTY;

    protected UhcAnvilMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) { super(type, id, inventory, access, slots); }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void brainage_minigames$specialBook(CallbackInfo ci) {
        ItemStack item = inputSlots.getItem(0), book = inputSlots.getItem(1);
        if (UhcAdvancedRecipes.anvilForbidden(item) || UhcAdvancedRecipes.anvilForbidden(book)) { resultSlots.setItem(0, ItemStack.EMPTY); cost.set(0); return; }
        if (!UhcCrafting.kind(book).equals("enhancement_book") && UhcAdvancedRecipes.swanLevel(book) == 0) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!ItemStack.matches(item, brainage_minigames$input) || !ItemStack.matches(book, brainage_minigames$book)) {
            brainage_minigames$input = item.copy(); brainage_minigames$book = book.copy();
            brainage_minigames$result = UhcAdvancedRecipes.anvil(serverPlayer, item, book);
        }
        resultSlots.setItem(0, brainage_minigames$result.copy());
        cost.set(brainage_minigames$result.isEmpty() ? 0 : 3); repairItemCountCost = 1;
        broadcastChanges();
    }
}
