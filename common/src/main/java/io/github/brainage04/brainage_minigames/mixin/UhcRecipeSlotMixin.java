package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class UhcRecipeSlotMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$verifyRecipe(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResultSlot) || !(player instanceof ServerPlayer serverPlayer)) return;
        String kind = UhcCrafting.kind(getItem());
        if (kind.isEmpty()) return;
        for (UhcCrafting.Recipe recipe : UhcCrafting.recipes()) if (recipe.id().equals(kind)) {
            if (!UhcProgression.canCraft(serverPlayer, recipe.tree(), recipe.slot(), recipe.id())) cir.setReturnValue(false);
            return;
        }
    }
}
