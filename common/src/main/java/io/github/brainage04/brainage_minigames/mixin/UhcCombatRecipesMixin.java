package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class UhcCombatRecipesMixin {
    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean brainage_minigames$trueDamage(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
        // A proc follows the landed hit; vanilla's half-window would otherwise subtract that hit.
        return tag == DamageTypeTags.BYPASSES_COOLDOWN && source.is(DamageTypes.GENERIC_KILL)
                && source.getEntity() instanceof ServerPlayer attacker && UhcProgression.match(attacker) != null
                || original.call(source, tag);
    }
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float brainage_minigames$swan(float amount, ServerLevel level, DamageSource source, float original) { return UhcAdvancedRecipes.swanDamage(source, amount); }
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void brainage_minigames$quiet(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this instanceof ServerPlayer player) UhcAdvancedRecipes.hurt(player);
        if (cir.getReturnValueZ()) UhcExtraRecipes.hit((LivingEntity) (Object) this, source);
    }
}
