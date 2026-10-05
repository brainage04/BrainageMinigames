package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Turns natural regeneration off for players in a match that disables it, exactly as the {@code
 * natural_health_regeneration} game rule would for them alone: hunger still drains and starves.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
    @ModifyExpressionValue(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"),
            slice =
                    @Slice(
                            from =
                                    @At(
                                            value = "FIELD",
                                            target =
                                                    "Lnet/minecraft/world/level/gamerules/GameRules;NATURAL_HEALTH_REGENERATION:Lnet/minecraft/world/level/gamerules/GameRule;")))
    private Object brainage_minigames$matchRegeneration(
            Object naturalRegeneration, @Local(argsOnly = true) ServerPlayer player) {
        return MatchManager.naturalRegenerationDisabled(player)
                ? Boolean.FALSE
                : naturalRegeneration;
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z", ordinal = 0))
    private boolean brainage_minigames$noSaturationHealing(boolean hurt, @Local(argsOnly = true) ServerPlayer player) {
        return hurt && !CombatRules.classic(player);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V", ordinal = 1))
    private float brainage_minigames$regenerationExhaustion(float amount, @Local(argsOnly = true) ServerPlayer player) {
        return CombatRules.classic(player) ? 3F : amount;
    }
}
