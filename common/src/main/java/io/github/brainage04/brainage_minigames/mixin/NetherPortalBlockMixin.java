package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcNether;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Links UHC-style dimension pairs. Vanilla finds or builds the exit near scaled coordinates,
 * constrained by the participant's match-local destination border.
 */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {
    @Shadow
    private TeleportTransition getExitPortal(
            ServerLevel newLevel,
            Entity entity,
            BlockPos portalEntryPos,
            BlockPos approximateExitPos,
            boolean toNether,
            WorldBorder worldBorder) {
        throw new AssertionError();
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$uhcNether(
            ServerLevel currentLevel,
            Entity entity,
            BlockPos portalEntryPos,
            CallbackInfoReturnable<TeleportTransition> cir) {
        if (!UhcNether.isLinked(currentLevel)) {
            return;
        }
        ServerLevel newLevel = UhcNether.destination(currentLevel, entity);
        if (newLevel == null) {
            cir.setReturnValue(null);
            return;
        }
        boolean toNether = ModDimensions.nether(newLevel.dimension());
        WorldBorder newWorldBorder = UhcNether.destinationBorder(newLevel, entity);
        double scale =
                DimensionType.getTeleportationScale(
                        currentLevel.dimensionType(), newLevel.dimensionType());
        BlockPos approximateExitPos =
                newWorldBorder.clampToBounds(
                        entity.getX() * scale, entity.getY(), entity.getZ() * scale);
        cir.setReturnValue(
                getExitPortal(
                        newLevel,
                        entity,
                        portalEntryPos,
                        approximateExitPos,
                        toNether,
                        newWorldBorder));
    }
}
