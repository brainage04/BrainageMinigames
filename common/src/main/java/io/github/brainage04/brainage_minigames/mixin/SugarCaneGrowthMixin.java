package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Records sugar cane that grows during play so it keeps vanilla drops; see {@link
 * UhcResourceRules#sugarCaneGrown}.
 */
@Mixin(SugarCaneBlock.class)
abstract class SugarCaneGrowthMixin {
    @WrapOperation(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean brainage_minigames$recordGrowth(
            ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        boolean grown = original.call(level, pos, state);
        if (grown) UhcResourceRules.sugarCaneGrown(level, pos);
        return grown;
    }
}
