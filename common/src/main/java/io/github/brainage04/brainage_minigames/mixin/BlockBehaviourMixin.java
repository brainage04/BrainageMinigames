package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brainage04.brainage_minigames.game.DeathLoot;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The shared loot path for mining, leaf decay and explosions on both loaders. */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
    @ModifyReturnValue(method = "getDrops", at = @At("RETURN"))
    private List<ItemStack> brainage_minigames$resourceDrops(
            List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        return io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks.drops(
                io.github.brainage04.brainage_minigames.game.uhc.UhcEffects.drops(
                        UhcResourceRules.multiplyDrops(drops, state, params), state, params),
                state, params);
    }

    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$protectedChest(BlockState state, ServerLevel level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (DeathLoot.protectedChest(level, pos)) ci.cancel();
    }
}
