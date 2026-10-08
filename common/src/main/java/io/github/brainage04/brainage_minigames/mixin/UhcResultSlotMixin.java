package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
abstract class UhcResultSlotMixin {
    @Shadow @Final private CraftingContainer craftSlots;
    @Shadow @Final private Player player;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void brainage_minigames$countCraft(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || UhcProgression.match(serverPlayer) == null) return;
        UhcCrafting.Recipe recipe = UhcCrafting.matching(craftSlots.asCraftInput(), UhcCrafting.smelted(serverPlayer));
        if (recipe != null) {
            UhcProgression.crafted(serverPlayer, recipe.id());
            io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes.crafted(serverPlayer, recipe.id());
            if (recipe.id().equals("deus_ex_machina")) serverPlayer.setHealth(serverPlayer.getHealth() * 0.5f);
        }
    }

    @Inject(method = "getRemainingItems", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$consumeIngredients(CraftingInput input, Level level,
            CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        if (player instanceof ServerPlayer serverPlayer && UhcProgression.match(serverPlayer) != null
                && UhcCrafting.matching(input, UhcCrafting.smelted(serverPlayer)) != null) cir.setReturnValue(UhcCrafting.remainders(input));
    }
}
