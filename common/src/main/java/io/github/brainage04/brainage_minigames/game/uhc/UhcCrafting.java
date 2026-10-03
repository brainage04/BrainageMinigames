package io.github.brainage04.brainage_minigames.game.uhc;

import static io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.Tree.*;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/** Vanilla crafting grids, with entitlement/limits checked server-side on preview and take. */
public final class UhcCrafting {
    private static final String MARKER = "brainage_uhc_item";
    private static final List<Recipe> RECIPES = new ArrayList<>();
    private static final int[] PANDORA_WEIGHTS = {7,7,6,1,7,7,7,6,6,3,2,3,1,1,3,3,6,3,7,6,5,2,1};

    public record Recipe(String id, UhcProgression.Tree tree, int slot, Item output, int count, Item[] grid) {
        public boolean matches(CraftingInput input) {
            if (id.equals("obsidian") || id.equals("eves_temptation")) {
                Item first = id.equals("obsidian") ? Items.LAVA_BUCKET : Items.BONE_MEAL;
                Item second = id.equals("obsidian") ? Items.WATER_BUCKET : Items.APPLE;
                int a = 0, b = 0;
                for (ItemStack stack : input.items()) {
                    if (stack.isEmpty()) continue;
                    if (stack.is(first)) a++;
                    else if (stack.is(second)) b++;
                    else return false;
                }
                return a == 1 && b == 1;
            }
            if (id.equals("fusion_armor")) return UhcAdvancedRecipes.fusionIngredients(input);
            int left = 3, top = 3, right = -1, bottom = -1;
            for (int i = 0; i < 9; i++) if (grid[i] != Items.AIR) {
                left = Math.min(left, i % 3); right = Math.max(right, i % 3);
                top = Math.min(top, i / 3); bottom = Math.max(bottom, i / 3);
            }
            int width = right - left + 1, height = bottom - top + 1;
            if (input.width() != width || input.height() != height) return false;
            for (int mirrored = 0; mirrored < 2; mirrored++) {
                boolean same = true;
                for (int y = 0; y < height && same; y++) for (int x = 0; x < width; x++) {
                    Item expected = grid[(top + y) * 3 + left + (mirrored == 1 ? width - x - 1 : x)];
                    if (!ingredient(input.getItem(x, y), expected, id)) { same = false; break; }
                }
                if (same) return true;
            }
            return false;
        }
    }

