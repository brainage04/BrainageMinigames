package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class UhcHeadPerksMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$head(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!UhcCrafting.kind(stack).equals("golden_head")) return;
        if (!(user instanceof ServerPlayer player) || UhcProgression.match(player) == null) {
            cir.setReturnValue(stack);
        } else {
            UhcCrafting.celerity(player);
        }
    }
}
