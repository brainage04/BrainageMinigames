package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownSplashPotion.class)
abstract class CombatBalanceSplashMixin {
    @Inject(method = "onHitAsPotion", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$legacySplash(ServerLevel level, ItemStack stack, HitResult hit, CallbackInfo ci) {
        ThrownSplashPotion potion = (ThrownSplashPotion) (Object) this;
        if (!CombatRules.classic(potion.getOwner())) return;
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        float durationScale = stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1F);
        var bounds = potion.getBoundingBox().inflate(4, 2, 4);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            double distance = potion.distanceToSqr(target);
            if (!target.isAffectedByPotions() || distance >= 16) continue;
            double strength = hit instanceof EntityHitResult direct && direct.getEntity() == target ? 1 : 1 - Math.sqrt(distance) / 4;
            for (MobEffectInstance effect : contents.getAllEffects()) {
                if (effect.getEffect().value().isInstantaneous()) {
                    effect.getEffect().value().applyInstantaneousEffect(level, potion, potion.getOwner(), target, effect.getAmplifier(), strength);
                } else {
                    MobEffectInstance adjusted = CombatBalance.potionEffect(contents, effect, 1);
                    int duration = adjusted.mapDuration(d -> (int) (strength * Math.ceil(d * 0.75) * durationScale + 0.5));
                    MobEffectInstance applied = new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
                    if (!applied.endsWithin(20)) target.addEffect(applied, potion.getEffectSource());
                }
            }
        }
        ci.cancel();
    }
}
