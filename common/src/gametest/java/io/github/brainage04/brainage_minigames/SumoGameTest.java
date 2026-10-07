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
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.sumo.SumoGame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/** Sumo on its bundled maps, pasted into the test level. */
public final class SumoGameTest {
    private static final SumoGame SUMO = Minigames.SUMO;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    /** How far from the platform anything a knocked-off player could land on must be. */
    private static final double REACH = 6.0;

    /**
     * Every map's spawns stand on one platform whose surface is a block above the void height, so a
     * player is out once a block below it, and nothing outside the platform from level with the void
     * height to three blocks above the surface is within reach of it, so a knocked-off player lands
     * nowhere they could stay in. The lobby, where players who are out watch, stands clear above.
     */
    public void everyMapKeepsItsPlatformClear(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, SumoGame.ID);
        assertTrue(maps.size() >= 3, "Expected three Sumo maps, found " + maps + ".");
        int mostTeams = 0;
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                ServerLevel level = arena.level();
                mostTeams = Math.max(mostTeams, arena.teamSlots());
                assertTrue(arena.teamSlots() >= 2, map + " needs spawns for two teams.");
                Vec3 first = arena.spawnsOf(1).getFirst().position();
                int surface = BlockPos.containing(first).getY() - 1;
                assertTrue(
                        arena.voidY() == surface,
                        map + " needs its void at the platform's surface, y " + surface + ", not " + arena.voidY() + ".");
                Set<Long> footprint = platform(level, BlockPos.containing(first).below());
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                        BlockPos feet = BlockPos.containing(spawn.position());
                        assertStandable(level, feet, map + " spawn " + team);
                        assertTrue(
                                feet.getY() == surface + 1
                                        && footprint.contains(BlockPos.asLong(feet.getX(), 0, feet.getZ())),
                                map + " spawn " + team + " is not on the platform with spawn 1.");
                    }
                }
                BlockPos lobby = BlockPos.containing(arena.lobbyPosition());
                assertStandable(level, lobby, map + " lobby");
                BoundingBox bounds = arena.bounds();
                for (BlockPos pos : BlockPos.betweenClosed(
                        bounds.minX(), surface - 1, bounds.minZ(), bounds.maxX(), surface + 3, bounds.maxZ())) {
                    if (footprint.contains(BlockPos.asLong(pos.getX(), 0, pos.getZ()))
                            || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                        continue;
                    }
                    double nearest = Double.MAX_VALUE;
                    for (long column : footprint) {
                        nearest = Math.min(nearest, Math.hypot(
                                pos.getX() - BlockPos.getX(column), pos.getZ() - BlockPos.getZ(column)));
                    }
                    assertTrue(nearest >= REACH, map + ": " + level.getBlockState(pos) + " at " + pos
                            + " is " + nearest + " blocks from the platform; a knocked-off player could land on it.");
                }
            } finally {
                arena.close();
            }
        }
        assertTrue(mostTeams >= 4, "Expected a Sumo map for four teams.");
        context.succeed();
    }

    /** The columns of the platform's surface layer reachable from {@code start}, as x/z longs. */
    private static Set<Long> platform(ServerLevel level, BlockPos start) {
        Set<Long> columns = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>(List.of(start));
        while (!open.isEmpty()) {
            BlockPos pos = open.poll();
            if (!seen.add(pos) || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
            columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
            for (Direction direction : Direction.Plane.HORIZONTAL) open.add(pos.relative(direction));
        }
        return columns;
    }

    /**
     * A hit only knocks back. Falling off gives the round to the opponent; the next round puts both
     * back on their spawns, frozen and unhittable through the countdown, and the first to the target
     * wins.
     */
    public void knockingOffWinsTheRoundAndTheNextStartsFrozen(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        SettingsStorage.set(server, SUMO, SUMO.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, SUMO, SumoGame.ROUNDS_TO_WIN, 2);
        SettingsStorage.set(server, SUMO, SumoGame.ROUND_COUNTDOWN_SECONDS, 1);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "dohyo", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer knocker = players.get(0);
        TestPlayers.ChatPlayer victim = players.get(1);
        MatchManager.join(knocker, match, 1);
        MatchManager.join(victim, match, 2);
        MapArena arena = (MapArena) match.arena();
        context.runAfterDelay(3, () -> step(context, match, players, () -> {
            assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
            for (ServerPlayer player : players) {
                assertEquals(GameType.ADVENTURE, player.gameMode.getGameModeForPlayer(), "game mode");
                assertTrue(player.getInventory().isEmpty(), "Sumo players start empty-handed.");
            }
            victim.teleportTo(knocker.getX(), knocker.getY(), knocker.getZ() + 1.5);
            float health = victim.getHealth();
            knocker.attack(victim);
            assertEquals(health, victim.getHealth(), "victim's health after a hit");
            // The hit landed, so the client was sent its knockback (the server keeps no player motion).
            assertTrue(victim.getLastHurtByMob() == knocker && victim.invulnerableTime > 0, "The hit must land.");
            victim.teleportTo(victim.getX(), arena.voidY() - 0.5, victim.getZ());
        }, () -> {
            assertEquals(1, team(match, knocker).score(), "knocker's rounds");
            assertEquals(0, team(match, victim).score(), "victim's rounds");
            assertTrue(victim.messages.stream().anyMatch(message -> message.getString().contains(
                            victim.getScoreboardName() + " was knocked off by " + knocker.getScoreboardName())),
                    "Expected the knock-off message, found " + victim.messages + ".");
            assertEquals(2, SUMO.round(match), "round");
            assertTrue(SUMO.isCountingDown(match), "The next round must start with a countdown.");
            assertNear(arena.spawnsOf(1).getFirst().position(), knocker.position(), "knocker in round 2");
            assertNear(arena.spawnsOf(2).getFirst().position(), victim.position(), "victim in round 2");
            assertEquals(GameType.ADVENTURE, victim.gameMode.getGameModeForPlayer(), "victim's game mode");
            assertTrue(knocker.hasEffect(MobEffects.SLOWNESS), "Players are frozen through the countdown.");
            assertTrue(!SUMO.allowDamage(match, victim, victim.damageSources().playerAttack(knocker)),
                    "Hits must not land during the countdown.");
        }, () -> {
            assertTrue(!SUMO.isCountingDown(match), "The countdown must be over after a second.");
            assertTrue(!knocker.hasEffect(MobEffects.SLOWNESS), "Players are released after the countdown.");
            assertTrue(SUMO.allowDamage(match, victim, victim.damageSources().playerAttack(knocker)),
                    "Hits land once the round is on.");
            // Falling off unhit still gives the round away.
            victim.teleportTo(victim.getX(), arena.voidY() - 0.5, victim.getZ());
        }, () -> {
            assertEquals(MatchPhase.ENDED, match.phase(), "phase at the target");
            assertEquals(List.of(team(match, knocker)), match.winners(), "winners");
            context.succeed();
        }));
    }

    /**
     * In a team match a knocked-off player watches in spectator mode, free to fly below the void
     * height, until the last opponent falls; then everyone is back for the next round.
     */
    public void aKnockedOffTeammateWatchesUntilTheRoundEnds(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        SettingsStorage.set(server, SUMO, SUMO.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        List<TestPlayers.ChatPlayer> players = players(context, 4);
        Match match = open(context, "lotus", "2v2");
        resetSettings(server);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 1);
        MatchManager.join(players.get(2), match, 2);
        MatchManager.join(players.get(3), match, 2);
        ServerPlayer first = players.get(2);
        ServerPlayer second = players.get(3);
        MapArena arena = (MapArena) match.arena();
        context.runAfterDelay(3, () -> step(context, match, players, () -> {
            assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
            first.teleportTo(first.getX(), arena.voidY() - 0.5, first.getZ());
        }, () -> {
            assertEquals(GameType.SPECTATOR, first.gameMode.getGameModeForPlayer(), "a knocked-off player's game mode");
            assertTrue(match.isAlive(first.getUUID()) && SUMO.isOut(match, first.getUUID()),
                    "A knocked-off player stays in the match, out of the round.");
            assertEquals(0, team(match, players.get(0)).score(), "rounds with an opponent still on");
            assertEquals(1, SUMO.round(match), "round");
            first.teleportTo(first.getX(), arena.voidY() - 5.0, first.getZ());
        }, () -> {
            assertTrue(first.getY() < arena.voidY() - 4.0,
                    "A player watching flies freely below the void height, found " + first.position() + ".");
            assertEquals(GameType.SPECTATOR, first.gameMode.getGameModeForPlayer(), "a watching player's game mode");
            second.teleportTo(second.getX(), arena.voidY() - 0.5, second.getZ());
        }, () -> {
            assertEquals(1, team(match, players.get(0)).score(), "rounds once both opponents fell");
            assertEquals(2, SUMO.round(match), "round");
            for (ServerPlayer player : players) {
                assertEquals(GameType.ADVENTURE, player.gameMode.getGameModeForPlayer(),
                        player.getScoreboardName() + "'s game mode in round 2");
                assertTrue(!SUMO.isOut(match, player.getUUID()), "Everyone is back for the next round.");
            }
            assertNear(arena.spawnsOf(2).getFirst().position(), first.position(), "the watcher in round 2");
            context.succeed();
        }));
    }

    /**
     * Runs {@code steps} two ticks apart, or after a round's countdown of a second, stopping the
     * match and removing the players after the last one or after a failure.
     */
    private static void step(GameTestHelper context, Match match, List<? extends ServerPlayer> players, Runnable... steps) {
        step(context, match, players, steps, 0);
    }

    private static void step(
            GameTestHelper context, Match match, List<? extends ServerPlayer> players, Runnable[] steps, int index) {
        boolean more = false;
        try {
            steps[index].run();
            if (index + 1 < steps.length) {
                int delay = SUMO.isCountingDown(match) ? 25 : 2;
                context.runAfterDelay(delay, () -> step(context, match, players, steps, index + 1));
                more = true;
            }
        } finally {
            if (!more) finish(match, players);
        }
    }

    private static Match open(GameTestHelper context, String map, String layout) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id = MapArena.maps(server, SumoGame.ID).stream()
                .filter(candidate -> candidate.getPath().endsWith("/" + map))
                .findFirst()
                .orElseThrow(() -> failure("Missing Sumo map " + map + "."));
        return MatchManager.open(server, SUMO, TeamLayout.parse(layout).orElseThrow(), null,
                (unused, settings) -> MapArena.open(context.getLevel(), id));
    }

    private static List<TestPlayers.ChatPlayer> players(GameTestHelper context, int count) {
        List<TestPlayers.ChatPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            players.add(TestPlayers.chat(context, "sumo" + NEXT_NAME.incrementAndGet()));
        }
        return players;
    }

    /** Stops the match and removes the players, as their clients disconnecting would. */
    private static void finish(Match match, List<? extends ServerPlayer> players) {
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) playerList.remove(player);
        }
    }

    /** Settings are read when a match opens, so they can be reset straight afterwards. */
    private static void resetSettings(MinecraftServer server) {
        for (GameSetting setting : SUMO.settings()) SettingsStorage.reset(server, SUMO, setting);
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID())
                .orElseThrow(() -> failure("Expected " + player.getScoreboardName() + " on a team."));
    }

    private static void assertStandable(ServerLevel level, BlockPos feet, String what) {
        assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(),
                what + " is inside a block.");
        assertTrue(!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty(),
                what + " has nothing to stand on.");
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceTo(actual) > 0.5) {
            throw failure("Expected " + description + " at " + expected + ", found " + actual + ".");
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
