package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Prices;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Hypixel's Bed Wars team upgrades and traps, bought with diamonds from the team upgrades
 * shopkeeper ("Upgrades &amp; Traps"). Prices are the Hypixel wiki's menus for Solo/Doubles,
 * 3v3v3v3/4v4v4v4 and 40v40 Castle.
 */
public final class BedWarsUpgrades {
    private BedWarsUpgrades() {}

    /** A team upgrade with one or more tiers. */
    public enum Upgrade {
        SHARPENED_SWORDS("sharpened_swords", "Sharpened Swords", Items.IRON_SWORD,
                "Your team permanently gains Sharpness I on all swords and axes!",
                List.of(""), new int[] {4}, new int[] {8}, new int[] {40}),
        REINFORCED_ARMOR("reinforced_armor", "Reinforced Armor", Items.IRON_CHESTPLATE,
                "Your team permanently gains Protection on all armor pieces!",
                List.of("Protection I", "Protection II", "Protection III", "Protection IV"),
                new int[] {2, 4, 8, 16}, new int[] {5, 10, 20, 30}, new int[] {25, 50, 100, 150}),
        MANIAC_MINER("maniac_miner", "Maniac Miner", Items.GOLDEN_PICKAXE,
                "All players on your team permanently gain Haste.",
                List.of("Haste I", "Haste II"), new int[] {2, 4}, new int[] {4, 6}, new int[] {20, 30}),
        FORGE("forge", "Iron Forge", Items.FURNACE, "Upgrade resource spawning on your island.",
                List.of("+50% Resources", "+100% Resources", "Spawn emeralds", "+200% Resources"),
                new int[] {2, 4, 6, 8}, new int[] {4, 8, 12, 16}, new int[] {20, 40, 60, 80}),
        HEAL_POOL("heal_pool", "Heal Pool", Items.BEACON, "Creates a Regeneration field around your base!",
                List.of(""), new int[] {1}, new int[] {3}, new int[] {15}),
        DRAGON_BUFF("dragon_buff", "Dragon Buff", Items.DRAGON_EGG,
                "Your team will have 2 dragons instead of 1 during deathmatch!",
                List.of(""), new int[] {5}, new int[] {5}, new int[] {25});

        public final String id;
        public final String displayName;
        public final Item icon;
        public final String description;
        /** What each tier gives, for the tier list of upgrades with several. */
        public final List<String> tierNames;
        private final int[] solo;
        private final int[] teams;
        private final int[] castle;

        Upgrade(String id, String displayName, Item icon, String description, List<String> tierNames, int[] solo,
                int[] teams, int[] castle) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.tierNames = tierNames;
            this.solo = solo;
            this.teams = teams;
            this.castle = castle;
        }

        public int tiers() {
            return solo.length;
        }

        /** Diamonds tier {@code tier} (from 1) costs under {@code prices}. */
        public int cost(Prices prices, int tier) {
            int[] costs = switch (prices) {
                case SOLO -> solo;
                case TEAMS -> teams;
                case CASTLE -> castle;
            };
            return costs[Math.clamp(tier, 1, costs.length) - 1];
        }

        /** The forge's name at {@code tier}: Iron, Golden, Emerald and Molten Forge. */
        public String nameAt(int tier) {
            if (this == FORGE) {
                return switch (Math.clamp(tier, 1, 4)) {
                    case 1 -> "Iron Forge";
                    case 2 -> "Golden Forge";
                    case 3 -> "Emerald Forge";
                    default -> "Molten Forge";
                };
            }
            return tiers() > 1 ? displayName + " " + roman(tier) : displayName;
        }

        public static Optional<Upgrade> find(String id) {
            for (Upgrade upgrade : values()) if (upgrade.id.equals(id)) return Optional.of(upgrade);
            return Optional.empty();
        }
    }

    /** A trap: the first enemy to walk into the base sets off the first trap in the queue. */
    public enum Trap {
        ITS_A_TRAP("its_a_trap", "It's a trap!", Items.TRIPWIRE_HOOK, "Inflicts Blindness and Slowness for 8 seconds."),
        COUNTER_OFFENSIVE("counter_offensive", "Counter-Offensive Trap", Items.FEATHER,
                "Grants Speed II and Jump Boost II for 15 seconds to allied players near your base."),
        ALARM("alarm", "Alarm Trap", Items.REDSTONE_TORCH, "Reveals invisible players as well as their name and team."),
        MINER_FATIGUE("miner_fatigue", "Miner Fatigue Trap", Items.IRON_PICKAXE, "Inflict Mining Fatigue for 10 seconds.");

        public final String id;
        public final String displayName;
        public final Item icon;
        public final String description;

        Trap(String id, String displayName, Item icon, String description) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
        }

        public static Optional<Trap> find(String id) {
            for (Trap trap : values()) if (trap.id.equals(id)) return Optional.of(trap);
            return Optional.empty();
        }
    }

    /** At most this many traps wait in a team's queue. */
    public static final int TRAP_QUEUE = 3;

    /** The diamonds the next trap costs with {@code queued} traps already waiting: 1, 2 then 4; 5 each in Castle. */
    public static int trapCost(Prices prices, int queued) {
        if (prices == Prices.CASTLE) return 5;
        return 1 << Math.clamp(queued, 0, 2);
    }

    static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(number);
        };
    }
}
