package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
abstract class CombatBalancePlayerMixin {
    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"), index = 1)
    private float brainage_minigames$swordBlock(float damage, @Local(argsOnly = true) DamageSource source) {
        Player player = (Player) (Object) this;
        return damage > 0 && CombatBalance.swordBlocking(player) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                ? CombatBalance.blockedDamage(damage) : damage;
    }

    @ModifyArg(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private float brainage_minigames$attackExhaustion(float amount) {
        return CombatRules.classic((Entity) (Object) this) ? 0.3F : amount;
    }

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;getFoodExhaustion()F"))
    private float brainage_minigames$damageExhaustion(float amount) {
        return CombatRules.classic((Entity) (Object) this) && amount == 0.1F ? 0.3F : amount;
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean brainage_minigames$fireAspectBeforeHit(Entity victim, DamageSource source, float damage,
            Operation<Boolean> original) {
        Player attacker = (Player) (Object) this;
        boolean preIgnite = CombatRules.classic(attacker) && !victim.isOnFire()
                && CombatBalance.enchantmentLevel(attacker.getMainHandItem(), Enchantments.FIRE_ASPECT) > 0;
        if (preIgnite) victim.igniteForSeconds(1);
        boolean accepted = original.call(victim, source, damage);
        if (preIgnite && !accepted) victim.clearFire();
        return accepted;
    }
}
