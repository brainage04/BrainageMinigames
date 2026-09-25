package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The loader-sensitive subset of the Fabric GameTests: command registration, persistence, and the
 * event wiring.
 */
public final class NeoForgeGameTestFunctions {
    private NeoForgeGameTestFunctions() {}

    public static void commands(GameTestHelper context) {
        var minigames =
                context.getLevel()
                        .getServer()
                        .getCommands()
                        .getDispatcher()
                        .getRoot()
                        .getChild("minigames");
        check(minigames != null, "/minigames is not registered");
        for (String child :
                new String[] {
                    "list",
                    "status",
                    "join",
                    "watch",
                    "leave",
                    "open",
                    "start",
                    "stop",
                    "settings",
                    "kit"
                }) {
            check(minigames.getChild(child) != null, "/minigames " + child + " is not registered");
        }
        context.succeed();
    }

    public static void snapshot(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        var server = context.getLevel().getServer();
        Vec3 position = context.absoluteVec(new Vec3(2.5, 3, 2.5));
        player.setGameMode(GameType.CREATIVE);
        player.snapTo(position.x(), position.y(), position.z(), 37, -12);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        check(PlayerSnapshotStorage.save(player), "snapshot was not saved");
        check(
                PlayerSnapshotStorage.addRewards(player, List.of(new ItemStack(Items.EMERALD, 2))),
                "rewards were not added");
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        check(PlayerSnapshotStorage.restore(player), "snapshot was not restored");
        check(
                player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                "game mode was not restored");
        check(
                count(player, Items.DIAMOND) == 3 && count(player, Items.EMERALD) == 2,
                "inventory and rewards were not restored");
        check(player.position().equals(position), "position was not restored");
        check(
                !PlayerSnapshotStorage.hasSnapshot(server, player.getUUID()),
                "snapshot was not consumed");
        context.succeed();
    }

