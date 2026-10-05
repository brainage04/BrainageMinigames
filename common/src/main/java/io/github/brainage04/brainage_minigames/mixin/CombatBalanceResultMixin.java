package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatCrafting;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class CombatBalanceResultMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noStaleApple(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && slot.container instanceof ResultContainer result
                && result.getRecipeUsed() != null && result.getRecipeUsed().id().equals(CombatCrafting.APPLE_ID)
                && !CombatRules.legacyBalance(serverPlayer.level())) {
            CombatCrafting.refresh(serverPlayer);
            cir.setReturnValue(false);
        }
    }
}
