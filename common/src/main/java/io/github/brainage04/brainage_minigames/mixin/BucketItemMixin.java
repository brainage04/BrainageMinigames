package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
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
 * Applies match place rules to emptying buckets, with the fluid's block state, and records the
 * fluid as placed; see {@link MatchManager#allowPlace}. Vanilla (Fabric) empties a bucket through
 * {@code emptyContents} with four arguments; NeoForge adds a fifth, the bucket stack, and its
 * {@code use} calls that one directly, so each loader's variant is hooked and the other is absent.
 */
@Mixin(BucketItem.class)
abstract class BucketItemMixin {
    private static final String EMPTY =
            "emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)Z";
    private static final String EMPTY_WITH_CONTAINER =
            "emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;"
                    + "Lnet/minecraft/world/item/ItemStack;)Z";

    @Shadow @Final protected Fluid content;

    @Inject(method = EMPTY, at = @At("HEAD"), cancellable = true, require = 0)
    private void brainage_minigames$matchEmpty(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            CallbackInfoReturnable<Boolean> cir) {
        brainage_minigames$refuse(user, pos, cir);
    }

    @Inject(method = EMPTY_WITH_CONTAINER, at = @At("HEAD"), cancellable = true, require = 0)
    private void brainage_minigames$matchEmptyContainer(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            ItemStack container,
            CallbackInfoReturnable<Boolean> cir) {
        brainage_minigames$refuse(user, pos, cir);
    }

    @Inject(method = EMPTY, at = @At("RETURN"), require = 0)
    private void brainage_minigames$recordFluid(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            CallbackInfoReturnable<Boolean> cir) {
        brainage_minigames$record(user, level, pos, cir);
    }

    @Inject(method = EMPTY_WITH_CONTAINER, at = @At("RETURN"), require = 0)
    private void brainage_minigames$recordFluidContainer(
            LivingEntity user,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            ItemStack container,
            CallbackInfoReturnable<Boolean> cir) {
        brainage_minigames$record(user, level, pos, cir);
    }

    private void brainage_minigames$refuse(LivingEntity user, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (content != Fluids.EMPTY
                && user instanceof ServerPlayer player
                && !MatchManager.allowPlace(
                        player, pos, content.defaultFluidState().createLegacyBlock())) {
            PlayerUtils.resyncBlock(player, pos);
            PlayerUtils.resyncInventory(player);
            cir.setReturnValue(false);
        }
    }

    private static void brainage_minigames$record(
            LivingEntity user, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Only a fluid block of its own counts as placed; filling a waterloggable map block or
        // the recursive call's neighbour does not.
        if (cir.getReturnValueZ()
                && user instanceof ServerPlayer player
                && level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
            MatchManager.blockPlaced(player, pos);
        }
    }
}