    static {
        add("vorpal_sword", WEAPONSMITH, 1, Items.IRON_SWORD, 1, " B / S / R ", 'B', Items.BONE, 'S', Items.IRON_SWORD, 'R', Items.ROTTEN_FLESH);
        add("sharpness_book", WEAPONSMITH, 2, Items.ENCHANTED_BOOK, 1, "F  / PP/ PS", 'F', Items.FLINT, 'P', Items.PAPER, 'S', Items.IRON_SWORD);
        add("power_book", WEAPONSMITH, 3, Items.ENCHANTED_BOOK, 1, "F  / PP/ PB", 'F', Items.FLINT, 'P', Items.PAPER, 'B', Items.BONE);
        add("dragon_sword", WEAPONSMITH, 4, Items.DIAMOND_SWORD, 1, " B / S /OBO", 'B', Items.BLAZE_POWDER, 'S', Items.DIAMOND_SWORD, 'O', Items.OBSIDIAN);
        add("leather_economy", ARMORSMITH, 1, Items.LEATHER, 8, "SLS/SLS/SLS", 'S', Items.STICK, 'L', Items.LEATHER);
        add("protection_book", ARMORSMITH, 2, Items.ENCHANTED_BOOK, 1, "   / PP/ PI", 'P', Items.PAPER, 'I', Items.IRON_INGOT);
        add("artemis_book", ARMORSMITH, 3, Items.ENCHANTED_BOOK, 1, "   / PP/ PA", 'P', Items.PAPER, 'A', Items.ARROW);
        add("dragon_armor", ARMORSMITH, 4, Items.DIAMOND_CHESTPLATE, 1, " M / C /OAO", 'M', Items.MAGMA_CREAM, 'C', Items.DIAMOND_CHESTPLATE, 'O', Items.OBSIDIAN, 'A', Items.ANVIL);
        add("dust_of_light", ALCHEMY, 1, Items.GLOWSTONE_DUST, 8, "RRR/RFR/RRR", 'R', Items.REDSTONE, 'F', Items.FLINT_AND_STEEL);
        add("brewing_artifact", ALCHEMY, 2, Items.NETHER_WART, 1, " S /SES/ S ", 'S', Items.WHEAT_SEEDS, 'E', Items.FERMENTED_SPIDER_EYE);
        add("nectar", ALCHEMY, 3, Items.POTION, 1, " E /GMG/ B ", 'E', Items.EMERALD, 'G', Items.GOLD_INGOT, 'M', Items.MELON_SLICE, 'B', Items.GLASS_BOTTLE);
        add("nether_artifact", ALCHEMY, 4, Items.BLAZE_ROD, 1, "WLW/WFW/WLW", 'W', Items.OAK_LOG, 'L', Items.LAVA_BUCKET, 'F', Items.FIREWORK_ROCKET);
        add("food_economy", SURVIVALISM, 1, Items.COOKED_BEEF, 10, "RRR/RCR/RRR", 'R', Items.BEEF, 'C', Items.COAL);
        add("toughness", SURVIVALISM, 2, Items.POTION, 1, " S / W / B ", 'S', Items.SLIME_BALL, 'W', Items.WOOL.white(), 'B', Items.GLASS_BOTTLE);
        add("spiked_armor", SURVIVALISM, 3, Items.LEATHER_CHESTPLATE, 1, " L / C / A ", 'L', Items.LILY_PAD, 'C', Items.CACTUS, 'A', Items.LEATHER_CHESTPLATE);
        add("seven_league_boots", SURVIVALISM, 4, Items.DIAMOND_BOOTS, 1, "FEF/FBF/FWF", 'F', Items.FEATHER, 'E', Items.ENDER_PEARL, 'B', Items.DIAMOND_BOOTS, 'W', Items.WATER_BUCKET);
        add("iron_economy", ENGINEERING, 1, Items.IRON_INGOT, 10, "OOO/OCO/OOO", 'O', Items.IRON_ORE, 'C', Items.COAL);
        add("obsidian", ENGINEERING, 2, Items.OBSIDIAN, 1, "   / L / W ", 'L', Items.LAVA_BUCKET, 'W', Items.WATER_BUCKET);
        add("tarnhelm", ENGINEERING, 3, Items.DIAMOND_HELMET, 1, "DID/DRD/   ", 'D', Items.DIAMOND, 'I', Items.IRON_INGOT, 'R', Items.REDSTONE_BLOCK);
        add("philosophers_pickaxe", ENGINEERING, 4, Items.DIAMOND_PICKAXE, 1, "OGO/LSL/ S ", 'O', Items.IRON_ORE, 'G', Items.GOLD_ORE, 'L', Items.LAPIS_BLOCK, 'S', Items.STICK);
        add("eves_temptation", COOKING, 1, Items.APPLE, 2, "   / B / A ", 'B', Items.BONE_MEAL, 'A', Items.APPLE);
        add("healing_fruit", COOKING, 2, Items.MELON_SLICE, 1, "BSB/SAS/BSB", 'B', Items.BONE_MEAL, 'S', Items.WHEAT_SEEDS, 'A', Items.APPLE);
        add("holy_water", COOKING, 3, Items.POTION, 1, "GRG/ D / B ", 'G', Items.GOLD_INGOT, 'R', Items.REDSTONE_BLOCK, 'D', Items.MUSIC_DISC_13, 'B', Items.GLASS_BOTTLE);
        add("light_apple", COOKING, 4, Items.GOLDEN_APPLE, 1, " G /GAG/ G ", 'G', Items.GOLD_INGOT, 'A', Items.APPLE);
        add("enlightening_pack", ENCHANTING, 1, Items.EXPERIENCE_BOTTLE, 8, " R /RBR/ R ", 'R', Items.REDSTONE_BLOCK, 'B', Items.GLASS_BOTTLE);
        add("light_anvil", ENCHANTING, 2, Items.ANVIL, 1, "III/ B /III", 'I', Items.IRON_INGOT, 'B', Items.IRON_BLOCK);
        add("light_enchanting_table", ENCHANTING, 3, Items.ENCHANTING_TABLE, 1, " S /ODO/OEO", 'S', Items.BOOKSHELF, 'O', Items.OBSIDIAN, 'D', Items.DIAMOND, 'E', Items.EXPERIENCE_BOTTLE);
        add("book_of_thoth", ENCHANTING, 4, Items.ENCHANTED_BOOK, 1, "E  / PP/ PF", 'E', Items.ENDER_EYE, 'P', Items.PAPER, 'F', Items.FIRE_CHARGE);
        add("arrow_economy", HUNTER, 1, Items.ARROW, 20, "FFF/SSS/EEE", 'F', Items.FLINT, 'S', Items.STICK, 'E', Items.FEATHER);
        add("saddle", HUNTER, 2, Items.SADDLE, 1, "LLL/SLS/I I", 'L', Items.LEATHER, 'S', Items.STRING, 'I', Items.IRON_INGOT);
        add("velocity", HUNTER, 3, Items.SPLASH_POTION, 1, " C / S / B ", 'C', Items.COCOA_BEANS, 'S', Items.SUGAR, 'B', Items.GLASS_BOTTLE);
        add("fenrir", HUNTER, 4, Items.WOLF_SPAWN_EGG, 1, "LLL/BPB/LLL", 'L', Items.LEATHER, 'B', Items.BONE, 'P', Items.POTION);
        add("golden_head", BLOODCRAFT, 1, Items.PLAYER_HEAD, 1, "GGG/GHG/GGG", 'G', Items.GOLD_INGOT, 'H', Items.PLAYER_HEAD);
        add("pandoras_box", BLOODCRAFT, 2, Items.CHEST, 1, "CCC/CHC/CCC", 'C', Items.CHEST, 'H', Items.PLAYER_HEAD);
        add("panacea", BLOODCRAFT, 3, Items.POTION, 1, "   /HMH/ B ", 'H', Items.PLAYER_HEAD, 'M', Items.GLISTERING_MELON_SLICE, 'B', Items.GLASS_BOTTLE);
        add("cupids_bow", BLOODCRAFT, 4, Items.BOW, 1, "H H/RBR/ L ", 'H', Items.PLAYER_HEAD, 'R', Items.BLAZE_ROD, 'B', Items.BOW, 'L', Items.LAVA_BUCKET);
        add("forge", TOOLSMITH, 1, Items.FURNACE, 1, "CCC/CKC/CCC", 'C', Items.COBBLESTONE, 'K', Items.COAL);
        add("quick_pick", TOOLSMITH, 2, Items.IRON_PICKAXE, 1, "OOO/CSC/ S ", 'O', Items.IRON_ORE, 'C', Items.COAL, 'S', Items.STICK);
        add("lumberjacks_axe", TOOLSMITH, 3, Items.IRON_AXE, 1, "IIF/IS / S ", 'I', Items.IRON_INGOT, 'F', Items.FLINT, 'S', Items.STICK);
        add("enhancement_book", TOOLSMITH, 4, Items.ENCHANTED_BOOK, 1, "PPP/KTA/SSS", 'P', Items.POTION, 'K', Items.GOLDEN_PICKAXE, 'T', Items.ENCHANTING_TABLE, 'A', Items.IRON_AXE, 'S', Items.BOOKSHELF);
        add("apprentice_helmet", APPRENTICE, 1, Items.IRON_HELMET, 1, "III/ITI/   ", 'I', Items.IRON_INGOT, 'T', Items.REDSTONE_TORCH);
        add("apprentice_sword", APPRENTICE, 2, Items.IRON_SWORD, 1, " R / S / R ", 'R', Items.REDSTONE_BLOCK, 'S', Items.IRON_SWORD);
        add("apprentice_bow", APPRENTICE, 3, Items.BOW, 1, " TS/T S/ TS", 'T', Items.REDSTONE_TORCH, 'S', Items.STRING);
        add("masters_compass", APPRENTICE, 4, Items.COMPASS, 1, "RTR/RCR/RRR", 'R', Items.REDSTONE, 'T', Items.REDSTONE_TORCH, 'C', Items.COMPASS);
        add("gold_pack", INVENTION, 1, Items.GOLD_INGOT, 10, "OOO/OCO/OOO", 'O', Items.GOLD_ORE, 'C', Items.COAL);
        add("sugar_rush", INVENTION, 2, Items.SUGAR_CANE, 4, " S /WUW/   ", 'S', Items.OAK_SAPLING, 'W', Items.WHEAT_SEEDS, 'U', Items.SUGAR);
        add("backpack", INVENTION, 3, Items.CHEST, 1, "SLS/SCS/SLS", 'S', Items.STICK, 'L', Items.LEATHER, 'C', Items.CHEST);
        add("fusion_armor", INVENTION, 4, Items.DIAMOND_CHESTPLATE, 1, "HCL/BB /   ", 'H', Items.DIAMOND_HELMET, 'C', Items.DIAMOND_CHESTPLATE, 'L', Items.DIAMOND_LEGGINGS, 'B', Items.DIAMOND_BOOTS);
        add("lucky_shears", STRATEGIST, 1, Items.SHEARS, 1, "LLL/LSL/LLL", 'L', Items.OAK_LEAVES, 'S', Items.SHEARS);
        add("the_deep", STRATEGIST, 2, Items.FISHING_ROD, 1, "  B/ BS/B S", 'B', Items.BONE, 'S', Items.STRING);
        add("swan_song", STRATEGIST, 3, Items.ENCHANTED_BOOK, 1, "F  / PP/ PD", 'F', Items.FEATHER, 'P', Items.PAPER, 'D', Items.MUSIC_DISC_13);
        add("el_dorado", STRATEGIST, 4, Items.FILLED_MAP, 1, " N /WCW/S S", 'N', Items.NETHERRACK, 'W', Items.WOOL.white(), 'C', Items.CHEST, 'S', Items.STRING);
        UhcExtraRecipes.registerRecipes();
    }

