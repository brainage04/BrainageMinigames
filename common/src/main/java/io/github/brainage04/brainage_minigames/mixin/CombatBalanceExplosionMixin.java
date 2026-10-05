package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerExplosion.class)
abstract class CombatBalanceExplosionMixin {
    @WrapOperation(method = "hurtEntities*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double brainage_minigames$removeStackedBlast(LivingEntity entity, Holder<Attribute> attribute, Operation<Double> original) {
        return CombatRules.classic(entity) ? CombatRules.attributeWithoutEnchantment(entity, attribute, "enchantment.blast_protection") : original.call(entity, attribute);
    }

    @ModifyArg(method = "hurtEntities*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private double brainage_minigames$highestBlastProtection(double impulse, @Local Entity entity) {
        return CombatRules.classic(entity) && entity instanceof LivingEntity living
                ? CombatBalance.blastImpulse(impulse, CombatBalance.highestEnchantment(living, Enchantments.BLAST_PROTECTION)) : impulse;
    }
}
