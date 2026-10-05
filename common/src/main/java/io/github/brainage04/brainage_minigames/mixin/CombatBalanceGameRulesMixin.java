package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatCrafting;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRules.class)
abstract class CombatBalanceGameRulesMixin {
    @Inject(method = "set", at = @At("TAIL"))
    private void brainage_minigames$liveBalance(GameRule<?> rule, Object value, MinecraftServer server, CallbackInfo ci) {
        if (rule != CombatRules.COMBAT_1_8 || server == null) return;
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.level().getGameRules() != (Object) this) continue;
            CombatBalance.updateInventory(player, CombatRules.classic(player));
            CombatCrafting.refresh(player);
        }
    }
}
