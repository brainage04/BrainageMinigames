package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Damage a SkyWars participant takes, for SkyWars perks, abilities and rules; see {@link SkyWarsPerks#hurt}. */
@Mixin(ServerPlayer.class)
abstract class SkyWarsPerksMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float brainage_minigames$skyWarsHurt(float amount, ServerLevel level, DamageSource source, float original) {
        return SkyWarsPerks.hurt((ServerPlayer) (Object) this, source, amount);
    }
}
