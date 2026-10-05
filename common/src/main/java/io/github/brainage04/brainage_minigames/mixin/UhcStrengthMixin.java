package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class UhcStrengthMixin {
    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double brainage_minigames$strengthPercent(double damage) {
        if (!((Object) this instanceof ServerPlayer player) || UhcProgression.match(player) == null || CombatRules.classic(player)) return damage;
        var strength = player.getEffect(MobEffects.STRENGTH);
        if (strength == null || strength.getAmplifier() > 1) return damage;
        int level = strength.getAmplifier() + 1;
        // UHC's modern-mode Strength scales the unmodified weapon damage by 30% per level.
        return Math.max(0, damage - 3 * level) * (1 + 0.3 * level);
    }
}
