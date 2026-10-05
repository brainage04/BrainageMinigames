package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.brainage04.brainage_minigames.game.CombatBalance;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import java.util.function.Consumer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class CombatBalanceStackMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$swordInput(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (level.isClientSide()) return;
        boolean enabled = CombatRules.classic(player);
        CombatBalance.updateSword(stack, enabled);
        if (enabled && (stack.is(Items.SHIELD) || hand == InteractionHand.OFF_HAND && stack.is(ItemTags.SWORDS))) cir.setReturnValue(InteractionResult.FAIL);
    }

    @ModifyExpressionValue(method = "applyDamage*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isBroken()Z"))
    private boolean brainage_minigames$extraDurabilityUse(boolean broken, int damage, @Coerce LivingEntity player, Consumer<Item> onBreak) {
        ItemStack stack = (ItemStack) (Object) this;
        return CombatRules.classic(player) ? stack.isDamageableItem() && damage > stack.getMaxDamage() : broken;
    }
}
