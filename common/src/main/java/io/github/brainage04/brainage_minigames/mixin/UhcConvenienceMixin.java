package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcEffects;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class UhcConvenienceMixin {
    // NeoForge moves the implementation into its (BlockState, BlockPos) overload.
    @WrapOperation(method = "getDestroySpeed*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double brainage_minigames$convenience(Player player, Holder<Attribute> attribute, Operation<Double> original, BlockState state) {
        double value = original.call(player, attribute);
        if (!(player instanceof ServerPlayer serverPlayer) || attribute != Attributes.MINING_EFFICIENCY || !UhcEffects.convenienceBlock(state)) return value;
        int level = UhcProgression.level(serverPlayer, UhcProgression.Tree.INVENTION);
        if (level == 0) return value;
        int enchantment = EnchantmentHelper.getItemEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), player.getMainHandItem());
        int combined = enchantment + level;
        return value + combined * combined + 1 - (enchantment == 0 ? 0 : enchantment * enchantment + 1);
    }
}
