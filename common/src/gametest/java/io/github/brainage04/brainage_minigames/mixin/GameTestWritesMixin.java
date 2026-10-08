package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameTest worlds are thrown away, so their levels need not write region files synchronously. A
 * level asks its server as it is created: the GameTest server always says yes, and a dedicated
 * server says what {@code sync-chunk-writes} in server.properties says (yes by default). Writing
 * synchronously opens every chunk, entity and point-of-interest region file with O_DSYNC, so each
 * chunk save waits for the disk, hundreds of times a second while tests generate terrain.
 */
@Mixin({MinecraftServer.class, DedicatedServer.class})
public abstract class GameTestWritesMixin {
    @Inject(method = "forceSynchronousWrites", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$discardableWorld(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
