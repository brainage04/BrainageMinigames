package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.race.IceBoatRacingGame;
import io.github.brainage04.brainage_minigames.game.race.RaceGame;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

/** Parkour and Ice Boat Racing on their bundled maps, pasted into the test level. */
public final class RaceGameTest {
    /** Racers are lifted this far above the course between moves, clear of every gate. */
    private static final double ABOVE = 300.0;

    @GameTest(maxTicks = 100)
    public void everyMapHasAnOrderedCourseAndStartSlots(GameTestHelper context)
            throws MatchException {
        ServerLevel level = context.getLevel();
        for (Minigame game : List.<Minigame>of(Minigames.PARKOUR, Minigames.ICE_BOAT_RACING)) {
            List<Identifier> maps = MapArena.maps(level.getServer(), game.id());
            assertTrue(maps.size() >= 2, "Expected at least two " + game.id() + " maps.");
            for (Identifier id : maps) {
                MapArena arena = MapArena.open(level, id);
                try {
                    RaceGame.Course course = RaceGame.Course.of(arena);
                    assertTrue(!course.checkpoints().isEmpty(), id + " has no checkpoints.");
                    assertTrue(arena.teamSlots() >= 8, id + " has fewer than eight start slots.");
                    for (int team = 1; team <= arena.teamSlots(); team++) {
                        Vec3 spawn = arena.spawnsOf(team).getFirst().position();
                        assertTrue(
                                !level.getBlockState(BlockPos.containing(spawn).below()).isAir(),
                                id + " spawn " + team + " is not on the ground.");
                        if (game == Minigames.ICE_BOAT_RACING) {
                            assertTrue(
                                    arena.point("boat_" + team).isPresent(),
                                    id + " has no boat_" + team + ".");
                        }
                    }
                    List<RaceGame.Gate> gates = new ArrayList<>(course.checkpoints());
                    gates.add(course.finish());
                    for (RaceGame.Gate gate : gates) {
                        assertTrue(
                                !level.getBlockState(BlockPos.containing(gate.respawn()).below())
                                        .isAir(),
                                id + " puts racers back in mid-air at " + gate.respawn() + ".");
                    }
                } finally {
                    arena.close();
                }
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 200)
    public void parkourCheckpointsCountInOrderFallsReturnAndFinishWins(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        RaceGame parkour = Minigames.PARKOUR;
        setCountdown(server, parkour, 0);
        ServerPlayer runner = player(context);
        ServerPlayer rival = player(context);
        Match match = open(context, parkour, "canopy");
        MatchManager.join(runner, match, 1);
        MatchManager.join(rival, match, 2);
        RaceGame.Course course = RaceGame.Course.of((MapArena) match.arena());
        assertTrue(course.checkpoints().size() >= 2, "Expected two checkpoints.");
        Vec3 firstRespawn = course.checkpoints().get(0).respawn();
        Steps steps =
                new Steps(
                        context,
                        () -> {
                            MatchManager.stop(match);
                            setCountdown(server, parkour, -1);
                        });
        steps.then(
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    assertEquals(
                            Team.CollisionRule.NEVER,
                            match.teamOf(runner.getUUID())
                                    .orElseThrow()
                                    .scoreboardTeam()
                                    .getCollisionRule(),
                            "collision rule");
                    float health = rival.getHealth();
                    rival.hurtServer(
                            rival.level(), rival.damageSources().playerAttack(runner), 4.0F);
                    assertEquals(health, rival.getHealth(), "health after a punch");
                });
        // The second checkpoint and the finish do not count before the first checkpoint.
        steps.moveInto(runner, () -> course.checkpoints().get(1));
        steps.then(() -> assertProgress(parkour, match, runner, 0, 0));
        steps.moveInto(runner, course::finish);
        steps.then(() -> assertProgress(parkour, match, runner, 0, 0));
        steps.moveInto(runner, () -> course.checkpoints().get(0));
        steps.then(
                () -> {
                    assertProgress(parkour, match, runner, 0, 1);
                    assertEquals(1, match.teamOf(runner.getUUID()).orElseThrow().score(), "score");
                    // Dropping more than five blocks below the checkpoint sends the runner back.
                    runner.teleportTo(
                            firstRespawn.x() + 2.0, firstRespawn.y() - 7.0, firstRespawn.z());
                });
        steps.then(
                () -> {
                    assertNear(firstRespawn, runner.position(), "runner after a fall");
                    // So does the void, through a death that respawns instead of eliminating.
                    runner.teleportTo(runner.getX(), match.arena().voidY() - 5.0, runner.getZ());
                });
        steps.then(
                () -> {
                    assertNear(firstRespawn, runner.position(), "runner after the void");
                    assertTrue(
                            match.isAlive(runner.getUUID()), "Expected the runner still racing.");
                    assertEquals(
                            GameType.ADVENTURE,
                            runner.gameMode.getGameModeForPlayer(),
                            "game mode");
                    assertProgress(parkour, match, runner, 0, 1);
                });
        for (int index = 1; index < course.checkpoints().size(); index++) {
            int checkpoint = index;
            steps.moveInto(runner, () -> course.checkpoints().get(checkpoint));
        }
        steps.then(() -> assertProgress(parkour, match, runner, 0, course.checkpoints().size()));
        steps.moveInto(runner, course::finish);
        steps.then(
                () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase after the finish");
                    assertEquals(
                            List.of(match.teamOf(runner.getUUID()).orElseThrow()),
                            match.winners(),
                            "winners");
                });
        steps.run();
    }

