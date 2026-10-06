package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bots playing in a match are listed as {@code [BOT] name} in the tab list. */
@Mixin(ServerPlayer.class)
public abstract class MatchBotTabNameMixin {
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$botTabName(CallbackInfoReturnable<Component> callback) {
        Component name = MatchManager.botTabName((ServerPlayer) (Object) this);
        if (name != null) callback.setReturnValue(name);
    }
}
