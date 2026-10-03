package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
abstract class UhcCraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$uhcRecipe(AbstractContainerMenu menu, ServerLevel level,
            Player player, CraftingContainer grid, ResultContainer result,
            RecipeHolder<CraftingRecipe> previous, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || UhcProgression.match(serverPlayer) == null) return;
        UhcCrafting.Recipe recipe = UhcCrafting.matching(grid.asCraftInput());
        if (recipe == null) return;
        ItemStack output = UhcCrafting.preview(serverPlayer, recipe);
        result.setRecipeUsed(null);
        result.setItem(0, output);
        menu.setRemoteSlot(0, output);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, output));
        ci.cancel();
    }
}