    private static final List<Recipe> CATALOG = List.copyOf(RECIPES);

    private UhcCrafting() {}

    static void add(String id, UhcProgression.Tree tree, int slot, Item output, int count, String pattern, Object... keys) {
        String cells = pattern.replace("/", "");
        Item[] grid = new Item[9];
        for (int i = 0; i < 9; i++) {
            grid[i] = Items.AIR;
            for (int j = 0; j < keys.length; j += 2) if ((Character) keys[j] == cells.charAt(i)) grid[i] = (Item) keys[j + 1];
        }
        RECIPES.add(new Recipe(id, tree, slot, output, count, grid));
    }


    public static List<Recipe> recipes() { return CATALOG; }

    private static boolean ingredient(ItemStack stack, Item item, String recipe) {
        if (item == Items.AIR) return stack.isEmpty();
        if (item == Items.PLAYER_HEAD) return stack.is(Items.PLAYER_HEAD) && kind(stack).equals("player_head");
        if (item == Items.OAK_LOG) return stack.is(ItemTags.LOGS);
        if (item == Items.OAK_LEAVES) return stack.is(ItemTags.LEAVES);
        if (item == Items.OAK_SAPLING) return stack.is(ItemTags.SAPLINGS);
        if (item == Items.WOOL.white()) return stack.is(ItemTags.WOOL);
        if (item == Items.MUSIC_DISC_13) return stack.has(DataComponents.JUKEBOX_PLAYABLE);
        if (item == Items.IRON_ORE) return stack.is(Items.IRON_ORE) || stack.is(Items.DEEPSLATE_IRON_ORE);
        if (item == Items.GOLD_ORE) return stack.is(Items.GOLD_ORE) || stack.is(Items.DEEPSLATE_GOLD_ORE);
        if (item == Items.POTION) return stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(recipe.equals("fenrir") ? Potions.SWIFTNESS : recipe.equals("shoes_of_vidar") ? Potions.WATER_BREATHING : recipe.equals("barbarian_chestplate") ? Potions.STRENGTH : Potions.WATER);
        return stack.is(item);
    }

