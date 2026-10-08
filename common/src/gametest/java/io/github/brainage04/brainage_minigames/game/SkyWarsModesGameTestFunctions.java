package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsLuckyBlocks;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMatch;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Mini, Mega and Lucky Block SkyWars: their maps, kits, kit perks, perk slots, loot and lucky blocks. */
public final class SkyWarsModesGameTestFunctions {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private SkyWarsModesGameTestFunctions() {}

    /**
     * Each mode is its own game on its own maps: Mini's small four-island maps, Mega's twelve
     * two-player islands with sub-mid chests, Lucky Block on the Insane maps; every map has a spawn
     * and a cage per player, its chests and a void marker.
     */
    public static void modesHaveTheirGamesAndMaps(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        for (String id : List.of("skywars_mini", "skywars_mega", "skywars_lucky")) {
            check(Minigames.byId(id).orElse(null) instanceof SkyWarsGame, "no game " + id);
        }
        check(Minigames.SKYWARS_LUCKY.mapDirectory().equals(Minigames.SKYWARS.mapDirectory())
                && Minigames.SKYWARS_LUCKY.mode() == SkyWarsMode.INSANE && Minigames.SKYWARS_LUCKY.luckyBlocks(),
                "Lucky Block is not Insane on the Insane maps with lucky blocks");
        List<Identifier> mini = MapArena.maps(server, Minigames.SKYWARS_MINI.mapDirectory());
        List<Identifier> mega = MapArena.maps(server, Minigames.SKYWARS_MEGA.mapDirectory());
        check(mini.size() >= 2 && mega.size() >= 1, "Mini maps " + mini + ", Mega maps " + mega);
        check(Minigames.SKYWARS_MEGA.layoutPresets(SettingsStorage.resolve(server, Minigames.SKYWARS_MEGA)).getFirst()
                .equals(TeamLayout.teamsOf(2, 12)), "Mega does not offer teams of two first");
        for (Identifier map : mini) {
            MapArena arena = Minigames.SKYWARS_MINI.prepare(MapArena.open(context.getLevel(), map));
            try {
                checkMap(Minigames.SKYWARS_MINI, arena, 4, 1);
                check(arena.bounds().getXSpan() < 80, map + " is not smaller than the Insane maps: " + arena.bounds().getXSpan());
            } finally {
                arena.close();
            }
        }
        for (Identifier map : mega) {
            MapArena arena = Minigames.SKYWARS_MEGA.prepare(MapArena.open(context.getLevel(), map));
            try {
                checkMap(Minigames.SKYWARS_MEGA, arena, 12, 2);
                check(arena.bounds().getXSpan() > 150, map + " is not a large map: " + arena.bounds().getXSpan());
                // Four sub-mid islands with two mid chests each, besides the mid's own.
                check(Minigames.SKYWARS_MEGA.chests(arena, SkyWarsMatch.ChestKind.MID).size() >= 14,
                        map + " has " + Minigames.SKYWARS_MEGA.chests(arena, SkyWarsMatch.ChestKind.MID).size() + " mid chests");
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    private static void checkMap(SkyWarsGame game, MapArena arena, int teams, int spawnsPerTeam) {
        Identifier map = arena.map();
        check(arena.teamSlots() == teams, map + " has " + arena.teamSlots() + " teams, not " + teams);
        Set<BlockPos> spawns = new HashSet<>();
        for (int team = 1; team <= teams; team++) {
            check(arena.spawnsOf(team).size() == spawnsPerTeam, map + " team " + team + " has " + arena.spawnsOf(team).size() + " spawns");
            for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                BlockPos feet = BlockPos.containing(spawn.position());
                check(arena.level().getBlockState(feet.below()).is(Blocks.GLASS), map + " has no cage floor under " + feet);
                check(arena.level().getBlockState(feet).isAir() && arena.level().getBlockState(feet.above()).isAir(),
                        map + " has no room in the cage at " + feet);
                spawns.add(feet);
            }
        }
        check(spawns.size() == teams * spawnsPerTeam, map + " has spawns sharing a cage");
        check(game.chests(arena, SkyWarsMatch.ChestKind.ISLAND).size() == teams * 3, map + " lacks three chests per island");
        check(!game.chests(arena, SkyWarsMatch.ChestKind.MID).isEmpty(), map + " has no mid chests");
        check(arena.voidY() < arena.spawnsOf(1).getFirst().position().y() && arena.point("bounds_min").isPresent(),
                map + " lacks its void or bounds markers");
    }

    /**
     * Mini: Champion by default, a chosen kit otherwise, the bundled kit is Champion's, and island
     * chests roll Mini's table with its doubled blocks.
     */
    public static void miniPlaysItsKitsAndLoot(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_MINI);
        ServerPlayer scout = f.player();
        ServerPlayer plain = f.player();
        check(command(scout, "minigames skywars mini kit scout") == 1, "selecting Scout failed");
        check(command(scout, "minigames skywars mini kit pyro") == 0, "an Insane kit was accepted for Mini");
        Match match = f.open("blossom", "1v1");
        MatchManager.join(scout, match, 1);
        MatchManager.join(plain, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(count(scout, Items.DIAMOND_AXE) == 1 && count(scout, Items.SPLASH_POTION) == 3
                    && scout.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), "Scout's kit is missing");
            check(plain.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS) && count(plain, Items.ANVIL) == 1
                    && count(plain, Items.ENCHANTED_BOOK) == 3, "the default kit is not Champion");
            ItemStack sword = first(plain, stack -> stack.is(Items.DIAMOND_SWORD));
            check(sword != null && level(f.server, sword, Enchantments.SHARPNESS) == 2, "Champion's sword is " + sword);
            List<Item> bundled = LootUtils.roll(plain, Minigames.SKYWARS_MINI.defaultKit()).stream().map(ItemStack::getItem).toList();
            List<Item> champion = SkyWarsKits.find(SkyWarsMode.MINI, "champion")
                    .items(f.server.registryAccess(), plain.getRandom()).stream().map(ItemStack::getItem).toList();
            check(bundled.equals(champion), "kits/skywars_mini " + bundled + " differs from Champion " + champion);
            check(SkyWarsKits.of(SkyWarsMode.MINI).size() == 11, "expected Hypixel's 11 Mini kits");
            for (SkyWarsKit kit : SkyWarsKits.of(SkyWarsMode.MINI)) {
                check(kit.notes().stream().anyMatch(note -> note.startsWith("Perk: ")), kit.id() + " shows no perk");
            }
            MapArena arena = (MapArena) match.arena();
            for (BlockPos pos : Minigames.SKYWARS_MINI.chests(arena, SkyWarsMatch.ChestKind.ISLAND)) {
                Container chest = container(arena, pos);
                // Chest loot splits stacks over empty slots, so the blocks are counted across the chest.
                int planks = chest.countItem(Items.OAK_PLANKS);
                int stone = chest.countItem(Items.STONE);
                check(planks == 64 && stone == 0 || stone == 64 && planks == 0,
                        "island chest " + pos + " holds " + planks + " planks and " + stone + " stone, not Mini's 64 blocks");
            }
            context.succeed();
        });
    }

    /** Every Mini kit's own perk, and the global Juggernaut, on kills credited to it. */
    public static void miniKitPerksTakeEffect(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_MINI);
        List<String> kits = List.of("champion", "healer", "scout", "hound");
        List<ServerPlayer> players = f.players(kits);
        Match match = f.open("blossom", "ffa");
        for (ServerPlayer player : players) MatchManager.join(player, match, 0);
        match.start();
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            ServerPlayer champion = players.get(0);
            ServerPlayer healer = players.get(1);
            ServerPlayer scout = players.get(2);
            ServerPlayer hound = players.get(3);
            check(wolves(hound) == 1, "Hound starts with " + wolves(hound) + " wolves");
            Wolf wolf = hound.level().getEntitiesOfClass(Wolf.class, new AABB(hound.blockPosition()).inflate(8)).getFirst();
            check(wolf.isTame() && wolf.getMaxHealth() == 20 && wolf.hasEffect(MobEffects.RESISTANCE), "Hound's wolf is " + wolf);

            credit(match, champion, scout);
            ItemStack sword = first(champion, stack -> stack.is(Items.DIAMOND_SWORD));
            check(level(f.server, sword, Enchantments.SHARPNESS) == 3, "Champion's kill left Sharpness " + level(f.server, sword, Enchantments.SHARPNESS));
            check(champion.hasEffect(MobEffects.REGENERATION), "the global Juggernaut gave no Regeneration");

            int apples = count(healer, Items.GOLDEN_APPLE);
            healer.setHealth(4);
            credit(match, healer, scout);
            check(healer.getMaxHealth() == 24 && count(healer, Items.GOLDEN_APPLE) == apples + 1 && healer.getHealth() >= 12,
                    "Healer's kill: max " + healer.getMaxHealth() + ", health " + healer.getHealth());

            credit(match, scout, champion);
            check(count(scout, Items.ENDER_PEARL) == 1, "Scout's kill gave no pearl");

            int steak = count(hound, Items.COOKED_BEEF);
            credit(match, hound, champion);
            check(wolves(hound) == 2 && count(hound, Items.COOKED_BEEF) == steak + 16, "Hound's kill: " + wolves(hound) + " wolves");
            for (Wolf each : hound.level().getEntitiesOfClass(Wolf.class, new AABB(hound.blockPosition()).inflate(8))) each.discard();
            context.runAfterDelay(1, () -> {
                credit(match, hound, champion);
                check(wolves(hound) == 2, "with no wolf alive Hound's kill spawned " + wolves(hound));
                context.succeed();
            });
        });
    }

    /**
     * Mini perk slots: empty until chosen, six usable and the seventh only with maxed perks, no
     * global perk in a slot; chosen perks take effect on top of the kit's own perk, and only those.
     */
    public static void miniSelectedPerksTakeEffect(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_MINI);
        List<ServerPlayer> players = f.players(List.of("scout", "champion"));
        ServerPlayer hero = players.get(0);
        ServerPlayer other = players.get(1);
        SkyWarsMode mini = SkyWarsMode.MINI;
        check(SkyWarsProgression.slots(f.server, hero.getUUID(), mini).stream().allMatch(java.util.Objects::isNull),
                "Mini slots do not start empty");
        check(command(hero, "minigames skywars mini perk 1 rusher") == 1, "putting Rusher in slot 1 failed");
        check(command(hero, "minigames skywars mini perk 7 tank") == 1, "the seventh slot was refused with maxed perks");
        check(command(hero, "minigames skywars mini perk 2 juggernaut") == 0, "a global perk went into a Mini slot");
        check(command(hero, "minigames skywars perk bridger false") == 1
                        && SkyWarsProgression.slots(f.server, hero.getUUID(), mini).get(1) == null,
                "an Insane toggle changed a Mini slot");
        f.rules(false);
        check(command(other, "minigames skywars mini perk 7 rusher") == 0, "the seventh slot was usable without maxed perks");
        check(command(other, "minigames skywars mini perk 1 rusher") == 0, "an unowned perk went into a Mini slot");
        check(!SkyWarsProgression.active(f.server, hero.getUUID(), SkyWarsPerk.find(mini, SkyWarsPerk.TANK)),
                "the locked seventh slot's perk is active");
        f.rules(true);
        Match match = f.open("blossom", "1v1");
        MatchManager.join(hero, match, 1);
        MatchManager.join(other, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(amplifier(hero, MobEffects.SPEED) == 0 && hero.getEffect(MobEffects.SPEED).getDuration() > 250,
                    "the chosen Rusher gave " + hero.getEffect(MobEffects.SPEED));
            check(!other.hasEffect(MobEffects.SPEED), "a player with empty slots got Rusher");
            hero.removeAllEffects();
            credit(match, hero, other);
            check(amplifier(hero, MobEffects.RESISTANCE) == 0, "the chosen Tank gave " + hero.getEffect(MobEffects.RESISTANCE));
            check(count(hero, Items.ENDER_PEARL) == 1, "Scout's own perk stopped working beside chosen perks");
            other.removeAllEffects();
            credit(match, other, hero);
            check(!other.hasEffect(MobEffects.RESISTANCE) && other.hasEffect(MobEffects.REGENERATION),
                    "empty slots: " + other.getActiveEffects());
            context.succeed();
        });
    }

    /** The other Mini kits' perks: Paladin, Bowman, Blacksmith, Armorer, Pyromancer, Magician and Athlete. */
    public static void moreMiniKitPerksTakeEffect(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_MINI);
        List<ServerPlayer> first = f.players(List.of("paladin", "bowman", "blacksmith", "armorer"));
        Match match = f.open("oasis", "ffa");
        for (ServerPlayer player : first) MatchManager.join(player, match, 0);
        match.start();
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            ServerPlayer paladin = first.get(0);
            ServerPlayer bowman = first.get(1);
            ServerPlayer blacksmith = first.get(2);
            ServerPlayer armorer = first.get(3);
            paladin.removeAllEffects();
            credit(match, paladin, bowman);
            check(amplifier(paladin, MobEffects.RESISTANCE) == 2, "Paladin's first kill gave " + paladin.getEffect(MobEffects.RESISTANCE));
            paladin.removeAllEffects();
            credit(match, paladin, bowman);
            check(amplifier(paladin, MobEffects.RESISTANCE) == 1, "Paladin's second kill gave " + paladin.getEffect(MobEffects.RESISTANCE));
            paladin.removeAllEffects();
            credit(match, paladin, bowman);
            check(amplifier(paladin, MobEffects.RESISTANCE) == -1, "Paladin's third kill gave Resistance");

            int splashes = count(bowman, Items.SPLASH_POTION);
            credit(match, bowman, paladin);
            ItemStack bow = first(bowman, stack -> stack.is(Items.BOW));
            check(level(f.server, bow, Enchantments.POWER) == 4 && count(bowman, Items.SPLASH_POTION) == splashes + 1,
                    "Bowman's kill: Power " + level(f.server, bow, Enchantments.POWER));

            check(blacksmith.experienceLevel == 15, "Blacksmith starts at level " + blacksmith.experienceLevel);
            check(count(blacksmith, Items.ENCHANTED_BOOK) == 2 && armor(blacksmith).stream().anyMatch(stack -> stack.is(Items.DIAMOND_HELMET)
                    || stack.is(Items.DIAMOND_CHESTPLATE) || stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.DIAMOND_BOOTS)),
                    "Blacksmith lacks its books or diamond piece");
            credit(match, blacksmith, paladin);
            check(blacksmith.experienceLevel == 18 && count(blacksmith, Items.ENCHANTED_BOOK) == 3, "Blacksmith's kill: level "
                    + blacksmith.experienceLevel + ", books " + count(blacksmith, Items.ENCHANTED_BOOK));

            int protection = protection(f.server, armorer);
            credit(match, armorer, paladin);
            check(protection(f.server, armorer) == protection + 1, "Armorer's kill left Protection " + protection(f.server, armorer));
            MatchManager.stop(match);

            List<ServerPlayer> second = f.players(List.of("pyromancer", "magician", "athlete"));
            Match next;
            try {
                next = f.open("oasis", "ffa");
                for (ServerPlayer player : second) MatchManager.join(player, next, 0);
                next.start();
            } catch (MatchException exception) {
                throw new IllegalStateException(exception);
            }
            context.runAfterDelay(3, () -> {
                check(next.phase() == MatchPhase.ACTIVE, "second match did not start");
                ServerPlayer pyromancer = second.get(0);
                ServerPlayer magician = second.get(1);
                ServerPlayer athlete = second.get(2);
                pyromancer.removeAllEffects();
                credit(next, pyromancer, magician);
                check(pyromancer.hasEffect(MobEffects.FIRE_RESISTANCE) && amplifier(pyromancer, MobEffects.SPEED) == 1,
                        "Pyromancer's kill gave no Fire Resistance and Speed II");
                Arrow arrow = new Arrow(pyromancer.level(), pyromancer, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                SkyWarsPerks.shot(pyromancer, arrow);
                check(arrow.getRemainingFireTicks() > 0, "Pyromancer's arrow does not burn after a kill");
                // Fire stands on solid ground: the mid's altar under its lobby point.
                Vec3 altar = next.arena().lobbyPosition();
                pyromancer.teleportTo(altar.x, altar.y, altar.z);
                BlockPos feet = pyromancer.blockPosition();
                pyromancer.setOnGround(true);

                magician.removeAllEffects();
                credit(next, magician, pyromancer);
                check(magician.getActiveEffects().stream().anyMatch(effect -> effect.getDuration() > 150 && effect.getDuration() <= 200
                                && (effect.getEffect() != MobEffects.REGENERATION || effect.getAmplifier() == 1)),
                        "Magician's kill gave " + magician.getActiveEffects());

                athlete.removeEffect(MobEffects.SPEED);
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS).finishUsingItem(athlete.level(), athlete);
                MobEffectInstance speed = athlete.getEffect(MobEffects.SPEED);
                check(speed != null && speed.getDuration() > 5000, "Athlete's Swiftness lasts " + speed);
                context.runAfterDelay(6, () -> {
                    check(pyromancer.level().getBlockState(feet).is(Blocks.FIRE), "Pyromancer left no fire at " + feet);
                    context.succeed();
                });
            });
        });
    }

    /**
     * Mega: six default perks in slots, a seventh slot only when perks are maxed, slots changed by
     * command, and only slotted perks and the globals take effect.
     */
    public static void megaPerkSlotsChooseTheActivePerks(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_MEGA);
        ServerPlayer hero = f.player();
        ServerPlayer other = f.player();
        SkyWarsMode mega = SkyWarsMode.MEGA;
        List<String> defaults = SkyWarsProgression.slots(f.server, hero.getUUID(), mega).stream()
                .map(perk -> perk == null ? "" : perk.id()).toList();
        check(defaults.equals(List.of("bridger", "lucky_charm", "rusher", "arrow_recovery", "blazing_arrows", "tank", "")),
                "Mega's default slots are " + defaults);
        check(command(hero, "minigames skywars mega perk 7 notoriety") == 1, "the seventh slot was refused with maxed perks");
        check(command(hero, "minigames skywars mega perk 4 notoriety") == 1, "moving Notoriety failed");
        List<String> moved = SkyWarsProgression.slots(f.server, hero.getUUID(), mega).stream()
                .map(perk -> perk == null ? "" : perk.id()).toList();
        check(moved.equals(List.of("bridger", "lucky_charm", "rusher", "notoriety", "blazing_arrows", "tank", "")),
                "after moving Notoriety the slots are " + moved);
        check(command(hero, "minigames skywars mega perk 1 juggernaut") == 0, "a global perk went into a slot");
        f.rules(false);
        check(command(other, "minigames skywars mega perk 7 bridger") == 0, "the seventh slot was usable without maxed perks");
        check(command(other, "minigames skywars mega perk 1 necromancer") == 0, "an unowned perk went into a slot");
        check(!SkyWarsProgression.active(f.server, other.getUUID(), SkyWarsPerk.find(mega, SkyWarsPerk.TANK)),
                "an unowned default perk is active");
        check(SkyWarsProgression.active(f.server, other.getUUID(), SkyWarsPerk.find(mega, SkyWarsPerk.JUGGERNAUT)),
                "the global Juggernaut is not active");
        f.rules(true);
        Match match = f.open("highlands", "1v1");
        MatchManager.join(hero, match, 1);
        MatchManager.join(other, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(hero.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE) && count(hero, Items.IRON_SWORD) == 1,
                    "the Mega Default kit is missing");
            check(amplifier(hero, MobEffects.SPEED) == 0 && hero.getEffect(MobEffects.SPEED).getDuration() > 250,
                    "Rusher gave " + hero.getEffect(MobEffects.SPEED));
            hero.removeAllEffects();
            credit(match, hero, other);
            check(amplifier(hero, MobEffects.RESISTANCE) == 0 && amplifier(hero, MobEffects.REGENERATION) == 0,
                    "Tank and Juggernaut gave " + hero.getActiveEffects());
            // Arrow Recovery is no longer slotted: no bow hit gives an arrow back.
            ServerLevel level = hero.level();
            Arrow arrow = new Arrow(level, hero, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            int arrows = count(hero, Items.ARROW);
            for (int hit = 0; hit < 60; hit++) {
                other.setHealth(20);
                other.invulnerableTime = 0;
                other.hurtServer(level, level.damageSources().arrow(arrow, hero), 0.1F);
            }
            check(count(hero, Items.ARROW) == arrows, "an unslotted Arrow Recovery returned arrows");
            context.succeed();
        });
    }

    /**
     * Mega's large map pastes a few chunks per tick, and no tick, the last one building every cage
     * and finding every chest included, is held up for long.
     */
    public static void megaMapPastesGradually(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier map = MapArena.maps(server, Minigames.SKYWARS_MEGA.mapDirectory()).getFirst();
        MapArena arena = Minigames.SKYWARS_MEGA.prepare(MapArena.reserve(context.getLevel(), map));
        GameTestLifecycle.afterTest(context, arena::close);
        long[] slowest = {0};
        int[] ticks = {0};
        // Chunks load on worker threads, so the wait is measured in real time, not GameTest ticks.
        GameTestLifecycle.awaitPreparation(context, () -> {
            long started = System.nanoTime();
            arena.prepare();
            slowest[0] = Math.max(slowest[0], System.nanoTime() - started);
            ticks[0]++;
            return arena.prepared();
        }, () -> {
            check(ticks[0] > 20, "the Mega map pasted in " + ticks[0] + " ticks");
            check(slowest[0] < 100_000_000L, "a paste step took " + slowest[0] / 1_000_000.0 + " ms");
            check(Minigames.SKYWARS_MEGA.chests(arena).size() == 12 * 3 + 6 + 4 * 2, "the pasted map's chests were not found");
            context.succeed();
        });
    }

    /** Bots that chose no kit pick one of the mode's bot kits in Mini and Mega. */
    public static void modeBotsPickAKit(GameTestHelper context) throws Exception {
        MinecraftServer server = context.getLevel().getServer();
        TestBotProvider provider = TestBotProvider.register(server);
        GameTestLifecycle.afterTest(context, provider::close);
        Fixture mini = new Fixture(context, Minigames.SKYWARS_MINI);
        Fixture mega = new Fixture(context, Minigames.SKYWARS_MEGA);
        Match miniMatch = mini.open("blossom", "1v1");
        MatchManager.join(mini.player(), miniMatch, 1);
        miniMatch.addBots(2, 1);
        Match megaMatch = mega.open("highlands", "1v1");
        MatchManager.join(mega.player(), megaMatch, 1);
        megaMatch.addBots(2, 1);
        context.runAfterDelay(5, () -> {
            check(miniMatch.phase() == MatchPhase.ACTIVE && megaMatch.phase() == MatchPhase.ACTIVE && provider.spawned.size() == 2,
                    "the bot matches did not start");
            for (ServerPlayer bot : provider.spawned) {
                Match match = MatchManager.matchOf(bot.getUUID()).orElseThrow();
                SkyWarsGame game = (SkyWarsGame) match.game();
                String kit = game.kitOf(match, bot).map(SkyWarsKit::id).orElse("");
                check(SkyWarsKits.botKits(game.mode()).contains(kit), "a " + game.id() + " bot got " + kit);
            }
            context.succeed();
        });
    }

    /**
     * Lucky Block SkyWars: lucky blocks stand on the islands and around the mid; breaking one
     * drops nothing of itself and runs an outcome; a refill puts it back; every outcome does what
     * it says and the weights are percentages.
     */
    public static void luckyBlocksRollTheirOutcomes(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context, Minigames.SKYWARS_LUCKY);
        SettingsStorage.set(f.server, Minigames.SKYWARS_LUCKY, SkyWarsGame.FIRST_REFILL, 3);
        SettingsStorage.set(f.server, Minigames.SKYWARS_LUCKY, SkyWarsGame.SECOND_REFILL, 0);
        ServerPlayer breaker = f.player();
        ServerPlayer other = f.player();
        Match match = f.open("mesa", "1v1");
        MapArena arena = (MapArena) match.arena();
        List<BlockPos> lucky = Minigames.SKYWARS_LUCKY.luckyBlocks(arena);
        check(lucky.size() == arena.teamSlots() * 2 + 4, "mesa got " + lucky.size() + " lucky blocks");
        for (BlockPos pos : lucky) {
            check(arena.level().getBlockState(pos).is(SkyWarsLuckyBlocks.BLOCK), "no lucky block at " + pos);
        }
        check(Minigames.SKYWARS.luckyBlocks(arena).isEmpty(), "Insane reports lucky blocks");
        check(Arrays.stream(SkyWarsLuckyBlocks.Outcome.values()).mapToInt(outcome -> outcome.weight).sum() == 100,
                "the outcome weights are not percentages");
        RandomSource random = RandomSource.create(5);
        Set<SkyWarsLuckyBlocks.Outcome> rolled = EnumSet.noneOf(SkyWarsLuckyBlocks.Outcome.class);
        for (int roll = 0; roll < 5000; roll++) rolled.add(SkyWarsLuckyBlocks.roll(random));
        check(rolled.size() == SkyWarsLuckyBlocks.Outcome.values().length, "5000 rolls missed " + rolled);
        MatchManager.join(breaker, match, 1);
        MatchManager.join(other, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            ServerLevel level = arena.level();
            BlockPos block = lucky.getFirst();
            breaker.teleportTo(block.getX() + 0.5, block.getY(), block.getZ() + 1.5);
            check(!breaker.gameMode.destroyBlock(block), "the lucky block broke as a block");
            check(level.getBlockState(block).isAir() && !Minigames.SKYWARS_LUCKY.luckyBlocks(arena).contains(block),
                    "the lucky block is still there");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(block).inflate(3))
                    .stream().noneMatch(item -> item.getItem().is(SkyWarsLuckyBlocks.BLOCK.asItem())), "the lucky block dropped itself");
            clearAround(level, block);
            context.runAfterDelay(65, () -> {
                check(level.getBlockState(block).is(SkyWarsLuckyBlocks.BLOCK), "the refill did not put the lucky block back");
                checkOutcomes(match, breaker, arena);
                context.succeed();
            });
        });
    }

    private static void checkOutcomes(Match match, ServerPlayer player, MapArena arena) {
        SkyWarsMatch state = SkyWarsGame.state(match);
        ServerLevel level = arena.level();
        BlockPos pos = BlockPos.containing(arena.lobbyPosition()).above(3);
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Map<SkyWarsLuckyBlocks.Outcome, Predicate<ServerPlayer>> checks = new java.util.EnumMap<>(SkyWarsLuckyBlocks.Outcome.class);
        checks.put(SkyWarsLuckyBlocks.Outcome.DIAMOND_ARMOR, p -> dropped(level, pos, stack -> stack.is(Items.DIAMOND_HELMET)
                || stack.is(Items.DIAMOND_CHESTPLATE) || stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.DIAMOND_BOOTS)));
        checks.put(SkyWarsLuckyBlocks.Outcome.GOLDEN_APPLES, p -> dropped(level, pos, stack -> stack.is(Items.GOLDEN_APPLE) && stack.getCount() == 3));
        checks.put(SkyWarsLuckyBlocks.Outcome.SHARP_SWORD, p -> dropped(level, pos, stack -> stack.is(Items.DIAMOND_SWORD)));
        checks.put(SkyWarsLuckyBlocks.Outcome.ENDER_PEARLS, p -> dropped(level, pos, stack -> stack.is(Items.ENDER_PEARL)));
        checks.put(SkyWarsLuckyBlocks.Outcome.BUILDING_BLOCKS, p -> dropped(level, pos, stack -> stack.is(Items.OAK_PLANKS)));
        checks.put(SkyWarsLuckyBlocks.Outcome.POWER_BOW, p -> dropped(level, pos, stack -> stack.is(Items.BOW))
                && dropped(level, pos, stack -> stack.is(Items.ARROW)));
        checks.put(SkyWarsLuckyBlocks.Outcome.HEALING_POTIONS, p -> dropped(level, pos, stack -> stack.is(Items.SPLASH_POTION) && stack.getCount() == 2));
        checks.put(SkyWarsLuckyBlocks.Outcome.ENCHANTING, p -> dropped(level, pos, stack -> stack.is(Items.EXPERIENCE_BOTTLE))
                && dropped(level, pos, stack -> stack.is(Items.ENCHANTED_BOOK)));
        checks.put(SkyWarsLuckyBlocks.Outcome.TNT_KIT, p -> dropped(level, pos, stack -> stack.is(Items.TNT))
                && dropped(level, pos, stack -> stack.is(Items.FLINT_AND_STEEL)));
        checks.put(SkyWarsLuckyBlocks.Outcome.LUCKY_SWORD, p -> dropped(level, pos, stack -> stack.is(Items.GOLDEN_SWORD)
                && stack.getHoverName().getString().equals("Lucky Sword")));
        checks.put(SkyWarsLuckyBlocks.Outcome.ENCHANTED_GOLDEN_APPLE, p -> dropped(level, pos, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE)));
        checks.put(SkyWarsLuckyBlocks.Outcome.TOTEM, p -> dropped(level, pos, stack -> stack.is(Items.TOTEM_OF_UNDYING)));
        checks.put(SkyWarsLuckyBlocks.Outcome.LIGHTNING, p -> !near(level, p.blockPosition(), LightningBolt.class).isEmpty());
        checks.put(SkyWarsLuckyBlocks.Outcome.ZOMBIES, p -> near(level, pos, Zombie.class).size() == 3);
        checks.put(SkyWarsLuckyBlocks.Outcome.PRIMED_TNT, p -> near(level, pos, PrimedTnt.class).size() == 1);
        checks.put(SkyWarsLuckyBlocks.Outcome.BLINDNESS, p -> p.hasEffect(MobEffects.BLINDNESS) && amplifier(p, MobEffects.SLOWNESS) == 1);
        checks.put(SkyWarsLuckyBlocks.Outcome.LEVITATION, p -> amplifier(p, MobEffects.LEVITATION) == 1);
        checks.put(SkyWarsLuckyBlocks.Outcome.COBWEBS, p -> level.getBlockState(p.blockPosition()).is(Blocks.COBWEB));
        checks.put(SkyWarsLuckyBlocks.Outcome.FIREWORKS, p -> near(level, pos, FireworkRocketEntity.class).size() == 3
                && dropped(level, pos, stack -> stack.is(Items.CAKE)));
        checks.put(SkyWarsLuckyBlocks.Outcome.WOLF_PACK, p -> near(level, pos, Wolf.class).stream().filter(wolf -> wolf.isOwnedBy(p)).count() == 2);
        checks.put(SkyWarsLuckyBlocks.Outcome.CHICKENS, p -> near(level, pos, Chicken.class).size() == 6
                && dropped(level, pos, stack -> stack.is(Items.EGG)));
        checks.put(SkyWarsLuckyBlocks.Outcome.ANVIL, p -> !level.getEntitiesOfClass(FallingBlockEntity.class,
                new AABB(p.blockPosition()).inflate(1, 8, 1)).isEmpty());
        for (SkyWarsLuckyBlocks.Outcome outcome : SkyWarsLuckyBlocks.Outcome.values()) {
            clearAround(level, pos);
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            SkyWarsLuckyBlocks.apply(outcome, state, match, player, pos);
            check(checks.containsKey(outcome) && checks.get(outcome).test(player), "the " + outcome + " outcome did nothing it says");
        }
        clearAround(level, pos);
    }

    private static void clearAround(ServerLevel level, BlockPos pos) {
        for (Entity entity : level.getEntities((Entity) null, new AABB(pos).inflate(12), entity -> !(entity instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos web : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 8, 2))) {
            if (level.getBlockState(web).is(Blocks.COBWEB) || level.getBlockState(web).is(Blocks.ANVIL)) level.removeBlock(web, false);
        }
    }

    private static boolean dropped(ServerLevel level, BlockPos pos, Predicate<ItemStack> wanted) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().anyMatch(item -> wanted.test(item.getItem()));
    }

    private static <T extends Entity> List<T> near(ServerLevel level, BlockPos pos, Class<T> type) {
        return level.getEntitiesOfClass(type, new AABB(pos).inflate(4));
    }

    /**
     * No SkyWars chest table of any mode can roll an empty chest, so every chest has loot at the
     * start and after each refill: 5,000 seeded rolls of each island and mid table all give items.
     */
    public static void chestTablesNeverRollEmpty(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (SkyWarsMode mode : SkyWarsMode.values()) {
            for (SkyWarsMatch.ChestKind kind : SkyWarsMatch.ChestKind.values()) {
                var key = SkyWarsGame.lootTable(mode, kind);
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                check(table != LootTable.EMPTY, "missing loot table " + key.identifier());
                for (long seed = 0; seed < 5000; seed++) {
                    check(!table.getRandomItems(params, seed).isEmpty(),
                            key.identifier() + " rolled an empty chest with seed " + seed);
                }
            }
        }
        context.succeed();
    }

    // Helpers

    /** Credits {@code killer} with killing {@code victim}, as a death in the match does. */
    private static void credit(Match match, ServerPlayer killer, ServerPlayer victim) {
        match.game().onDeath(match, victim, killer);
    }

    private static long wolves(ServerPlayer owner) {
        return owner.level().getEntitiesOfClass(Wolf.class, new AABB(owner.blockPosition()).inflate(8)).stream()
                .filter(wolf -> wolf.isAlive() && wolf.isOwnedBy(owner)).count();
    }

    private static int level(MinecraftServer server, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
        Holder<Enchantment> enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static int protection(MinecraftServer server, ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : armor(player)) total += level(server, stack, Enchantments.PROTECTION);
        return total;
    }

    private static List<ItemStack> armor(ServerPlayer player) {
        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            worn.add(player.getItemBySlot(slot));
        }
        return worn;
    }

    private static int amplifier(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    private static ItemStack first(ServerPlayer player, Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (wanted.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Container container(MapArena arena, BlockPos pos) {
        if (arena.level().getBlockEntity(pos) instanceof Container container) return container;
        throw failure("expected a chest at " + pos);
    }

    private static int command(ServerPlayer player, String command) {
        try {
            return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (Exception exception) {
            return 0;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    /** Players, matches of one SkyWars game and changed rules and settings of one test, undone when it ends. */
    private static final class Fixture {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final SkyWarsGame game;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Match> matches = new ArrayList<>();
        private final boolean maxKits;
        private final boolean maxPerks;

        Fixture(GameTestHelper context, SkyWarsGame game) {
            this.context = context;
            this.server = context.getLevel().getServer();
            this.game = game;
            this.maxKits = server.getGameRules().get(SkyWarsProgression.MAX_ALL_KITS);
            this.maxPerks = server.getGameRules().get(SkyWarsProgression.MAX_ALL_PERKS);
            rules(true);
            GameTestLifecycle.afterTest(context, this::close);
        }

        void rules(boolean max) {
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, max, server);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, max, server);
        }

        ServerPlayer player() {
            ServerPlayer player = TestPlayers.connect(context, "Mode" + NAMES.incrementAndGet());
            players.add(player);
            return player;
        }

        /** One player per kit, each with that kit of the game's mode selected. */
        List<ServerPlayer> players(List<String> kits) {
            List<ServerPlayer> chosen = new ArrayList<>();
            for (String kit : kits) {
                ServerPlayer player = player();
                SkyWarsProgression.selectKit(server, player.getUUID(), SkyWarsKits.find(game.mode(), kit));
                chosen.add(player);
            }
            return chosen;
        }

        /**
         * Opens a match on the named map with no countdown. The countdown is set here, not once per
         * fixture: another test of the same game ending meanwhile resets the game's settings.
         */
        Match open(String map, String layout) throws MatchException {
            Identifier id = MapArena.maps(server, game.mapDirectory()).stream()
                    .filter(candidate -> candidate.getPath().endsWith("/" + map))
                    .findFirst()
                    .orElseThrow(() -> failure("missing " + game.id() + " map " + map));
            SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
            Match match = MatchManager.open(server, game, TeamLayout.parse(layout).orElseThrow(), null,
                    (unused, settings) -> game.prepare(MapArena.open(context.getLevel(), id)));
            matches.add(match);
            return match;
        }

        void close() {
            for (Match match : matches) MatchManager.stop(match);
            CompoundTag root = server.getCommandStorage().get(SkyWarsProgression.STORAGE);
            for (ServerPlayer player : players) root.remove(player.getUUID().toString());
            server.getCommandStorage().set(SkyWarsProgression.STORAGE, root);
            TestPlayers.disconnect(players.toArray(ServerPlayer[]::new));
            for (GameSetting setting : game.settings()) SettingsStorage.reset(server, game, setting);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, maxKits, server);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, maxPerks, server);
        }
    }
}
