package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "dealDefaultKnockback", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$ownerDirectedKnockback(DamageSource source, float damage,
            boolean blocked, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;
        Entity owner = source.getEntity();
        if (!CombatRules.classic(victim) || owner == null) return;
        // Legacy thrown hits point away from the thrower, not along projectile flight velocity.
        victim.knockback(0.4, owner.getX() - victim.getX(), owner.getZ() - victim.getZ(), source, damage);
        if (!blocked) victim.indicateDamage(owner.getX() - victim.getX(), owner.getZ() - victim.getZ());
        ci.cancel();
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$airborneKnockback(double strength, double x, double z,
            DamageSource source, float damage, boolean extra, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (!CombatRules.classic(victim)) return;
        ci.cancel();
        double resistance = victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (strength <= 0 || resistance > 0 && victim.getRandom().nextDouble() < resistance) return;
        while (x * x + z * z < 1.0E-4) {
            x = (victim.getRandom().nextDouble() - victim.getRandom().nextDouble()) * 0.01;
            z = (victim.getRandom().nextDouble() - victim.getRandom().nextDouble()) * 0.01;
        }
        double distance = Math.sqrt(x * x + z * z);
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x / 2 - x / distance * strength,
                Math.min(0.4, motion.y / 2 + strength), motion.z / 2 - z / distance * strength);
        victim.needsSync = true;
    }
}
