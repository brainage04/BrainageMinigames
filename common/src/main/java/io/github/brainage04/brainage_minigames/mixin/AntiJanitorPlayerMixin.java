package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only committed damage starts/resets a duel, before the outer hurt method handles death. */
@Mixin(Player.class)
abstract class AntiJanitorPlayerMixin {
    @Unique private float brainage_minigames$healthBefore;
    @Unique private float brainage_minigames$absorptionBefore;

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void brainage_minigames$beforeDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        brainage_minigames$healthBefore = self.getHealth();
        brainage_minigames$absorptionBefore = self.getAbsorptionAmount();
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void brainage_minigames$committedDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && amount > 0
                && (player.getHealth() < brainage_minigames$healthBefore
                || player.getAbsorptionAmount() < brainage_minigames$absorptionBefore)) {
            MatchManager.damaged(player, source);
        }
    }
}
