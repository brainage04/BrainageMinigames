package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/** A normal recipe registered in code, with authoritative world-rule matching. */
public final class CombatCrafting {
    public static final ResourceKey<Recipe<?>> APPLE_ID = ResourceKey.create(Registries.RECIPE,
            BrainageMinigames.id("enchanted_golden_apple"));
    private CombatCrafting() {}

    public static RecipeHolder<CraftingRecipe> recipe() {
        return new RecipeHolder<>(APPLE_ID, new ShapedRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                ShapedRecipePattern.of(Map.of('G', Ingredient.of(Items.GOLD_BLOCK), 'A', Ingredient.of(Items.APPLE)), "GGG", "GAG", "GGG"),
                new ItemStackTemplate(Items.ENCHANTED_GOLDEN_APPLE)) {
            @Override public boolean matches(CraftingInput input, Level level) {
                return level instanceof ServerLevel serverLevel && CombatRules.legacyBalance(serverLevel) && super.matches(input, level);
            }
        });
    }

    public static void refresh(ServerPlayer player) {
        player.inventoryMenu.slotsChanged(player.getInventory());
        if (player.containerMenu instanceof CraftingMenu menu) menu.slotsChanged(player.getInventory());
    }
}
