package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * A SkyWars mode: its chest loot, kits and perks. Every kit, perk and saved choice belongs to one
 * mode, so modes with their own kits and perks (Hypixel's Mini, Mega or Lucky Block) add a constant
 * here plus their entries in {@link SkyWarsKits} and {@link SkyWarsPerk}.
 */
public enum SkyWarsMode {
    INSANE("insane", "Insane");

    public final String id;
    public final String displayName;
    /** Rolled into island chests (maps mark them {@code skywars/island}) at the start and refills. */
    public final ResourceKey<LootTable> islandLoot;
    /** Rolled into mid chests (maps mark them {@code skywars/mid}) at the start and refills. */
    public final ResourceKey<LootTable> midLoot;

    SkyWarsMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.islandLoot = table(id + "/island");
        this.midLoot = table(id + "/mid");
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
