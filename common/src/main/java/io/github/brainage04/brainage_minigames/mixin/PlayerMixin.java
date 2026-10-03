package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMixin extends Avatar {
    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$fullStrength(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (CombatRules.noAttackCooldown((Player) (Object) this)) cir.setReturnValue(1.0F);
    }

    // The packet handler also checks MINIMUM_ATTACK_CHARGE against the raw timer.
    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noMinimumCharge(ItemStack weapon, int toleranceTicks,
            CallbackInfoReturnable<Boolean> cir) {
        if (CombatRules.noAttackCooldown((Player) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noSweep(boolean charged, boolean critical, boolean sprint,
            CallbackInfoReturnable<Boolean> cir) {
        if (CombatRules.classic(this)) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "canCriticalAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z"))
    private boolean brainage_minigames$sprintingCritical(boolean sprinting) {
        return sprinting && !CombatRules.classic(this);
    }

    // Player rejects zero damage, but LivingEntity still implements the legacy hit/damage-difference
    // immunity path. Enter that path after Player's invulnerability checks, not with fake damage.
    @Inject(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;removeEntitiesOnShoulder()V",
            shift = At.Shift.AFTER), cancellable = true)
    private void brainage_minigames$zeroDamageProjectile(ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount == 0 && CombatRules.zeroDamageHit((Player) (Object) this, source)) {
            cir.setReturnValue(super.hurtServer(level, source, 0));
        }
    }

    // 1.8 adds sprint/enchantment knockback to the default hit; modern knockback halves it again.
    @WrapOperation(method = "causeExtraKnockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V"))
    private void brainage_minigames$addSprintKnockback(LivingEntity target, double strength,
            double x, double z, DamageSource source, float damage, boolean extra,
            Operation<Void> original) {
        if (CombatRules.classic(this)) {
            target.push(-x * strength, 0.1, -z * strength);
        } else {
            original.call(target, strength, x, z, source, damage, extra);
        }
    }
}
