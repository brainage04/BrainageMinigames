package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The map framework: {@link MapArena} pasting, markers and reset, the shared block rules, and the
 * respawn and void paths of {@link Match}. The map is {@code tools/maps/test_map.py}'s {@code
 * test_map/basic}: a 64 by 9 stone floor 5 blocks above the template's minimum corner, so it lies
 * at y 69 once pasted with its minimum corner at y 64.
 */
public final class MapFrameworkGameTest {
    private static final Identifier TEST_MAP = BrainageMinigames.id("maps/test_map/basic");
    private static final int FLOOR_Y = MapArena.BASE_Y + 5;
    private static final int ABOVE_FLOOR = FLOOR_Y + 1;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    @GameTest
    public void mapPastesWithMarkersParsedAndReplacedByAir(GameTestHelper context)
            throws MatchException {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        MapArena arena = MapArena.open(level, TEST_MAP);
        int x0;
        int z0;
        try {
            BoundingBox bounds = arena.bounds();
            x0 = bounds.minX();
            z0 = bounds.minZ();
            assertEquals(64, bounds.getXSpan(), "map length");
            assertEquals(MapArena.BASE_Y, bounds.minY(), "map minimum Y");
            assertEquals(TEST_MAP, arena.map(), "map id");
            assertEquals("basic", arena.mapName(), "map name");
            // Wider than the 48 blocks a structure block saves: the far corner is pasted too.
            assertBlock(level, new BlockPos(x0 + 63, FLOOR_Y, z0 + 8), Blocks.STONE, "far corner");
            assertBlock(level, new BlockPos(x0, FLOOR_Y, z0), Blocks.STONE, "near corner");
            assertBlock(level, new BlockPos(x0 + 32, ABOVE_FLOOR, z0 + 8), Blocks.GLASS, "glass");

            assertEquals(2, arena.teamSlots(), "team slots");
            assertEquals(2, arena.maxTeams(), "max teams");
            List<Arena.Spawn> red = arena.spawnsOf(1);
            assertEquals(2, red.size(), "team 1 spawns");
            assertNear(new Vec3(x0 + 2.5, ABOVE_FLOOR, z0 + 4.5), red.get(0).position(), "spawn");
            assertEquals(-90.0F, red.get(0).yaw(), "explicit spawn yaw");
            assertTrue(
                    Math.abs(red.get(1).yaw() + 90.0F) < 10.0F,
                    "Expected a spawn without a yaw to face the map centre (east), found "
                            + red.get(1).yaw()
                            + ".");
            assertNear(
                    new Vec3(x0 + 61.5, ABOVE_FLOOR, z0 + 4.5),
                    arena.spawnsOf(2).getFirst().position(),
                    "team 2 spawn");
            assertEquals(
                    List.of(red.getFirst(), arena.spawnsOf(2).getFirst()),
                    arena.spawns(2),
                    "spawns(2)");
            assertNear(new Vec3(x0 + 32.5, ABOVE_FLOOR, z0 + 4.5), arena.lobbyPosition(), "lobby");

            MapArena.Point chest =
                    arena.point("chest_mid").orElseThrow(() -> failure("Expected chest_mid."));
            assertNear(new Vec3(x0 + 32.5, ABOVE_FLOOR, z0 + 2.5), chest.position(), "point");
            List<MapArena.Point> boats = arena.points("boat_");
            assertEquals(
                    List.of("boat_1", "boat_2"),
                    boats.stream().map(MapArena.Point::name).toList(),
                    "boat points");
            assertEquals(-90.0F, boats.getFirst().yaw(), "boat_1 yaw");
            assertTrue(arena.point("missing").isEmpty(), "Expected no point named missing.");

            assertEquals(
                    new AABB(x0 + 20, ABOVE_FLOOR, z0, x0 + 44, ABOVE_FLOOR + 11, z0 + 9),
                    arena.region("build").orElseThrow().box(),
                    "build region");
            MapArena.Region goal = arena.regions("goal_").getFirst();
            assertEquals("goal_1", goal.name(), "goal region name");
            assertTrue(
                    goal.contains(new Vec3(x0 + 1.9, ABOVE_FLOOR + 1.5, z0 + 0.1)),
                    "Expected the goal region to cover whole blocks.");
            assertTrue(
                    !goal.contains(new Vec3(x0 + 2.1, ABOVE_FLOOR, z0)),
                    "Expected the goal region to end after its last block.");
            assertEquals(64.0, arena.voidY(), "void height");

            assertTrue(
                    arena.canBuild(new BlockPos(x0 + 30, ABOVE_FLOOR, z0 + 2)),
                    "Expected building inside the build region.");
            assertTrue(
                    !arena.canBuild(new BlockPos(x0 + 10, ABOVE_FLOOR, z0 + 2)),
                    "Expected no building outside the build region.");
            assertTrue(
                    !arena.canBuild(new BlockPos(x0 + 30, ABOVE_FLOOR, z0 + 20)),
                    "Expected no building outside the map.");

            for (BlockPos marker :
                    new BlockPos[] {
                        new BlockPos(x0 + 32, ABOVE_FLOOR, z0 + 4),
                        new BlockPos(x0 + 2, ABOVE_FLOOR, z0 + 4),
                        new BlockPos(x0 + 20, ABOVE_FLOOR, z0),
                        new BlockPos(x0 + 32, MapArena.BASE_Y, z0 + 4)
                    }) {
                assertBlock(level, marker, Blocks.AIR, "marker at " + marker.toShortString());
            }

            assertEquals(List.of(TEST_MAP), MapArena.maps(server, "test_map"), "listed maps");
            assertEquals(2, MapArena.teamSlots(server, TEST_MAP), "team slots without pasting");
        } finally {
            arena.close();
        }
        assertBlock(level, new BlockPos(x0, FLOOR_Y, z0), Blocks.AIR, "cleared floor");
        assertBlock(level, new BlockPos(x0 + 32, ABOVE_FLOOR, z0 + 8), Blocks.AIR, "cleared glass");

        assertRefused(
                level,
                BrainageMinigames.id("maps/test_map_invalid/missing_team"),
                "none for team 1");
        assertRefused(
                level, BrainageMinigames.id("maps/test_map_invalid/bad_marker"), "unknown marker");
        assertRefused(level, BrainageMinigames.id("maps/test_map/nonexistent"), "Unknown map");
        context.succeed();
    }

