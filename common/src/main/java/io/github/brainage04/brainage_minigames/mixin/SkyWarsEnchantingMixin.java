package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enchanting table offers for a SkyWars participant; see {@link SkyWarsPerks#enchantments}. */
@Mixin(EnchantmentMenu.class)
abstract class SkyWarsEnchantingMixin {
    @Unique private ServerPlayer brainage_minigames$enchanter;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void brainage_minigames$rememberEnchanter(int containerId, Inventory inventory, ContainerLevelAccess access,
            CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) {
            brainage_minigames$enchanter = player;
        }
    }

    @ModifyReturnValue(method = "getEnchantmentList", at = @At("RETURN"))
    private List<EnchantmentInstance> brainage_minigames$fortuneTeller(List<EnchantmentInstance> offers,
            RegistryAccess registries, ItemStack stack, int option, int cost) {
        ServerPlayer player = brainage_minigames$enchanter;
        return player == null ? offers : SkyWarsPerks.enchantments(player, registries, stack, option, offers);
    }
}
