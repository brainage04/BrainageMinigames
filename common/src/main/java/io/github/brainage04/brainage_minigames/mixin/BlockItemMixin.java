package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies match place rules to block items, once the final position and state are known; see {@link
 * MatchManager#allowPlace}. A refused placement fails and the client is resynced.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @WrapOperation(
            method = "place",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean brainage_minigames$matchPlace(
            BlockItem item,
            BlockPlaceContext context,
            BlockState state,
            Operation<Boolean> original) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return original.call(item, context, state);
        }
        BlockPos pos = context.getClickedPos();
        BlockPos other = otherPart(pos, state);
        if (!MatchManager.allowPlace(player, pos, state)
                || (other != null && !MatchManager.allowPlace(player, other, state))) {
            PlayerUtils.resyncBlock(player, pos);
            if (other != null) {
                PlayerUtils.resyncBlock(player, other);
            }
            PlayerUtils.resyncInventory(player);
            return false;
        }
        boolean placed = original.call(item, context, state);
        if (placed) {
            MatchManager.blockPlaced(player, pos);
            if (other != null) {
                MatchManager.blockPlaced(player, other);
            }
        }
        return placed;
    }

    /** The second block that placing a two-block state (door, tall plant, bed) fills. */
    private static BlockPos otherPart(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return pos.above();
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return null;
    }
}
