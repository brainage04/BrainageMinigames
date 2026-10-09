package io.github.brainage04.brainage_minigames.game.bedwars;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.util.Unit;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

/**
 * Hypixel's Bed Wars Item Shop: every item in the order of its "Adding to Quick Buy..." list, its
 * category, description and price. Prices are the Hypixel wiki's shop menus for Solo/Doubles and
 * 3v3v3v3/4v4v4v4 (also 4v4), with the forum's later balance changes (Packed Ice: 8 iron for 8, from
 * its 2023 item rotation). Tools are tiered: each purchase buys the next tier.
 */
public final class BedWarsShop {
    private BedWarsShop() {}

    /** Custom data key naming a Bed Wars item with an ability, such as {@code fireball}. */
    public static final String ITEM_KEY = "brainage_minigames:bedwars_item";

    /** The currencies the generators drop. */
    public enum Currency {
        IRON(Items.IRON_INGOT, "Iron", ChatFormatting.WHITE),
        GOLD(Items.GOLD_INGOT, "Gold", ChatFormatting.GOLD),
        DIAMOND(Items.DIAMOND, "Diamond", ChatFormatting.AQUA),
        EMERALD(Items.EMERALD, "Emerald", ChatFormatting.DARK_GREEN);

        public final Item item;
        public final String displayName;
        public final ChatFormatting color;

        Currency(Item item, String displayName, ChatFormatting color) {
            this.item = item;
            this.displayName = displayName;
            this.color = color;
        }

        /** "Iron", "Emerald" or "Emeralds" as the shop counts them. */
        public String count(int amount) {
            return amount + " " + displayName + (amount != 1 && (this == DIAMOND || this == EMERALD) ? "s" : "");
        }

        public static @Nullable Currency of(ItemStack stack) {
            for (Currency currency : values()) if (stack.is(currency.item)) return currency;
            return null;
        }
    }

    public record Cost(Currency currency, int amount) {
        public String describe() {
            return currency.count(amount);
        }
    }

    /** The tabs along the top of the Item Shop. */
    public enum Category {
        QUICK_BUY("Quick Buy", Items.NETHER_STAR),
        BLOCKS("Blocks", Items.TERRACOTTA),
        MELEE("Melee", Items.GOLDEN_SWORD),
        ARMOR("Armor", Items.CHAINMAIL_BOOTS),
        TOOLS("Tools", Items.STONE_PICKAXE),
        RANGED("Ranged", Items.BOW),
        POTIONS("Potions", Items.BREWING_STAND),
        UTILITY("Utility", Items.TNT),
        ROTATING("Rotating Items", Items.CLOCK);

        public final String title;
        public final Item icon;

