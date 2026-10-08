package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcChainBreaks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Timber and Vein Miner drop every block's items and experience where the first block broke. */
@Mixin(Block.class)
abstract class UhcChainDropMixin {
    @ModifyVariable(
            method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), argsOnly = true)
    private static BlockPos brainage_minigames$chainItems(BlockPos pos) {
        return UhcChainBreaks.dropPosition(pos);
    }

    @ModifyVariable(method = "popExperience", at = @At("HEAD"), argsOnly = true)
    private BlockPos brainage_minigames$chainExperience(BlockPos pos) {
        return UhcChainBreaks.dropPosition(pos);
    }
}