    @GameTest(maxTicks = 400)
    public void boatRaceCountsLapsReplacesLostBoatsAndCleansUp(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        RaceGame race = Minigames.ICE_BOAT_RACING;
        setCountdown(server, race, 0);
        SettingsStorage.set(server, race, IceBoatRacingGame.LAPS, 2);
        ServerPlayer driver = player(context);
        ServerPlayer rival = player(context);
        Vec3 home = context.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        for (ServerPlayer player : List.of(driver, rival)) {
            player.snapTo(home.x(), home.y(), home.z(), 0.0F, 0.0F);
            player.setGameMode(GameType.CREATIVE);
        }
        Match match = open(context, race, "frostbite_oval");
        MatchManager.join(driver, match, 1);
        MatchManager.join(rival, match, 2);
        MapArena arena = (MapArena) match.arena();
        AABB area = AABB.of(arena.bounds()).inflate(4.0);
        RaceGame.Course course = RaceGame.Course.of(arena);
        Entity[] firstBoat = new Entity[1];
        Steps steps =
                new Steps(
                        context,
                        () -> {
                            MatchManager.stop(match);
                            setCountdown(server, race, -1);
                            SettingsStorage.reset(server, race, IceBoatRacingGame.LAPS);
                        });
        steps.then(
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    for (ServerPlayer player : List.of(driver, rival)) {
                        assertTrue(
                                player.getVehicle() instanceof Boat,
                                "Expected every racer in a boat at the start.");
                    }
                    assertNear(
                            arena.point("boat_1").orElseThrow().position(),
                            driver.getVehicle().position(),
                            "first grid slot");
                    firstBoat[0] = driver.getVehicle();
                });
        // Skipping ahead to a later gate counts for nothing.
        steps.moveInto(driver, () -> course.checkpoints().get(1));
        steps.then(() -> assertProgress(race, match, driver, 0, 0));
        for (int lap = 0; lap < 2; lap++) {
            for (int index = 0; index < course.checkpoints().size(); index++) {
                int checkpoint = index;
                steps.moveInto(driver, () -> course.checkpoints().get(checkpoint));
            }
            if (lap == 0) {
                steps.moveInto(driver, course::finish);
                steps.then(
                        () -> {
                            assertProgress(race, match, driver, 1, 0);
                            // Getting out of the boat puts the driver in a new one at the line.
                            driver.stopRiding();
                        });
                steps.then(
                        () -> {
                            assertTrue(
                                    driver.getVehicle() instanceof Boat
                                            && driver.getVehicle() != firstBoat[0],
                                    "Expected a new boat.");
                            assertTrue(firstBoat[0].isRemoved(), "Expected the old boat removed.");
                            assertNear(
                                    course.finish().respawn(),
                                    driver.getVehicle().position(),
                                    "new boat");
                            assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after one lap");
                        });
            }
        }
        steps.moveInto(driver, course::finish);
        steps.then(
                () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase after the last lap");
                    assertEquals(
                            List.of(match.teamOf(driver.getUUID()).orElseThrow()),
                            match.winners(),
                            "winners");
                });
        steps.after(
                110,
                () -> {
                    assertTrue(
                            MatchManager.get(match.id()).isEmpty(), "Expected the match closed.");
                    for (ServerPlayer player : List.of(driver, rival)) {
                        assertTrue(!player.isPassenger(), "Expected racers out of their boats.");
                        assertEquals(
                                GameType.CREATIVE,
                                player.gameMode.getGameModeForPlayer(),
                                "restored game mode");
                        assertNear(home, player.position(), "restored position");
                    }
                    assertTrue(
                            arena.level().getEntitiesOfClass(Boat.class, area).isEmpty(),
                            "Expected no boats left behind.");
                });
        steps.run();
    }

    // Test plumbing.

    /** Steps that each run a few ticks after the previous one; the cleanup always runs. */
    private static final class Steps {
        private final GameTestHelper context;
        private final Runnable cleanup;
        private final List<Runnable> actions = new ArrayList<>();
        private final List<Integer> delays = new ArrayList<>();

        Steps(GameTestHelper context, Runnable cleanup) {
            this.context = context;
            this.cleanup = cleanup;
        }

        void then(Runnable action) {
            after(3, action);
        }

        void after(int ticks, Runnable action) {
            actions.add(action);
            delays.add(ticks);
        }

        /** Lifts the entity (or its boat) clear of the course, over the gate, then into it. */
        void moveInto(ServerPlayer player, java.util.function.Supplier<RaceGame.Gate> gate) {
            then(() -> teleport(player, player.getX(), ABOVE, player.getZ()));
            then(
                    () -> {
                        Vec3 target = inside(gate.get());
                        teleport(player, target.x(), ABOVE, target.z());
                    });
            then(() -> teleport(player, inside(gate.get())));
        }

        void run() {
            runFrom(0);
        }

        private void runFrom(int index) {
            if (index >= actions.size()) {
                cleanup.run();
                context.succeed();
                return;
            }
            context.runAfterDelay(
                    delays.get(index),
                    () -> {
                        try {
                            actions.get(index).run();
                        } catch (RuntimeException | Error failure) {
                            cleanup.run();
                            throw failure;
                        }
                        runFrom(index + 1);
                    });
        }
    }

    private static Vec3 inside(RaceGame.Gate gate) {
        AABB box = gate.box();
        return new Vec3(box.getCenter().x(), box.minY + 0.5, box.getCenter().z());
    }

    private static void teleport(ServerPlayer player, Vec3 target) {
        teleport(player, target.x(), target.y(), target.z());
    }

    /** Moves the player, or the boat they sit in. */
    private static void teleport(ServerPlayer player, double x, double y, double z) {
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicle.snapTo(x, y, z);
        } else {
            player.teleportTo(x, y, z);
        }
    }

    private static void assertProgress(
            RaceGame game, Match match, ServerPlayer player, int laps, int checkpoints) {
        RaceGame.Progress progress =
                game.progress(match, player.getUUID())
                        .orElseThrow(() -> failure("Expected the player to be racing."));
        assertEquals(laps, progress.laps(), "laps completed");
        assertEquals(checkpoints, progress.checkpoints(), "checkpoints passed");
    }

    private static Match open(GameTestHelper context, Minigame game, String map)
            throws MatchException {
        Identifier id = BrainageMinigames.id("maps/" + game.id() + "/" + map);
        return MatchManager.open(
                context.getLevel().getServer(),
                game,
                TeamLayout.parse("1v1").orElseThrow(),
                null,
                (server, settings) -> MapArena.open(context.getLevel(), id));
    }

    /** Sets the countdown; a negative value resets it. */
    private static void setCountdown(MinecraftServer server, Minigame game, int seconds) {
        GameSetting countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        if (seconds < 0) {
            SettingsStorage.reset(server, game, countdown);
        } else {
            SettingsStorage.set(server, game, countdown, seconds);
        }
    }

    private static ServerPlayer player(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceToSqr(actual) > 1.0) {
            throw failure(
                    "Expected " + description + " at " + expected + ", found " + actual + ".");
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
