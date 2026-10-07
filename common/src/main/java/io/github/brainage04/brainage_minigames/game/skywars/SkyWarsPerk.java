package io.github.brainage04.brainage_minigames.game.skywars;

import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.COMMON;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.LEGENDARY;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.RARE;
import static net.minecraft.world.item.Items.*;

import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * A SkyWars perk of one mode. {@code base} is the perk's number (a chance in percent, seconds or
 * levels, as its description says); an {@link Upgrade} adds {@code perLevel} for each of its
 * {@code levels} once the perk is maxed. Insane perks are Hypixel's "Toggle Insane Perks" menu as
 * exported on 2026-10-06; upgrade level counts come from Hypixel's Angel's Descent and are listed
 * in HOW_TO_PLAY.
 */
public record SkyWarsPerk(
        String id,
        String name,
        Rarity rarity,
        Item icon,
        SkyWarsMode mode,
        String description,
        int base,
        @Nullable Upgrade upgrade,
        boolean enabledByDefault) {

    public record Upgrade(String name, String description, int perLevel, int levels) {}

    // Insane perk ids; effects are in SkyWarsPerks.
    public static final String BRIDGER = "bridger";
    public static final String BULLDOZER = "bulldozer";
    public static final String JUGGERNAUT = "juggernaut";
    public static final String KNOWLEDGE = "knowledge";
    public static final String LUCKY_CHARM = "lucky_charm";
    public static final String MINING_EXPERTISE = "mining_expertise";
    public static final String RESISTANCE_BOOST = "resistance_boost";
    public static final String SAVIOR = "savior";
    public static final String ANNOY_O_MITE = "annoy_o_mite";
    public static final String ARROW_RECOVERY = "arrow_recovery";
    public static final String BLAZING_ARROWS = "blazing_arrows";
    public static final String ENVIRONMENTAL_EXPERT = "environmental_expert";
    public static final String FAT = "fat";
    public static final String SPEED_BOOST = "speed_boost";
    public static final String BARBARIAN = "barbarian";
    public static final String BLACK_MAGIC = "black_magic";
    public static final String DIAMONDPIERCER = "diamondpiercer";
    public static final String FROST = "frost";
    public static final String MARKSMANSHIP = "marksmanship";
    public static final String NECROMANCER = "necromancer";
    public static final String ROBBERY = "robbery";
    public static final String APOTHECARY = "apothecary";
    public static final String DIAMOND_IN_THE_ROUGH = "diamond_in_the_rough";
    public static final String DOUBLE_EDGED_SWORD = "double_edged_sword";
    public static final String DRAGONS_PLEDGE = "dragons_pledge";
    public static final String ENDER_END_GAME = "ender_end_game";
    public static final String FORTUNE_TELLER = "fortune_teller";
    public static final String FRUIT_FINDER = "fruit_finder";
    public static final String HIDE_AND_SEEK = "hide_and_seek";
    public static final String LIBRARIAN = "librarian";
    public static final String TENACITY = "tenacity";

    private static final List<SkyWarsPerk> INSANE = insane();
    private static final Map<SkyWarsMode, List<SkyWarsPerk>> BY_MODE = Map.of(SkyWarsMode.INSANE, INSANE);

    public static List<SkyWarsPerk> of(SkyWarsMode mode) {
        return BY_MODE.get(mode);
    }

    public static @Nullable SkyWarsPerk find(SkyWarsMode mode, String id) {
        for (SkyWarsPerk perk : of(mode)) {
            if (perk.id().equals(id)) {
                return perk;
            }
        }
        return null;
    }

    /** The perk's number, with every upgrade level when {@code upgraded}. */
    public int value(boolean upgraded) {
        return base + (upgraded && upgrade != null ? upgrade.perLevel() * upgrade.levels() : 0);
    }

    private static List<SkyWarsPerk> insane() {
        SkyWarsMode m = SkyWarsMode.INSANE;
        return List.of(
                perk(m, BRIDGER, "Bridger", COMMON, OAK_PLANKS,
                        "Grants a 50% chance to not consume blocks when placing them.", 50, null),
                perk(m, BULLDOZER, "Bulldozer", COMMON, REDSTONE,
                        "Kills grant 5s of Strength I in solo games and 2s in team games.", 5, null),
                perk(m, JUGGERNAUT, "Juggernaut", COMMON, DYE.pick(net.minecraft.world.item.DyeColor.RED),
                        "Kills grant 10s of Regeneration I.", 10, null),
                perk(m, KNOWLEDGE, "Knowledge", COMMON, BOOK, "Kills grant 3 EXP Levels.", 3,
                        new Upgrade("Big Brain", "Kills grant an additional +2 EXP Levels.", 2, 1)),
                perk(m, LUCKY_CHARM, "Lucky Charm", COMMON, RABBIT_FOOT,
                        "Grants a 30% chance to get a Golden Apple on kill.", 30,
                        new Upgrade("Luckier Charm", "Kills have an additional 1% chance to earn you a Golden Apple.", 1, 3)),
                perk(m, MINING_EXPERTISE, "Mining Expertise", COMMON, IRON_PICKAXE,
                        "Ores have a 40% chance to drop double the materials.", 40,
                        new Upgrade("Meticulous Miner", "Grants an additional +2% chance to get double drops from ores.", 2, 5)),
                perk(m, RESISTANCE_BOOST, "Resistance Boost", COMMON, IRON_INGOT,
                        "Grants 15s of Resistance II at the start of the game.", 15, null),
                perk(m, SAVIOR, "Savior", COMMON, GOLDEN_APPLE, "Kills grant 7s of Absorption I.", 7, null),
                perk(m, ANNOY_O_MITE, "Annoy-o-mite", RARE, SILVERFISH_SPAWN_EGG,
                        "Bow hits have a 10% chance to spawn a Silverfish next to your enemy.", 10, null),
                perk(m, ARROW_RECOVERY, "Arrow Recovery", RARE, HOPPER,
                        "Bow hits have a 50% chance to give you an Arrow back.", 50, null),
                perk(m, BLAZING_ARROWS, "Blazing Arrows", RARE, BLAZE_POWDER,
                        "Arrows you shoot have a 15% chance to light on fire.", 15,
                        new Upgrade("Steel Quiver", "Arrows you shoot have an additional 2% chance to light on fire.", 2, 5)),
                perk(m, ENVIRONMENTAL_EXPERT, "Environmental Expert", RARE, OAK_SAPLING,
                        "Reduces 50% of damage taken from environmental sources.", 50, null),
                perk(m, FAT, "Fat", RARE, COOKED_BEEF, "Grants 20s of Absorption I at the start of the game.", 20, null),
                perk(m, SPEED_BOOST, "Speed Boost", RARE, SUGAR, "Grants 300s of Haste II at the start of the game.", 300,
                        new Upgrade("Adrenaline", "Grants 7s of Speed I at the start of the game.", 0, 1)),
                perk(m, BARBARIAN, "Barbarian", LEGENDARY, IRON_AXE,
                        "Grants a Sharpness level after 3 kills with an Axe.", 3, null),
                perk(m, BLACK_MAGIC, "Black Magic", LEGENDARY, CAULDRON,
                        "Void kills have a 30% chance to give you an Ender Pearl.", 30,
                        new Upgrade("Sorcerer's Spell", "Void kills have an additional 1% chance to earn you an Ender Pearl.", 1, 5)),
                perk(m, DIAMONDPIERCER, "Diamondpiercer", LEGENDARY, DIAMOND_CHESTPLATE,
                        "20% chance to crit diamond armor wearers for 20% increased damage.", 20, null),
                perk(m, FROST, "Frost", LEGENDARY, SNOWBALL,
                        "Bow crits have a 40% chance to inflict 3s of Slowness I.", 40,
                        new Upgrade("Chilled Quiver", "Bow crits have an additional 2% chance to inflict 3s of Slowness I.", 2, 5)),
                perk(m, MARKSMANSHIP, "Marksmanship", LEGENDARY, BOW,
                        "Grants a Power level after 2 kills with a Bow.", 2, null),
                perk(m, NECROMANCER, "Necromancer", LEGENDARY, ROTTEN_FLESH,
                        "Kills have a 16% chance to spawn a friendly Zombie.", 16, null),
                perk(m, ROBBERY, "Robbery", LEGENDARY, IRON_BARS,
                        "Melee hits with your fist have a 20% chance to steal a held item.", 20, null),
                perk(m, APOTHECARY, "Apothecary", LEGENDARY, BREWING_STAND,
                        "Positive potion effects have a 30% longer duration.", 30, null),
                perk(m, DIAMOND_IN_THE_ROUGH, "Diamond In The Rough", LEGENDARY, DIAMOND,
                        "Grants a 5% chance to earn a Diamond on kill.", 5,
                        new Upgrade("Diamond In The Rough", "Each further tier adds a 5% chance.", 5, 4)),
                new SkyWarsPerk(DOUBLE_EDGED_SWORD, "Double-Edged Sword", LEGENDARY, WOODEN_SWORD, m,
                        "Your first 3 Sword kills put a Sharpness level on your Sword but inflict -2 Max ❤.",
                        3, null, false),
                new SkyWarsPerk(DRAGONS_PLEDGE, "Dragon's Pledge", LEGENDARY, END_STONE, m,
                        "An Ender Pearl replaces the loot in one of your island chests. You start with only 7 Max ❤.",
                        7, null, false),
                perk(m, ENDER_END_GAME, "Ender End Game", LEGENDARY, ENDER_CHEST,
                        "Refilled chests are 10% more likely to contain an Ender Pearl.", 10, null),
                perk(m, FORTUNE_TELLER, "Fortune Teller", LEGENDARY, ENCHANTING_TABLE,
                        "Level 2 enchants are guaranteed to include Sharpness I or Protection I for Swords and Armor respectively.",
                        1, null),
                perk(m, FRUIT_FINDER, "Fruit Finder", LEGENDARY, APPLE,
                        "The first mid chest you open is guaranteed to have a Golden Apple.", 1, null),
                perk(m, HIDE_AND_SEEK, "Hide and Seek", LEGENDARY, COMPASS,
                        "Earn a Tracking Compass 30s before chests refill.", 30, null),
                perk(m, LIBRARIAN, "Librarian", LEGENDARY, BOOKSHELF,
                        "Earn a Sharpness I, Protection I, or Power I book every 3 kills.", 3, null),
                perk(m, TENACITY, "Tenacity", LEGENDARY, MAGMA_CREAM, "Heal 1❤ after each kill.", 2, null));
    }

    private static SkyWarsPerk perk(SkyWarsMode mode, String id, String name, Rarity rarity, Item icon,
            String description, int base, @Nullable Upgrade upgrade) {
        return new SkyWarsPerk(id, name, rarity, icon, mode, description, base, upgrade, true);
    }
}
