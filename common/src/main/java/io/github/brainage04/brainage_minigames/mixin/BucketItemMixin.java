package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies match place rules to emptying buckets, with the fluid's block state; see {@link
 * MatchManager#allowPlace}.
 */
@Mixin(BucketItem.class)
abstract class BucketItemMixin {
    @Shadow @Final protected Fluid content;

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$matchEmpty(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            CallbackInfoReturnable<Boolean> cir) {
        if (content != Fluids.EMPTY
                && user instanceof ServerPlayer player
                && !MatchManager.allowPlace(
                        player, pos, content.defaultFluidState().createLegacyBlock())) {
            PlayerUtils.resyncBlock(player, pos);
            PlayerUtils.resyncInventory(player);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "emptyContents", at = @At("RETURN"))
    private void brainage_minigames$recordFluid(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            CallbackInfoReturnable<Boolean> cir) {
        // Only a fluid block of its own counts as placed; filling a waterloggable map block or
        // the recursive call's neighbour does not.
        if (cir.getReturnValueZ()
                && user instanceof ServerPlayer player
                && level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
            MatchManager.blockPlaced(player, pos);
        }
    }
}
