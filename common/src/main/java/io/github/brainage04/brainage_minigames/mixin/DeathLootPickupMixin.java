package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.DeathLoot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Claimed death drops stay on the ground for everyone but the claim's team. */
@Mixin(ItemEntity.class)
abstract class DeathLootPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$claimedLoot(Player player, CallbackInfo ci) {
        if (!DeathLoot.canPickUp((ItemEntity) (Object) this, player)) ci.cancel();
    }
}
