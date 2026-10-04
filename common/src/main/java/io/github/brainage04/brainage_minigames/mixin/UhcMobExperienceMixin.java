package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.UhcCombatLogger;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class UhcMobExperienceMixin {
    @Shadow protected boolean dead;

    @WrapMethod(method = "die")
    private void brainage_minigames$hostileCoins(DamageSource source, Operation<Void> original) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (dead || !(victim instanceof Enemy) || UhcCombatLogger.participant(victim) != null) {
            original.call(source);
            return;
        }
        LivingEntity credit = source.getEntity() instanceof ServerPlayer player ? player : victim.getKillCredit();
        original.call(source);
        if (dead && credit instanceof ServerPlayer player) UhcProgression.hostileKilled(player);
    }

    @WrapOperation(method = "dropExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void brainage_minigames$magnetism(ServerLevel level, Vec3 pos, int experience,
            Operation<Void> original, ServerLevel enclosingLevel, Entity killer) {
        original.call(level, pos, killer instanceof ServerPlayer player ? UhcEffects.experience(player, experience) : experience);
    }
}
