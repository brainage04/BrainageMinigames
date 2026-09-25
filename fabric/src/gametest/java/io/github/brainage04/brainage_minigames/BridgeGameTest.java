package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bridge.BridgeGame;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Bridge and Battle Rush on their bundled maps, pasted into the test level. */
public final class BridgeGameTest {
    private static final BridgeGame BRIDGE = Minigames.BRIDGE;
    private static final BridgeGame BATTLE_RUSH = Minigames.BATTLE_RUSH;

    @GameTest(maxTicks = 200)
    public void everyMapHasGoalsCagesAndBuildLimits(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        for (BridgeGame game : List.of(BRIDGE, BATTLE_RUSH)) {
            List<Identifier> maps = MapArena.maps(server, game.id());
            assertTrue(!maps.isEmpty(), "Expected " + game.id() + " maps.");
            for (Identifier map : maps) {
                MapArena arena = MapArena.open(context.getLevel(), map);
                try {
                    assertTrue(arena.teamSlots() >= 2, map + " needs at least two teams.");
                    assertTrue(!arena.regions("build").isEmpty(), map + " has no build region.");
                    for (int team = 1; team <= arena.teamSlots(); team++) {
                        MapArena.Region goal =
                                arena.region("goal_" + team)
                                        .orElseThrow(() -> failure(map + " lacks a goal."));
                        MapArena.Region cage =
                                arena.region("cage_" + team)
                                        .orElseThrow(() -> failure(map + " lacks a cage."));
                        Vec3 spawn = arena.spawnsOf(team).getFirst().position();
                        assertTrue(cage.contains(spawn), map + ": spawn outside its cage.");
                        assertTrue(
                                !arena.level()
                                        .getBlockState(BlockPos.containing(spawn).below())
                                        .isAir(),
                                map + ": the cage needs a floor.");
                        assertTrue(
                                arena.voidY() < goal.box().minY
                                        && arena.voidY() > arena.bounds().minY() - 1,
                                map + " needs a void marker below its islands.");
                    }
                } finally {
                    arena.close();
                }
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 200)
    public void scoringInTheEnemyGoalScoresAndStartsANewRound(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, BRIDGE, 5, 1);
        List<ServerPlayer> players = players(context, 2);
        Match match = start(context, BRIDGE, "grove", players);
        context.runAfterDelay(
                3,
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    ServerPlayer red = players.get(0);
                    MapArena arena = (MapArena) match.arena();
                    assertTrue(
                            red.getInventory()
                                    .contains(stack -> stack.is(Items.DYED_TERRACOTTA.red())),
                            "Expected the red team's clay to be red.");
                    assertTrue(
                            !red.getInventory()
                                    .contains(stack -> stack.is(Items.DYED_TERRACOTTA.white())),
                            "Expected no uncoloured clay.");
                    BlockPos placed = place(match, red, middle(arena).above());
                    teleport(red, arena.region("goal_2").orElseThrow().box().getCenter());
                    context.runAfterDelay(
                            2,
                            () -> {
                                try {
                                    assertEquals(1, team(match, red).score(), "red goals");
                                    assertEquals(
                                            0, team(match, players.get(1)).score(), "blue goals");
                                    assertTrue(BRIDGE.isCaged(match), "Expected a cage countdown.");
                                    assertTrue(
                                            arena.level().getBlockState(placed).isAir()
                                                    && !match.isPlacedBlock(placed),
                                            "Expected the placed block to be cleared.");
                                    for (int team = 1; team <= 2; team++) {
                                        ServerPlayer player = players.get(team - 1);
                                        assertTrue(
                                                arena.region("cage_" + team)
                                                        .orElseThrow()
                                                        .contains(player.position()),
                                                "Expected player " + team + " in their cage.");
                                    }
                                    assertTrue(
                                            !MatchManager.allowPlace(
                                                    red, middle(arena).above(), clay()),
                                            "Expected no building while caged.");
                                } catch (RuntimeException exception) {
                                    MatchManager.stop(match);
                                    resetSettings(server);
                                    throw exception;
                                }
                                context.runAfterDelay(
                                        25,
                                        () -> {
                                            try {
                                                assertTrue(
                                                        !BRIDGE.isCaged(match),
                                                        "Expected the cages to open.");
                                                BlockPos spawn =
                                                        BlockPos.containing(
                                                                arena.spawnsOf(1)
                                                                        .getFirst()
                                                                        .position());
                                                assertTrue(
                                                        arena.level()
                                                                .getBlockState(spawn.below())
                                                                .isAir(),
                                                        "Expected the cage floor removed.");
                                                context.succeed();
                                            } finally {
                                                MatchManager.stop(match);
                                                resetSettings(server);
                                            }
                                        });
                            });
                });
    }

