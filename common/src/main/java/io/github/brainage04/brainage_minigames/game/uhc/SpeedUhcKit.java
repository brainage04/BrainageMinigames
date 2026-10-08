package io.github.brainage04.brainage_minigames.game.uhc;

import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.COMMON;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.LEGENDARY;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.RARE;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Stack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * A Speed UHC kit: what the kit menu shows and what a player gets when the match starts. The
 * sixteen purchasable kits are Hypixel's Speed UHC "Kits" menu as exported on 2026-10-06, with its
 * rarities and coin costs; Default (six oak planks and an iron chestplate) is the kit every player
 * owns, from player guides of 2016 and 2020.
 */
public record SpeedUhcKit(String id, String name, @Nullable Rarity rarity, Item icon, int cost, List<Stack> items) {
    /** The kit a Speed UHC match gives when it was not opened with another kit: Default's items. */
    public static final Identifier DEFAULT_KIT = BrainageMinigames.id("kits/speed_uhc");
    public static final String DEFAULT = "default";

    public static final List<SpeedUhcKit> ALL = List.of(
            kit(DEFAULT, "Default", null, Items.OAK_PLANKS, 0,
                    new Stack(Items.OAK_PLANKS).count(6), new Stack(Items.IRON_CHESTPLATE)),
            kit("archaeologist", "Archaeologist", RARE, Items.IRON_SHOVEL, 5000,
                    new Stack(Items.IRON_INGOT), new Stack(Items.STICK).count(2),
                    new Stack(Items.IRON_HELMET).enchant(Enchantments.PROTECTION, 1),
                    new Stack(Items.LEATHER_CHESTPLATE), new Stack(Items.LEATHER_LEGGINGS), new Stack(Items.LEATHER_BOOTS)),
            kit("archer", "Archer", COMMON, Items.BOW, 2500,
                    new Stack(Items.ENCHANTED_BOOK).enchant(Enchantments.POWER, 1), new Stack(Items.STICK).count(3),
                    new Stack(Items.STRING).count(3), new Stack(Items.ARROW).count(10)),
            kit("cowboy", "Cowboy", RARE, Items.ENCHANTED_BOOK, 5000,
                    new Stack(Items.ENCHANTED_BOOK).enchant(Enchantments.SHARPNESS, 1),
                    new Stack(Items.LEATHER_BOOTS).enchant(Enchantments.FEATHER_FALLING, 4).enchant(Enchantments.UNBREAKING, 3)),
            kit("enchanter", "Enchanter", RARE, Items.BOOK, 5000,
                    new Stack(Items.BOOK), new Stack(Items.OBSIDIAN).count(4), new Stack(Items.EXPERIENCE_BOTTLE).count(8),
                    new Stack(Items.ANVIL), new Stack(Items.ENCHANTED_BOOK).enchant(Enchantments.POWER, 1)),
            kit("farmer", "Farmer", LEGENDARY, Items.APPLE, 15000,
                    new Stack(Items.APPLE).count(2), new Stack(Items.BLAZE_POWDER), awkward(1)),
            kit("fisherman", "Fisherman", LEGENDARY, Items.STRING, 15000,
                    new Stack(Items.STRING).count(2), new Stack(Items.STICK).count(3), new Stack(Items.DIAMOND)),
            kit("healer", "Healer", RARE, Items.GLISTERING_MELON_SLICE, 5000,
                    new Stack(Items.GLISTERING_MELON_SLICE).count(2), awkward(2), new Stack(Items.GOLDEN_APPLE)),
            kit("knight", "Knight", LEGENDARY, Items.IRON_INGOT, 15000,
                    new Stack(Items.IRON_INGOT).count(2), new Stack(Items.STICK),
                    new Stack(Items.GOLDEN_HELMET).enchant(Enchantments.PROTECTION, 2).enchant(Enchantments.UNBREAKING, 10)),
            kit("logger", "Logger", COMMON, Items.CRAFTING_TABLE, 2500,
                    new Stack(Items.CRAFTING_TABLE), new Stack(Items.GOLD_BLOCK).count(2),
                    new Stack(Items.GOLDEN_AXE).enchant(Enchantments.EFFICIENCY, 5)),
            kit("miner", "Miner", COMMON, Items.COBBLESTONE, 2500,
                    new Stack(Items.COBBLESTONE).count(8), new Stack(Items.TNT).count(2), new Stack(Items.WATER_BUCKET),
                    new Stack(Items.IRON_HELMET).enchant(Enchantments.BLAST_PROTECTION, 2)),
            kit("nether_walker", "Nether Walker", LEGENDARY, Items.NETHER_QUARTZ_ORE, 15000,
                    new Stack(Items.NETHER_QUARTZ_ORE).count(2), new Stack(Items.GHAST_TEAR), awkward(1)),
            kit("oink", "Oink", RARE, Items.CARROT_ON_A_STICK, 5000,
                    new Stack(Items.CARROT_ON_A_STICK), new Stack(Items.PIG_SPAWN_EGG), new Stack(Items.IRON_LEGGINGS)),
            kit("pyro", "Pyro", RARE, Items.FLINT, 5000,
                    new Stack(Items.FLINT), new Stack(Items.IRON_INGOT), new Stack(Items.MAGMA_CREAM).count(2), awkward(2),
                    new Stack(Items.ENCHANTED_BOOK).enchant(Enchantments.FIRE_PROTECTION, 2)),
            kit("scout", "Scout", COMMON, Items.IRON_BOOTS, 2500,
                    new Stack(Items.SUGAR), awkward(1), new Stack(Items.IRON_BOOTS).enchant(Enchantments.FEATHER_FALLING, 2)),
            kit("summoner", "Summoner", LEGENDARY, Items.SPIDER_EYE, 15000,
                    new Stack(Items.SPIDER_EYE), awkward(2), new Stack(Items.SKELETON_SPAWN_EGG)),
            kit("tamer", "Tamer", COMMON, Items.WOLF_SPAWN_EGG, 2500,
                    new Stack(Items.WOLF_SPAWN_EGG), new Stack(Items.IRON_LEGGINGS), new Stack(Items.COOKED_BEEF).count(4)));

