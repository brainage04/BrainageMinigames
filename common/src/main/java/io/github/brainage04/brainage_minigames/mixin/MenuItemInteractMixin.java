package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.menu.MenuItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking a player with the game menu opens the duel builder against them; a match's game
 * handles its players' right-clicks on entities first (Bed Wars shopkeepers open their shops).
 */
@Mixin(Player.class)
abstract class MenuItemInteractMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$duelFromGameMenu(
            Entity target, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (MatchManager.interactEntity(player, target)
                || target instanceof ServerPlayer other && MenuItems.interact(player, other, player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
