package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Block.class)
abstract class UhcOreExperienceMixin {
    @WrapOperation(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void brainage_minigames$miningContext(BlockState state, Level level, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool, Operation<Void> original) {
        if (!(entity instanceof ServerPlayer player) || UhcResourceRules.ore(state) == null
                || UhcProgression.match(player) == null && !SpeedUhc.forces(player)) {
            original.call(state, level, pos, blockEntity, entity, tool);
            return;
        }
        ServerPlayer previous = UhcEffects.MINER.get();
        UhcEffects.MINER.set(player);
        try {
            original.call(state, level, pos, blockEntity, entity, tool);
            UhcEffects.mined(player, state);
        } finally {
            if (previous == null) UhcEffects.MINER.remove(); else UhcEffects.MINER.set(previous);
        }
    }

    @ModifyVariable(method = "popExperience", at = @At("HEAD"), argsOnly = true)
    private int brainage_minigames$magnetism(int experience) {
        ServerPlayer player = UhcEffects.MINER.get();
        return player == null ? experience : SpeedUhc.experience(player, UhcEffects.experience(player, experience));
    }
}
