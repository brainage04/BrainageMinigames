package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import io.github.brainage04.brainage_minigames.game.uhc.UhcScenarios;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
abstract class UhcHeadPerksMixin {
    @WrapMethod(method = "finishUsingItem")
    private ItemStack brainage_minigames$head(Level level, LivingEntity user, Operation<ItemStack> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty() || !UhcCrafting.kind(stack).equals("golden_head")) return original.call(level, user);
        if (!(user instanceof ServerPlayer player) || !UhcScenarios.participant(player)) return stack;
        UhcCrafting.celerity(player);
        int before = stack.getCount();
        ItemStack result = original.call(level, user);
        if (stack.getCount() < before) UhcProgression.goldenHead(player);
        return result;
    }
}
