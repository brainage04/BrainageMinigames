package io.github.brainage04.brainage_minigames.game.uhc;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A Speed UHC Mastery; a player has exactly one active. These are the nine Masteries of Hypixel's
 * Speed UHC Shop as exported on 2026-10-06, with its descriptions and its 20,000-coin price. Wild
 * Specialist is every player's first, owned without buying it.
 */
public enum SpeedUhcMastery {
    WILD_SPECIALIST("Wild Specialist", Items.MAGMA_CREAM, "All environmental damage is reduced by 50%."),
    SNIPER("Sniper", Items.BOW, "Bow shots from over 20 blocks away deal 2% extra damage per block travelled."),
    BERSERK("Berserk", Items.BLAZE_POWDER, "Obtain Strength I when below 3 hearts."),
    FORTUNE("Fortune", Items.DIAMOND, "All ores have a 25% chance of dropping an extra item."),
    MASTER_BAKER("Master Baker", Items.GOLDEN_APPLE, "Healing from Golden Apples is increased by 50%."),
    INVIGORATE("Invigorate", Items.GLISTERING_MELON_SLICE,
            "Increases your maximum health by 1 hp after each kill, with up to 4 hp extra."),
    HUNTSMAN("Huntsman", Items.SUGAR, "Gain 30 seconds of Speed II after a kill."),
    VAMPIRISM("Vampirism", Items.GHAST_TEAR, "Increases the health on kill by +1 health."),
    GUARDIAN("Guardian", Items.IRON_CHESTPLATE,
            "Reduces damage taken by other players by 5% (includes melee and bow damage).");

    public static final int COST = 20_000;

    public final String id;
    public final String displayName;
    public final Item icon;
    public final String description;

    SpeedUhcMastery(String displayName, Item icon, String description) {
        this.id = name().toLowerCase(java.util.Locale.ROOT);
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public static @Nullable SpeedUhcMastery find(String id) {
        for (SpeedUhcMastery mastery : values()) {
            if (mastery.id.equals(id)) return mastery;
        }
        return null;
    }
}
