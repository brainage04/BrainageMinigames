package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownSplashPotion.class)
abstract class UhcVelocityMixin {
    @Inject(method = "onHitAsPotion", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$selfSplash(ServerLevel level, ItemStack potion, HitResult hit, CallbackInfo ci) {
        var entity = (ThrownSplashPotion) (Object) this;
        if (UhcExtraRecipes.splash(level, entity.getOwner() instanceof net.minecraft.world.entity.LivingEntity owner ? owner : null, potion, hit.getLocation())) { ci.cancel(); return; }
        if (!UhcCrafting.kind(potion).equals("velocity")) return;
        if (((ThrownSplashPotion) (Object) this).getOwner() instanceof ServerPlayer player && UhcProgression.match(player) != null) {
            for (var effect : potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) player.addEffect(UhcEffects.potion(player, effect));
        }
        ci.cancel();
    }
}
