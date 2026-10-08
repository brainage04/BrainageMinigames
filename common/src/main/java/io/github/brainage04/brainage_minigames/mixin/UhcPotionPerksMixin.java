package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PotionContents.class)
abstract class UhcPotionPerksMixin {
    @WrapOperation(method = "lambda$applyToLivingEntity$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private static boolean brainage_minigames$endurance(LivingEntity target, MobEffectInstance effect, Operation<Boolean> original) {
        return original.call(target, target instanceof ServerPlayer player
                ? UhcEffects.potion(player, io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks.potion(player,
                        io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc.potion(player, effect)))
                : effect);
    }
}
