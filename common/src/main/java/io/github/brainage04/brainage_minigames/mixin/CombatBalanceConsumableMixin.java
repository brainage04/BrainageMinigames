package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Consumable.class)
abstract class CombatBalanceConsumableMixin {
    @Unique private static final List<ConsumeEffect> brainage_minigames$appleEffects = List.of(
            new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 600, 4),
                    new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0),
                    new MobEffectInstance(MobEffects.RESISTANCE, 6000, 0),
                    new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0))));

    @ModifyExpressionValue(method = "onConsume", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/item/component/Consumable;onConsumeEffects:Ljava/util/List;"))
    private List<ConsumeEffect> brainage_minigames$consumeTimeApple(List<ConsumeEffect> vanilla,
            @Local(argsOnly = true) LivingEntity user, @Local(argsOnly = true) ItemStack stack) {
        return CombatRules.classic(user) && stack.is(Items.ENCHANTED_GOLDEN_APPLE) ? brainage_minigames$appleEffects : vanilla;
    }
}