    @GameTest(maxTicks = 100)
    public void ownGoalScoresNothingAndKeepsTheRound(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, BRIDGE, 5, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = start(context, BRIDGE, "grove", players);
        context.runAfterDelay(
                3,
                () -> {
                    ServerPlayer red = players.get(0);
                    MapArena arena = (MapArena) match.arena();
                    BlockPos placed = place(match, red, middle(arena).above());
                    Vec3 ownGoal = arena.region("goal_1").orElseThrow().box().getCenter();
                    teleport(red, ownGoal);
                    context.runAfterDelay(
                            2,
                            () -> {
                                try {
                                    assertEquals(0, team(match, red).score(), "red goals");
                                    assertEquals(
                                            0, team(match, players.get(1)).score(), "blue goals");
                                    assertTrue(
                                            match.isPlacedBlock(placed)
                                                    && !arena.level().getBlockState(placed).isAir(),
                                            "Expected the round to go on with the placed block.");
                                    assertTrue(
                                            red.position().distanceTo(ownGoal) > 5,
                                            "Expected the player sent back out of their own goal.");
                                    context.succeed();
                                } finally {
                                    MatchManager.stop(match);
                                    resetSettings(server);
                                }
                            });
                });
    }

    @GameTest(maxTicks = 100)
    public void voidDeathRespawnsWithoutEliminating(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, BATTLE_RUSH, 3, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = start(context, BATTLE_RUSH, "lilypond", players);
        context.runAfterDelay(
                3,
                () -> {
                    ServerPlayer faller = players.get(1);
                    faller.getInventory().clearContent();
                    teleport(
                            faller,
                            new Vec3(faller.getX(), match.arena().voidY() - 2, faller.getZ()));
                    context.runAfterDelay(
                            2,
                            () -> {
                                try {
                                    assertTrue(
                                            match.isAlive(faller.getUUID()),
                                            "Expected no elimination.");
                                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                                    assertEquals(
                                            GameType.SURVIVAL,
                                            faller.gameMode.getGameModeForPlayer(),
                                            "game mode after respawning");
                                    MapArena arena = (MapArena) match.arena();
                                    assertTrue(
                                            faller.position()
                                                            .distanceTo(
                                                                    arena.spawnsOf(2)
                                                                            .getFirst()
                                                                            .position())
                                                    < 4,
                                            "Expected a respawn at the team's spawn.");
                                    assertTrue(
                                            faller.getInventory()
                                                    .contains(stack -> stack.is(Items.WOOL.blue())),
                                            "Expected the kit back in team colour.");
                                    context.succeed();
                                } finally {
                                    MatchManager.stop(match);
                                    resetSettings(server);
                                }
                            });
                });
    }

