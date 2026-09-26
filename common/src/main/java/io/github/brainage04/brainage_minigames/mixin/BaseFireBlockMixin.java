package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcNether;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets fire light nether portals in the UHC dimension and the UHC nether; vanilla only allows it in
 * the Overworld and the Nether.
 */
@Mixin(BaseFireBlock.class)
abstract class BaseFireBlockMixin {
    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$uhcPortals(
            Level level, CallbackInfoReturnable<Boolean> cir) {
        if (UhcNether.isLinked(level)) {
            cir.setReturnValue(true);
        }
    }
}
