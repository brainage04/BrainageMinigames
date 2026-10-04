package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.check;
import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.setTicks;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.*;
import io.github.brainage04.brainage_minigames.game.uhc.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Server menus, persisted profiles and live match transitions on both loaders. */
public final class UhcSettingsGameTestFunctions {
    private UhcSettingsGameTestFunctions() {}

    public static void kitsAndCoins(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var rules = f.server.getGameRules();
            var player = f.players.getFirst();
            check(UhcProgression.selectedKit(f.server, player.getUUID()).equals("stone"), "unselected kit is not Stone Gear");
            check(count(player, Items.STONE_PICKAXE) == 1 && count(player, Items.STONE_SWORD) == 1, "default kit did not equip stone tools");
            check(!new GameRules(List.of(UhcProgression.MAX_ALL_KITS)).get(UhcProgression.MAX_ALL_KITS), "max kits default is not false");
            check(!new GameRules(List.of(UhcProgression.CHOOSE_PRESTIGE)).get(UhcProgression.CHOOSE_PRESTIGE), "prestige choice default is not false");
            rules.set(UhcProgression.MAX_ALL, true, f.server);
            check(UhcKits.level(player, UhcKits.Kit.STONE) == 0 && !UhcKits.prestiged(player, UhcKits.Kit.STONE), "max perks unexpectedly maxed kits");
            rules.set(UhcProgression.MAX_ALL, false, f.server);
            rules.set(UhcProgression.MAX_ALL_KITS, true, f.server);
            for (var kit : UhcKits.Kit.values()) check(UhcKits.level(player, kit) == 3 && UhcKits.prestiged(player, kit), "max kits missed " + kit.id);
            check(!UhcProgression.maxed(player), "max kits unexpectedly maxed perks");
            check(command(player, "minigames uhc prestige_bonus stone iron_pickaxe") == 0, "disabled prestige choice was accepted");
            rules.set(UhcProgression.CHOOSE_PRESTIGE, true, f.server);
            check(command(player, "minigames uhc prestige_bonus stone iron_pickaxe") == 1, "prestige command failed");
            check(UhcProgression.prestigeChoice(f.server, player.getUUID(), UhcKits.Kit.STONE) == 2, "prestige choice did not persist per UUID");
            for (int i = 0; i < 12; i++) {
                UhcKits.equip(player);
                check(count(player, Items.IRON_PICKAXE) == 1 && count(player, Items.STONE_PICKAXE) == 0, "selected prestige bonus rerolled");
            }
            rules.set(UhcProgression.CHOOSE_PRESTIGE, false, f.server);
            player.getRandom().setSeed(42);
            boolean other = false;
            for (int i = 0; i < 12; i++) { UhcKits.equip(player); other |= count(player, Items.IRON_PICKAXE) == 0; }
            check(other, "random prestige ignored disabled selection");
            rules.set(UhcProgression.MAX_ALL_KITS, false, f.server);
            check(UhcKits.level(player, UhcKits.Kit.STONE) == 0 && UhcProgression.purchases(f.server, player.getUUID()).isEmpty(), "max kits wrote fake purchases");
            check(new GameRules(List.of(UhcProgression.COIN_MULTIPLIER)).get(UhcProgression.COIN_MULTIPLIER) == 100, "coin default is not 100 percent");
            rules.set(UhcProgression.COIN_MULTIPLIER, 250, f.server);
            for (var action : UhcProgression.CoinAction.values()) UhcProgression.award(f.match, player.getUUID(), action);
            check(UhcProgression.coins(f.server, player.getUUID()) == 562, "not every coin action used multiplier with integer rounding");
            rules.set(UhcProgression.COIN_MULTIPLIER, 0, f.server);
            UhcProgression.award(f.server, player.getUUID(), 100);
            check(UhcProgression.coins(f.server, player.getUUID()) == 562, "zero multiplier awarded coins");
            context.succeed();
        });
    }

    public static void craftPrompts(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var player = f.players.getFirst();
            var channel = f.channels.getFirst();
            player.getInventory().clearContent();
            f.server.getGameRules().set(UhcProgression.MAX_ALL, true, f.server);
            f.server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, false, f.server);
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 8));
            player.getInventory().add(new ItemStack(Items.APPLE, 2));
            int before = chatCount(channel, "You can craft light apple");
            UhcCraftPrompts.tick(player);
            check(chatCount(channel, "You can craft light apple") == before + 1, "ingredients did not send craft prompt");
            Component message = chat(channel, "You can craft light apple");
            check(message.getStyle().getClickEvent() instanceof ClickEvent.RunCommand, "prompt was not clickable");
            UhcCraftPrompts.tick(player);
            player.getInventory().add(new ItemStack(Items.DIRT));
            UhcCraftPrompts.tick(player);
            check(chatCount(channel, "You can craft light apple") == before + 1, "unchanged recipe ingredients repeated prompt");
            var click = (ClickEvent.RunCommand) message.getStyle().getClickEvent();
            check(command(player, click.command().substring(1)) == 1, "click did not open crafting table");
            check(player.containerMenu instanceof CraftingMenu, "click did not open server-side vanilla crafting menu");
            CraftingMenu menu = (CraftingMenu) player.containerMenu;
            var recipe = UhcProgressionGameTestFunctions.recipe("light_apple");
            for (int cell = 0; cell < 9; cell++) {
                Item expected = recipe.grid()[cell];
                ItemStack actual = menu.getInputGridSlots().get(cell).getItem();
                check(expected == Items.AIR ? actual.isEmpty() : actual.is(expected) && actual.getCount() == 1, "autofill grid wrong at " + cell);
            }
            check(count(player, Items.GOLD_INGOT) == 4 && count(player, Items.APPLE) == 1, "autofill did not move exact ingredients out of inventory");
            check(menu.getResultSlot().getItem().is(Items.GOLDEN_APPLE), "autofill did not produce output");
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            check(menu.getCarried().is(Items.GOLDEN_APPLE), "one output click failed to craft");
            player.closeContainer();
            check(count(player, Items.GOLDEN_APPLE) == 1, "closing lost crafted carried output");
            check(UhcCraftPrompts.open(player, "light_apple"), "profession prestige did not permit its second ultimate");
            player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
            player.closeContainer();
            check(count(player, Items.GOLDEN_APPLE) == 2, "second ultimate output was lost");
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
            player.getInventory().add(new ItemStack(Items.APPLE));
            check(!UhcCraftPrompts.open(player, "light_apple"), "autofill bypassed prestiged ultimate craft limit");
            f.server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, true, f.server);
            check(UhcCraftPrompts.open(player, "light_apple"), "unlimited crafts did not permit further autofill");
            player.closeContainer();
            check(count(player, Items.GOLD_INGOT) == 4 && count(player, Items.APPLE) == 1, "closing lost unused grid ingredients");
            f.server.getGameRules().set(UhcProgression.MAX_ALL, false, f.server);
            check(!UhcCraftPrompts.open(player, "light_apple"), "locked recipe opened");
            context.succeed();
        });
    }

    public static void presetsAndTiming(GameTestHelper context) {
        withMatch(context, Minigames.UHC, true, f -> {
            check(f.server.getCommands().getDispatcher().execute("minigames uhc preset hypixel", f.server.createCommandSourceStack()) == 1, "Hypixel preset command failed");
            var rules = f.server.getGameRules();
            check(rules.get(UhcModeRules.DOUBLE_HEALTH) && rules.get(UhcModeRules.BORDER_STYLE) == 0 && rules.get(UhcModeRules.DEATHMATCH), "Hypixel core preset values wrong");
            check(rules.get(UhcModeRules.DEATHMATCH_AFTER_GRACE) == 35 && rules.get(UhcModeRules.DEATHMATCH_SKIP_PLAYERS) == 15
                    && rules.get(UhcModeRules.DEATHMATCH_SKIP_MINUTES) == 10 && rules.get(UhcModeRules.DEATHMATCH_DURATION) == 15
                    && rules.get(UhcModeRules.TIMEOUT_MOST_KILLS) && rules.get(UhcModeRules.COMBAT_LOGGER), "Hypixel timing/logger preset values wrong");
            UhcGame game = (UhcGame) f.match.game();
            game.onStart(f.match);
            check(game.deathmatchStartTicks(f.match) == 35 * 60 * 20 && game.deathmatchDurationTicks(f.match) == 15 * 60 * 20, "after-grace/duration rules did not affect schedule");
            setTicks(f.match, 1);
            game.tick(f.match);
            check(game.deathmatchStartTicks(f.match) == 1 + 10 * 60 * 20, "player threshold did not skip remaining countdown to ten minutes");
            setTicks(f.match, game.deathmatchStartTicks(f.match));
            game.tick(f.match);
            UhcArena arena = (UhcArena) f.match.arena();
            check(arena.inDeathmatch(), "shortened countdown did not enter deathmatch");
            f.match.teams().getFirst().addScore(2);
            f.match.teams().get(1).addScore(1);
            setTicks(f.match, game.deathmatchStartTicks(f.match) + game.deathmatchDurationTicks(f.match));
            game.tick(f.match);
            check(f.match.phase() == MatchPhase.ENDED && f.match.winners().equals(List.of(f.match.teams().getFirst())), "deathmatch timeout did not award most kills");
            UhcPresets.apply(f.server, false);
            check(!rules.get(UhcModeRules.DOUBLE_HEALTH) && rules.get(UhcModeRules.BORDER_STYLE) == 1 && rules.get(UhcModeRules.DEATHMATCH)
                    && rules.get(UhcModeRules.DEATHMATCH_AFTER_GRACE) == 0 && rules.get(UhcModeRules.DEATHMATCH_SKIP_PLAYERS) == 0
                    && rules.get(UhcModeRules.DEATHMATCH_DURATION) == 0 && !rules.get(UhcModeRules.TIMEOUT_MOST_KILLS)
                    && !rules.get(UhcModeRules.COMBAT_LOGGER), "Badlion preset did not replace every differing rule");
            rules.set(UhcModeRules.DOUBLE_HEALTH, true, f.server);
            check(rules.get(UhcModeRules.DOUBLE_HEALTH), "individual rule could not override preset");
            context.succeed();
        });
    }

    public static void borders(GameTestHelper context) {
        withMatch(context, Minigames.UHC, true, f -> {
            var arena = (UhcArena) f.match.arena();
            check(new GameRules(List.of(UhcModeRules.DEATHMATCH_BORDER_START)).get(UhcModeRules.DEATHMATCH_BORDER_START) == 113
                    && new GameRules(List.of(UhcModeRules.DEATHMATCH_BORDER_FINAL)).get(UhcModeRules.DEATHMATCH_BORDER_FINAL) == 56
                    && new GameRules(List.of(UhcModeRules.NETHER_BORDER_SCALE)).get(UhcModeRules.NETHER_BORDER_SCALE) == 8, "border rule defaults changed");
            var game = (UhcGame) f.match.game();
            var loggedOut = f.players.get(1);
            MatchManager.handleDisconnect(loggedOut); f.server.getPlayerList().remove(loggedOut);
            var beforeMove = UhcCombatLogger.zombie(loggedOut.getUUID());
            setTicks(f.match, game.deathmatchStartTicks(f.match)); game.tick(f.match);
            check(arena.border().getSize() == 100, "deathmatch starting width was ignored");
            var moved = UhcCombatLogger.zombie(loggedOut.getUUID());
            check(moved != null && moved != beforeMove && moved.level() == arena.level()
                    && moved.level().dimension().equals(ModDimensions.MINIGAMES), "deathmatch did not move disconnected participant");
            check(!moved.hurtServer(arena.level(), moved.damageSources().playerAttack(f.players.getFirst()), 1),
                    "deathmatch freeze did not protect disconnected participant");
            var portal = (EndPortalBlock) Blocks.END_PORTAL;
            check(portal.getPortalDestination(arena.level(), moved, moved.blockPosition()) == null,
                    "End portal let a disconnected deathmatch participant escape");
            setTicks(f.match, game.deathmatchStartTicks(f.match) + f.match.settings().minutesInTicks(UhcGame.DEATHMATCH_SHRINK_TIME));
            game.tick(f.match);
            check(arena.border().getLerpTarget() == 40, "deathmatch final width was ignored");
            context.succeed();
        });
    }

    public static void loggerUhc(GameTestHelper context) { logger(context, Minigames.UHC); }
    public static void loggerMeetup(GameTestHelper context) { logger(context, Minigames.MEETUP); }
    public static void loggerFinalUhc(GameTestHelper context) { logger(context, Minigames.FINAL_UHC); }
    private static void logger(GameTestHelper context, Minigame game) {
        withMatch(context, game, false, f -> {
            check(new GameRules(List.of(UhcModeRules.COMBAT_LOGGER)).get(UhcModeRules.COMBAT_LOGGER), "combat logger default is not true");
            var victim = f.players.get(1);
            var attacker = f.players.getFirst();
            victim.getInventory().clearContent();
            victim.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
            victim.setHealth(12);
            UUID id = victim.getUUID();
            MatchManager.handleDisconnect(victim);
            f.server.getPlayerList().remove(victim);
            var zombie = UhcCombatLogger.zombie(id);
            check(zombie != null && f.match.isAlive(id) && f.match.involves(id), "disconnect did not retain participant as zombie");
            check(zombie.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).has(DataComponents.PROFILE)
                    && zombie.getMainHandItem().is(Items.STONE_AXE) && zombie.entityTags().contains(UhcCombatLogger.TAG), "logger head, held item or marker missing");
            check(UhcCombatLogger.participant(zombie).equals(id) && UhcCombatLogger.match(zombie) == f.match, "bot/camera logger interface lost identity");
            check(zombie.hurtServer((net.minecraft.server.level.ServerLevel) zombie.level(), zombie.damageSources().generic(), 2), "logger was not attackable");
            Vec3 position = zombie.position().add(2, 0, 0); zombie.setPos(position);
            float health = zombie.getHealth();
            var returned = f.connect(id, victim.getScoreboardName());
            MatchManager.handleConnect(returned);
            check(UhcCombatLogger.zombie(id) == null && zombie.isRemoved(), "rejoining left duplicate zombie");
            check(returned.position().distanceTo(position) < 0.01 && Math.abs(returned.getHealth() - health) < 0.01, "rejoin did not restore current zombie position and health");
            check(count(returned, Items.DIAMOND) == 3, "rejoin lost match inventory");
            returned.getInventory().clearContent(); returned.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            MatchManager.handleDisconnect(returned); f.server.getPlayerList().remove(returned);
            var doomed = UhcCombatLogger.zombie(id);
            BlockPos death = doomed.blockPosition();
            f.cleanup.add(doomed::discard);
            for (BlockPos pos : List.of(death, death.east(), death.above(), death.east().above())) {
                var state = doomed.level().getBlockState(pos);
                f.cleanup.add(() -> doomed.level().setBlockAndUpdate(pos, state));
            }
            f.cleanup.add(() -> doomed.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    doomed.getBoundingBox().inflate(3), entity -> UhcCrafting.kind(entity.getItem()).equals("player_head")
                            && entity.getItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString()
                            .equals(victim.getScoreboardName() + "'s Head")).forEach(net.minecraft.world.entity.Entity::discard));
            int chatBefore = chatCount(f.channels.getFirst(), "was killed while disconnected!");
            check(doomed.hurtServer((net.minecraft.server.level.ServerLevel) doomed.level(), doomed.damageSources().playerAttack(attacker), Float.MAX_VALUE), "lethal logger hit was refused");
            check(!f.match.isAlive(id) && !f.match.involves(id) && UhcCombatLogger.zombie(id) == null, "dead logger can still rejoin");
            check(chatCount(f.channels.getFirst(), "was killed while disconnected!") == chatBefore + 1, "logger death message missing");
            var chest = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, doomed.level().getBlockState(death), doomed.level(), death, true);
            check(chest != null && chest.countItem(Items.DIAMOND) == 3, "anti-janitor did not retain disconnected inventory in death chest");
            var last = f.players.get(3);
            f.server.getGameRules().set(UhcModeRules.COMBAT_LOGGER, false, f.server);
            MatchManager.handleDisconnect(last);
            check(!f.match.isAlive(last.getUUID()) && UhcCombatLogger.zombie(last.getUUID()) == null, "disabled logger did not eliminate on disconnect");
            context.succeed();
        });
    }

    public static void endPortals(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var player = f.players.getFirst();
            int before = chatCount(f.channels.getFirst(), "End portals are disabled");
            var portal = (EndPortalBlock) Blocks.END_PORTAL;
            check(portal.getPortalDestination(player.level(), player, player.blockPosition()) == null, "End portal let a UHC participant escape to the real End");
            check(chatCount(f.channels.getFirst(), "End portals are disabled") == before + 1, "blocked End portal did not explain refusal");
            check(f.match.isAlive(player.getUUID()) && player.level().dimension().equals(ModDimensions.UHC), "End refusal eliminated or moved participant");
            var disconnected = f.players.get(1);
            MatchManager.handleDisconnect(disconnected); f.server.getPlayerList().remove(disconnected);
            var logger = UhcCombatLogger.zombie(disconnected.getUUID());
            check(logger != null && portal.getPortalDestination((net.minecraft.server.level.ServerLevel) logger.level(),
                    logger, logger.blockPosition()) == null, "End portal let a combat logger escape");
            context.succeed();
        });
    }

    public static void netherBorder(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var oldRules = server.getGameRules().copy(context.getLevel().enabledFeatures());
        var settingsId = BrainageMinigames.id("settings");
        var oldSettings = server.getCommandStorage().get(settingsId).copy();
        io.github.brainage04.brainage_minigames.game.arena.Arena[] arena = new io.github.brainage04.brainage_minigames.game.arena.Arena[1];
        Runnable cleanup = () -> {
            if (arena[0] != null) arena[0].close();
            server.getGameRules().setAll(oldRules, server);
            server.getCommandStorage().set(settingsId, oldSettings);
        };
        context.runBeforeTestEnd(cleanup);
        try {
            server.getGameRules().set(UhcModeRules.DEATHMATCH, false, server);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.NETHER_CLOSE_TIME, 20);
            var values = SettingsStorage.resolve(server, Minigames.UHC);
            for (int divisor : List.of(1, 8)) {
                server.getGameRules().set(UhcModeRules.NETHER_BORDER_SCALE, divisor, server);
                arena[0] = Minigames.UHC.openArena(server, values);
                var surface = server.getLevel(ModDimensions.UHC).getWorldBorder();
                var nether = server.getLevel(ModDimensions.UHC_NETHER).getWorldBorder();
                check(Math.abs(nether.getSize() - surface.getSize() / divisor) < 0.001, "Nether width divisor ignored");
                check(Math.abs(nether.getCenterX() - surface.getCenterX() / 8) < 0.001, "Nether width rule changed portal-coordinate scaling");
                var shrink = UhcArena.class.getDeclaredMethod("shrinkBorder", double.class, long.class);
                shrink.setAccessible(true); shrink.invoke(arena[0], 100.0, 1200L);
                check(Math.abs(nether.getLerpTarget() - 100.0 / divisor) < 0.001, "Nether shrink ignored divisor");
                arena[0].close(); arena[0] = null;
            }
            context.succeed();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
        finally { cleanup.run(); }
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static int command(ServerPlayer player, String command) throws Exception {
        return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    }
    private static void flatten(Object packet, List<Component> chat) {
        if (packet instanceof ClientboundSystemChatPacket message) chat.add(message.content());
        else if (packet instanceof BundlePacket<?> bundle) for (var child : bundle.subPackets()) flatten(child, chat);
    }
    private static List<Component> chats(EmbeddedChannel channel) {
        channel.flushOutbound(); List<Component> chat = new ArrayList<>();
        for (var packet : channel.outboundMessages()) flatten(packet, chat);
        return chat;
    }
    private static int chatCount(EmbeddedChannel channel, String text) { return (int) chats(channel).stream().filter(message -> message.getString().contains(text)).count(); }
    private static Component chat(EmbeddedChannel channel, String text) { return chats(channel).stream().filter(message -> message.getString().contains(text)).findFirst().orElseThrow(); }
    @FunctionalInterface private interface Action { void run(Fixture fixture) throws Exception; }
    private static void withMatch(GameTestHelper context, Minigame game, boolean deathmatch, Action action) {
        Fixture f = new Fixture(context, game, deathmatch);
        UhcSpawnGameTestFunctions.awaitReady(context, f.match, () -> {
            try {
                MatchManager.tick();
                check(f.match.phase() == MatchPhase.ACTIVE, "fixture match did not become active");
                for (ServerPlayer player : f.players) {
                    player.hasChangedDimension();
                    player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }
                action.run(f);
            } catch (RuntimeException | Error exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
            finally { f.close(); }
        });
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final MinecraftServer server;
        final Minigame game;
        final GameRules rules;
        final net.minecraft.nbt.CompoundTag settings;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        Match match;
        boolean closed;
        Fixture(GameTestHelper context, Minigame game, boolean deathmatch) {
            this.context = context; this.server = context.getLevel().getServer(); this.game = game;
            rules = server.getGameRules().copy(context.getLevel().enabledFeatures());
            settings = server.getCommandStorage().get(BrainageMinigames.id("settings")).copy();
            context.runBeforeTestEnd(this::close);
            try {
                server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
                server.getGameRules().set(UhcProgression.MAX_ALL_KITS, false, server);
                server.getGameRules().set(UhcProgression.CHOOSE_PRESTIGE, false, server);
                server.getGameRules().set(UhcProgression.COIN_MULTIPLIER, 100, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_AFTER_GRACE, 0, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_DURATION, 0, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_SKIP_PLAYERS, 0, server);
                server.getGameRules().set(UhcModeRules.TIMEOUT_MOST_KILLS, false, server);
                server.getGameRules().set(UhcModeRules.COMBAT_LOGGER, true, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH, deathmatch, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_BORDER_START, 100, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_BORDER_FINAL, 40, server);
                server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
                server.getGameRules().set(AntiJanitor.ENABLED, true, server);
                SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
                if (game instanceof UhcGame) {
                    SettingsStorage.set(server, game, UhcGame.GRACE_PERIOD, 0);
                    SettingsStorage.set(server, game, UhcGame.NETHER_CLOSE_TIME, 0);
                }
                var level = server.getLevel(ModDimensions.UHC);
                BlockPos center = context.absolutePos(new BlockPos(2, 2, 2));
                match = MatchManager.open(server, game, TeamLayout.FREE_FOR_ALL, null, (ignored, values) -> {
                    if (!(game instanceof UhcGame)) return NaturalArena.at(level, center.getX(), center.getZ(), 200);
                    if (deathmatch) return game.openArena(ignored, values);
                    return UhcArena.at(level, center.getX(), center.getZ(), 200);
                });
                for (int i = 0; i < 4; i++) {
                    ServerPlayer player = connect(UUID.randomUUID(), "NewUhc" + i);
                    MatchManager.join(player, match, 0);
                }
                match.start();
            } catch (Exception exception) { close(); throw new IllegalStateException(exception); }
        }
        ServerPlayer connect(UUID id, String name) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
            var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            players.add(player); return player;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (match != null) MatchManager.stop(match);
            cleanup.forEach(Runnable::run);
            for (ServerPlayer player : players) {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
                var root = server.getCommandStorage().get(UhcProgression.STORAGE);
                root.remove(player.getUUID().toString()); server.getCommandStorage().set(UhcProgression.STORAGE, root);
                var snapshotsId = BrainageMinigames.id("player_snapshots");
                var snapshots = server.getCommandStorage().get(snapshotsId);
                snapshots.getCompoundOrEmpty("players").remove(player.getUUID().toString());
                server.getCommandStorage().set(snapshotsId, snapshots);
            }
            server.getCommandStorage().set(BrainageMinigames.id("settings"), settings);
            server.getGameRules().setAll(rules, server);
        }
    }
}
