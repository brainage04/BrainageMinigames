package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extra drops of mobs a Speed UHC participant kills; see {@link SpeedUhc#mobDrops}. */
@Mixin(LivingEntity.class)
abstract class SpeedUhcLootMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void brainage_minigames$speedUhcDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        SpeedUhc.mobDrops((LivingEntity) (Object) this, level, source);
    }
}
