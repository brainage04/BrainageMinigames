package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Arena slots share a permanently clear, mob-free dimension, not the survival world's rules. */
@Mixin(ServerLevel.class)
abstract class MinigamesLevelMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noArenaMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (((ServerLevel) (Object) this).dimension() == ModDimensions.MINIGAMES
                && entity instanceof Mob && !entity.entityTags().contains("brainage_minigames:combat_logger")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brainage_minigames$initialClearSky(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() == ModDimensions.MINIGAMES) {
            level.setRainLevel(0);
            level.setThunderLevel(0);
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$clearArenaSky(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() == ModDimensions.MINIGAMES) {
            level.setRainLevel(0);
            level.setThunderLevel(0);
            ci.cancel();
        }
    }
}
