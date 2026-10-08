package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** CutClean's cooked food and guaranteed animal drops, on a mob's death loot. */
@Mixin(LivingEntity.class)
abstract class UhcCutCleanMobMixin {
    @WrapOperation(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"))
    private void brainage_minigames$cutClean(LootTable table, LootParams params, long seed, Consumer<ItemStack> output,
            Operation<Void> original, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) DamageSource source) {
        if (!UhcResourceScenarios.cutClean(level, source.getEntity())) {
            original.call(table, params, seed, output);
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        original.call(table, params, seed, (Consumer<ItemStack>) drops::add);
        UhcResourceScenarios.mobDrops(drops, (LivingEntity) (Object) this, level, source.getEntity()).forEach(output);
    }
}
