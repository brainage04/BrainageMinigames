package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Damage a Speed UHC participant takes, for its perks and Masteries; see {@link SpeedUhc#hurt}. */
@Mixin(ServerPlayer.class)
abstract class SpeedUhcHurtMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float brainage_minigames$speedUhcHurt(float amount, ServerLevel level, DamageSource source, float original) {
        return SpeedUhc.hurt((ServerPlayer) (Object) this, source, amount);
    }
}