    private static SpeedUhcKit kit(String id, String name, @Nullable Rarity rarity, Item icon, int cost, Stack... items) {
        return new SpeedUhcKit(id, name, rarity, icon, cost, List.of(items));
    }

    private static Stack awkward(int count) {
        return new Stack(Items.POTION).count(count).potion(Potions.AWKWARD);
    }

    public static @Nullable SpeedUhcKit find(String id) {
        for (SpeedUhcKit kit : ALL) {
            if (kit.id.equals(id)) return kit;
        }
        return null;
    }

    public boolean isDefault() {
        return id.equals(DEFAULT);
    }

    public ChatFormatting color() {
        return rarity == null ? ChatFormatting.GREEN : rarity.color;
    }

    public List<ItemStack> stacks(HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Stack item : items) stacks.add(item.make(registries, RandomSource.create(0)));
        return stacks;
    }

    /** The menu's description: each item with its count and enchantments, as Hypixel lists them. */
    public List<Component> contents(HolderLookup.Provider registries) {
        List<Component> lines = new ArrayList<>();
        for (ItemStack sample : stacks(registries)) {
            lines.add(Component.literal(sample.getHoverName().getString()
                    + (sample.getCount() > 1 ? " x" + sample.getCount() : "")).withStyle(ChatFormatting.GRAY));
            ItemEnchantments enchantments = sample.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (enchantments.isEmpty()) {
                enchantments = sample.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            }
            for (var entry : enchantments.entrySet()) {
                lines.add(Component.literal("   ∙ ").append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).copy())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }
}