    public static void kit(GameTestHelper context) {
        var server = context.getLevel().getServer();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        var id = BrainageMinigames.id("gametest/neoforge_editable");
        KitStorage.delete(server, id);
        try {
            KitStorage.openEditor(player, id);
            player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2));
            player.closeContainer();
            check(KitStorage.get(server, id).orElseThrow().size() == 1, "edited kit was not saved");
            player.getInventory().clearContent();
            check(
                    KitStorage.give(
                            server, BrainageMinigames.id("kits/barebones"), List.of(player)),
                    "bundled kit was not found");
            check(
                    player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "bundled kit armour was not worn");
        } finally {
            KitStorage.delete(server, id);
        }
        context.succeed();
    }

    /** Exercises the NeoForge damage and death listeners through a complete 1v1. */
    public static void duel(GameTestHelper context) {
        var server = context.getLevel().getServer();
        Minigame game = Minigames.GAPPLE;
        GameSetting countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, game, countdown, 0);
        ServerPlayer winner = loadedPlayer(context);
        ServerPlayer loser = loadedPlayer(context);
        Vec3 home = context.absoluteVec(new Vec3(1.5, 2, 1.5));
        winner.snapTo(home.x(), home.y(), home.z(), 0, 0);
        loser.snapTo(home.x(), home.y(), home.z(), 0, 0);
        Match match;
        try {
            match =
                    MatchManager.open(
                            server,
                            game,
                            TeamLayout.parse("1v1").orElseThrow(),
                            null,
                            (ignored, settings) ->
                                    BoxArena.open(
                                            context.getLevel(),
                                            21,
                                            Blocks.SMOOTH_STONE.defaultBlockState()));
            MatchManager.join(winner, match, 1);
            MatchManager.join(loser, match, 2);
        } catch (MatchException exception) {
            throw failure(exception.getMessage());
        }
        context.runAfterDelay(
                2,
                () -> {
                    check(match.phase() == MatchPhase.ACTIVE, "match did not start");
                    loser.hurtServer(loser.level(), loser.damageSources().fall(), 1.0F);
                    check(
                            loser.getHealth() < loser.getMaxHealth(),
                            "active players did not take damage");
                    loser.hurtServer(
                            loser.level(), loser.damageSources().genericKill(), Float.MAX_VALUE);
                    check(
                            loser.isAlive()
                                    && loser.gameMode.getGameModeForPlayer() == GameType.SPECTATOR,
                            "death was not turned into an elimination");
                    context.runAfterDelay(
                            2,
                            () -> {
                                check(
                                        match.phase() == MatchPhase.ENDED,
                                        "match did not end after the elimination");
                                context.runAfterDelay(
                                        110,
                                        () -> {
                                            check(
                                                    MatchManager.get(match.id()).isEmpty(),
                                                    "match did not close");
                                            check(
                                                    winner.position().distanceToSqr(home) < 1.0E-6,
                                                    "winner was not restored");
                                            SettingsStorage.reset(server, game, countdown);
                                            context.succeed();
                                        });
                            });
                });
    }

    /**
     * Exercises the common mixin on NeoForge: a match with natural_regeneration 0 stops food from
     * healing its members while hunger still drains.
     */
    public static void regeneration(GameTestHelper context) {
        var server = context.getLevel().getServer();
        Minigame game = Minigames.BUILD_UHC;
        GameSetting countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, game, countdown, 0);
        boolean gameRule = server.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, true, server);
        ServerPlayer player = loadedPlayer(context);
        ServerPlayer opponent = loadedPlayer(context);
        Match match;
        try {
            match =
                    MatchManager.open(
                            server,
                            game,
                            TeamLayout.parse("1v1").orElseThrow(),
                            null,
                            (ignored, settings) ->
                                    BoxArena.open(
                                            context.getLevel(),
                                            21,
                                            Blocks.SMOOTH_STONE.defaultBlockState()));
            MatchManager.join(player, match, 1);
            MatchManager.join(opponent, match, 2);
        } catch (MatchException exception) {
            throw failure(exception.getMessage());
        }
        context.runAfterDelay(
                2,
                () -> {
                    try {
                        check(match.phase() == MatchPhase.ACTIVE, "match did not start");
                        player.setHealth(10);
                        player.getFoodData().setFoodLevel(20);
                        player.getFoodData().setSaturation(5);
                        for (int tick = 0; tick < 40; tick++) {
                            player.getFoodData().tick(player);
                        }
                        check(player.getHealth() == 10, "food healed a player in the match");
                        player.getFoodData().setSaturation(0);
                        player.getFoodData().addExhaustion(8.5F);
                        player.getFoodData().tick(player);
                        player.getFoodData().tick(player);
                        check(
                                player.getFoodData().getFoodLevel() == 18,
                                "hunger did not drain normally");
                    } finally {
                        MatchManager.stop(match);
                        SettingsStorage.reset(server, game, countdown);
                        server.getGameRules()
                                .set(GameRules.NATURAL_HEALTH_REGENERATION, gameRule, server);
                    }
                    context.succeed();
                });
    }

    /**
     * Exercises MapArena and the common block-rule mixins on NeoForge: the test map pastes with its
     * markers replaced, a lobby player cannot break it, a participant's placed block is tracked,
     * and a reset restores the map.
     */
    public static void mapArena(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var level = context.getLevel();
        Minigame game = Minigames.BUILD_UHC;
        GameSetting countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, game, countdown, 0);
        ServerPlayer player = loadedPlayer(context);
        ServerPlayer opponent = loadedPlayer(context);
        Match match;
        MapArena arena;
        BlockPos glass;
        try {
            match =
                    MatchManager.open(
                            server,
                            game,
                            TeamLayout.parse("1v1").orElseThrow(),
                            null,
                            (ignored, settings) ->
                                    MapArena.open(
                                            level, BrainageMinigames.id("maps/test_map/basic")));
            arena = (MapArena) match.arena();
            int floorY = MapArena.BASE_Y + 5;
            glass = new BlockPos(arena.bounds().minX() + 32, floorY + 1, arena.bounds().minZ() + 8);
            check(level.getBlockState(glass).is(Blocks.GLASS), "map was not pasted");
            check(arena.teamSlots() == 2, "map markers were not parsed");
            check(
                    level.getBlockState(BlockPos.containing(arena.lobbyPosition())).isAir(),
                    "lobby marker was not replaced by air");
            MatchManager.join(player, match, 1);
            player.setGameMode(GameType.SURVIVAL);
            check(!player.gameMode.destroyBlock(glass), "a lobby player broke a map block");
            check(level.getBlockState(glass).is(Blocks.GLASS), "refused break removed the block");
            MatchManager.join(opponent, match, 2);
        } catch (MatchException exception) {
            SettingsStorage.reset(server, game, countdown);
            throw failure(exception.getMessage());
        }
        context.runAfterDelay(
                2,
                () -> {
                    try {
                        check(match.phase() == MatchPhase.ACTIVE, "match did not start");
                        BlockPos floor = glass.west(2).north(6).below();
                        player.setItemInHand(
                                InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
                        player.gameMode.useItemOn(
                                player,
                                level,
                                player.getMainHandItem(),
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0),
                                        Direction.UP,
                                        floor,
                                        false));
                        check(
                                level.getBlockState(floor.above()).is(Blocks.OAK_PLANKS),
                                "a participant could not place in the build region");
                        check(match.isPlacedBlock(floor.above()), "placed block was not tracked");
                        check(player.gameMode.destroyBlock(glass), "a participant could not break");
                        arena.reset();
                        check(level.getBlockState(glass).is(Blocks.GLASS), "reset lost the glass");
                        check(level.getBlockState(floor.above()).isAir(), "reset kept the planks");
                        check(!match.isPlacedBlock(floor.above()), "reset kept placed blocks");
                    } finally {
                        MatchManager.stop(match);
                        SettingsStorage.reset(server, game, countdown);
                    }
                    check(level.getBlockState(glass).isAir(), "closing did not clear the map");
                    context.succeed();
                });
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw failure(failure);
        }
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    /**
     * Real clients report when they have loaded the world; until then the server makes them immune
     * to damage.
     */
    private static ServerPlayer loadedPlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }
}
