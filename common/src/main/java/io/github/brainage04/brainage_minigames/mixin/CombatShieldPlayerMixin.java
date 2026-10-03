package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class CombatShieldPlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$doNotDropBlockingShield(ItemStack stack, boolean randomly,
            boolean thrower, CallbackInfoReturnable<ItemEntity> cir) {
        if (CombatRules.isBlockingShield(stack)) cir.setReturnValue(null);
    }
}
