package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** Arena slots share a permanently clear, mob-free dimension, not the survival world's rules. */
@Mixin(ServerLevel.class)
abstract class MinigamesLevelMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$noArenaMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (brainage_minigames$arenaMob(entity)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Mobs that come in with a chunk's saved or generated entities do not go through {@code addEntity}; they are
     * discarded as they arrive too, loaded chunk or not, so no sweep of a map has to find them later.
     */
    @ModifyVariable(method = {"addLegacyChunkEntities", "addWorldGenChunkEntities"}, at = @At("HEAD"), argsOnly = true)
    private Stream<Entity> brainage_minigames$noLoadedArenaMobs(Stream<Entity> entities) {
        return entities.filter(entity -> {
            if (!brainage_minigames$arenaMob(entity)) return true;
            entity.discard();
            return false;
        });
    }

    @Unique
    private boolean brainage_minigames$arenaMob(Entity entity) {
        return ((ServerLevel) (Object) this).dimension() == ModDimensions.MINIGAMES
                && entity instanceof Mob && !entity.entityTags().contains("brainage_minigames:combat_logger");
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
