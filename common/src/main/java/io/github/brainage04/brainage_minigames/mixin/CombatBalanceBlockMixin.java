package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Block.class)
abstract class CombatBalanceBlockMixin {
    @ModifyArg(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private float brainage_minigames$miningExhaustion(float amount, @Local(argsOnly = true) Player player) {
        return CombatRules.classic(player) ? 0.025F : amount;
    }
}
