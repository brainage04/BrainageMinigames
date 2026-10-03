package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla checks both halves when opening a double chest, including spectator menus. */
@Mixin(BaseContainerBlockEntity.class)
abstract class AntiJanitorContainerMixin {
    @Inject(method = {"canOpen", "stillValid"}, at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$privateLoot(Player player, CallbackInfoReturnable<Boolean> cir) {
        BaseContainerBlockEntity self = (BaseContainerBlockEntity) (Object) this;
        if (self.getLevel() != null && !AntiJanitor.canOpen(self.getLevel(), self.getBlockPos(), player)) {
            cir.setReturnValue(false);
        }
    }
}
