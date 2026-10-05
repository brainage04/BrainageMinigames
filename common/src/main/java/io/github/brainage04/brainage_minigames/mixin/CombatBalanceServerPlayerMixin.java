package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.CombatCrafting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class CombatBalanceServerPlayerMixin {
    @Unique private boolean brainage_minigames$balanceEnabled;

    @Inject(method = "doTick", at = @At("HEAD"))
    private void brainage_minigames$refreshBalance(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        boolean enabled = CombatRules.legacyBalance(player.level());
        if (enabled != brainage_minigames$balanceEnabled) {
            brainage_minigames$balanceEnabled = enabled;
            CombatCrafting.refresh(player);
        }
    }

    @ModifyArg(method = "jumpFromGround", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private float brainage_minigames$jumpExhaustion(float amount) {
        return CombatRules.classic((ServerPlayer) (Object) this) ? amount * 4 : amount;
    }

    @WrapOperation(method = "checkMovementStatistics", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void brainage_minigames$movementExhaustion(ServerPlayer player, float amount, Operation<Void> original,
            double dx, double dy, double dz) {
        if (CombatRules.classic(player)) {
            if (player.isSwimming() || player.isEyeInFluid(FluidTags.WATER) || player.isInWater()) amount *= 1.5F;
            else if (player.onGround() && !player.isSprinting()) amount = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100) * 0.0001F;
        }
        original.call(player, amount);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
    private void brainage_minigames$plainDroppedSword(ItemStack stack, boolean randomly, boolean thrower,
            CallbackInfoReturnable<ItemEntity> cir) {
        CombatBalance.updateSword(stack, false);
    }
}
