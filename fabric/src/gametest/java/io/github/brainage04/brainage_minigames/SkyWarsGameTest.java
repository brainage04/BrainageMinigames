package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** SkyWars on its bundled maps, pasted into the test level. */
public final class SkyWarsGameTest {
    private static final SkyWarsGame SKYWARS = Minigames.SKYWARS;

    @GameTest(maxTicks = 100)
    public void everyMapHasIslandsChestsAndVoid(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, SkyWarsGame.ID);
        assertTrue(maps.size() >= 2, "Expected at least two SkyWars maps, found " + maps + ".");
        for (Identifier map : maps) {
            MapArena arena = SKYWARS.prepare(MapArena.open(context.getLevel(), map));
            try {
                assertTrue(arena.teamSlots() >= 2, map + " needs at least two islands.");
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    assertTrue(!arena.spawnsOf(team).isEmpty(), map + " has no spawn " + team);
                }
                // Three chests per island plus the mid chests.
                assertTrue(
                        SKYWARS.chests(arena).size() > arena.teamSlots() * 3,
                        map + " has too few SkyWars chests: " + SKYWARS.chests(arena).size());
                assertTrue(
                        arena.voidY() > arena.bounds().minY() - 1
                                && arena.voidY() < arena.spawnsOf(1).getFirst().position().y(),
                        map + " needs a void marker below its islands.");
                assertTrue(arena.point("bounds_min").isPresent(), map + " lacks bounds_min.");
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 100)
    public void cagesOpenAndChestsAreFilledAtStart(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, "frostbite", "1v1");
        List<BlockPos> cageFloors = new ArrayList<>();
        MapArena arena = (MapArena) match.arena();
        for (int team = 1; team <= arena.teamSlots(); team++) {
            for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                BlockPos floor = BlockPos.containing(spawn.position()).below();
                assertTrue(
                        arena.level().getBlockState(floor).is(Blocks.GLASS),
                        "Expected a glass cage under spawn " + team + " before the start.");
                cageFloors.add(floor);
            }
        }
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.runAfterDelay(
                3,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        for (BlockPos floor : cageFloors) {
                            assertTrue(
                                    arena.level().getBlockState(floor).isAir(),
                                    "Expected the cage at " + floor + " to be removed.");
                        }
                        List<BlockPos> chests = SKYWARS.chests(arena);
                        assertTrue(!chests.isEmpty(), "Expected SkyWars chests.");
                        for (BlockPos chest : chests) {
                            assertTrue(
                                    !container(arena, chest).isEmpty(),
                                    "Expected loot in the chest at " + chest + ".");
                        }
                        assertEquals(
                                GameType.SURVIVAL,
                                players.get(0).gameMode.getGameModeForPlayer(),
                                "game mode");
                        context.succeed();
                    } finally {
                        MatchManager.stop(match);
                        resetSettings(server);
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void voidDeathEliminatesAndLastStandingWins(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, 0);
        List<ServerPlayer> players = players(context, 3);
        Match match = open(context, "mesa", "ffa");
        for (ServerPlayer player : players) {
            MatchManager.join(player, match, 0);
        }
        match.start();
        context.runAfterDelay(
                3,
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    ServerPlayer faller = players.get(0);
                    double voidY = match.arena().voidY();
                    faller.teleportTo(faller.getX(), voidY - 2, faller.getZ());
                    context.runAfterDelay(
                            2,
                            () -> {
                                assertTrue(
                                        !match.isAlive(faller.getUUID()),
                                        "Expected the player below the void to be eliminated.");
                                assertEquals(
                                        GameType.SPECTATOR,
                                        faller.gameMode.getGameModeForPlayer(),
                                        "eliminated player's game mode");
                                assertEquals(
                                        MatchPhase.ACTIVE,
                                        match.phase(),
                                        "phase with two players left");
                                ServerPlayer loser = players.get(1);
                                loser.teleportTo(loser.getX(), voidY - 2, loser.getZ());
                                context.runAfterDelay(
                                        3,
                                        () -> {
                                            try {
                                                assertEquals(
                                                        MatchPhase.ENDED,
                                                        match.phase(),
                                                        "phase with one player left");
                                                assertEquals(
                                                        List.of(
                                                                match.teamOf(
                                                                                players.get(2)
                                                                                        .getUUID())
                                                                        .orElseThrow()),
                                                        match.winners(),
                                                        "winners");
                                                context.succeed();
                                            } finally {
                                                MatchManager.stop(match);
                                                resetSettings(server);
                                            }
                                        });
                            });
                });
    }

    @GameTest(maxTicks = 120)
    public void refillFillsAnEmptiedChest(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, 0);
        SettingsStorage.set(server, SKYWARS, SkyWarsGame.FIRST_REFILL, 2);
        SettingsStorage.set(server, SKYWARS, SkyWarsGame.SECOND_REFILL, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, "frostbite", "1v1");
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.runAfterDelay(
                3,
                () -> {
                    MapArena arena = (MapArena) match.arena();
                    List<BlockPos> chests = SKYWARS.chests(arena);
                    for (BlockPos chest : chests) {
                        container(arena, chest).clearContent();
                    }
                    context.runAfterDelay(
                            20,
                            () ->
                                    assertTrue(
                                            container(arena, chests.getFirst()).isEmpty(),
                                            "Expected no refill before first_refill_seconds."));
                    context.runAfterDelay(
                            45,
                            () -> {
                                try {
                                    for (BlockPos chest : chests) {
                                        assertTrue(
                                                !container(arena, chest).isEmpty(),
                                                "Expected the refill to fill the chest at "
                                                        + chest
                                                        + ".");
                                    }
                                    context.succeed();
                                } finally {
                                    MatchManager.stop(match);
                                    resetSettings(server);
                                }
                            });
                });
    }

    @GameTest
    public void layoutsBeyondTheMapsAreRefused(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        try {
            Match match =
                    MatchManager.open(
                            server,
                            SKYWARS,
                            TeamLayout.parse("1v1v1v1v1v1v1v1v1").orElseThrow(),
                            null,
                            (unused, settings) ->
                                    SKYWARS.prepare(
                                            MapArena.open(
                                                    context.getLevel(), map(server, "mesa"))));
            MatchManager.stop(match);
            throw failure("Expected nine teams on a four-island map to be refused.");
        } catch (MatchException expected) {
            assertTrue(!expected.getMessage().isEmpty(), "Expected a reason.");
        }
        context.succeed();
    }

    private static Match open(GameTestHelper context, String map, String layout)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        return MatchManager.open(
                server,
                SKYWARS,
                TeamLayout.parse(layout).orElseThrow(),
                null,
                (unused, settings) ->
                        SKYWARS.prepare(MapArena.open(context.getLevel(), map(server, map))));
    }

    private static Identifier map(MinecraftServer server, String name) {
        return MapArena.maps(server, SkyWarsGame.ID).stream()
                .filter(id -> id.getPath().endsWith("/" + name))
                .findFirst()
                .orElseThrow(() -> failure("Missing SkyWars map " + name + "."));
    }

    private static Container container(MapArena arena, BlockPos pos) {
        if (arena.level().getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw failure("Expected a chest at " + pos + ".");
    }

    private static List<ServerPlayer> players(GameTestHelper context, int count) {
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ServerPlayer player = context.makeMockServerPlayerInLevel();
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            players.add(player);
        }
        return players;
    }

    private static void setCountdown(MinecraftServer server, int seconds) {
        SettingsStorage.set(
                server,
                SKYWARS,
                SKYWARS.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(),
                seconds);
    }

    private static void resetSettings(MinecraftServer server) {
        for (GameSetting setting : SKYWARS.settings()) {
            SettingsStorage.reset(server, SKYWARS, setting);
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw failure(message);
        }
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