        Category(String title, Item icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    /** How buying an item changes what the player has. */
    public enum Kind {
        /** Added to the inventory. */
        ITEM,
        /** Replaces a weaker sword, including the wooden one everyone spawns with. */
        SWORD,
        /** Permanent leggings and boots of a tier. */
        ARMOR,
        /** The next pickaxe tier. */
        PICKAXE,
        /** The next axe tier. */
        AXE,
        /** Permanent shears. */
        SHEARS
    }

    /**
     * One item of the shop. {@code solo} and {@code teams} hold one price per tier (one for every
     * item but the tools): Solo/Doubles and 3v3v3v3/4v4v4v4 prices.
     */
    public record Entry(String id, Category category, String name, String description, Kind kind, int tier,
            List<Cost> solo, List<Cost> teams) {
        public int tiers() {
            return solo.size();
        }
    }

    /** Which price list a match uses. */
    public enum Prices {
        /** Solo and Doubles: eight teams of one or two. */
        SOLO,
        /** 3v3v3v3, 4v4v4v4, 4v4 and every larger team. */
        TEAMS,
        /** 40v40 Castle: the 3v3v3v3 item prices, its own upgrade prices. */
        CASTLE
    }

    private static Cost iron(int amount) {
        return new Cost(Currency.IRON, amount);
    }

    private static Cost gold(int amount) {
        return new Cost(Currency.GOLD, amount);
    }

    private static Cost emerald(int amount) {
        return new Cost(Currency.EMERALD, amount);
    }

    private static Entry entry(String id, Category category, String name, String description, Kind kind, int tier,
            Cost solo, Cost teams) {
        return new Entry(id, category, name, description, kind, tier, List.of(solo), List.of(teams));
    }

    private static Entry item(String id, Category category, String name, String description, Cost price) {
        return entry(id, category, name, description, Kind.ITEM, 0, price, price);
    }

    private static final String TOOL_TEXT = "This is an upgradable item. It will lose 1 tier upon death! "
            + "You will permanently respawn with at least the lowest tier.";

    /** Every item, in the order of Hypixel's "Adding to Quick Buy..." list. */
    public static final List<Entry> ITEMS = List.of(
            item("wool", Category.BLOCKS, "Wool", "Great for bridging across islands. Turns into your team's color.", iron(4)),
            item("hardened_clay", Category.BLOCKS, "Hardened Clay", "Basic block to defend your bed.", iron(12)),
            item("blast_proof_glass", Category.BLOCKS, "Blast-Proof Glass", "Immune to explosions.", iron(12)),
            item("end_stone", Category.BLOCKS, "End Stone", "Solid block to defend your bed.", iron(24)),
            item("ladder", Category.BLOCKS, "Ladder", "Useful to save cats stuck in trees.", iron(4)),
            item("wood", Category.BLOCKS, "Wood", "Good block to defend your bed. Strong against pickaxes.", gold(4)),
            item("obsidian", Category.BLOCKS, "Obsidian", "Extreme protection for your bed.", emerald(4)),
            item("packed_ice", Category.BLOCKS, "Packed Ice", "Just normal ice. Good way to make a speedway.", iron(8)),
            entry("stone_sword", Category.MELEE, "Stone Sword", "", Kind.SWORD, 1, iron(10), iron(10)),
            entry("iron_sword", Category.MELEE, "Iron Sword", "", Kind.SWORD, 2, gold(7), gold(7)),
            entry("diamond_sword", Category.MELEE, "Diamond Sword", "", Kind.SWORD, 3, emerald(4), emerald(3)),
            item("knockback_stick", Category.MELEE, "Stick (Knockback I)", "", gold(5)),
            entry("chainmail_armor", Category.ARMOR, "Permanent Chainmail Armor",
                    "Chainmail leggings and boots which you will always spawn with.", Kind.ARMOR, 1, iron(30), iron(30)),
            entry("iron_armor", Category.ARMOR, "Permanent Iron Armor",
                    "Iron leggings and boots which you will always spawn with.", Kind.ARMOR, 2, gold(12), gold(12)),
            entry("diamond_armor", Category.ARMOR, "Permanent Diamond Armor",
                    "Diamond leggings and boots which you will always crush with.", Kind.ARMOR, 3, emerald(6), emerald(6)),
            new Entry("pickaxe", Category.TOOLS, "Pickaxe", TOOL_TEXT, Kind.PICKAXE, 0,
                    List.of(iron(10), iron(10), gold(3), gold(6)), List.of(iron(10), iron(10), gold(3), gold(6))),
            new Entry("axe", Category.TOOLS, "Axe", TOOL_TEXT, Kind.AXE, 0,
                    List.of(iron(10), iron(10), gold(3), gold(6)), List.of(iron(10), iron(10), gold(3), gold(6))),
            entry("shears", Category.TOOLS, "Permanent Shears",
                    "Great to get rid of wool. You will always spawn with these shears.", Kind.SHEARS, 0, iron(20), iron(20)),
            item("arrow", Category.RANGED, "Arrow", "", gold(2)),
            item("bow", Category.RANGED, "Bow", "", gold(12)),
            item("bow_power", Category.RANGED, "Bow (Power I)", "", gold(20)),
            item("bow_power_punch", Category.RANGED, "Bow (Power I, Punch I)", "", emerald(6)),
            item("speed_potion", Category.POTIONS, "Speed II Potion (45 seconds)", "Speed II (0:45)", emerald(1)),
            item("jump_potion", Category.POTIONS, "Jump V Potion (45 seconds)", "Jump Boost V (0:45)", emerald(1)),
            item("invisibility_potion", Category.POTIONS, "Invisibility Potion (30 seconds)", "Complete Invisibility (0:30)", emerald(2)),
            item("golden_apple", Category.UTILITY, "Golden Apple", "Well-rounded healing.", gold(3)),
            item("bedbug", Category.UTILITY, "Bedbug",
                    "Spawns silverfish where the snowball lands to distract your enemies. Lasts 15 seconds.", iron(30)),
            item("dream_defender", Category.UTILITY, "Dream Defender",
                    "Iron Golem to help defend your base. Lasts 4 minutes.", iron(120)),
            item("fireball", Category.UTILITY, "Fireball",
                    "Right-click to launch! Great to knock back enemies walking on thin bridges.", iron(40)),
            entry("tnt", Category.UTILITY, "TNT", "Instantly ignites, appropriate to explode things!", Kind.ITEM, 0, gold(4), gold(8)),
            item("ender_pearl", Category.UTILITY, "Ender Pearl", "The quickest way to invade enemy bases.", emerald(4)),
            entry("water_bucket", Category.UTILITY, "Water Bucket",
                    "Great to slow down approaching enemies. Can also protect against TNT.", Kind.ITEM, 0, gold(3), gold(6)),
            item("bridge_egg", Category.UTILITY, "Bridge Egg", "This egg creates a bridge in its trail after being thrown.", emerald(1)),
            item("magic_milk", Category.UTILITY, "Magic Milk", "Avoid triggering traps for 30 seconds after consuming.", gold(4)),
            entry("sponge", Category.UTILITY, "Sponge", "Great for soaking up water.", Kind.ITEM, 0, gold(3), gold(6)),
            item("popup_tower", Category.UTILITY, "Compact Pop-up Tower", "Place a pop-up defence!", iron(24)),
            // Armed's guns, sold only in that mode; the Pistol is free for everyone there.
            item("magnum", Category.RANGED, "Magnum", "6 damage, 6 rounds, 40 blocks. Left-click to reload.", gold(6)),
            item("rifle", Category.RANGED, "Rifle", "4 damage, 25 rounds, 40 blocks. Left-click to reload.", gold(8)),
            item("smg", Category.RANGED, "SMG", "2 damage, 45 rounds, 30 blocks. Left-click to reload.", iron(50)),
            item("flamethrower", Category.RANGED, "Flamethrower", "2 damage and fire, 50 rounds, 20 blocks. Left-click to reload.", gold(12)),
            item("shotgun", Category.RANGED, "Shotgun", "Six pellets of 2 damage, 4 rounds, 10 blocks. Left-click to reload.", emerald(1)),
            // The rotating items, sold only in the weeks BedWarsRotation picks them.
            item("cobweb", Category.ROTATING, "Cobweb", "A spider's home!", gold(3)),
            item("hay_bale", Category.ROTATING, "Hay Bale",
                    "Relive your assassin days by landing on the center of this Hay Bale to completely negate fall damage.", gold(4)),
            entry("mega_tnt", Category.ROTATING, "Mega TNT",
                    "This super-packed TNT has the explosive capability of blasting through even the toughest glass.",
                    Kind.ITEM, 0, emerald(1), emerald(2)),
            item("sugar_cookie", Category.ROTATING, "Sugar Cookie", "Gain Speed III and Jump IV for 15 seconds!", emerald(1)),
            item("block_zapper", Category.ROTATING, "Block Zapper", "Right Click to break a single player-placed block.", gold(5)),
            item("bridge_zapper", Category.ROTATING, "Bridge Zapper", "Right Click on wool to break 16 adjacent wool blocks.", gold(3)),
            item("lucky_chest", Category.ROTATING, "Lucky Chest", "Spawns a lucky chest with random resource drops!", gold(5)),
            entry("throwable_tnt", Category.ROTATING, "Throwable TNT", "Like normal TNT, but lighter so you can throw it!",
                    Kind.ITEM, 0, gold(6), gold(10)));

    /** How many of an item one player may buy in a match, for the items Hypixel limits. */
    public static int limit(Entry entry) {
        return switch (entry.id()) {
            case "cobweb" -> 4;
            case "lucky_chest" -> 10;
            default -> Integer.MAX_VALUE;
        };
    }

    /** The items Hypixel's Quick Buy editor lists: every item but Armed's guns and the rotating items. */
    public static List<Entry> quickBuyItems() {
        return ITEMS.stream().filter(entry -> !BedWarsGuns.isGun(entry.id()) && entry.category() != Category.ROTATING).toList();
    }

    /**
     * The Quick Buy every player starts with, row by row (21 slots, {@code ""} for an empty one): the
     * layout of the captured "Edit Quick Buy" menu.
     */
    public static final List<String> DEFAULT_QUICK_BUY = List.of(
            "wool", "stone_sword", "", "axe", "bow", "bridge_egg", "tnt",
            "wood", "iron_sword", "iron_armor", "pickaxe", "arrow", "jump_potion", "golden_apple",
            "end_stone", "diamond_sword", "ender_pearl", "shears", "bow_power_punch", "invisibility_potion", "fireball");

    public static final int QUICK_BUY_SLOTS = 21;

    public static Optional<Entry> find(String id) {
        return ITEMS.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public static List<Entry> category(Category category) {
        return ITEMS.stream().filter(entry -> entry.category() == category).toList();
    }

    /** The price of {@code entry}'s tier {@code tier} (from 0) under {@code prices}. */
    public static Cost cost(Entry entry, Prices prices, int tier) {
        List<Cost> costs = prices == Prices.SOLO ? entry.solo() : entry.teams();
        return costs.get(Math.clamp(tier, 0, costs.size() - 1));
    }

    /** Pickaxe tiers from 1: wooden, iron, golden and diamond. */
    static final Item[] PICKAXES = {Items.WOODEN_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE};
    static final String[] PICKAXE_NAMES = {
        "Wooden Pickaxe (Efficiency I)", "Iron Pickaxe (Efficiency II)",
        "Golden Pickaxe (Efficiency III, Sharpness II)", "Diamond Pickaxe (Efficiency III)"};
    /** Axe tiers from 1: wooden, stone, iron and diamond. */
    static final Item[] AXES = {Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE};
    static final String[] AXE_NAMES = {
        "Wooden Axe (Efficiency I)", "Stone Axe (Efficiency I)", "Iron Axe (Efficiency II)", "Diamond Axe (Efficiency III)"};
    private static final int[] PICKAXE_EFFICIENCY = {1, 2, 3, 3};
    private static final int[] AXE_EFFICIENCY = {1, 1, 2, 3};

    /** The pickaxe of tier {@code tier} (1 to 4). */
    public static ItemStack pickaxe(HolderLookup.Provider registries, int tier) {
        ItemStack stack = unbreakable(new ItemStack(PICKAXES[tier - 1]));
        enchant(registries, stack, Enchantments.EFFICIENCY, PICKAXE_EFFICIENCY[tier - 1]);
        if (tier == 3) enchant(registries, stack, Enchantments.SHARPNESS, 2);
        return stack;
    }

    /** The axe of tier {@code tier} (1 to 4). */
    public static ItemStack axe(HolderLookup.Provider registries, int tier) {
        ItemStack stack = unbreakable(new ItemStack(AXES[tier - 1]));
        enchant(registries, stack, Enchantments.EFFICIENCY, AXE_EFFICIENCY[tier - 1]);
        return stack;
    }

    /**
     * What buying {@code entry} puts into the inventory, in the team's colour where it has one; empty
     * for armour, which is worn instead, and for the tools, which {@link #pickaxe} and {@link #axe} make.
     */
    public static ItemStack stack(Entry entry, DyeColor team, HolderLookup.Provider registries) {
        return switch (entry.id()) {
            case "wool" -> new ItemStack(Items.WOOL.pick(team), 16);
            case "hardened_clay" -> new ItemStack(Items.DYED_TERRACOTTA.pick(team), 16);
            case "blast_proof_glass" -> new ItemStack(Items.STAINED_GLASS.pick(team), 4);
            case "end_stone" -> new ItemStack(Items.END_STONE, 12);
            case "ladder" -> new ItemStack(Items.LADDER, 8);
            case "wood" -> new ItemStack(Items.OAK_PLANKS, 16);
            case "obsidian" -> new ItemStack(Items.OBSIDIAN, 4);
            case "packed_ice" -> new ItemStack(Items.PACKED_ICE, 8);
            case "stone_sword" -> unbreakable(new ItemStack(Items.STONE_SWORD));
            case "iron_sword" -> unbreakable(new ItemStack(Items.IRON_SWORD));
            case "diamond_sword" -> unbreakable(new ItemStack(Items.DIAMOND_SWORD));
            case "knockback_stick" -> {
                ItemStack stick = new ItemStack(Items.STICK);
                enchant(registries, stick, Enchantments.KNOCKBACK, 1);
                yield stick;
            }
            case "shears" -> unbreakable(new ItemStack(Items.SHEARS));
            case "arrow" -> new ItemStack(Items.ARROW, 6);
            case "bow" -> unbreakable(new ItemStack(Items.BOW));
            case "bow_power" -> {
                ItemStack bow = unbreakable(new ItemStack(Items.BOW));
                enchant(registries, bow, Enchantments.POWER, 1);
                yield bow;
            }
            case "bow_power_punch" -> {
                ItemStack bow = unbreakable(new ItemStack(Items.BOW));
                enchant(registries, bow, Enchantments.POWER, 1);
                enchant(registries, bow, Enchantments.PUNCH, 1);
                yield bow;
            }
            case "speed_potion" -> potion(entry.name(), new MobEffectInstance(MobEffects.SPEED, 45 * 20, 1));
            case "jump_potion" -> potion(entry.name(), new MobEffectInstance(MobEffects.JUMP_BOOST, 45 * 20, 4));
            case "invisibility_potion" -> potion(entry.name(), new MobEffectInstance(MobEffects.INVISIBILITY, 30 * 20, 0));
            case "golden_apple" -> new ItemStack(Items.GOLDEN_APPLE);
            case "bedbug" -> tagged(Items.SNOWBALL, "bedbug", entry.name());
            case "dream_defender" -> tagged(Items.IRON_GOLEM_SPAWN_EGG, "dream_defender", entry.name());
            case "fireball" -> tagged(Items.FIRE_CHARGE, "fireball", entry.name());
            case "tnt" -> new ItemStack(Items.TNT);
            case "ender_pearl" -> new ItemStack(Items.ENDER_PEARL);
            case "water_bucket" -> new ItemStack(Items.WATER_BUCKET);
            case "bridge_egg" -> tagged(Items.EGG, "bridge_egg", entry.name());
            case "magic_milk" -> tagged(Items.MILK_BUCKET, "magic_milk", entry.name());
            case "sponge" -> new ItemStack(Items.SPONGE, 4);
            case "popup_tower" -> tagged(Items.TRAPPED_CHEST, "popup_tower", entry.name());
            case "cobweb" -> new ItemStack(Items.COBWEB, 4);
            case "hay_bale" -> new ItemStack(Items.HAY_BLOCK, 5);
            case "mega_tnt" -> tagged(Items.TNT, "mega_tnt", entry.name());
            case "sugar_cookie" -> tagged(Items.COOKIE, "sugar_cookie", entry.name());
            case "block_zapper" -> tagged(Items.BREEZE_ROD, "block_zapper", entry.name());
            case "bridge_zapper" -> tagged(Items.BLAZE_ROD, "bridge_zapper", entry.name());
            case "lucky_chest" -> tagged(Items.CHEST, "lucky_chest", entry.name());
            case "throwable_tnt" -> tagged(Items.TNT, "throwable_tnt", entry.name());
            default -> BedWarsGuns.Gun.of(entry.id()).map(BedWarsGuns::stack).orElse(ItemStack.EMPTY);
        };
    }

    /** The icon the shop shows for {@code entry}, at the tier the player would buy next. */
    public static ItemStack icon(Entry entry, DyeColor team, HolderLookup.Provider registries, int nextTier) {
        return switch (entry.kind()) {
            case ARMOR -> new ItemStack(switch (entry.tier()) {
                case 1 -> Items.CHAINMAIL_BOOTS;
                case 2 -> Items.IRON_BOOTS;
                default -> Items.DIAMOND_BOOTS;
            });
            case PICKAXE -> pickaxe(registries, Math.clamp(nextTier, 1, 4));
            case AXE -> axe(registries, Math.clamp(nextTier, 1, 4));
            default -> stack(entry, team, registries);
        };
    }

    /** {@code entry}'s name as the shop shows it, naming a tool's next tier. */
    public static String displayName(Entry entry, int nextTier) {
        return switch (entry.kind()) {
            case PICKAXE -> PICKAXE_NAMES[Math.clamp(nextTier, 1, 4) - 1];
            case AXE -> AXE_NAMES[Math.clamp(nextTier, 1, 4) - 1];
            default -> entry.name();
        };
    }

    /** The ability a Bed Wars item carries ({@code fireball}, {@code bridge_egg}, ...), or an empty string. */
    public static String ability(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(ITEM_KEY, "");
    }

    static ItemStack tagged(ItemLike item, String ability, String name) {
        return tag(new ItemStack(item), ability, name);
    }

    /** Gives {@code stack} the ability {@code ability} and the name {@code name}. */
    static ItemStack tag(ItemStack stack, String ability, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ITEM_KEY, ability);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    static void enchant(HolderLookup.Provider registries, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        stack.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
    }

    private static ItemStack potion(String name, MobEffectInstance effect) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), List.of(effect), Optional.empty()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** Every item id, for command suggestions. */
    public static List<String> ids() {
        return ITEMS.stream().map(Entry::id).toList();
    }
}
