package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMatch;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** SkyWars Insane: kits at cage open, perks, the max rules and the Insane chest loot. */
public final class SkyWarsInsaneGameTestFunctions {
    private static final SkyWarsGame SKYWARS = Minigames.SKYWARS;
    private static final AtomicInteger NAMES = new AtomicInteger();

    /** Every item the Insane mid table can roll. */
    private static final Set<Item> MID_ITEMS = Set.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, Items.BOW, Items.ARROW,
            Items.SPLASH_POTION, Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.SNOWBALL, Items.EXPERIENCE_BOTTLE,
            Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.FISHING_ROD, Items.TNT, Items.FLINT_AND_STEEL,
            Items.OAK_PLANKS);

    private SkyWarsInsaneGameTestFunctions() {}

    /** A chosen kit replaces the old stone tools at cage open; a player with no choice gets Default. */
    public static void chosenKitsAreGrantedWhenTheCagesOpen(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context);
        ServerPlayer pyro = f.player();
        ServerPlayer plain = f.player();
        check(command(pyro, "minigames skywars kit pyro") == 1, "selecting Pyro failed");
        Match match = f.open("frostbite", "1v1");
        MatchManager.join(pyro, match, 1);
        MatchManager.join(plain, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(pyro.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "Pyro does not wear its diamond chestplate");
            check(count(pyro, Items.LAVA_BUCKET) == 5 && count(pyro, Items.FLINT_AND_STEEL) == 1 && count(pyro, Items.POTION) == 1,
                    "Pyro's lava, flint and steel or potion is missing");
            check(count(pyro, Items.STONE_PICKAXE) == 0 && count(pyro, Items.IRON_PICKAXE) == 0, "Pyro also got the Default tools");
            check(SKYWARS.kitOf(match, pyro).map(SkyWarsKit::id).orElse("").equals("pyro"), "Pyro's kit was not recorded");
            check(plain.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Default does not wear its iron chestplate");
            for (Item tool : List.of(Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_SWORD)) {
                check(count(plain, tool) == 1, "Default lacks " + tool);
            }
            check(count(plain, Items.STONE_PICKAXE) == 0, "Default still has the stone tools");
            // The bundled kit, shown in the kit picker and given for an explicit kit, is Default.
            List<Item> bundled = LootUtils.roll(plain, SKYWARS.defaultKit()).stream().map(ItemStack::getItem).toList();
            List<Item> defaults = SkyWarsKits.find(SkyWarsMode.INSANE, SkyWarsKits.DEFAULT)
                    .items(f.server.registryAccess(), plain.getRandom()).stream().map(ItemStack::getItem).toList();
            check(bundled.equals(defaults), "kits/skywars " + bundled + " differs from Default " + defaults);
            for (SkyWarsKit kit : SkyWarsKits.of(SkyWarsMode.INSANE)) {
                List<ItemStack> items = kit.items(f.server.registryAccess(), plain.getRandom());
                check(items.size() == kit.parts().size() && items.stream().noneMatch(ItemStack::isEmpty), kit.id() + " has empty items");
                check(!kit.contents(f.server.registryAccess()).isEmpty(), kit.id() + " has no menu contents");
            }
            String farmer = SkyWarsKits.find(SkyWarsMode.INSANE, "farmer").contents(f.server.registryAccess()).stream()
                    .map(Component::getString).collect(Collectors.joining("\n"));
            check(farmer.contains("Diamond Leggings") && farmer.contains("Projectile Protection IV") && farmer.contains("Egg x16"),
                    "Farmer's contents read " + farmer);
            check(SkyWarsKits.of(SkyWarsMode.INSANE).size() == 48, "expected Hypixel's 48 Insane kits");
            context.succeed();
        });
    }

    /** A bot with no saved choice picks one of the bot kits. */
    public static void botsPickAKit(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context);
        TestBotProvider provider = TestBotProvider.register(f.server);
        GameTestLifecycle.afterTest(context, () -> MatchBots.unregister(TestBotProvider.ID));
        ServerPlayer human = f.player();
        Match match = f.open("frostbite", "1v1");
        MatchManager.join(human, match, 1);
        match.addBots(2, 1);
        context.runAfterDelay(5, () -> {
            check(match.phase() == MatchPhase.ACTIVE && provider.spawned.size() == 1, "the bot match did not start");
            ServerPlayer bot = provider.spawned.getFirst();
            f.players.add(bot);
            String kit = SKYWARS.kitOf(match, bot).map(SkyWarsKit::id).orElse("");
            check(SkyWarsGame.BOT_KITS.contains(kit), "the bot got " + kit);
            context.succeed();
        });
    }

    /** Insane perks with max-perks on: start effects, damage, kills, blocks, potions, bows and fists. */
    public static void perksTakeEffect(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context);
        ServerPlayer hero = f.player();
        ServerPlayer target = f.player();
        ServerPlayer victim = f.player();
        Match match = f.open("mesa", "ffa");
        for (ServerPlayer player : List.of(hero, target, victim)) MatchManager.join(player, match, 0);
        match.start();
        context.runAfterDelay(3, () -> {
            ServerLevel level = hero.level();
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(amplifier(hero, MobEffects.RESISTANCE) == 1, "Resistance Boost missing");
            check(amplifier(hero, MobEffects.ABSORPTION) == 0, "Fat missing");
            check(amplifier(hero, MobEffects.HASTE) == 1, "Speed Boost missing");
            check(amplifier(hero, MobEffects.SPEED) == 0, "Adrenaline missing");

            hero.removeAllEffects();
            hero.setHealth(20);
            hero.hurtServer(level, level.damageSources().fall(), 10);
            check(Math.abs(hero.getHealth() - 15) < 0.01, "Environmental Expert left " + hero.getHealth());
            hero.invulnerableTime = 0;
            hero.hurtServer(level, level.damageSources().enderPearl(), 5);
            check(Math.abs(hero.getHealth() - 15) < 0.01, "ender pearls hurt: " + hero.getHealth());

            // Bridger: some of eight placed blocks come back.
            hero.getRandom().setSeed(7);
            hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 64));
            BlockPos base = hero.blockPosition().offset(2, -1, 0);
            level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
            for (int height = 0; height < 8; height++) {
                BlockPos clicked = base.above(height);
                hero.getMainHandItem().useOn(new UseOnContext(hero, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
            }
            int stone = hero.getMainHandItem().getCount();
            check(level.getBlockState(base.above(8)).is(Blocks.STONE), "the column was not placed");
            check(stone > 56 && stone < 64, "Bridger left " + stone + " of 64 stone after 8 placements");

            // Mining Expertise, instant smelting and drops into the inventory.
            BlockPos ore = base.south();
            level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
            hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            int ingots = count(hero, Items.IRON_INGOT);
            check(hero.gameMode.destroyBlock(ore), "the ore was not broken");
            check(count(hero, Items.IRON_INGOT) > ingots && count(hero, Items.RAW_IRON) == 0, "ore did not smelt into the inventory");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(ore).inflate(3)).isEmpty(), "mined drops fell on the ground");

            // Apothecary.
            hero.removeEffect(MobEffects.SPEED);
            PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS).finishUsingItem(level, hero);
            MobEffectInstance speed = hero.getEffect(MobEffects.SPEED);
            check(speed != null && speed.getDuration() > 4500, "Apothecary gave " + speed);

            // Fortune Teller.
            var registries = f.server.registryAccess();
            var knockback = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK);
            var sharpness = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            List<EnchantmentInstance> offer = SkyWarsPerks.enchantments(hero, registries, new ItemStack(Items.IRON_SWORD), 1,
                    List.of(new EnchantmentInstance(knockback, 1)));
            check(offer.stream().anyMatch(e -> e.enchantment().equals(sharpness) && e.level() == 1), "Fortune Teller offered " + offer);

            // Bow hits: Arrow Recovery, Frost on crits and Annoy-o-mite.
            hero.getRandom().setSeed(11);
            Arrow arrow = new Arrow(level, hero, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setCritArrow(true);
            boolean slowed = false;
            boolean silverfish = false;
            int arrows = count(hero, Items.ARROW);
            AABB around = new AABB(target.blockPosition()).inflate(4);
            // Each chance is at least 10%, so 200 hits miss one with odds below 1e-9.
            for (int hit = 0; hit < 200 && !(slowed && silverfish && count(hero, Items.ARROW) > arrows); hit++) {
                target.setHealth(20);
                target.invulnerableTime = 0;
                target.hurtServer(level, level.damageSources().arrow(arrow, hero), 0.1F);
                slowed |= target.hasEffect(MobEffects.SLOWNESS);
                silverfish |= !level.getEntitiesOfClass(Silverfish.class, around).isEmpty();
            }
            check(count(hero, Items.ARROW) > arrows, "Arrow Recovery gave no arrows");
            check(slowed, "Frost never slowed the target");
            check(silverfish, "Annoy-o-mite spawned no silverfish");

            // Robbery: a fist hit takes the held item.
            hero.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            for (int hit = 0; hit < 40 && !target.getMainHandItem().isEmpty(); hit++) {
                target.setHealth(20);
                target.invulnerableTime = 0;
                target.hurtServer(level, level.damageSources().playerAttack(hero), 0.1F);
            }
            check(target.getMainHandItem().isEmpty() && count(hero, Items.DIAMOND) >= 1, "Robbery stole nothing");

            // A void kill: Bulldozer, Juggernaut, Savior and Knowledge with Big Brain.
            hero.removeAllEffects();
            hero.experienceLevel = 0;
            victim.invulnerableTime = 0;
            victim.hurtServer(level, level.damageSources().playerAttack(hero), 1);
            victim.teleportTo(victim.getX(), match.arena().voidY() - 2, victim.getZ());
            context.runAfterDelay(2, () -> {
                check(!match.isAlive(victim.getUUID()), "the victim survived the void");
                check(amplifier(hero, MobEffects.STRENGTH) == 0, "Bulldozer missing");
                check(amplifier(hero, MobEffects.REGENERATION) == 0, "Juggernaut missing");
                check(amplifier(hero, MobEffects.ABSORPTION) == 0, "Savior missing");
                check(hero.experienceLevel == 5, "Knowledge gave " + hero.experienceLevel + " levels");
                context.succeed();
            });
        });
    }

    /** With both max rules off, only Default and granted kits and perks are owned, at base numbers. */
    public static void maxRulesOffLockKitsAndPerks(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context);
        check(new GameRules(List.of(SkyWarsProgression.MAX_ALL_KITS)).get(SkyWarsProgression.MAX_ALL_KITS), "max kits default is not true");
        check(new GameRules(List.of(SkyWarsProgression.MAX_ALL_PERKS)).get(SkyWarsProgression.MAX_ALL_PERKS), "max perks default is not true");
        f.rules(false);
        ServerPlayer locked = f.player();
        ServerPlayer other = f.player();
        check(command(locked, "minigames skywars kit pyro") == 0, "a locked kit was selectable");
        SkyWarsProgression.selectKit(f.server, locked.getUUID(), SkyWarsKits.find(SkyWarsMode.INSANE, "pyro"));
        check(SkyWarsProgression.selectedKit(f.server, locked.getUUID(), SkyWarsMode.INSANE).id().equals(SkyWarsKits.DEFAULT),
                "a saved locked kit was used");
        Match match = f.open("frostbite", "1v1");
        MatchManager.join(locked, match, 1);
        MatchManager.join(other, match, 2);
        context.runAfterDelay(3, () -> {
            check(match.phase() == MatchPhase.ACTIVE, "match did not start");
            check(count(locked, Items.LAVA_BUCKET) == 0 && count(locked, Items.IRON_PICKAXE) == 1,
                    "the locked player did not get Default: " + locked.getInventory().getNonEquipmentItems());
            check(!locked.hasEffect(MobEffects.RESISTANCE) && !locked.hasEffect(MobEffects.HASTE)
                    && !locked.hasEffect(MobEffects.ABSORPTION), "unowned perks took effect");
            locked.setHealth(20);
            locked.hurtServer(locked.level(), locked.level().damageSources().fall(), 10);
            check(Math.abs(locked.getHealth() - 10) < 0.01, "unowned Environmental Expert reduced fall damage");
            SkyWarsPerk fat = SkyWarsPerk.find(SkyWarsMode.INSANE, SkyWarsPerk.FAT);
            SkyWarsKit pyro = SkyWarsKits.find(SkyWarsMode.INSANE, "pyro");
            try {
                check(command(f.server, "minigames skywars grant " + locked.getScoreboardName() + " perk fat") == 1, "grant failed");
                check(command(f.server, "minigames skywars grant " + locked.getScoreboardName() + " kit pyro") == 1, "grant failed");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            check(SkyWarsProgression.active(f.server, locked.getUUID(), fat), "a granted perk is not active");
            check(SkyWarsProgression.selectedKit(f.server, locked.getUUID(), SkyWarsMode.INSANE) == pyro, "a granted kit is not selected");
            check(SkyWarsPerk.find(SkyWarsMode.INSANE, SkyWarsPerk.KNOWLEDGE).value(false) == 3
                    && SkyWarsPerk.find(SkyWarsMode.INSANE, SkyWarsPerk.KNOWLEDGE).value(true) == 5, "Knowledge numbers");
            context.succeed();
        });
    }

    /** Island and mid chests roll the Insane tables at the start and at a refill. */
    public static void refillsRollTheInsaneTables(GameTestHelper context) throws Exception {
        Fixture f = new Fixture(context);
        SettingsStorage.set(f.server, SKYWARS, SkyWarsGame.FIRST_REFILL, 2);
        SettingsStorage.set(f.server, SKYWARS, SkyWarsGame.SECOND_REFILL, 0);
        ServerPlayer one = f.player();
        ServerPlayer two = f.player();
        Match match = f.open("frostbite", "1v1");
        MatchManager.join(one, match, 1);
        MatchManager.join(two, match, 2);
        context.runAfterDelay(3, () -> {
            MapArena arena = (MapArena) match.arena();
            checkInsaneLoot(arena, "at the start");
            for (var chest : SKYWARS.chests(arena)) container(arena, chest).clearContent();
            context.runAfterDelay(45, () -> {
                checkInsaneLoot(arena, "after the refill");
                context.succeed();
            });
        });
    }

    private static void checkInsaneLoot(MapArena arena, String when) {
        List<BlockPos> island = SKYWARS.chests(arena, SkyWarsMatch.ChestKind.ISLAND);
        List<BlockPos> mid = SKYWARS.chests(arena, SkyWarsMatch.ChestKind.MID);
        check(!island.isEmpty() && !mid.isEmpty(), "expected island and mid chests");
        for (BlockPos pos : island) {
            Container chest = container(arena, pos);
            check(chest.hasAnyMatching(stack -> stack.is(Items.IRON_SWORD) || stack.is(Items.DIAMOND_SWORD)),
                    "island chest " + pos + " has no sword " + when);
            check(chest.hasAnyMatching(stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.STONE)),
                    "island chest " + pos + " has no blocks " + when);
        }
        boolean any = false;
        for (BlockPos pos : mid) {
            Container chest = container(arena, pos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                check(stack.isEmpty() || MID_ITEMS.contains(stack.getItem()), "mid chest " + pos + " holds " + stack + " " + when);
                any |= !stack.isEmpty();
            }
        }
        check(any, "every mid chest is empty " + when);
    }

    // Helpers

    private static int amplifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
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

    private static int command(ServerPlayer player, String command) throws Exception {
        return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    }

    private static int command(MinecraftServer server, String command) throws Exception {
        return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    /** Players, a SkyWars match and changed rules and settings of one test, undone when it ends. */
    private static final class Fixture {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Match> matches = new ArrayList<>();
        private final boolean maxKits;
        private final boolean maxPerks;

        Fixture(GameTestHelper context) {
            this.context = context;
            this.server = context.getLevel().getServer();
            this.maxKits = server.getGameRules().get(SkyWarsProgression.MAX_ALL_KITS);
            this.maxPerks = server.getGameRules().get(SkyWarsProgression.MAX_ALL_PERKS);
            rules(true);
            SettingsStorage.set(server, SKYWARS, SKYWARS.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
            GameTestLifecycle.afterTest(context, this::close);
        }

        void rules(boolean max) {
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, max, server);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, max, server);
        }

        ServerPlayer player() {
            ServerPlayer player = TestPlayers.connect(context, "Sky" + NAMES.incrementAndGet());
            players.add(player);
            return player;
        }

        Match open(String map, String layout) throws MatchException {
            Identifier id = MapArena.maps(server, SkyWarsGame.ID).stream()
                    .filter(candidate -> candidate.getPath().endsWith("/" + map))
                    .findFirst()
                    .orElseThrow(() -> failure("missing SkyWars map " + map));
            Match match = MatchManager.open(server, SKYWARS, TeamLayout.parse(layout).orElseThrow(), null,
                    (unused, settings) -> SKYWARS.prepare(MapArena.open(context.getLevel(), id)));
            matches.add(match);
            return match;
        }

        void close() {
            for (Match match : matches) MatchManager.stop(match);
            CompoundTag root = server.getCommandStorage().get(SkyWarsProgression.STORAGE);
            for (ServerPlayer player : players) root.remove(player.getUUID().toString());
            server.getCommandStorage().set(SkyWarsProgression.STORAGE, root);
            TestPlayers.disconnect(players.toArray(ServerPlayer[]::new));
            for (GameSetting setting : SKYWARS.settings()) SettingsStorage.reset(server, SKYWARS, setting);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, maxKits, server);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, maxPerks, server);
        }
    }
}
