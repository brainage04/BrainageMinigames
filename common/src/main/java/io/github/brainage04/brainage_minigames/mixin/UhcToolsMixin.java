package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
abstract class UhcToolsMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @WrapMethod(method = "destroyBlock")
    private boolean brainage_minigames$tree(BlockPos pos, Operation<Boolean> original) {
        BlockState before = level.getBlockState(pos);
        var tool = player.getMainHandItem();
        int damage = tool.getDamageValue();
        boolean destroyed = original.call(pos);
        if (destroyed) UhcAdvancedRecipes.mined(player, pos, before);
        if (destroyed) UhcExtraRecipes.mined(player, tool, damage);
        return destroyed;
    }
}
