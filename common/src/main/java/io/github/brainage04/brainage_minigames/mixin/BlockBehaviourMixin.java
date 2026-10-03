package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The shared loot path for mining, leaf decay and explosions on both loaders. */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
    @ModifyReturnValue(method = "getDrops", at = @At("RETURN"))
    private List<ItemStack> brainage_minigames$resourceDrops(
            List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        return io.github.brainage04.brainage_minigames.game.uhc.UhcEffects.drops(UhcResourceRules.multiplyDrops(drops, state, params.getLevel()), state, params);
    }
}
