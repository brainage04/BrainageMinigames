package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.DeathLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoppers and hopper minecarts cannot turn private death loot, in a chest or on the ground, into public items. */
@Mixin(HopperBlockEntity.class)
abstract class AntiJanitorHopperMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$claimedDrop(net.minecraft.world.Container container,
            net.minecraft.world.entity.item.ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (DeathLoot.claimed(item)) cir.setReturnValue(false);
    }

    // Gate before Fabric's storage fallback / NeoForge's inventory capability lookup.
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$privateLoot(Level level, Hopper hopper,
            CallbackInfoReturnable<Boolean> cir) {
        long source = BlockPos.asLong(net.minecraft.util.Mth.floor(hopper.getLevelX()),
                net.minecraft.util.Mth.floor(hopper.getLevelY() + 1),
                net.minecraft.util.Mth.floor(hopper.getLevelZ()));
        BlockPos destination = hopper instanceof HopperBlockEntity block ? block.getBlockPos() : null;
        if (DeathLoot.protectedChest(level, source)
                || !io.github.brainage04.brainage_minigames.game.ContainerProtection.canExtract(level, source, destination)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$ownedHopper(Level level, BlockPos pos, HopperBlockEntity hopper,
            CallbackInfoReturnable<Boolean> cir) {
        if (!io.github.brainage04.brainage_minigames.game.ContainerProtection.protectsContainers(level)) return;
        BlockPos destination = pos.relative(hopper.getBlockState().getValue(net.minecraft.world.level.block.HopperBlock.FACING));
        if (!io.github.brainage04.brainage_minigames.game.ContainerProtection.canExtract(level, pos, destination)) {
            cir.setReturnValue(false);
        }
    }
}
