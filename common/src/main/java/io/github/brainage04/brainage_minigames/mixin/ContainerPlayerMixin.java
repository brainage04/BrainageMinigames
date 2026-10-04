package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.ContainerProtection;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures successful menu opens even for container classes that override createMenu. */
@Mixin(ServerPlayer.class)
abstract class ContainerPlayerMixin {
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("RETURN"))
    private void brainage_minigames$containerOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent() && provider instanceof BlockEntity entity) {
            ContainerProtection.used((ServerPlayer) (Object) this, entity.getBlockPos());
        }
    }
}
