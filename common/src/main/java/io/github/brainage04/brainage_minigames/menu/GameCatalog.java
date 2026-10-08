package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/** How each game appears in menus: its icon, category and a one-line summary. */
final class GameCatalog {
    enum Category {
        ALL("All Games", Items.NETHER_STAR, "Every game on the server."),
        DUELS("Duels", Items.IRON_SWORD, "Kit fights: eliminate the other teams."),
        UHC("UHC", Items.GOLDEN_APPLE, "Ultra hardcore survival fights without natural healing."),
        ARENA("Arena", Items.ENDER_EYE, "Map games: islands, floors, railguns and the void."),
        GOALS("Goals", Items.DYED_TERRACOTTA.pick(DyeColor.BLUE), "Score in the enemy goal to win."),
        WOOL("Wool Games", Items.WOOL.pick(DyeColor.ORANGE), "Steal the enemy's wool and bring it home."),
        RACES("Races", Items.FEATHER, "First to the finish wins."),
        ARCADE("Arcade", Items.CAKE, "Party and seasonal games.");

        final String title;
        final Item icon;
        final String summary;

        Category(String title, Item icon, String summary) {
            this.title = title;
            this.icon = icon;
            this.summary = summary;
        }
    }

    record Entry(Item icon, Category category, String summary) {}

    private static final Map<Minigame, Entry> ENTRIES =
            Map.ofEntries(
                    Map.entry(Minigames.UHC, new Entry(Items.GOLDEN_APPLE, Category.UHC,
                            "Gather, craft and be the last team alive. No natural regeneration; the border shrinks.")),
                    Map.entry(Minigames.SPEED_UHC, new Entry(Items.GOLDEN_CARROT, Category.UHC,
                            "Hypixel's Speed UHC: a small map, smelted ores, felled trees and a kit, perks and Mastery; about 10-15 minutes.")),
                    Map.entry(Minigames.MINI_UHC, new Entry(Items.APPLE, Category.UHC,
                            "Badlion's MiniUHC: a smaller UHC with instant border shrinks every 5 minutes down to 100 wide.")),
                    Map.entry(Minigames.MEETUP, new Entry(Items.DIAMOND_SWORD, Category.UHC,
                            "A ready-equipped UHC finish with immediate PvP inside a shrinking border.")),
                    Map.entry(Minigames.FINAL_UHC, new Entry(Items.DIAMOND_CHESTPLATE, Category.UHC,
                            "Identical diamond kits on natural terrain inside a fixed border.")),
                    Map.entry(Minigames.BUILD_UHC, new Entry(Items.LAVA_BUCKET, Category.DUELS,
                            "Enchanted diamond gear, blocks, water and lava. No natural regeneration.")),
                    Map.entry(Minigames.CLASSIC, new Entry(Items.FISHING_ROD, Category.DUELS,
                            "Iron armour, sword, axe, bow and rod. No building.")),
                    Map.entry(Minigames.NO_DEBUFF, new Entry(Items.SPLASH_POTION, Category.DUELS,
                            "Diamond gear, healing potions and pearls. Manage your healing.")),
                    Map.entry(Minigames.GAPPLE, new Entry(Items.ENCHANTED_GOLDEN_APPLE, Category.DUELS,
                            "Protection IV diamond armour and 64 golden apples.")),
                    Map.entry(Minigames.BOXING, new Entry(Items.COD, Category.DUELS,
                            "First team to 100 hits wins; hits knock back but never hurt.")),
                    Map.entry(Minigames.COMBO, new Entry(Items.SUGAR, Category.DUELS,
                            "Hits every two ticks: land long combos.")),
                    Map.entry(Minigames.BOW, new Entry(Items.BOW, Category.DUELS,
                            "Infinity bows only; melee cannot hurt.")),
                    Map.entry(Minigames.SUMO, new Entry(Items.SLIME_BALL, Category.DUELS,
                            "Empty-handed on a small platform: knock everyone off to win the round.")),
                    Map.entry(Minigames.SKYWARS, new Entry(Items.ENDER_EYE, Category.ARENA,
                            "Hypixel Insane: pick a kit and perks, loot strong island and mid chests, knock rivals into the void.")),
                    Map.entry(Minigames.SKYWARS_MINI, new Entry(Items.WOODEN_SWORD, Category.ARENA,
                            "Hypixel Mini: four players on a small map, kits with their own perks, plus perks chosen into slots.")),
                    Map.entry(Minigames.SKYWARS_MEGA, new Entry(Items.DIAMOND_SWORD, Category.ARENA,
                            "Hypixel Mega Doubles: teams of two on a large map, Mega kits and six perk slots.")),
                    Map.entry(Minigames.SKYWARS_LUCKY, new Entry(Items.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW), Category.ARENA,
                            "Insane SkyWars with lucky blocks on every island: break one for a random item or event.")),
                    Map.entry(Minigames.SPLEEF, new Entry(Items.IRON_SHOVEL, Category.ARENA,
                            "Dig the floor out from under your opponents.")),
                    Map.entry(Minigames.BOW_SPLEEF, new Entry(Items.TNT, Category.ARENA,
                            "Shoot holes in TNT floors with a flaming bow.")),
                    Map.entry(Minigames.QUAKE, new Entry(Items.WOODEN_HOE, Category.ARENA,
                            "One-hit railguns and dashes; first to 25 kills.")),
                    Map.entry(Minigames.PEARL_FIGHT, new Entry(Items.ENDER_PEARL, Category.ARENA,
                            "Knock opponents into the void with sticks and pearls.")),
                    Map.entry(Minigames.BRIDGE, new Entry(Items.DYED_TERRACOTTA.pick(DyeColor.BLUE), Category.GOALS,
                            "Cross the bridge and jump into the enemy goal; first to five.")),
                    Map.entry(Minigames.BATTLE_RUSH, new Entry(Items.WOOL.pick(DyeColor.RED), Category.GOALS,
                            "Build across the gap with wool; first to three goals.")),
                    Map.entry(Minigames.CAPTURE_THE_WOOL, new Entry(Items.WOOL.pick(DyeColor.ORANGE), Category.WOOL,
                            "Steal the enemy's wools from their wool rooms and place them on your monument.")),
                    Map.entry(Minigames.PARKOUR, new Entry(Items.LEATHER_BOOTS, Category.RACES,
                            "Reach every checkpoint in order and finish first.")),
                    Map.entry(Minigames.ICE_BOAT_RACING, new Entry(Items.OAK_BOAT, Category.RACES,
                            "Three laps through the checkpoint gates on ice.")),
                    Map.entry(Minigames.GRINCH_SIMULATOR, new Entry(Items.PLAYER_HEAD, Category.ARCADE,
                            "Seasonal: steal the most presents from the village's houses in 4 minutes.")));

    private GameCatalog() {}

    static Entry entry(Minigame game) {
        Entry entry = ENTRIES.get(game);
        return entry != null ? entry : new Entry(Items.PAPER, Category.ALL, "");
    }

    /** The games of {@code category} in the order of {@link Minigames#ALL}. */
    static List<Minigame> games(Category category) {
        return Minigames.ALL.stream()
                .filter(game -> category == Category.ALL || entry(game).category() == category)
                .toList();
    }
}
