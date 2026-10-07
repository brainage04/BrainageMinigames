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
 * Every SkyWars kit per mode. Insane kits and their contents are Hypixel's Insane kit menu as
 * exported on 2026-10-06; what that menu does not show (custom item effects, potion durations it
 * leaves out) comes from Hypixel's patch notes or is a documented local choice, see HOW_TO_PLAY.
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

    private static final List<SkyWarsKit> INSANE = insane();
    private static final Map<SkyWarsMode, List<SkyWarsKit>> BY_MODE = Map.of(SkyWarsMode.INSANE, INSANE);

    private SkyWarsKits() {}

    public static List<SkyWarsKit> of(SkyWarsMode mode) {
        return BY_MODE.get(mode);
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
