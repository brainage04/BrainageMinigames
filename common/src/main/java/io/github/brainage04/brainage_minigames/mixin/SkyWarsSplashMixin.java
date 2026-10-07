package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Splash potion effects on a SkyWars participant; see {@link SkyWarsPerks#potion}. */
@Mixin(ThrownSplashPotion.class)
abstract class SkyWarsSplashMixin {
    @WrapOperation(method = "onHitAsPotion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean brainage_minigames$skyWarsSplash(LivingEntity target, MobEffectInstance effect, Entity source,
            Operation<Boolean> original) {
        return original.call(target, target instanceof ServerPlayer player ? SkyWarsPerks.potion(player, effect) : effect, source);
    }
}
