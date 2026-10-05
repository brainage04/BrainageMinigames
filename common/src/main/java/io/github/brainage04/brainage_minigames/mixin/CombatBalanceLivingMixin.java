package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class CombatBalanceLivingMixin {
    @ModifyReturnValue(method = "getAttributeValue", at = @At("RETURN"))
    private double brainage_minigames$legacyAttack(double vanilla, Holder<Attribute> attribute) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!attribute.equals(Attributes.ATTACK_DAMAGE) || !CombatRules.classic(entity)) return vanilla;
        var instance = entity.getAttribute(attribute);
        return instance == null ? vanilla : CombatBalance.attackDamage(entity, instance);
    }

    @WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
    private float brainage_minigames$flatArmor(LivingEntity victim, float damage, DamageSource source,
            float armor, float toughness, Operation<Float> original) {
        return CombatRules.legacyDamage(victim, source) ? damage * (1 - CombatBalance.armorReduction(armor))
                : original.call(victim, damage, source, armor, toughness);
    }

    @Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noShieldDamage(ServerLevel level, DamageSource source, float damage,
            CallbackInfoReturnable<Float> cir) {
        if (CombatRules.classic((Entity) (Object) this)) cir.setReturnValue(0F);
    }

    @WrapOperation(method = "igniteForTicks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;igniteForTicks(I)V"))
    private void brainage_minigames$highestFireProtection(LivingEntity entity, int modifiedTicks,
            Operation<Void> original, int ticks) {
        if (CombatRules.classic(entity)) {
            int baseTicks = Mth.ceil(ticks * CombatRules.attributeWithoutEnchantment(entity, Attributes.BURNING_TIME, "enchantment.fire_protection"));
            modifiedTicks = CombatBalance.fireDuration(baseTicks, CombatBalance.highestEnchantment(entity, Enchantments.FIRE_PROTECTION));
        }
        original.call(entity, modifiedTicks);
    }
}
