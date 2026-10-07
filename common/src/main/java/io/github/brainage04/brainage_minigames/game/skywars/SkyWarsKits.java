package io.github.brainage04.brainage_minigames.game.skywars;

import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.COMMON;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.LEGENDARY;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.MYTHICAL;
import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity.RARE;
import static net.minecraft.world.item.Items.*;
import static net.minecraft.world.item.enchantment.Enchantments.*;

import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Part;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Rarity;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * Every SkyWars kit per mode. Insane, Mini and Mega kits and their contents are Hypixel's kit menus
 * for those modes as exported on 2026-10-06 (Mega's with every kit maxed, as Hypixel has them by
 * default); what those menus do not show (custom item effects, potion durations they leave out)
 * comes from Hypixel's patch notes or is a documented local choice, see docs/SKYWARS.md.
 */
public final class SkyWarsKits {
    public static final String DEFAULT = "default";

    private static final int WHITE = 0xF9FFFE;
    private static final int PURPLE = 0x8932B8;
    private static final int GREEN = 0x5E7C16;
    private static final int ORANGE = 0xF9801D;
    private static final int GREY = 0x474F52;
    private static final int GRAY = 0x9D9D97;
    private static final int BLUE = 0x3C44AA;
    private static final int RED = 0xB02E26;

    /** The 1.8 music discs. */
    private static final List<Item> DISCS = List.of(MUSIC_DISC_13, MUSIC_DISC_CAT, MUSIC_DISC_BLOCKS,
            MUSIC_DISC_CHIRP, MUSIC_DISC_FAR, MUSIC_DISC_MALL, MUSIC_DISC_MELLOHI, MUSIC_DISC_STAL,
            MUSIC_DISC_STRAD, MUSIC_DISC_WARD, MUSIC_DISC_11, MUSIC_DISC_WAIT);

    private static final List<Item> SWORDS = List.of(WOODEN_SWORD, STONE_SWORD, GOLDEN_SWORD, IRON_SWORD, DIAMOND_SWORD);
    private static final List<ResourceKey<Enchantment>> SWORD_ENCHANTMENTS =
            List.of(SHARPNESS, SMITE, BANE_OF_ARTHROPODS, KNOCKBACK, FIRE_ASPECT);

    private static final List<ResourceKey<Enchantment>> BOOK_ENCHANTMENTS = List.of(SHARPNESS, PROTECTION, POWER);
    private static final List<Item> DIAMOND_ARMOR = List.of(DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS);

    private static final Map<SkyWarsMode, List<SkyWarsKit>> BY_MODE = Map.of(
            SkyWarsMode.INSANE, insane(), SkyWarsMode.MINI, mini(), SkyWarsMode.MEGA, mega());

    /**
     * Kits bots pick from when they chose none: kits whose armour and weapon a combat bot uses
     * straight away.
     */
    private static final Map<SkyWarsMode, List<String>> BOT_KITS = Map.of(
            SkyWarsMode.INSANE, List.of("armorer", "knight", "pro", "scout", "baseball_player", "speleologist",
                    "pig_rider", "farmer", "salmon", "ecologist", "fallen_angel", "golem"),
            SkyWarsMode.MINI, List.of("athlete", "blacksmith", "bowman", "champion", "healer", "hound", "magician",
                    "paladin", "pyromancer", "scout"),
            SkyWarsMode.MEGA, List.of("default", "baseball_player", "cannoneer", "hellhound", "knight", "paladin", "scout"));

    private SkyWarsKits() {}

    public static List<SkyWarsKit> of(SkyWarsMode mode) {
        return BY_MODE.get(mode);
    }

    /** The ids of the kits bots pick from in the mode; see {@link SkyWarsGame#kitFor}. */
    public static List<String> botKits(SkyWarsMode mode) {
        return BOT_KITS.get(mode);
    }

    public static @Nullable SkyWarsKit find(SkyWarsMode mode, String id) {
        for (SkyWarsKit kit : of(mode)) {
            if (kit.id().equals(id)) {
                return kit;
            }
        }
        return null;
    }

