package io.github.brainage04.brainage_minigames.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.brainage_minigames.game.CombatCrafting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeMap.class)
abstract class CombatBalanceRecipeMixin {
    @Inject(method = "create", at = @At(value = "NEW", target = "net/minecraft/world/item/crafting/RecipeMap"))
    private static void brainage_minigames$appleRecipe(Iterable<RecipeHolder<?>> recipes, CallbackInfoReturnable<RecipeMap> cir,
            @Local ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> types,
            @Local ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> keys) {
        var apple = CombatCrafting.recipe();
        types.put(RecipeType.CRAFTING, apple);
        keys.put(apple.id(), apple);
    }
}
