package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Instant brewing inside a Speed UHC's border: a started brew finishes on the stand's next tick. */
@Mixin(BrewingStandBlockEntity.class)
abstract class SpeedUhcBrewingMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void brainage_minigames$instantBrew(Level level, BlockPos pos, BlockState state,
            BrewingStandBlockEntity stand, CallbackInfo ci) {
        BrewingStandAccess access = (BrewingStandAccess) stand;
        if (access.brainage_minigames$brewTime() > 1 && SpeedUhc.instantBrewing(level, pos)) {
            access.brainage_minigames$setBrewTime(1);
        }
    }
}