    private static List<SkyWarsKit> insane() {
        List<SkyWarsKit> kits = new ArrayList<>();
        SkyWarsMode mode = SkyWarsMode.INSANE;
        // Hypixel's lowest-rarity-first order.
        kits.add(kit(mode, DEFAULT, "Default", COMMON, IRON_PICKAXE,
                s(IRON_PICKAXE), s(IRON_AXE), s(IRON_SHOVEL), s(IRON_SWORD), s(IRON_CHESTPLATE)));
        kits.add(kit(mode, "armorer", "Armorer", COMMON, IRON_CHESTPLATE,
                s(IRON_CHESTPLATE), s(IRON_LEGGINGS), s(DIAMOND_BOOTS),
                potion(SPLASH_POTION, "Splash Potion of Resistance", MobEffects.RESISTANCE, 10, 0)));
        kits.add(kit(mode, "armorsmith", "Armorsmith", COMMON, ANVIL,
                s(ANVIL),
                s(ENCHANTED_BOOK).enchant(PROTECTION, 3).enchant(SHARPNESS, 1).enchant(POWER, 3)
                        .enchant(FEATHER_FALLING, 4).enchant(INFINITY, 1),
                s(EXPERIENCE_BOTTLE).count(64), s(DIAMOND_HELMET), s(ENCHANTED_BOOK).enchant(POWER, 1)));
        kits.add(kit(mode, "ecologist", "Ecologist", COMMON, IRON_AXE,
                s(IRON_AXE).enchant(EFFICIENCY, 2).enchant(SHARPNESS, 1), s(OAK_LOG).count(24),
                s(LEATHER_LEGGINGS).enchant(PROTECTION, 3)));
        kits.add(kit(mode, "healer", "Healer", COMMON, CAKE,
                s(SPLASH_POTION).potion(Potions.HEALING).name("Splash Potion of Instant Health I").count(3),
                potion(SPLASH_POTION, "Splash Potion of Regeneration II (16s)", MobEffects.REGENERATION, 16, 1).count(2),
                s(GOLDEN_APPLE).count(2)));
        kits.add(kit(mode, "knight", "Knight", COMMON, IRON_SWORD,
                s(IRON_SWORD).enchant(SHARPNESS, 2), s(GOLDEN_HELMET).enchant(PROTECTION, 2).enchant(UNBREAKING, 3)));
        kits.add(kit(mode, "pro", "Pro", COMMON, IRON_HELMET,
                s(IRON_HELMET), s(IRON_CHESTPLATE), s(IRON_SWORD).enchant(SHARPNESS, 1), s(GLASS).count(16),
                s(GOLDEN_APPLE)));
        kits.add(kit(mode, "scout", "Scout", COMMON, POTION,
                s(DIAMOND_SWORD), potion(POTION, "Potion of Speed II (60s)", MobEffects.SPEED, 60, 1).count(2),
                s(DIAMOND_BOOTS)));
        kits.add(kit(mode, "batguy", "Batguy", COMMON, LEATHER_HELMET,
                s(LEATHER_HELMET).enchant(PROTECTION, 2), s(LEATHER_CHESTPLATE), s(LEATHER_LEGGINGS),
                s(IRON_BOOTS).enchant(PROTECTION, 2),
                potion(SPLASH_POTION, "Splash Potion of Blindness I (11s)", MobEffects.BLINDNESS, 11, 0).count(2),
                s(BAT_SPAWN_EGG).count(10)));
        kits.add(kit(mode, "disco", "Disco", COMMON, JUKEBOX,
                s(LEATHER_HELMET).enchant(PROJECTILE_PROTECTION, 4),
                s(LEATHER_CHESTPLATE).enchant(PROTECTION, 3).enchant(THORNS, 3),
                s(LEATHER_LEGGINGS).enchant(PROTECTION, 4), s(LEATHER_BOOTS).enchant(FEATHER_FALLING, 4),
                s(JUKEBOX), s(NOTE_BLOCK).count(12),
                new Part("Random Music Disc", (registries, random) -> new ItemStack(DISCS.get(random.nextInt(DISCS.size()))))));
        kits.add(kit(mode, "energix", "Energix", COMMON, POTION,
                potion(POTION, "Potion of Strength I (7s)", MobEffects.STRENGTH, 7, 0),
                s(LEATHER_LEGGINGS).enchant(PROTECTION, 2)));
        kits.add(kit(mode, "cactus", "Cactus", COMMON, CACTUS,
                s(CACTUS).count(16), s(SAND).count(32), s(SANDSTONE).count(16),
                s(LEATHER_CHESTPLATE).enchant(THORNS, 5).enchant(UNBREAKING, 5),
                s(LEATHER_LEGGINGS).enchant(THORNS, 5).enchant(UNBREAKING, 5),
                s(LEATHER_HELMET).enchant(THORNS, 5).enchant(UNBREAKING, 5)));
        kits.add(kit(mode, "frog", "Frog", COMMON, PLAYER_HEAD,
                s(PLAYER_HEAD).name("Frog's Hat"), s(LEATHER_CHESTPLATE), s(LEATHER_LEGGINGS), s(LEATHER_BOOTS),
                s(SPLASH_POTION).name("Frog's Potion")
                        .effect(new MobEffectInstance(MobEffects.SPEED, 40 * 20, 1))
                        .effect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40 * 20, 3)),
                s(LILY_PAD).count(16)));
        kits.add(kit(mode, "grenade", "Grenade", COMMON, CREEPER_SPAWN_EGG,
                s(CREEPER_SPAWN_EGG).name("Charged Creeper Egg").count(3).ability(SkyWarsItems.CHARGED_CREEPER_EGG),
                s(LEATHER_CHESTPLATE).enchant(BLAST_PROTECTION, 4), s(LEATHER_LEGGINGS).enchant(BLAST_PROTECTION, 4),
                s(LEATHER_BOOTS).enchant(BLAST_PROTECTION, 4)));
        kits.add(kit(mode, "farmer", "Farmer", RARE, DIAMOND_LEGGINGS,
                s(DIAMOND_LEGGINGS).enchant(PROJECTILE_PROTECTION, 4), s(EGG).count(16), s(GOLDEN_APPLE)));
        kits.add(kit(mode, "baseball_player", "Baseball Player", RARE, IRON_HELMET,
                s(IRON_HELMET).enchant(PROTECTION, 4), s(STONE_SWORD).enchant(KNOCKBACK, 1)));
        kits.add(kit(mode, "enchanter", "Enchanter", RARE, ENCHANTING_TABLE,
                s(ENCHANTING_TABLE), s(EXPERIENCE_BOTTLE).count(64), s(BOOKSHELF).count(8),
                s(GOLDEN_HELMET), s(GOLDEN_CHESTPLATE), s(GOLDEN_LEGGINGS), s(GOLDEN_BOOTS)));
        kits.add(kit(mode, "hunter", "Hunter", RARE, BOW,
                s(BOW).enchant(POWER, 2), s(ARROW).count(16)));
        kits.add(kit(mode, "pharaoh", "Pharaoh", RARE, GOLDEN_HELMET,
                s(GOLDEN_HELMET).enchant(PROTECTION, 4).enchant(UNBREAKING, 5),
                s(LEATHER_CHESTPLATE).dye(WHITE).name("White Leather Chestplate"),
                s(LEATHER_LEGGINGS).dye(WHITE).name("White Leather Leggings"),
                s(BEACON), s(EMERALD_BLOCK).count(42),
                s(GOLDEN_BOOTS).enchant(PROTECTION, 4).enchant(UNBREAKING, 5),
                s(IRON_BLOCK).count(2), s(GOLD_BLOCK).count(2)));
        kits.add(kit(mode, "snowman", "Snowman", RARE, SNOWBALL,
                s(SNOWBALL).count(32), s(SNOW_BLOCK).count(2), s(DIAMOND_SHOVEL).enchant(SHARPNESS, 3),
                // A carved pumpkin, since only that builds a snow golem on current versions.
                s(CARVED_PUMPKIN), s(COAL_BLOCK).count(16), s(LEATHER_HELMET).enchant(FIRE_PROTECTION, 4),
                s(LEATHER_CHESTPLATE).dye(WHITE).name("White Leather Chestplate"),
                s(LEATHER_LEGGINGS).dye(WHITE).name("White Leather Leggings"),
                s(LEATHER_BOOTS).dye(WHITE).name("White Leather Boots")));
        kits.add(kit(mode, "speleologist", "Speleologist", RARE, DIAMOND_PICKAXE,
                s(DIAMOND_PICKAXE).enchant(EFFICIENCY, 3).enchant(SHARPNESS, 2).enchant(UNBREAKING, 3)
                        .enchant(FORTUNE, 2),
                s(STONE).count(32), s(DIAMOND_HELMET)));
        kits.add(kit(mode, "warlock", "Warlock", RARE, BREWING_STAND,
                s(SPLASH_POTION).potion(Potions.HARMING).name("Splash Potion of Instant Damage I").count(2),
                potion(SPLASH_POTION, "Splash Potion of Weakness III (10s)", MobEffects.WEAKNESS, 10, 2).count(2),
                potion(SPLASH_POTION, "Splash Potion of Poison I (10s)", MobEffects.POISON, 10, 0),
                s(GOLDEN_APPLE).count(2), s(LEATHER_CHESTPLATE).enchant(PROTECTION, 4),
                s(CHAINMAIL_BOOTS).enchant(PROTECTION, 1)));
        List<Stack> engineer = new ArrayList<>(List.of(
                s(TRIPWIRE_HOOK).count(64), s(COBWEB).count(8), s(PISTON).count(64), s(SLIME_BALL).count(64),
                s(REDSTONE).count(64), s(LEVER).count(64), s(DISPENSER).count(64), s(GUNPOWDER).count(64),
                s(SAND).count(64), s(GRAVEL).count(64), s(ARROW).count(64)));
        // "Flint and Steel x4": four single items, since it does not stack.
        for (int i = 0; i < 4; i++) {
            engineer.add(s(FLINT_AND_STEEL));
        }
        engineer.add(s(GOLDEN_HELMET).enchant(PROTECTION, 2));
        kits.add(kit(mode, "engineer", "Engineer", RARE, REDSTONE, engineer.toArray()));
        kits.add(kit(mode, "pig_rider", "Pig Rider", RARE, CARROT_ON_A_STICK,
                s(SADDLE), s(PIG_SPAWN_EGG),
                s(GOLDEN_HELMET).enchant(PROTECTION, 2), s(GOLDEN_CHESTPLATE).enchant(PROTECTION, 2),
                s(GOLDEN_LEGGINGS).enchant(PROTECTION, 2), s(GOLDEN_BOOTS).enchant(PROTECTION, 2),
                s(CARROT_ON_A_STICK), s(GOLDEN_SWORD).enchant(SHARPNESS, 2), s(HAY_BLOCK).count(16)));
        kits.add(withNotes(kit(mode, "sloth", "Sloth", RARE, POTION,
                        potion(SPLASH_POTION, "Sloth Potion", MobEffects.SLOWNESS, 10, 1).count(5),
                        s(LEATHER_CHESTPLATE).enchant(PROTECTION, 1), s(LEATHER_LEGGINGS).enchant(PROTECTION, 1),
                        s(LEATHER_HELMET).enchant(PROTECTION, 1), s(LEATHER_BOOTS).enchant(PROTECTION, 1),
                        s(JUNGLE_LOG).count(16), s(GHAST_TEAR).enchant(SHARPNESS, 5)),
                "Has permanent slowness II"));
        kits.add(kit(mode, "magician", "Magician", RARE, SPLASH_POTION,
                s(RABBIT_SPAWN_EGG), s(LEATHER_HELMET),
                potion(SPLASH_POTION, "Magician Potion", MobEffects.INVISIBILITY, 15, 0).count(2),
                s(STICK).name("The Wand").enchant(SHARPNESS, 7)));
        kits.add(withNotes(kit(mode, "enderchest", "Enderchest", RARE, ENDER_CHEST, s(GOLDEN_APPLE).count(3)),
                "Spawns a 4th chest with spawn loot below the cage"));
        kits.add(kit(mode, "fisherman", "Fisherman", RARE, FISHING_ROD,
                s(FISHING_ROD).enchant(UNBREAKING, 10).enchant(LUCK_OF_THE_SEA, 10).enchant(LURE, 10),
                s(CHAINMAIL_HELMET).enchant(PROTECTION, 2).enchant(RESPIRATION, 3),
                s(CHAINMAIL_LEGGINGS).enchant(PROTECTION, 1)));
        kits.add(kit(mode, "princess", "Princess", RARE, POPPY,
                s(GOLDEN_HELMET).enchant(PROTECTION, 4), s(BOW).enchant(POWER, 1), s(ARROW).count(5)));
        kits.add(kit(mode, "cannoneer", "Cannoneer", LEGENDARY, TNT,
                s(TNT).count(24), s(REDSTONE_BLOCK).count(10),
                s(DIAMOND_BOOTS).enchant(FEATHER_FALLING, 4).enchant(BLAST_PROTECTION, 4),
                s(WATER_BUCKET), s(STONE_PRESSURE_PLATE).count(4)));
        kits.add(kit(mode, "enderman", "Enderman", LEGENDARY, ENDER_PEARL,
                s(ENDER_PEARL).name("Corrupted Pearl").ability(SkyWarsItems.CORRUPTED_PEARL).glint(),
                s(IRON_HELMET).enchant(PROTECTION, 2), s(IRON_BOOTS).enchant(PROTECTION, 2)));
        kits.add(kit(mode, "guardian", "Guardian", LEGENDARY, OBSIDIAN,
                s(OBSIDIAN).count(10),
                potion(SPLASH_POTION, "Splash Potion of Resistance I (10s)", MobEffects.RESISTANCE, 10, 0).count(2),
                s(SKELETON_SPAWN_EGG).count(2), s(ZOMBIE_SPAWN_EGG).count(3),
                s(CHAINMAIL_LEGGINGS).enchant(PROTECTION, 1), s(CHAINMAIL_BOOTS).enchant(PROTECTION, 1)));
        kits.add(kit(mode, "archeologist", "Archeologist", LEGENDARY, GOLDEN_PICKAXE,
                s(GOLDEN_HELMET).name("Knight's Helmet").enchant(UNBREAKING, 10).enchant(PROTECTION, 2),
                s(IRON_CHESTPLATE).name("Legionnaire's Chestplate").enchant(PROTECTION, 1),
                s(LEATHER_BOOTS).name("Red Socks").dye(RED).enchant(PROTECTION, 2),
                s(FISHING_ROD).name("Simple Fishing Rod").enchant(UNBREAKING, 10).enchant(LUCK_OF_THE_SEA, 3),
                s(POTION).name("Blazing Potion")
                        .effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30 * 20, 0))
                        .effect(new MobEffectInstance(MobEffects.SPEED, 30 * 20, 0)),
                s(POTION).name("Potion of Hearts").effect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0))));
        kits.add(kit(mode, "fallen_angel", "Fallen Angel", LEGENDARY, DIAMOND_HOE,
                s(DIAMOND_HOE).enchant(SHARPNESS, 9)));
        kits.add(kit(mode, "salmon", "Salmon", LEGENDARY, SALMON,
                s(SALMON).enchant(SHARPNESS, 3).enchant(KNOCKBACK, 1), s(SPONGE).count(16),
                s(LEATHER_LEGGINGS).enchant(PROTECTION, 2), s(LEATHER_BOOTS).enchant(PROTECTION, 2)));
        kits.add(kit(mode, "slime", "Slime", LEGENDARY, SLIME_BLOCK,
                s(DIAMOND_BOOTS).enchant(FEATHER_FALLING, 10).enchant(PROTECTION, 3), s(SLIME_BLOCK).count(64)));
        kits.add(kit(mode, "jester", "Jester", LEGENDARY, DIAMOND_SWORD,
                new Part("Random Sword", SkyWarsKits::randomSword),
                potion(SPLASH_POTION, "Splash Potion of Regeneration II (10s)", MobEffects.REGENERATION, 10, 1).count(2),
                s(GOLDEN_APPLE).count(2)));
        kits.add(kit(mode, "zookeeper", "Zookeeper", LEGENDARY, POLAR_BEAR_SPAWN_EGG,
                s(POLAR_BEAR_SPAWN_EGG).name("Mystery Egg").count(5).ability(SkyWarsItems.MYSTERY_EGG)));
        kits.add(kit(mode, "pyro", "Pyro", LEGENDARY, FLINT_AND_STEEL,
                s(FLINT_AND_STEEL), s(LAVA_BUCKET), s(LAVA_BUCKET), s(LAVA_BUCKET), s(LAVA_BUCKET), s(LAVA_BUCKET),
                s(DIAMOND_CHESTPLATE), s(POTION).potion(Potions.FIRE_RESISTANCE).name("Potion of Fire Resistance")));
        kits.add(kit(mode, "troll", "Troll", LEGENDARY, COBWEB,
                s(COBWEB).count(12), s(FIREWORK_ROCKET).count(5),
                troll(LEATHER_HELMET), troll(LEATHER_CHESTPLATE), troll(LEATHER_LEGGINGS), troll(LEATHER_BOOTS),
                potion(SPLASH_POTION, "Splash Potion of Poison II (5s)", MobEffects.POISON, 5, 1),
                potion(SPLASH_POTION, "Splash Potion of Slowness II (5s)", MobEffects.SLOWNESS, 5, 1).count(2)));
        kits.add(kit(mode, "golem", "Golem", LEGENDARY, BAKED_POTATO,
                s(IRON_CHESTPLATE).enchant(PROTECTION, 2), s(IRON_BOOTS).enchant(PROTECTION, 3),
                s(BAKED_POTATO).name("My Precious").ability(SkyWarsItems.MY_PRECIOUS),
                s(POPPY).name("Golem's Poppy").enchant(SHARPNESS, 1)));
        kits.add(kit(mode, "end_lord", "End Lord", MYTHICAL, ENDER_EYE,
                leather(LEATHER_CHESTPLATE, PURPLE, "Purple"), leather(LEATHER_LEGGINGS, PURPLE, "Purple"),
                leather(LEATHER_BOOTS, PURPLE, "Purple"),
                s(ENDER_PEARL).name("Time Warp Pearl").count(2).ability(SkyWarsItems.TIME_WARP_PEARL).glint()));
        kits.add(withNotes(kit(mode, "monster_trainer", "Monster Trainer", MYTHICAL, ZOMBIE_SPAWN_EGG,
                        leather(LEATHER_CHESTPLATE, GREEN, "Green"), leather(LEATHER_LEGGINGS, GREEN, "Green"),
                        leather(LEATHER_BOOTS, GREEN, "Green"),
                        s(EGG).name("Capture Egg").count(5).ability(SkyWarsItems.CAPTURE_EGG),
                        s(ZOMBIE_SPAWN_EGG).name("Throwable Spawn Egg").ability(SkyWarsItems.THROWABLE_SPAWN_EGG)),
                "Throwable Spawn Egg."));
        kits.add(kit(mode, "nether_lord", "Nether Lord", MYTHICAL, BLAZE_POWDER,
                leather(LEATHER_CHESTPLATE, ORANGE, "Orange"), leather(LEATHER_LEGGINGS, ORANGE, "Orange"),
                leather(LEATHER_BOOTS, ORANGE, "Orange"),
                s(GOLD_NUGGET).name("Nether Lord's Fire Nugget").enchant(FIRE_ASPECT, 1).enchant(SHARPNESS, 2)
                        .ability(SkyWarsItems.FIRE_NUGGET),
                potion(POTION, "Potion of Fire Resistance", MobEffects.FIRE_RESISTANCE, 120, 1),
                s(NETHERRACK).count(16)));
        kits.add(kit(mode, "fishmonger", "Fishmonger", MYTHICAL, SILVERFISH_SPAWN_EGG,
                leather(LEATHER_CHESTPLATE, GREY, "Grey"), leather(LEATHER_LEGGINGS, GREY, "Grey"),
                leather(LEATHER_BOOTS, GREY, "Grey"),
                s(SILVERFISH_SPAWN_EGG).name("Fishmonger Silverfish").count(3).ability(SkyWarsItems.FISHMONGER_SILVERFISH)));
        kits.add(kit(mode, "thundermeister", "Thundermeister", MYTHICAL, GOLDEN_AXE,
                leather(LEATHER_CHESTPLATE, BLUE, "Blue"), leather(LEATHER_LEGGINGS, BLUE, "Blue"),
                leather(LEATHER_BOOTS, BLUE, "Blue"),
                s(GOLDEN_AXE).name("Thundermeister Axe").ability(SkyWarsItems.THUNDERMEISTER_AXE)));
        kits.add(kit(mode, "chronobreaker", "Chronobreaker", MYTHICAL, CLOCK,
                leather(LEATHER_CHESTPLATE, BLUE, "Blue"), leather(LEATHER_LEGGINGS, BLUE, "Blue"),
                leather(LEATHER_BOOTS, BLUE, "Blue"),
                s(CLOCK).name("Echo").count(2).ability(SkyWarsItems.ECHO)));
        kits.add(kit(mode, "cryomancer", "Cryomancer", MYTHICAL, PACKED_ICE,
                leather(LEATHER_CHESTPLATE, GRAY, "Gray"), leather(LEATHER_LEGGINGS, GRAY, "Gray"),
                leather(LEATHER_BOOTS, GRAY, "Gray"), s(SNOWBALL).count(16),
                s(EGG).name("Ice Bridge Egg").count(2).ability(SkyWarsItems.ICE_BRIDGE_EGG)));
        return List.copyOf(kits);
    }

    /**
     * Mini kits: Hypixel's "Mini Kits" menu, A to Z. Each kit's perk is part of the kit (Hypixel's
     * Mini has no perk slots); its effect is in {@link SkyWarsPerks}. Champion is the default kit.
     */
    private static List<SkyWarsKit> mini() {
        SkyWarsMode mode = SkyWarsMode.MINI;
        List<SkyWarsKit> kits = new ArrayList<>();
        kits.add(withNotes(kit(mode, "armorer", "Armorer", null, DIAMOND_CHESTPLATE,
                        s(DIAMOND_CHESTPLATE).name("Armorer's Diamond Chestplate").enchant(PROTECTION, 1),
                        s(DIAMOND_BOOTS).name("Armorer's Diamond Boots"), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET),
                        s(IRON_LEGGINGS)),
                "Perk: Kills grant a Protection level on your armor."));
        kits.add(withNotes(kit(mode, "athlete", "Athlete", null, WATER_BUCKET,
                        s(WATER_BUCKET), s(DIAMOND_SWORD), s(FISHING_ROD),
                        potion(POTION, "Potion of Strength (0:40)", MobEffects.STRENGTH, 40, 0),
                        potion(SPLASH_POTION, "Splash Potion of Speed III (0:04)", MobEffects.SPEED, 4, 2).count(2),
                        s(GOLDEN_APPLE).count(2), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE),
                        s(IRON_LEGGINGS), s(IRON_BOOTS).enchant(FEATHER_FALLING, 10)),
                "Perk: Positive potion effects have a 50% longer duration."));
        List<Object> blacksmith = new ArrayList<>(List.of(s(ANVIL),
                new Part("Random Diamond Armor Piece.", (registries, random) ->
                        new ItemStack(DIAMOND_ARMOR.get(random.nextInt(DIAMOND_ARMOR.size())))),
                s(IRON_SWORD), s(DIAMOND_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE), s(IRON_LEGGINGS),
                s(IRON_BOOTS)));
        for (int book = 0; book < 2; book++) {
            blacksmith.add(new Part(book == 0 ? "Two enchanted books with Sharpness I, Protection I or Power I." : "",
                    (registries, random) -> randomBook(registries, random, 1)));
        }
        blacksmith.add(s(BOW));
        blacksmith.add(s(ARROW).count(16));
        kits.add(withNotes(kit(mode, "blacksmith", "Blacksmith", null, ANVIL, blacksmith.toArray()),
                "Start the game with 15 EXP levels.",
                "Perk: Kills grant 3 EXP Levels and a random enchanted book (up to LVL 3)."));
        kits.add(withNotes(kit(mode, "bowman", "Bowman", null, BOW,
                        s(BOW).name("Bowman's Bow").enchant(POWER, 3), s(ARROW).count(32), s(IRON_SWORD), s(IRON_PICKAXE),
                        s(IRON_AXE), s(DIAMOND_HELMET).name("Bowman's Diamond Helmet").enchant(PROTECTION, 2),
                        s(IRON_CHESTPLATE), s(IRON_LEGGINGS), s(IRON_BOOTS)),
                "Perk: Kills grant a Power level on your bow and a Splash Potion of Instant Heal I."));
        List<Object> champion = new ArrayList<>(List.of(
                s(DIAMOND_SWORD).name("Champion's Diamond Sword").enchant(SHARPNESS, 2), s(IRON_PICKAXE), s(IRON_AXE),
                s(IRON_HELMET), s(IRON_CHESTPLATE), s(IRON_LEGGINGS), s(IRON_BOOTS), s(ANVIL)));
        // "Enchanted Book x3": three single books, since they do not stack.
        for (int book = 0; book < 3; book++) {
            champion.add(s(ENCHANTED_BOOK).enchant(SHARPNESS, 1));
        }
        kits.add(withNotes(kit(mode, "champion", "Champion", null, DIAMOND_SWORD, champion.toArray()),
                "Perk: Kills grant a Sharpness level on your sword."));
        kits.add(withNotes(kit(mode, "healer", "Healer", null, CAKE,
                        potion(SPLASH_POTION, "Splash Potion of Regeneration II (12s)", MobEffects.REGENERATION, 12, 1).count(2),
                        s(SPLASH_POTION).potion(Potions.HEALING).name("Splash Potion of Healing (2❤)").count(2),
                        s(DIAMOND_SWORD), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE),
                        s(IRON_LEGGINGS), s(DIAMOND_BOOTS), s(GOLDEN_APPLE).count(2)),
                "Perk: Kills grant +2 Max ❤, a Golden Apple, and heal 4❤."));
        kits.add(withNotes(kit(mode, "hound", "Hound", null, WOLF_SPAWN_EGG,
                        s(COOKED_BEEF).count(16), s(DIAMOND_BOOTS).name("Hound's Diamond Boots").enchant(PROTECTION, 2),
                        s(DIAMOND_SWORD), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE),
                        s(IRON_LEGGINGS)),
                "You spawn with a tamed wolf (20HP, Resistance II).",
                "Perk: Kills spawn a Resistance II 10❤ Wolf and 16 Steaks. If no Wolves are alive, spawn 2 instead."));
        kits.add(withNotes(kit(mode, "magician", "Magician", null, BREWING_STAND,
                        s(DIAMOND_SWORD), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET),
                        s(DIAMOND_CHESTPLATE).enchant(FIRE_PROTECTION, 2), s(IRON_LEGGINGS), s(IRON_BOOTS),
                        s(MILK_BUCKET), s(MILK_BUCKET),
                        s(SPLASH_POTION).name("Splash Potion of Weakness I (8s) and Poison I (8s)")
                                .effect(new MobEffectInstance(MobEffects.WEAKNESS, 8 * 20, 0))
                                .effect(new MobEffectInstance(MobEffects.POISON, 8 * 20, 0)).count(2),
                        s(SPLASH_POTION).potion(Potions.HARMING).name("Splash Potion of Harming (2❤)").count(3)),
                "Perk: Kills grant a random positive potion effect."));
        kits.add(withNotes(kit(mode, "paladin", "Paladin", null, DIAMOND_LEGGINGS,
                        s(DIAMOND_SWORD), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE),
                        s(DIAMOND_LEGGINGS).name("Paladin's Diamond Leggings").enchant(PROTECTION, 2), s(IRON_BOOTS),
                        s(SPLASH_POTION).name("Splash Potion of Resistance I (15s) and Regeneration I (8s)")
                                .effect(new MobEffectInstance(MobEffects.RESISTANCE, 15 * 20, 0))
                                .effect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 0)).count(3)),
                "Perk: Your first kill grants 3s of Resistance III. Your second kill grants 4s of Resistance II."));
        kits.add(withNotes(kit(mode, "pyromancer", "Pyromancer", null, FLINT_AND_STEEL,
                        potion(SPLASH_POTION, "Splash Potion of Fire Resistance I (90s)", MobEffects.FIRE_RESISTANCE, 90, 0),
                        s(DIAMOND_SWORD).enchant(FIRE_ASPECT, 1),
                        s(DIAMOND_BOOTS).enchant(PROTECTION, 3).enchant(FIRE_PROTECTION, 4),
                        s(LAVA_BUCKET), s(LAVA_BUCKET), s(IRON_PICKAXE), s(IRON_AXE), s(IRON_HELMET), s(IRON_CHESTPLATE),
                        s(IRON_LEGGINGS)),
                "Perk: Kills grant 24s of Fire Resistance, 10s of Speed II, lights arrows you shoot on fire, and you leave a trail of fire walking."));
        kits.add(withNotes(kit(mode, "scout", "Scout", null, DIAMOND_AXE,
                        s(DIAMOND_AXE).enchant(SHARPNESS, 1), s(DIAMOND_PICKAXE).enchant(EFFICIENCY, 2),
                        potion(SPLASH_POTION, "Splash Potion of Speed II (0:20)", MobEffects.SPEED, 20, 1).count(3),
                        s(IRON_HELMET), s(IRON_CHESTPLATE), s(IRON_LEGGINGS),
                        s(DIAMOND_BOOTS).name("Scout's Diamond Boots").enchant(FEATHER_FALLING, 1)),
                "Perk: Kills grant an Ender Pearl."));
        return List.copyOf(kits);
    }

    /** Mega kits: Hypixel's "Mega Kits" menu, maxed, Default first and then A to Z. */
    private static List<SkyWarsKit> mega() {
        SkyWarsMode mode = SkyWarsMode.MEGA;
        List<SkyWarsKit> kits = new ArrayList<>();
        kits.add(kit(mode, DEFAULT, "Default", null, IRON_PICKAXE,
                s(IRON_PICKAXE), s(IRON_AXE), s(IRON_SHOVEL), s(LEATHER_HELMET), s(LEATHER_CHESTPLATE),
                s(LEATHER_LEGGINGS), s(LEATHER_BOOTS), s(IRON_SWORD)));
        kits.add(kit(mode, "armorer", "Armorer", null, DIAMOND_CHESTPLATE,
                s(DIAMOND_CHESTPLATE).enchant(PROTECTION, 1), s(DIAMOND_BOOTS).enchant(PROTECTION, 1),
                potion(SPLASH_POTION, "Splash Potion of Resistance (9s)", MobEffects.RESISTANCE, 9, 0).count(2)));
        kits.add(kit(mode, "armorsmith", "Armorsmith", null, ANVIL,
                s(ANVIL).count(3), s(EXPERIENCE_BOTTLE).count(24),
                s(ENCHANTED_BOOK).enchant(PROTECTION, 3).enchant(SHARPNESS, 1), s(DIAMOND_LEGGINGS), s(ENCHANTING_TABLE)));
        kits.add(kit(mode, "baseball_player", "Baseball Player", null, DIAMOND_HELMET,
                s(DIAMOND_HELMET).enchant(PROTECTION, 4), s(DIAMOND_SWORD).enchant(KNOCKBACK, 1), s(LEATHER_CHESTPLATE)));
        kits.add(kit(mode, "cannoneer", "Cannoneer", null, TNT,
                s(TNT).count(32), s(REDSTONE_BLOCK).count(4), s(WATER_BUCKET),
                s(DIAMOND_LEGGINGS).enchant(BLAST_PROTECTION, 4), s(LEATHER_CHESTPLATE), s(STONE_SWORD).enchant(SHARPNESS, 1),
                s(LEATHER_HELMET)));
        kits.add(kit(mode, "enderman", "Enderman", null, LEATHER_CHESTPLATE,
                s(LEATHER_CHESTPLATE), s(LEATHER_LEGGINGS),
                s(DIAMOND_BOOTS).enchant(FEATHER_FALLING, 2).enchant(PROTECTION, 1),
                s(ENDER_PEARL).name("Corrupted Pearl").count(2).ability(SkyWarsItems.CORRUPTED_PEARL).glint()));
        kits.add(kit(mode, "fisherman", "Fisherman", null, FISHING_ROD,
                s(FISHING_ROD).enchant(LURE, 5).enchant(LUCK_OF_THE_SEA, 40).enchant(UNBREAKING, 10),
                s(DIAMOND_BOOTS).enchant(PROTECTION, 1), s(DIAMOND_HELMET)));
        kits.add(kit(mode, "healer", "Healer", null, CAKE,
                s(SPLASH_POTION).potion(Potions.STRONG_HEALING).name("Splash Potion of Instant Health II").count(3),
                potion(SPLASH_POTION, "Splash Potion of Regeneration II (16s)", MobEffects.REGENERATION, 16, 1),
                s(DIAMOND_SHOVEL), s(GOLDEN_CHESTPLATE).enchant(PROTECTION, 1), s(GOLDEN_LEGGINGS).enchant(PROTECTION, 1),
                s(GOLDEN_HELMET).enchant(PROTECTION, 1), s(GOLDEN_BOOTS).enchant(PROTECTION, 1)));
        kits.add(kit(mode, "hellhound", "Hellhound", null, DIAMOND_AXE,
                s(DIAMOND_AXE), s(DIAMOND_BOOTS).enchant(PROTECTION, 4), s(WOLF_SPAWN_EGG).name("Wolf Egg").count(3),
                s(LEATHER_HELMET), s(LEATHER_CHESTPLATE), s(LEATHER_LEGGINGS)));
        kits.add(kit(mode, "hunter", "Hunter", null, BOW,
                s(BOW).enchant(POWER, 3), s(ARROW).count(32), s(CHAINMAIL_CHESTPLATE), s(CHAINMAIL_LEGGINGS),
                s(CHAINMAIL_BOOTS)));
        kits.add(kit(mode, "knight", "Knight", null, DIAMOND_SWORD,
                s(DIAMOND_SWORD).enchant(SHARPNESS, 2), s(GOLDEN_HELMET), s(GOLDEN_LEGGINGS), s(GOLDEN_CHESTPLATE),
                s(GOLDEN_BOOTS), s(FLINT_AND_STEEL)));
        kits.add(kit(mode, "paladin", "Paladin", null, GOLDEN_APPLE,
                s(GOLDEN_APPLE).count(2), s(IRON_HELMET), s(IRON_CHESTPLATE),
                s(LEATHER_LEGGINGS).dye(WHITE).name("White Leather Leggings"),
                s(LEATHER_BOOTS).dye(WHITE).name("White Leather Boots"), s(IRON_SWORD).enchant(SMITE, 10)));
        kits.add(kit(mode, "pyro", "Pyro", null, FLINT_AND_STEEL,
                s(DIAMOND_AXE).enchant(FIRE_ASPECT, 1), s(FLINT_AND_STEEL),
                potion(SPLASH_POTION, "Splash Potion of Fire Resistance II (1200s)", MobEffects.FIRE_RESISTANCE, 1200, 1),
                s(LEATHER_CHESTPLATE).enchant(UNBREAKING, 10).enchant(PROTECTION, 5),
                s(LEATHER_LEGGINGS).enchant(UNBREAKING, 10).enchant(PROTECTION, 5), s(LAVA_BUCKET)));
        kits.add(kit(mode, "scout", "Scout", null, DIAMOND_SWORD,
                s(DIAMOND_SWORD), potion(SPLASH_POTION, "Splash Potion of Speed II (67s)", MobEffects.SPEED, 67, 1).count(4),
                s(LEATHER_CHESTPLATE), s(LEATHER_LEGGINGS), s(WOOL.pick(net.minecraft.world.item.DyeColor.BLUE)).count(32)));
        kits.add(kit(mode, "skeletor", "Skeletor", null, CHAINMAIL_BOOTS,
                s(CHAINMAIL_BOOTS).enchant(PROTECTION, 2), s(SKELETON_SPAWN_EGG).name("Skeleton Egg").count(4),
                s(CHAINMAIL_HELMET).enchant(PROTECTION, 2), s(CHAINMAIL_CHESTPLATE).enchant(PROTECTION, 2),
                s(CHAINMAIL_LEGGINGS).enchant(PROTECTION, 2), s(ARROW).count(16), s(BOW)));
        kits.add(kit(mode, "witch", "Witch", null, BREWING_STAND,
                s(SPLASH_POTION).name("Splash Potion of Poison (0:12) and Slow (0:15)")
                        .effect(new MobEffectInstance(MobEffects.POISON, 12 * 20, 0))
                        .effect(new MobEffectInstance(MobEffects.SLOWNESS, 15 * 20, 0)).count(3),
                potion(SPLASH_POTION, "Splash Potion of Blindness I (10s)", MobEffects.BLINDNESS, 10, 0).count(3),
                potion(POTION, "Potion of Strength I (6s)", MobEffects.STRENGTH, 6, 0).count(2),
                s(LEATHER_CHESTPLATE).enchant(PROTECTION, 1), s(LEATHER_HELMET).enchant(PROTECTION, 1),
                s(LEATHER_BOOTS).enchant(PROTECTION, 1), s(LEATHER_LEGGINGS).enchant(PROTECTION, 1),
                s(WOODEN_SWORD).enchant(SHARPNESS, 4)));
        return List.copyOf(kits);
    }

    /** An enchanted book of Sharpness, Protection or Power, of level 1 to {@code maxLevel}. */
    static ItemStack randomBook(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.util.RandomSource random,
            int maxLevel) {
        ItemStack book = new ItemStack(ENCHANTED_BOOK);
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(BOOK_ENCHANTMENTS.get(random.nextInt(BOOK_ENCHANTMENTS.size())));
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(book,
                enchantments -> enchantments.set(enchantment, 1 + random.nextInt(maxLevel)));
        return book;
    }

    /** Jester's sword: a random material, one to three random enchantments and little durability. */
    private static ItemStack randomSword(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.util.RandomSource random) {
        ItemStack sword = new ItemStack(SWORDS.get(random.nextInt(SWORDS.size())));
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            var enchantment = SWORD_ENCHANTMENTS.get(random.nextInt(SWORD_ENCHANTMENTS.size()));
            int level = 1 + random.nextInt(enchantment == SHARPNESS || enchantment == SMITE || enchantment == BANE_OF_ARTHROPODS ? 5 : 3);
            sword.enchant(lookup.getOrThrow(enchantment), level);
        }
        int durability = sword.getMaxDamage();
        sword.setDamageValue(durability - Math.max(1, durability / 8));
        return sword;
    }

    private static SkyWarsKit kit(SkyWarsMode mode, String id, String name, Rarity rarity, Item icon, Object... parts) {
        List<Part> list = new ArrayList<>();
        for (Object part : parts) {
            list.add(part instanceof Part fixed ? fixed : new Part(null, (Stack) part));
        }
        return new SkyWarsKit(id, name, rarity, icon, mode, List.copyOf(list), List.of());
    }

    private static SkyWarsKit withNotes(SkyWarsKit kit, String... notes) {
        return new SkyWarsKit(kit.id(), kit.name(), kit.rarity(), kit.icon(), kit.mode(), kit.parts(), List.of(notes));
    }

    private static Stack s(Item item) {
        return new Stack(item);
    }

    private static Stack potion(Item item, String name, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int seconds, int amplifier) {
        return s(item).name(name).effect(new MobEffectInstance(effect, seconds * 20, amplifier));
    }

    private static Stack leather(Item item, int rgb, String color) {
        String piece = item == LEATHER_CHESTPLATE ? "Chestplate" : item == LEATHER_LEGGINGS ? "Leggings" : "Boots";
        return s(item).dye(rgb).name(color + " Leather " + piece).enchant(PROTECTION, 3);
    }

    private static Stack troll(Item item) {
        return s(item).enchant(PROTECTION, 3).enchant(FIRE_PROTECTION, 4).enchant(BLAST_PROTECTION, 4)
                .enchant(THORNS, 1).enchant(UNBREAKING, 2);
    }
}