    @GameTest(maxTicks = 100)
    public void blockRulesTrackPlacedBlocksAndResetRestoresTheMap(GameTestHelper context)
            throws MatchException {
        ServerLevel level = context.getLevel();
        TestGame game = new TestGame(Minigame.DeathResult.ELIMINATE);
        ServerPlayer builder = loadedPlayer(context);
        ServerPlayer other = loadedPlayer(context);
        Match match = openOnTestMap(context, game);
        MapArena arena = (MapArena) match.arena();
        int x0 = arena.bounds().minX();
        int z0 = arena.bounds().minZ();
        BlockPos glass = new BlockPos(x0 + 32, ABOVE_FLOOR, z0 + 8);
        try {
            MatchManager.join(builder, match, 1);
            // Even with the power to break blocks, a lobby player cannot.
            builder.setGameMode(GameType.SURVIVAL);
            assertTrue(
                    !builder.gameMode.destroyBlock(glass),
                    "Expected a lobby player's break to be refused.");
            assertBlock(level, glass, Blocks.GLASS, "glass after a refused break");
            MatchManager.join(other, match, 2);
        } catch (MatchException | RuntimeException exception) {
            stop(match);
            throw exception;
        }

        context.runAfterDelay(
                2,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        assertEquals(Optional.of("basic"), match.mapName(), "map name");
                        assertTrue(
                                match.describe().getString().contains("map basic"),
                                "Expected /minigames status to name the map.");
                        BlockPos inside = new BlockPos(x0 + 30, ABOVE_FLOOR, z0 + 2);
                        BlockPos outside = new BlockPos(x0 + 10, ABOVE_FLOOR, z0 + 2);
                        builder.setItemInHand(
                                InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));

                        place(builder, inside);
                        assertBlock(level, inside, Blocks.OAK_PLANKS, "placed block");
                        assertTrue(
                                match.isPlacedBlock(inside), "Expected the placed block tracked.");
                        place(builder, outside);
                        assertBlock(
                                level,
                                outside,
                                Blocks.AIR,
                                "block refused outside the build region");
                        assertTrue(
                                !match.isPlacedBlock(outside),
                                "Expected a refused block untracked.");
                        assertEquals(3, builder.getMainHandItem().getCount(), "planks left");
                        assertTrue(
                                !match.isPlacedBlock(glass),
                                "Expected map blocks not to count as placed.");

                        assertTrue(
                                builder.gameMode.destroyBlock(inside),
                                "Expected the placed block to break.");
                        assertTrue(
                                !match.isPlacedBlock(inside), "Expected a broken block untracked.");
                        // Breaking is vanilla by default, map blocks included.
                        assertTrue(
                                builder.gameMode.destroyBlock(glass),
                                "Expected the glass to break.");
                        assertBlock(level, glass, Blocks.AIR, "broken glass");

                        BlockPos kept = inside.east();
                        place(builder, kept);
                        BlockPos water = inside.east(3);
                        BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
                        assertTrue(
                                !bucket.emptyContents(builder, level, outside, null),
                                "Expected water refused outside the build region.");
                        assertBlock(level, outside, Blocks.AIR, "refused water");
                        assertTrue(
                                bucket.emptyContents(builder, level, water, null),
                                "Expected water inside the build region.");
                        assertTrue(match.isPlacedBlock(water), "Expected placed water tracked.");

                        arena.reset();
                        assertBlock(level, glass, Blocks.GLASS, "glass after reset");
                        assertBlock(level, kept, Blocks.AIR, "placed block after reset");
                        assertBlock(level, water, Blocks.AIR, "water after reset");
                        assertTrue(
                                !match.isPlacedBlock(kept),
                                "Expected reset to forget placed blocks.");
                        assertTrue(
                                !match.isPlacedBlock(water),
                                "Expected reset to forget placed water.");
                        assertBlock(
                                level,
                                new BlockPos(x0 + 32, ABOVE_FLOOR, z0 + 4),
                                Blocks.AIR,
                                "lobby marker after reset");
                    } finally {
                        stop(match);
                    }
                    assertBlock(level, glass, Blocks.AIR, "glass once the match closed");
                    context.succeed();
                });
    }

    @GameTest(maxTicks = 100)
    public void respawnReturnsPlayersToTheirTeamSpawnWithTheKitAndCreditsKills(
            GameTestHelper context) throws MatchException {
        TestGame game = new TestGame(Minigame.DeathResult.RESPAWN);
        ServerPlayer red = loadedPlayer(context);
        ServerPlayer blue = loadedPlayer(context);
        Match match = openOnTestMap(context, game);
        MapArena arena = (MapArena) match.arena();
        try {
            MatchManager.join(red, match, 1);
            MatchManager.join(blue, match, 2);
        } catch (MatchException | RuntimeException exception) {
            stop(match);
            throw exception;
        }

        context.runAfterDelay(
                2,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        blue.teleportTo(blue.getX() - 20.0, blue.getY(), blue.getZ());
                        blue.getInventory().clearContent();
                        hit(red, blue);
                        blue.hurtServer(
                                blue.level(), blue.damageSources().genericKill(), Float.MAX_VALUE);
                        assertTrue(blue.isAlive(), "Expected the death to be cancelled.");
                        assertTrue(
                                match.isAlive(blue.getUUID()),
                                "Expected the player to stay in play.");
                        assertEquals(1, game.deaths.size(), "deaths");
                        assertEquals(blue, game.deaths.getFirst().victim(), "victim");
                        assertEquals(red, game.deaths.getFirst().killer(), "killer");
                        assertEquals(List.of(blue), game.respawned, "respawned players");
                        assertEquals(
                                GameType.SURVIVAL,
                                blue.gameMode.getGameModeForPlayer(),
                                "game mode");
                        assertEquals(blue.getMaxHealth(), blue.getHealth(), "health");
                        assertNear(
                                arena.spawnsOf(2).getFirst().position(),
                                blue.position(),
                                "respawned position");
                        assertTrue(
                                blue.getInventory().contains(new ItemStack(Items.IRON_SWORD)),
                                "Expected the kit again after respawning.");

                        // Knocked into the void: the last player to hit them gets the kill.
                        hit(blue, red);
                        red.teleportTo(red.getX(), MapArena.BASE_Y - 4.0, red.getZ());
                    } catch (RuntimeException exception) {
                        stop(match);
                        throw exception;
                    }
                    context.runAfterDelay(
                            2,
                            () -> {
                                try {
                                    assertEquals(2, game.deaths.size(), "deaths");
                                    assertEquals(red, game.deaths.get(1).victim(), "void victim");
                                    assertEquals(blue, game.deaths.get(1).killer(), "void killer");
                                    // Team 1 has two spawns; the second is next.
                                    assertNear(
                                            arena.spawnsOf(1).get(1).position(),
                                            red.position(),
                                            "position after the void");
                                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                                } finally {
                                    stop(match);
                                }
                                context.succeed();
                            });
                });
    }

    /**
     * A player attack, with the pvp game rule on only while it lands: other tests running alongside
     * toggle it too.
     */
    private static void hit(ServerPlayer attacker, ServerPlayer target) {
        MinecraftServer server = target.level().getServer();
        boolean pvp = server.getGameRules().get(GameRules.PVP);
        server.getGameRules().set(GameRules.PVP, true, server);
        target.hurtServer(target.level(), target.damageSources().playerAttack(attacker), 1.0F);
        server.getGameRules().set(GameRules.PVP, pvp, server);
    }

    @GameTest(maxTicks = 100)
    public void fallingIntoTheVoidEliminatesByDefault(GameTestHelper context)
            throws MatchException {
        TestGame game = new TestGame(Minigame.DeathResult.ELIMINATE);
        ServerPlayer red = loadedPlayer(context);
        ServerPlayer blue = loadedPlayer(context);
        Match match = openOnTestMap(context, game);
        try {
            MatchManager.join(red, match, 1);
            MatchManager.join(blue, match, 2);
        } catch (MatchException | RuntimeException exception) {
            stop(match);
            throw exception;
        }
        context.runAfterDelay(
                2,
                () -> {
                    blue.teleportTo(blue.getX(), MapArena.BASE_Y - 1.0, blue.getZ());
                    context.runAfterDelay(
                            2,
                            () -> {
                                try {
                                    assertEquals(1, game.deaths.size(), "deaths");
                                    assertEquals(null, game.deaths.getFirst().killer(), "killer");
                                    assertTrue(
                                            !match.isAlive(blue.getUUID()),
                                            "Expected elimination.");
                                    assertEquals(
                                            GameType.SPECTATOR,
                                            blue.gameMode.getGameModeForPlayer(),
                                            "eliminated game mode");
                                    assertTrue(
                                            blue.getY() > match.arena().voidY(),
                                            "Expected the spectator brought back above the void.");
                                    assertEquals(MatchPhase.ENDED, match.phase(), "phase");
                                    assertEquals(
                                            List.of(match.teamOf(red.getUUID()).orElseThrow()),
                                            match.winners(),
                                            "winners");
                                } finally {
                                    stop(match);
                                }
                                context.succeed();
                            });
                });
    }

    /** A game on the test map that records deaths and respawns. */
    private static final class TestGame implements Minigame {
        private final DeathResult deathResult;
        private final List<Death> deaths = new ArrayList<>();
        private final List<ServerPlayer> respawned = new ArrayList<>();

        private TestGame(DeathResult deathResult) {
            this.deathResult = deathResult;
        }

        @Override
        public String id() {
            return "map_framework_test";
        }

        @Override
        public String displayName() {
            return "Map Framework Test";
        }

        @Override
        public List<GameSetting> settings() {
            return GameSetting.common(0, 10, true);
        }

        @Override
        public Identifier defaultKit() {
            return BrainageMinigames.id("kits/classic");
        }

        @Override
        public GameType playerGameMode() {
            return GameType.SURVIVAL;
        }

        @Override
        public Arena openArena(MinecraftServer server, GameSettings settings)
                throws MatchException {
            throw new MatchException("The test game opens its arena through the test.");
        }

        @Override
        public DeathResult onDeath(
                Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
            deaths.add(new Death(victim, killer));
            return deathResult;
        }

        @Override
        public void onRespawn(Match match, ServerPlayer player) {
            respawned.add(player);
        }
    }

    private record Death(ServerPlayer victim, @Nullable ServerPlayer killer) {}

    private static Match openOnTestMap(GameTestHelper context, Minigame game)
            throws MatchException {
        return MatchManager.open(
                context.getLevel().getServer(),
                game,
                TeamLayout.parse("1v1").orElseThrow(),
                null,
                (server, settings) -> MapArena.open(context.getLevel(), TEST_MAP));
    }

    /** Places the held block on top of the block below {@code pos}, as a right click would. */
    private static void place(ServerPlayer player, BlockPos pos) {
        BlockPos below = pos.below();
        player.gameMode.useItemOn(
                player,
                player.level(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false));
    }

    private static void assertRefused(ServerLevel level, Identifier map, String reason) {
        try {
            MapArena.open(level, map).close();
        } catch (MatchException exception) {
            assertTrue(
                    exception.getMessage().contains(reason),
                    "Expected opening "
                            + map
                            + " to fail with '"
                            + reason
                            + "', found '"
                            + exception.getMessage()
                            + "'.");
            return;
        }
        throw failure("Expected opening " + map + " to be refused.");
    }

    private static void assertBlock(ServerLevel level, BlockPos pos, Block block, String what) {
        if (!level.getBlockState(pos).is(block)) {
            throw failure(
                    "Expected "
                            + what
                            + " to be "
                            + block
                            + ", found "
                            + level.getBlockState(pos)
                            + ".");
        }
    }

    /**
     * A connected, loaded player with a unique name: matches put players on scoreboard teams by
     * name, so the GameTest helper's mock players, who all share one, would end up allied.
     */
    private static ServerPlayer loadedPlayer(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(
                        new GameProfile(UUID.randomUUID(), "mapper" + NEXT_NAME.incrementAndGet()),
                        false);
        ServerPlayer player =
                new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    /** Stops the match, then disconnects its players as their clients leaving would. */
    private static void stop(Match match) {
        List<ServerPlayer> players = match.onlineMembers();
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) {
                playerList.remove(player);
            }
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceToSqr(actual) > 1.0E-6) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertEquals(
            @Nullable Object expected, @Nullable Object actual, String description) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
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
