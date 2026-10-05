package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentHelper.class)
abstract class CombatBalanceEnchantmentMixin {
    @ModifyReturnValue(method = "modifyDamage", at = @At("RETURN"))
    private static float brainage_minigames$sharpness(float vanilla, ServerLevel level, ItemStack weapon,
            Entity victim, DamageSource source, float damage) {
        if (!CombatRules.classic(source.getEntity())) return vanilla;
        int sharpness = CombatBalance.enchantmentLevel(weapon, Enchantments.SHARPNESS);
        return sharpness == 0 ? vanilla : vanilla + 1.25F * sharpness - (0.5F * sharpness + 0.5F);
    }

    @ModifyReturnValue(method = "getDamageProtection", at = @At("RETURN"))
    private static float brainage_minigames$sharedProtection(float vanilla, ServerLevel level,
            LivingEntity victim, DamageSource source) {
        return CombatRules.legacyDamage(victim, source) ? CombatBalance.protection(victim, source, vanilla) : vanilla;
    }

    @Inject(method = "lambda$doPostAttackEffectsWithItemSourceOnBreak$1", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$bane(ServerLevel level, Entity victim, DamageSource source,
            Holder<Enchantment> enchantment, int rank, EnchantedItemInUse item, CallbackInfo ci) {
        if (!enchantment.is(Enchantments.BANE_OF_ARTHROPODS) || !CombatRules.classic(source.getEntity())) return;
        ci.cancel();
        if (victim instanceof LivingEntity living && victim.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)
                && source.getDirectEntity() == source.getEntity()) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 + living.getRandom().nextInt(10 * rank), 3));
        }
    }
}
