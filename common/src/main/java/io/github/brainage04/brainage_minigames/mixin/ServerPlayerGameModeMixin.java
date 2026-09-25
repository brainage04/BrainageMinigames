package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies match block rules to breaking blocks, using items and using items on blocks; see {@link
 * MatchManager#allowBreak}, {@link MatchManager#useItem} and {@link MatchManager#allowUseOn}.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerLevel level;

    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$matchBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!MatchManager.allowBreak(player, pos, level.getBlockState(pos))) {
            // The game may have changed the block itself, so send whatever is there now.
            PlayerUtils.resyncBlock(player, pos);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void brainage_minigames$forgetBroken(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            MatchManager.blockBroken(player, pos);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$matchUseItem(
            ServerPlayer user,
            Level useLevel,
            ItemStack stack,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = MatchManager.useItem(user, hand, stack);
        if (result != InteractionResult.PASS) {
            if (result instanceof InteractionResult.Fail) {
                PlayerUtils.resyncInventory(user);
            }
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$matchUseItemOn(
            ServerPlayer user,
            Level useLevel,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!MatchManager.allowUseOn(user)) {
            PlayerUtils.resyncBlock(user, hit.getBlockPos());
            PlayerUtils.resyncBlock(user, hit.getBlockPos().relative(hit.getDirection()));
            PlayerUtils.resyncInventory(user);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
