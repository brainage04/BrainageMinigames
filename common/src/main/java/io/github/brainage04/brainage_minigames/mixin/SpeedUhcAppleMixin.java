package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** A Speed UHC participant finishing a golden apple, for Vitamins and Master Baker. */
@Mixin(ItemStack.class)
abstract class SpeedUhcAppleMixin {
    @WrapMethod(method = "finishUsingItem")
    private ItemStack brainage_minigames$speedUhcApple(Level level, LivingEntity user, Operation<ItemStack> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.is(Items.GOLDEN_APPLE) || !(user instanceof ServerPlayer player)) return original.call(level, user);
        int before = stack.getCount();
        ItemStack result = original.call(level, user);
        if (stack.getCount() < before || player.hasInfiniteMaterials()) SpeedUhc.ateGoldenApple(player);
        return result;
    }
}
