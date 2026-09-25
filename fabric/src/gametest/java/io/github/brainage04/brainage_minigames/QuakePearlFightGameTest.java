package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.pearlfight.PearlFightGame;
import io.github.brainage04.brainage_minigames.game.quake.QuakeGame;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Quake and Pearl Fight on their bundled maps, pasted into the test level. */
public final class QuakePearlFightGameTest {
    private static final QuakeGame QUAKE = Minigames.QUAKE;
    private static final PearlFightGame PEARL_FIGHT = Minigames.PEARL_FIGHT;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    @GameTest(maxTicks = 100)
    public void everyMapHasSpawnsRespawnsAndSolidGround(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> quakeMaps = MapArena.maps(server, QuakeGame.ID);
        assertTrue(quakeMaps.size() >= 2, "Expected two Quake maps, found " + quakeMaps + ".");
        for (Identifier map : quakeMaps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                assertTrue(arena.teamSlots() >= 8, map + " needs spawns for 8 players.");
                List<MapArena.Point> respawns = arena.points("respawn_");
                assertTrue(respawns.size() >= 16, map + " needs at least 16 respawn points.");
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                        assertStandable(arena, spawn.position(), map + " spawn " + team);
                    }
                }
                for (MapArena.Point point : respawns) {
                    assertStandable(arena, point.position(), map + " " + point.name());
                }
            } finally {
                arena.close();
            }
        }

        List<Identifier> pearlMaps = MapArena.maps(server, PearlFightGame.ID);
        assertTrue(!pearlMaps.isEmpty(), "Expected a Pearl Fight map.");
        for (Identifier map : pearlMaps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                assertTrue(arena.teamSlots() >= 2, map + " needs spawns for two teams.");
                assertTrue(arena.region("build").isPresent(), map + " needs a build region.");
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                        assertStandable(arena, spawn.position(), map + " spawn " + team);
                        assertTrue(
                                arena.voidY() < spawn.position().y() - 3.0,
                                map + " needs its void below spawn " + team + ".");
                        assertTrue(
                                arena.canBuild(BlockPos.containing(spawn.position()).above(2)),
                                map + " must allow building above spawn " + team + ".");
                    }
                }
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 100)
    public void railgunKillsThroughTheUseHookButNotThroughWalls(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setSetting(server, QUAKE, QUAKE.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        setSetting(server, QUAKE, QuakeGame.RELOAD_TICKS, 4);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, QUAKE, QuakeGame.ID, "cloister", "1v1");
        resetSettings(server, QUAKE);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerPlayer shooter = players.get(0);
        ServerPlayer target = players.get(1);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        assertTrue(
                                shooter.getMainHandItem().is(Items.WOODEN_HOE),
                                "Expected the railgun in hand, found "
                                        + shooter.getMainHandItem()
                                        + ".");
                        MapArena arena = (MapArena) match.arena();
                        ServerLevel level = arena.level();
                        Vec3 origin = faceOff(arena, shooter, target, 8.0);

                        // A wall between them stops the beam.
                        BlockPos wall = BlockPos.containing(origin.add(4.0, 0.0, 0.0));
                        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
                        use(shooter);
                        assertEquals(0, score(match, shooter), "score after shooting a wall");
                        assertTrue(
                                target.position().distanceTo(origin.add(8.0, 0.0, 0.0)) < 0.01,
                                "Expected the wall to protect the target.");
                        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());

                        waiting = true;
                        context.runAfterDelay(
                                5,
                                () -> {
                                    try {
                                        faceOff(arena, shooter, target, 8.0);
                                        Vec3 before = target.position();
                                        use(shooter);
                                        assertEquals(1, score(match, shooter), "shooter's score");
                                        assertEquals(0, score(match, target), "target's score");
                                        assertTrue(
                                                match.isAlive(target.getUUID()),
                                                "Expected the target to respawn, not be eliminated.");
                                        assertEquals(
                                                GameType.ADVENTURE,
                                                target.gameMode.getGameModeForPlayer(),
                                                "target's game mode");
                                        assertTrue(
                                                target.position().distanceTo(before) > 1.0
                                                        && arena.points("respawn_").stream()
                                                                .anyMatch(
                                                                        point ->
                                                                                point.position()
                                                                                                .distanceTo(
                                                                                                        target
                                                                                                                .position())
                                                                                        < 0.01),
                                                "Expected the target at a respawn point, found "
                                                        + target.position()
                                                        + ".");
                                        assertTrue(
                                                target.getInventory().countItem(Items.WOODEN_HOE)
                                                        == 1,
                                                "Expected the respawned target to get the kit.");
                                        context.succeed();
                                    } finally {
                                        finish(match, players);
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            finish(match, players);
                        }
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void railgunMustReloadAndFirstToTheTargetWins(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setSetting(server, QUAKE, QUAKE.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        setSetting(server, QUAKE, QuakeGame.RELOAD_TICKS, 10);
        setSetting(server, QUAKE, QuakeGame.KILLS_TO_WIN, 2);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, QUAKE, QuakeGame.ID, "foundry", "1v1");
        resetSettings(server, QUAKE);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerPlayer shooter = players.get(0);
        ServerPlayer target = players.get(1);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        MapArena arena = (MapArena) match.arena();
                        faceOff(arena, shooter, target, 6.0);
                        use(shooter);
                        assertEquals(1, score(match, shooter), "score after the first shot");

                        // Still reloading: a second shot straight away does nothing.
                        faceOff(arena, shooter, target, 6.0);
                        use(shooter);
                        assertEquals(1, score(match, shooter), "score while reloading");
                        assertTrue(
                                shooter.getCooldowns().isOnCooldown(shooter.getMainHandItem()),
                                "Expected the railgun to show its reload.");

                        waiting = true;
                        context.runAfterDelay(
                                11,
                                () -> {
                                    try {
                                        faceOff(arena, shooter, target, 6.0);
                                        use(shooter);
                                        assertEquals(
                                                2, score(match, shooter), "score after reloading");
                                        expectWinner(context, match, shooter, players);
                                    } catch (RuntimeException exception) {
                                        finish(match, players);
                                        throw exception;
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            finish(match, players);
                        }
                    }
                });
    }

    /** One tick after the winning kill the match has ended with the shooter's team as winner. */
    private static void expectWinner(
            GameTestHelper context, Match match, ServerPlayer winner, List<ServerPlayer> players) {
        context.runAfterDelay(
                1,
                () -> {
                    try {
                        assertEquals(MatchPhase.ENDED, match.phase(), "phase after the target");
                        assertEquals(
                                List.of(match.teamOf(winner.getUUID()).orElseThrow()),
                                match.winners(),
                                "winners");
                        context.succeed();
                    } finally {
                        finish(match, players);
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void knockingIntoTheVoidScoresForTheKnockerAndStartsANewRound(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setSetting(
                server,
                PEARL_FIGHT,
                PEARL_FIGHT.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(),
                0);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, PEARL_FIGHT, PearlFightGame.ID, "twin_peaks", "1v1");
        resetSettings(server, PEARL_FIGHT);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerPlayer knocker = players.get(0);
        ServerPlayer victim = players.get(1);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        assertTrue(
                                knocker.getMainHandItem().is(Items.STICK),
                                "Expected the knockback stick in hand.");
                        assertEquals(8, count(knocker, Items.ENDER_PEARL), "ender pearls");
                        Item wool = PearlFightGame.woolOf(team(match, knocker));
                        assertEquals(16, count(knocker, wool), "team-coloured wool");

                        MapArena arena = (MapArena) match.arena();
                        // A hit only knocks back: nobody takes damage.
                        victim.teleportTo(knocker.getX() + 1.5, knocker.getY(), knocker.getZ());
                        float health = victim.getHealth();
                        knocker.attack(victim);
                        assertEquals(health, victim.getHealth(), "victim's health after a hit");
                        knocker.teleportTo(knocker.getX() + 3.0, knocker.getY(), knocker.getZ());
                        victim.teleportTo(victim.getX(), arena.voidY() - 1.0, victim.getZ());

                        waiting = true;
                        context.runAfterDelay(
                                2,
                                () -> {
                                    try {
                                        assertEquals(1, score(match, knocker), "knocker's score");
                                        assertEquals(0, score(match, victim), "victim's score");
                                        assertTrue(
                                                match.isAlive(victim.getUUID()),
                                                "Expected the victim to respawn.");
                                        assertEquals(
                                                GameType.SURVIVAL,
                                                victim.gameMode.getGameModeForPlayer(),
                                                "victim's game mode");
                                        assertNear(
                                                arena.spawnsOf(2).getFirst().position(),
                                                victim.position(),
                                                "victim after the point");
                                        assertNear(
                                                arena.spawnsOf(1).getFirst().position(),
                                                knocker.position(),
                                                "knocker after the new round");
                                        assertEquals(
                                                8,
                                                count(victim, Items.ENDER_PEARL),
                                                "victim's pearls after respawning");
                                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                                        context.succeed();
                                    } finally {
                                        finish(match, players);
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            finish(match, players);
                        }
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void fallingOffScoresForTheOpponentAndFirstToTheTargetWins(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setSetting(
                server,
                PEARL_FIGHT,
                PEARL_FIGHT.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(),
                0);
        setSetting(server, PEARL_FIGHT, PearlFightGame.POINTS_TO_WIN, 2);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, PEARL_FIGHT, PearlFightGame.ID, "skyreach", "1v1");
        resetSettings(server, PEARL_FIGHT);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerPlayer winner = players.get(0);
        ServerPlayer faller = players.get(1);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        double voidY = match.arena().voidY();
                        faller.teleportTo(faller.getX(), voidY - 1.0, faller.getZ());
                        waiting = true;
                        context.runAfterDelay(
                                2,
                                () -> {
                                    boolean stillWaiting = false;
                                    try {
                                        assertEquals(
                                                1,
                                                score(match, winner),
                                                "opponent's score after a fall");
                                        assertEquals(
                                                MatchPhase.ACTIVE,
                                                match.phase(),
                                                "phase at 1 point");
                                        faller.teleportTo(
                                                faller.getX(), voidY - 1.0, faller.getZ());
                                        stillWaiting = true;
                                        context.runAfterDelay(
                                                2,
                                                () -> {
                                                    try {
                                                        assertEquals(
                                                                MatchPhase.ENDED,
                                                                match.phase(),
                                                                "phase at the target");
                                                        assertEquals(
                                                                List.of(team(match, winner)),
                                                                match.winners(),
                                                                "winners");
                                                        context.succeed();
                                                    } finally {
                                                        finish(match, players);
                                                    }
                                                });
                                    } finally {
                                        if (!stillWaiting) {
                                            finish(match, players);
                                        }
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            finish(match, players);
                        }
                    }
                });
    }

    /**
     * Puts the two players high above the map, {@code distance} blocks apart along +x, the shooter
     * looking straight at the target; returns the shooter's position.
     */
    private static Vec3 faceOff(
            MapArena arena, ServerPlayer shooter, ServerPlayer target, double distance) {
        Vec3 origin =
                new Vec3(
                        arena.bounds().getCenter().getX() + 0.5,
                        arena.bounds().maxY() + 6.0,
                        arena.bounds().getCenter().getZ() + 0.5);
        shooter.snapTo(origin.x, origin.y, origin.z, -90.0F, 0.0F);
        shooter.setYHeadRot(-90.0F);
        shooter.setDeltaMovement(Vec3.ZERO);
        target.snapTo(origin.x + distance, origin.y, origin.z, 90.0F, 0.0F);
        target.setDeltaMovement(Vec3.ZERO);
        return origin;
    }

    /** Right-clicks the item in the main hand through the server's item-use path. */
    private static void use(ServerPlayer player) {
        player.gameMode.useItem(
                player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID())
                .orElseThrow(
                        () -> failure("Expected " + player.getScoreboardName() + " on a team."));
    }

    private static int score(Match match, ServerPlayer player) {
        return team(match, player).score();
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static void assertStandable(MapArena arena, Vec3 position, String what) {
        BlockPos feet = BlockPos.containing(position);
        ServerLevel level = arena.level();
        assertTrue(
                level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above())
                                .getCollisionShape(level, feet.above())
                                .isEmpty(),
                what + " is inside a block.");
        assertTrue(
                level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                        || !level.getBlockState(feet.below())
                                .getCollisionShape(level, feet.below())
                                .isEmpty(),
                what + " has nothing to stand on.");
    }

    private static Match open(
            GameTestHelper context, Minigame game, String gameId, String map, String layout)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id =
                MapArena.maps(server, gameId).stream()
                        .filter(candidate -> candidate.getPath().endsWith("/" + map))
                        .findFirst()
                        .orElseThrow(() -> failure("Missing " + gameId + " map " + map + "."));
        return MatchManager.open(
                server,
                game,
                TeamLayout.parse(layout).orElseThrow(),
                null,
                (unused, settings) -> MapArena.open(context.getLevel(), id));
    }

    /**
     * Connected, loaded players with unique names: the helper's mock players all share one name,
     * and scoreboard teams go by name, so they would all count as teammates.
     */
    private static List<ServerPlayer> players(GameTestHelper context, int count) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            CommonListenerCookie cookie =
                    CommonListenerCookie.createInitial(
                            new GameProfile(
                                    UUID.randomUUID(), "quakepearl" + NEXT_NAME.incrementAndGet()),
                            false);
            ServerPlayer player =
                    new ServerPlayer(
                            server, level, cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            players.add(player);
        }
        return players;
    }

    /** Stops the match and removes the players, as their clients disconnecting would. */
    private static void finish(Match match, List<ServerPlayer> players) {
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) {
                playerList.remove(player);
            }
        }
    }

    private static void setSetting(
            MinecraftServer server, Minigame game, GameSetting setting, int value) {
        SettingsStorage.set(server, game, setting, value);
    }

    /** Settings are read when a match opens, so they can be reset straight afterwards. */
    private static void resetSettings(MinecraftServer server, Minigame game) {
        for (GameSetting setting : game.settings()) {
            SettingsStorage.reset(server, game, setting);
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceTo(actual) > 0.5) {
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
