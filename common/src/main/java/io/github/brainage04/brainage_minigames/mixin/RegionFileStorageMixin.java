package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.dimension.DiscardedWrites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops region-file writes (chunks, entities, points of interest) that {@link DiscardedWrites} skips
 * while the server stops, including writes queued while it was running.
 */
@Mixin(RegionFileStorage.class)
abstract class RegionFileStorageMixin {
    @Shadow @Final private RegionStorageInfo info;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$discardedDimension(ChunkPos pos, @Nullable CompoundTag value, CallbackInfo ci) {
        if (DiscardedWrites.writes(info.dimension())) ci.cancel();
    }
}
