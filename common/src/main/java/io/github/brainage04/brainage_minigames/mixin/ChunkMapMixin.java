package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.dimension.DiscardedWrites;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leaves unwritten the chunks that {@link DiscardedWrites} skips while the server stops. */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Shadow @Final ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$discardedDimension(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (DiscardedWrites.chunks(level)) cir.setReturnValue(false);
    }
}
