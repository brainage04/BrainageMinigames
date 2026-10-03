package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protects against fire/fluid replacement and non-player destruction, without dropping duplicates. */
@Mixin(Level.class)
abstract class AntiJanitorLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$retainChest(BlockPos pos, BlockState state, int flags, int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (AntiJanitor.protectedChest(self, pos) && state.getBlock() != self.getBlockState(pos).getBlock()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$protectLoot(BlockPos pos, boolean drops, Entity entity, int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        if (AntiJanitor.protectedChest((Level) (Object) this, pos)) cir.setReturnValue(false);
    }
}
