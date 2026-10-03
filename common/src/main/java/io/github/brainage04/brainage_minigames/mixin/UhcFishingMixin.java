package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
abstract class UhcFishingMixin {
    @Shadow private int nibble;
    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$deep(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        if (nibble > 0 && UhcCrafting.kind(rod).equals("kings_rod") && hook.getPlayerOwner() instanceof ServerPlayer king && UhcProgression.match(king) != null) {
            ItemStack nuggets = new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 3);
            if (!king.getInventory().add(nuggets)) king.drop(nuggets, false);
        }
        if (nibble <= 0 || !UhcCrafting.kind(rod).equals("the_deep") || !(hook.getPlayerOwner() instanceof ServerPlayer player) || UhcProgression.match(player) == null) return;
        ItemStack reward = UhcAdvancedRecipes.deepCatch(player, player.getRandom().nextInt(100), player.getRandom().nextInt(4) == 0);
        if (!reward.isEmpty() && !player.getInventory().add(reward)) player.drop(reward, false);
        hook.discard(); cir.setReturnValue(1);
    }
}
