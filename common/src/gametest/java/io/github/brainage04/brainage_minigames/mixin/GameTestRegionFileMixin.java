package io.github.brainage04.brainage_minigames.mixin;

import java.nio.channels.FileChannel;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GameTest worlds are thrown away, so flushing or closing a region file does not wait for the disk either: vanilla
 * forces every region file to disk as it is flushed and closed, a burst of hundreds of fsyncs as each GameTest server
 * stops. See {@link GameTestWritesMixin}.
 */
@Mixin(RegionFile.class)
public abstract class GameTestRegionFileMixin {
    @Redirect(method = {"flush", "close"},
            at = @At(value = "INVOKE", target = "Ljava/nio/channels/FileChannel;force(Z)V"))
    private void brainage_minigames$discardableWorld(FileChannel file, boolean metaData) {
        // Nothing to wait for.
    }
}
