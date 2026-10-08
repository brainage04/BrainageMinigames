package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcMobDrops;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Passes a mob's death loot through {@link UhcMobDrops}. Equipment, which is dropped separately, and
 * experience are untouched.
 */
@Mixin(LivingEntity.class)
abstract class UhcMobDropsMixin {
    @ModifyArg(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;"
                    + "ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"),
            index = 2)
    private Consumer<ItemStack> brainage_minigames$uhcLoot(Consumer<ItemStack> drop) {
        return UhcMobDrops.deathLoot((LivingEntity) (Object) this, drop);
    }
}
