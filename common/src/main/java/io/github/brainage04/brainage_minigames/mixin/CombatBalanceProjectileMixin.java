package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class CombatBalanceProjectileMixin {
    private boolean brainage_minigames$legacyBow() {
        return (Object) this instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                && arrow.getWeaponItem().is(Items.BOW) && CombatRules.classic(arrow.getOwner());
    }

    @Inject(method = "getMovementToShoot", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$gaussianSpread(double x, double y, double z, float speed, float inaccuracy,
            CallbackInfoReturnable<Vec3> cir) {
        if (!brainage_minigames$legacyBow()) return;
        Projectile arrow = (Projectile) (Object) this;
        var random = arrow.getRandom();
        Vec3 direction = new Vec3(x, y, z).normalize();
        double deviation = 0.0075 * inaccuracy;
        cir.setReturnValue(direction.add(random.nextGaussian() * deviation, random.nextGaussian() * deviation,
                random.nextGaussian() * deviation).scale(speed));
    }

    @WrapOperation(method = "shootFromRotation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getKnownMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 brainage_minigames$noShooterMotion(Entity shooter, Operation<Vec3> original) {
        return brainage_minigames$legacyBow() ? Vec3.ZERO : original.call(shooter);
    }
}
