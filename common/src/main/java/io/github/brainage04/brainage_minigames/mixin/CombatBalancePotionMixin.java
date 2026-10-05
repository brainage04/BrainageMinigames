package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PotionContents.class)
abstract class CombatBalancePotionMixin {
    @Inject(method = "applyToLivingEntity", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$oldPotionDuration(LivingEntity entity, float scale, CallbackInfo ci) {
        if (!CombatRules.classic(entity)) return;
        PotionContents contents = (PotionContents) (Object) this;
        ServerPlayer player = (ServerPlayer) entity;
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().value().isInstantaneous()) {
                effect.getEffect().value().applyInstantaneousEffect(player.level(), player, player, entity, effect.getAmplifier(), 1);
            } else {
                entity.addEffect(UhcEffects.potion(player, CombatBalance.potionEffect(contents, effect, scale)));
            }
        }
        ci.cancel();
    }
}