    public static @Nullable Recipe matching(CraftingInput input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) if (recipe.matches(input)) return recipe;
        return null;
    }

    public static ItemStack preview(ServerPlayer player, Recipe recipe) {
        return UhcProgression.canCraft(player, recipe.tree, recipe.slot, recipe.id) ? output(player, recipe) : ItemStack.EMPTY;
    }

    public static NonNullList<ItemStack> remainders(CraftingInput input) {
        return CraftingRecipe.defaultCraftingReminder(input);
    }

    public static ItemStack marked(ItemStack stack, String id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(MARKER, id));
        return stack;
    }

    public static String kind(net.minecraft.world.item.ItemInstance stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.isEmpty() ? "" : data.copyTag().getStringOr(MARKER, "");
    }

    public static ItemStack playerHead(ServerPlayer victim) {
        ItemStack head = marked(new ItemStack(Items.PLAYER_HEAD), "player_head");
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(victim.getGameProfile()));
        head.set(DataComponents.CUSTOM_NAME, Component.literal(victim.getScoreboardName() + "'s Head"));
        return head;
    }

    public static ItemStack output(ServerPlayer player, Recipe recipe) {
        ItemStack stack = marked(new ItemStack(recipe.output, recipe.count), recipe.id);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(recipe.id.replace('_', ' ')));
        switch (recipe.id) {
            case "vorpal_sword" -> { enchant(player, stack, Enchantments.SMITE, 2); enchant(player, stack, Enchantments.BANE_OF_ARTHROPODS, 2); enchant(player, stack, Enchantments.LOOTING, 1); }
            case "sharpness_book" -> enchant(player, stack, Enchantments.SHARPNESS, 1);
            case "power_book" -> enchant(player, stack, Enchantments.POWER, 1);
            case "protection_book" -> enchant(player, stack, Enchantments.PROTECTION, 1);
            case "artemis_book" -> enchant(player, stack, Enchantments.PROJECTILE_PROTECTION, 1);
            case "dragon_sword" -> stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(BrainageMinigames.id("dragon_sword"), 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
            case "dragon_armor" -> enchant(player, stack, Enchantments.PROTECTION, 4);
            case "nectar" -> potion(stack, effect(MobEffects.REGENERATION, 10, 1));
            case "toughness" -> potion(stack, effect(MobEffects.RESISTANCE, 120, 0));
            case "spiked_armor" -> { enchant(player, stack, Enchantments.PROTECTION, 5); enchant(player, stack, Enchantments.THORNS, 1); stack.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE); }
            case "seven_league_boots" -> { enchant(player, stack, Enchantments.PROTECTION, 3); enchant(player, stack, Enchantments.FEATHER_FALLING, 3); }
            case "tarnhelm" -> { enchant(player, stack, Enchantments.PROTECTION, 1); enchant(player, stack, Enchantments.FIRE_PROTECTION, 1); enchant(player, stack, Enchantments.AQUA_AFFINITY, 3); }
            case "philosophers_pickaxe" -> { enchant(player, stack, Enchantments.FORTUNE, 2); stack.set(DataComponents.MAX_DAMAGE, 3); stack.setDamageValue(0); }
            case "holy_water" -> potion(stack, effect(MobEffects.ABSORPTION, 120, 3));
            case "book_of_thoth" -> { enchant(player, stack, Enchantments.SHARPNESS, 2); enchant(player, stack, Enchantments.POWER, 1); enchant(player, stack, Enchantments.PROTECTION, 3); }
            case "velocity" -> potion(stack, effect(MobEffects.SPEED, 12, 2));
            case "panacea" -> potion(stack, effect(MobEffects.INSTANT_HEALTH, 1, 4));
            case "cupids_bow" -> { enchant(player, stack, Enchantments.POWER, 2); enchant(player, stack, Enchantments.FLAME, 1); }
            case "golden_head" -> {
                stack.set(DataComponents.FOOD, new FoodProperties(4, 9.6f, true));
                stack.set(DataComponents.CONSUMABLE, Consumable.builder().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effect(MobEffects.REGENERATION, 10, 1), effect(MobEffects.ABSORPTION, 120, 0)))).build());
            }
            case "quick_pick" -> { enchant(player, stack, Enchantments.EFFICIENCY, 1); enchant(player, stack, Enchantments.UNBREAKING, 1); }
            case "lumberjacks_axe" -> { stack.set(DataComponents.MAX_DAMAGE, 10); stack.remove(DataComponents.ENCHANTABLE); stack.remove(DataComponents.REPAIRABLE); }
            case "apprentice_helmet" -> {
                enchant(player, stack, Enchantments.PROTECTION, 1);
                enchant(player, stack, Enchantments.FIRE_PROTECTION, 1);
                enchant(player, stack, Enchantments.BLAST_PROTECTION, 1);
                enchant(player, stack, Enchantments.PROJECTILE_PROTECTION, 1);
            }
            case "apprentice_sword", "apprentice_bow" -> { stack.remove(DataComponents.ENCHANTABLE); UhcAdvancedRecipes.updateSword(player, stack); }
            case "backpack" -> { stack.set(DataComponents.MAX_STACK_SIZE, 1); stack.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY); }
            case "swan_song" -> UhcAdvancedRecipes.swan(stack, 1);
            case "fusion_armor" -> { stack = marked(UhcAdvancedRecipes.fusion(player), recipe.id); }
            default -> { }
        }
        UhcExtraRecipes.output(player, stack, recipe.id);
        return stack;
    }

    private static MobEffectInstance effect(Holder<MobEffect> effect, int seconds, int amplifier) { return new MobEffectInstance(effect, seconds * 20, amplifier); }

    public static void enchant(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        stack.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
    }

    private static void potion(ItemStack stack, MobEffectInstance... effects) {
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(effects), Optional.empty()));
    }

    public static InteractionResult use(ServerPlayer player, ItemStack stack) {
        String kind = kind(stack);
        if (!kind.isEmpty() && UhcProgression.match(player) == null) return InteractionResult.FAIL;
        InteractionResult advanced = UhcAdvancedRecipes.use(player, stack);
        if (advanced != InteractionResult.PASS) return advanced;
        InteractionResult extra = UhcExtraRecipes.use(player, stack);
        if (extra != InteractionResult.PASS) return extra;
        if (kind.equals("player_head")) {
            var match = UhcProgression.match(player);
            if (match == null) return InteractionResult.FAIL;
            var team = match.teamOf(player.getUUID()).orElseThrow();
            for (ServerPlayer teammate : match.alivePlayers()) if (team.members().contains(teammate.getUUID())) teammate.addEffect(effect(MobEffects.REGENERATION, 4, 1));
            celerity(player);
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        if (kind.equals("fenrir")) {
            Wolf wolf = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.SPAWN_ITEM_USE);
            if (wolf == null) return InteractionResult.FAIL;
            wolf.tame(player);
            wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            wolf.setHealth(2);
            wolf.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
            wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 1));
            wolf.addEffect(new MobEffectInstance(MobEffects.STRENGTH, -1, 1));
            wolf.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1));
            player.level().addFreshEntity(wolf);
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        if (kind.equals("pandoras_box")) {
            ItemStack reward = pandora(player, UhcAdvancedRecipes.pandoraRoll(player, PANDORA_WEIGHTS));
            stack.shrink(1);
            if (!reward.isEmpty() && !player.getInventory().add(reward)) player.drop(reward, false);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void celerity(ServerPlayer player) {
        int level = UhcProgression.level(player, BLOODCRAFT);
        if (level > 0) player.addEffect(effect(MobEffects.SPEED, 5 + level, 1));
    }

    /** Staff's 2017 table totals 100%; sword/bow rewards are distinct from their book counterparts. */
    public static ItemStack pandora(ServerPlayer player, int roll) {
        int[] weights = PANDORA_WEIGHTS;
        int index = 0;
        while (roll >= weights[index]) roll -= weights[index++];
        ItemStack reward;
        switch (index) {
            case 0,1,2,4,5,14,15 -> {
                reward = new ItemStack(Items.ENCHANTED_BOOK);
                enchant(player, reward, switch (index) { case 0 -> Enchantments.PROTECTION; case 1 -> Enchantments.PROJECTILE_PROTECTION; case 2 -> Enchantments.FEATHER_FALLING; case 4,14 -> Enchantments.POWER; default -> Enchantments.SHARPNESS; }, index >= 14 ? 3 : 2);
            }
            case 3,16,17 -> reward = new ItemStack(Items.GOLDEN_APPLE, index == 3 ? 4 : index == 16 ? 2 : 3);
            case 6 -> { reward = new ItemStack(Items.POTION); potion(reward, effect(MobEffects.INSTANT_HEALTH, 1, 1), effect(MobEffects.ABSORPTION, 90, 1)); }
            case 7 -> { reward = marked(new ItemStack(Items.COD), "slapfish"); reward.set(DataComponents.FOOD, new FoodProperties(2, 0.4f, true)); reward.set(DataComponents.CONSUMABLE, Consumable.builder().onConsume(new ApplyStatusEffectsConsumeEffect(effect(MobEffects.REGENERATION, 4, 2))).build()); }
            case 8 -> reward = output(player, RECIPES.stream().filter(r -> r.id.equals("golden_head")).findFirst().orElseThrow());
            case 9,12 -> { reward = new ItemStack(Items.DIAMOND_SWORD); enchant(player, reward, index == 9 ? Enchantments.SHARPNESS : Enchantments.FIRE_ASPECT, index == 9 ? 3 : 1); }
            case 11,13 -> { reward = new ItemStack(Items.BOW); enchant(player, reward, index == 11 ? Enchantments.POWER : Enchantments.FLAME, index == 11 ? 3 : 1); }
            case 10,18,19,20 -> reward = new ItemStack(Items.GOLD_INGOT, switch (index) { case 10 -> 32; case 18 -> 12; case 19 -> 16; default -> 24; });
            case 21 -> { reward = new ItemStack(Items.POTION); potion(reward, effect(MobEffects.INSTANT_HEALTH, 1, 2)); }
            case 22 -> { var dragon = EntityTypes.ENDER_DRAGON.create(player.level(), EntitySpawnReason.TRIGGERED); if (dragon != null) { dragon.snapTo(player.getX(), player.getY() + 12, player.getZ(), 0, 0); player.level().addFreshEntity(dragon); } reward = ItemStack.EMPTY; }
            default -> throw new IllegalArgumentException("Invalid Pandora roll");
        }
        return reward;
    }
}
