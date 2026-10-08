package io.github.brainage04.brainage_minigames.game.uhc;

import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.COMMON;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.LEGENDARY;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.RARE;

import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A Speed UHC perk, from Hypixel's Speed UHC "Perks" menu as exported on 2026-10-06 (names,
 * rarities, costs and tier-I numbers). Upgradable perks have five tiers that each add the tier-I
 * number: a player guide of 2020 gives Arrow Recovery's top tier as 75%, five times its 15%; the
 * other perks' top tiers follow the same rule. {@code %d} in the description is the perk's number.
 */
public enum SpeedUhcPerk {
    ARROW_RECOVERY("Arrow Recovery", COMMON, Items.ARROW, 2500, 15, true, "%d%% of arrows hit are given back."),
    BOW_FLEX("Bow Flex", LEGENDARY, Items.BOW, 9000, 2, false, "Every %d bow kills adds 1 level of Power to your bow."),
    COLD_BLOOD("Cold Blood", COMMON, Items.BLAZE_POWDER, 7500, 1, true,
            "Reduces damage from Lava and Fire by 50%% for the first %ds upon being damaged."),
    ENDER_GENEROSITY("Ender Generosity", LEGENDARY, Items.ENDER_PEARL, 2500, 5, true,
            "%d%% chance to obtain an extra Ender Pearl from Endermen."),
    EXPERT_MINER("Expert Miner", RARE, Items.EXPERIENCE_BOTTLE, 2500, 5, true, "Increases experience dropped by %d%%"),
    LOW_GRAVITY("Low Gravity", COMMON, Items.IRON_BOOTS, 2500, 5, true, "Reduces fall damage by %d%%"),
    MARKSMOB("Marksmob", LEGENDARY, Items.BOW, 7500, 5, true,
            "Killing a Skeleton or Spider has a %d%% chance of dropping a Power I bow."),
    MASTER_BREWER("Master Brewer", RARE, Items.REDSTONE, 2500, 5, true, "Increases the duration of brewed potions by %d%%"),
    MEDICINE("Medicine", RARE, Items.SPIDER_EYE, 2500, 10, true, "Reduces Poison effect durations by %d%%"),
    MONSTER_TAMER("Monster Tamer", COMMON, Items.IRON_CHESTPLATE, 7500, 1, true,
            "Prevents direct damage taken from Monsters when below %d hearts."),
    NO_MERCY("No Mercy", COMMON, Items.GOLD_NUGGET, 1500, 10, false, "%d%% chance to get more coins on kills."),
    NOURISHMENT("Nourishment", COMMON, Items.COOKED_BEEF, 1500, 0, false, "Every kill restores full hunger and saturation."),
    PORTAL_PROTECTION("Portal Protection", RARE, Items.NETHER_BRICK, 2500, 4, true,
            "Obtain Absorption I for %ds when entering a Nether Portal."),
    SWIMMING_CHAMPION("Swimming Champion", COMMON, Items.WATER_BUCKET, 1500, 0, false, "Obtain Speed I while in water."),
    TELEKINESIS("Telekinesis", COMMON, Items.ENDER_EYE, 1500, 0, false, "All mined ores go directly to your inventory."),
    TENACITY("Tenacity", RARE, Items.DIAMOND_CHESTPLATE, 7500, 15, true, "Gain Resistance I for %ds at the start of the game."),
    VITAMINS("Vitamins", LEGENDARY, Items.GOLDEN_APPLE, 7500, 3, true, "Obtain Speed II for %ds upon eating a Golden Apple.");

    public static final int TIERS = 5;

    public final String id;
    public final String displayName;
    public final Rarity rarity;
    public final Item icon;
    public final int cost;
    /** The tier-I number: a percentage, seconds or hearts, as the description says. */
    public final int base;
    public final boolean upgradable;
    private final String description;

    SpeedUhcPerk(String displayName, Rarity rarity, Item icon, int cost, int base, boolean upgradable, String description) {
        this.id = name().toLowerCase(java.util.Locale.ROOT);
        this.displayName = displayName;
        this.rarity = rarity;
        this.icon = icon;
        this.cost = cost;
        this.base = base;
        this.upgradable = upgradable;
        this.description = description;
    }

    /** The perk's number at its top tier when {@code maxed}, otherwise at tier I. */
    public int value(boolean maxed) {
        return upgradable && maxed ? base * TIERS : base;
    }

    /** The name with its tier numeral, as Hypixel's menu shows it. */
    public String title(boolean maxed) {
        return upgradable ? displayName + (maxed ? " V" : " I") : displayName;
    }

    public String description(boolean maxed) {
        return description.formatted(value(maxed));
    }

    public static @Nullable SpeedUhcPerk find(String id) {
        for (SpeedUhcPerk perk : values()) {
            if (perk.id.equals(id)) return perk;
        }
        return null;
    }
}