    @GameTest(maxTicks = 100)
    public void onlyPlacedBlocksBreakAndGoalsStayOpen(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, BRIDGE, 5, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = start(context, BRIDGE, "grove", players);
        context.runAfterDelay(
                3,
                () -> {
                    try {
                        ServerPlayer red = players.get(0);
                        MapArena arena = (MapArena) match.arena();
                        BlockPos bridge = middle(arena);
                        BlockState mapBlock = arena.level().getBlockState(bridge);
                        assertTrue(!mapBlock.isAir(), "Expected the bridge in the middle.");
                        assertTrue(
                                !red.gameMode.destroyBlock(bridge),
                                "Expected a map block to hold.");
                        assertEquals(mapBlock, arena.level().getBlockState(bridge), "bridge block");

                        BlockPos placed = place(match, red, bridge.above());
                        assertTrue(
                                red.gameMode.destroyBlock(placed),
                                "Expected a placed block to break.");
                        assertTrue(
                                arena.level().getBlockState(placed).isAir(), "Expected it gone.");
                        assertTrue(!match.isPlacedBlock(placed), "Expected it forgotten.");

                        BlockPos goal =
                                BlockPos.containing(
                                        arena.region("goal_2").orElseThrow().box().getCenter());
                        assertTrue(
                                !MatchManager.allowPlace(red, goal, clay()),
                                "Expected no blocks in a goal.");
                        BlockPos outside = bridge.above(20);
                        assertTrue(
                                !MatchManager.allowPlace(red, outside, clay()),
                                "Expected no blocks above the build limit.");
                        context.succeed();
                    } finally {
                        MatchManager.stop(match);
                        resetSettings(server);
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void firstTeamToTheTargetWins(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, BATTLE_RUSH, 2, 0);
        List<ServerPlayer> players = players(context, 2);
        Match match = start(context, BATTLE_RUSH, "driftwood", players);
        ServerPlayer blue = players.get(1);
        MapArena arena = (MapArena) match.arena();
        Vec3 redGoal = arena.region("goal_1").orElseThrow().box().getCenter();
        context.runAfterDelay(3, () -> teleport(blue, redGoal));
        context.runAfterDelay(
                6,
                () -> {
                    assertEquals(1, team(match, blue).score(), "blue goals after one");
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after one goal");
                    teleport(blue, redGoal);
                });
        context.runAfterDelay(
                9,
                () -> {
                    try {
                        assertEquals(MatchPhase.ENDED, match.phase(), "phase after two goals");
                        assertEquals(List.of(team(match, blue)), match.winners(), "winners");
                        context.succeed();
                    } finally {
                        MatchManager.stop(match);
                        resetSettings(server);
                    }
                });
    }

    /** Opens a 1v1 on {@code map}, puts the players on teams 1 and 2 and starts it. */
    private static Match start(
            GameTestHelper context, BridgeGame game, String map, List<ServerPlayer> players)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id =
                MapArena.maps(server, game.id()).stream()
                        .filter(candidate -> candidate.getPath().endsWith("/" + map))
                        .findFirst()
                        .orElseThrow(() -> failure("Missing " + game.id() + " map " + map + "."));
        Match match =
                MatchManager.open(
                        server,
                        game,
                        TeamLayout.parse("1v1").orElseThrow(),
                        null,
                        (unused, settings) -> MapArena.open(context.getLevel(), id));
        for (int index = 0; index < players.size(); index++) {
            MatchManager.join(players.get(index), match, index + 1);
        }
        return match;
    }

    /** The middle of the map at bridge height: a map block on every two-team map. */
    private static BlockPos middle(MapArena arena) {
        Vec3 red = arena.region("goal_1").orElseThrow().box().getCenter();
        Vec3 blue = arena.region("goal_2").orElseThrow().box().getCenter();
        BlockPos spawn = BlockPos.containing(arena.spawnsOf(1).getFirst().position());
        // Spawns stand on the cage floor three blocks above the surface.
        return BlockPos.containing((red.x + blue.x) / 2, spawn.getY() - 4, (red.z + blue.z) / 2);
    }

    /** Places clay as the player would, through the match's place rules. */
    private static BlockPos place(Match match, ServerPlayer player, BlockPos pos) {
        assertTrue(MatchManager.allowPlace(player, pos, clay()), "Expected placing at " + pos);
        match.arena().level().setBlock(pos, clay(), 3);
        MatchManager.blockPlaced(player, pos);
        assertTrue(match.isPlacedBlock(pos), "Expected the block recorded as placed.");
        return pos;
    }

    private static BlockState clay() {
        return Blocks.DYED_TERRACOTTA.red().defaultBlockState();
    }

    private static void teleport(ServerPlayer player, Vec3 position) {
        player.teleportTo(position.x, position.y, position.z);
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID()).orElseThrow(() -> failure("Expected a team."));
    }

    private static void configure(
            MinecraftServer server, BridgeGame game, int goals, int cageSeconds) {
        SettingsStorage.set(
                server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, game, game.goalsToWin(), goals);
        SettingsStorage.set(server, game, BridgeGame.CAGE_SECONDS, cageSeconds);
    }

    private static void resetSettings(MinecraftServer server) {
        for (BridgeGame game : List.of(BRIDGE, BATTLE_RUSH)) {
            for (GameSetting setting : game.settings()) {
                SettingsStorage.reset(server, game, setting);
            }
        }
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
