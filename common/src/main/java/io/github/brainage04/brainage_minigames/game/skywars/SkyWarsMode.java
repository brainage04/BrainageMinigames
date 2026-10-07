package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * A SkyWars mode's catalog: its chest loot, kits and perks, and how players pick perks. Every kit,
 * perk and saved choice belongs to one mode ({@link SkyWarsKits}, {@link SkyWarsPerk}); the games
 * in {@link SkyWarsGame} each play one mode.
 */
public enum SkyWarsMode {
    /** Every owned perk is active unless turned off ("Toggle Insane Perks"). */
    INSANE("insane", "Insane", PerkChoice.TOGGLE, 0, 0, 0, SkyWarsKits.DEFAULT),
    /** Seven perk slots, none usable: the kits carry their own perk ("Select Mini Perks"). */
    MINI("mini", "Mini", PerkChoice.SLOTS, 7, 0, 0, "champion"),
    /** Seven perk slots: six usable, the seventh once perks are maxed ("Select Mega Perks"). */
    MEGA("mega", "Mega", PerkChoice.SLOTS, 7, 6, 7, SkyWarsKits.DEFAULT);

    /** How players choose which of their perks are active. */
    public enum PerkChoice {
        /** Owned perks are on unless the player turns them off. */
        TOGGLE,
        /** Only the perks a player put into a perk slot are on, plus the mode's global perks. */
        SLOTS
    }

    public final String id;
    public final String displayName;
    public final PerkChoice perkChoice;
    /** Perk slots the perk menu shows; for {@link PerkChoice#SLOTS} modes only. */
    public final int perkSlots;
    private final int baseSlots;
    private final int maxedSlots;
    /** The kit a player who chose none, or no longer owns their choice, gets. */
    public final String defaultKit;
    /** Rolled into island chests (maps mark them {@code skywars/island}) at the start and refills. */
    public final ResourceKey<LootTable> islandLoot;
    /** Rolled into mid chests (maps mark them {@code skywars/mid}) at the start and refills. */
    public final ResourceKey<LootTable> midLoot;

    SkyWarsMode(String id, String displayName, PerkChoice perkChoice, int perkSlots, int baseSlots, int maxedSlots,
            String defaultKit) {
        this.id = id;
        this.displayName = displayName;
        this.perkChoice = perkChoice;
        this.perkSlots = perkSlots;
        this.baseSlots = baseSlots;
        this.maxedSlots = maxedSlots;
        this.defaultKit = defaultKit;
        this.islandLoot = table(id + "/island");
        this.midLoot = table(id + "/mid");
    }

    /** Perk slots a player may fill: every slot once perks are maxed, the base slots otherwise. */
    public int usableSlots(boolean maxedPerks) {
        return maxedPerks ? maxedSlots : baseSlots;
    }

    /** The perk menu's title, as Hypixel names it. */
    public String perksTitle() {
        return (perkChoice == PerkChoice.TOGGLE ? "Toggle " : "Select ") + displayName + " Perks";
    }

    public String kitsTitle() {
        return displayName + " Kits";
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, BrainageMinigames.id("skywars/" + path));
    }

    public static @Nullable SkyWarsMode find(String id) {
        for (SkyWarsMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return null;
    }
}
