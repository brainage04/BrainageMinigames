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
import io.github.brainage04.brainage_minigames.game.spleef.BowSpleefGame;
import io.github.brainage04.brainage_minigames.game.spleef.SpleefGame;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Spleef and Bow Spleef on their bundled maps, pasted into the test level. */
public final class SpleefGameTest {
    private static final Minigame SPLEEF = Minigames.SPLEEF;
    private static final Minigame BOW_SPLEEF = Minigames.BOW_SPLEEF;

    @GameTest(maxTicks = 100)
    public void everyMapHasFloorsSpawnsAndVoid(GameTestHelper context) throws MatchException {
        checkMaps(context, SpleefGame.ID, state -> state.is(Blocks.SNOW_BLOCK));
        checkMaps(context, BowSpleefGame.ID, state -> state.is(Blocks.TNT));
        context.succeed();
    }

    @GameTest(maxTicks = 100)
    public void shovelDigsFloorsIntoSnowballsButNotWalls(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, SPLEEF);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, SPLEEF, SpleefGame.ID, "glacier", "1v1");
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        MapArena arena = (MapArena) match.arena();
                        ServerLevel level = arena.level();
                        ServerPlayer digger = players.get(0);
                        assertEquals(
                                GameType.SURVIVAL,
                                digger.gameMode.getGameModeForPlayer(),
                                "game mode");
                        assertTrue(
                                digger.getMainHandItem().is(Items.DIAMOND_SHOVEL),
                                "Expected the kit's shovel in hand, found "
                                        + digger.getMainHandItem()
                                        + ".");

                        MapArena.Region top = arena.region("floor_1").orElseThrow();
                        BlockPos wall =
                                find(
                                        arena,
                                        top,
                                        players,
                                        state ->
                                                !state.isAir()
                                                        && !state.is(
                                                                BlockTags.MINEABLE_WITH_SHOVEL));
                        BlockState wallState = level.getBlockState(wall);
                        digger.gameMode.destroyBlock(wall);
                        assertEquals(wallState, level.getBlockState(wall), "wall after digging");

                        BlockPos snow =
                                find(arena, top, players, state -> state.is(Blocks.SNOW_BLOCK));
                        digger.gameMode.destroyBlock(snow);
                        assertTrue(
                                level.getBlockState(snow).isAir(),
                                "Expected the shovel to dig the floor block.");
                        assertEquals(
                                SpleefGame.SNOWBALLS_PER_BLOCK.defaultValue(),
                                count(digger, Items.SNOWBALL),
                                "snowballs after digging one block");
                        assertTrue(
                                level.getEntitiesOfClass(
                                                ItemEntity.class, new AABB(snow).inflate(3))
                                        .isEmpty(),
                                "Expected no drops from the dug block.");

                        // A snowball thrown by a player breaks the floor block it lands on.
                        BlockPos target =
                                find(arena, top, players, state -> state.is(Blocks.SNOW_BLOCK));
                        Snowball snowball =
                                new Snowball(level, digger, new ItemStack(Items.SNOWBALL));
                        Vec3 above = Vec3.atCenterOf(target).add(0.0, 1.5, 0.0);
                        snowball.setPos(above.x, above.y, above.z);
                        snowball.setDeltaMovement(0.0, -1.0, 0.0);
                        level.addFreshEntity(snowball);
                        waiting = true;
                        context.runAfterDelay(
                                5,
                                () -> {
                                    try {
                                        assertTrue(
                                                level.getBlockState(target).isAir(),
                                                "Expected the snowball to break the floor block.");
                                        context.succeed();
                                    } finally {
                                        MatchManager.stop(match);
                                        resetSettings(server, SPLEEF);
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            MatchManager.stop(match);
                            resetSettings(server, SPLEEF);
                        }
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void fallingThroughTheFloorsEliminatesAndLastStandingWins(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, SPLEEF);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, SPLEEF, SpleefGame.ID, "lantern_pit", "1v1");
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.runAfterDelay(
                3,
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    ServerPlayer loser = players.get(1);
                    loser.teleportTo(loser.getX(), match.arena().voidY() - 1.0, loser.getZ());
                    context.runAfterDelay(
                            3,
                            () -> {
                                try {
                                    assertEquals(
                                            GameType.SPECTATOR,
                                            loser.gameMode.getGameModeForPlayer(),
                                            "fallen player's game mode");
                                    assertEquals(MatchPhase.ENDED, match.phase(), "phase");
                                    assertEquals(
                                            List.of(
                                                    match.teamOf(players.get(0).getUUID())
                                                            .orElseThrow()),
                                            match.winners(),
                                            "winners");
                                    context.succeed();
                                } finally {
                                    MatchManager.stop(match);
                                    resetSettings(server, SPLEEF);
                                }
                            });
                });
    }

    @GameTest(maxTicks = 100)
    public void flamingArrowRemovesFloorTntWithoutExploding(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, BOW_SPLEEF);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, BOW_SPLEEF, BowSpleefGame.ID, "ember_court", "1v1");
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        MapArena arena = (MapArena) match.arena();
                        ServerLevel level = arena.level();
                        ServerPlayer shooter = players.get(0);
                        assertTrue(
                                shooter.getMainHandItem().is(Items.BOW),
                                "Expected the kit's bow in hand.");
                        AABB floor = arena.region("floor_1").orElseThrow().box();
                        BlockPos target =
                                BlockPos.containing(
                                        floor.getCenter().x, floor.minY, floor.getCenter().z);
                        assertTrue(
                                level.getBlockState(target).is(Blocks.TNT),
                                "Expected TNT in the middle of the floor.");
                        Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
                        Vec3 above = Vec3.atCenterOf(target).add(0.0, 2.0, 0.0);
                        arrow.setPos(above.x, above.y, above.z);
                        arrow.setDeltaMovement(0.0, -2.0, 0.0);
                        arrow.igniteForSeconds(100.0F);
                        level.addFreshEntity(arrow);
                        waiting = true;
                        context.runAfterDelay(
                                5,
                                () -> {
                                    try {
                                        assertTrue(
                                                level.getBlockState(target).isAir(),
                                                "Expected the arrow to remove the TNT it hit.");
                                        assertTrue(
                                                arrow.isRemoved(),
                                                "Expected the arrow to be used up.");
                                        AABB area = AABB.of(arena.bounds()).inflate(4);
                                        assertTrue(
                                                level.getEntitiesOfClass(PrimedTnt.class, area)
                                                        .isEmpty(),
                                                "Expected no TNT to be lit.");
                                        assertTrue(
                                                level.getBlockState(target.north()).is(Blocks.TNT)
                                                        && level.getBlockState(target.south())
                                                                .is(Blocks.TNT),
                                                "Expected the neighbouring TNT to stay.");
                                        context.succeed();
                                    } finally {
                                        MatchManager.stop(match);
                                        resetSettings(server, BOW_SPLEEF);
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            MatchManager.stop(match);
                            resetSettings(server, BOW_SPLEEF);
                        }
                    }
                });
    }

    @GameTest(maxTicks = 100)
    public void perksUseUpTheirItems(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        setCountdown(server, BOW_SPLEEF);
        List<ServerPlayer> players = players(context, 2);
        Match match = open(context, BOW_SPLEEF, BowSpleefGame.ID, "twin_decks", "1v1");
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        int jumps = BowSpleefGame.DOUBLE_JUMPS.defaultValue();
        int repulsors = BowSpleefGame.REPULSORS.defaultValue();
        context.runAfterDelay(
                3,
                () -> {
                    boolean waiting = false;
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        ServerPlayer jumper = players.get(0);
                        ServerPlayer target = players.get(1);
                        assertEquals(jumps, count(jumper, Items.FEATHER), "double jumps");
                        assertTrue(
                                jumper.getAbilities().mayfly,
                                "Expected double-tapping jump to be armed.");

                        jumper.getInventory().setSelectedSlot(slotOf(jumper, Items.FEATHER));
                        jumper.gameMode.useItem(
                                jumper,
                                jumper.level(),
                                jumper.getMainHandItem(),
                                InteractionHand.MAIN_HAND);
                        assertEquals(jumps - 1, count(jumper, Items.FEATHER), "double jumps left");
                        assertTrue(
                                jumper.getDeltaMovement().y > 0.5,
                                "Expected the double jump to launch the player upwards.");

                        target.teleportTo(jumper.getX() + 2.0, jumper.getY(), jumper.getZ());
                        target.setDeltaMovement(Vec3.ZERO);
                        jumper.setShiftKeyDown(true);
                        waiting = true;
                        context.runAfterDelay(
                                1,
                                () -> {
                                    try {
                                        assertEquals(
                                                repulsors - 1,
                                                count(jumper, Items.MAGMA_CREAM),
                                                "repulsors left after sneaking");
                                        assertTrue(
                                                target.getDeltaMovement().x > 1.0,
                                                "Expected the repulsor to fling the opponent away, found "
                                                        + target.getDeltaMovement()
                                                        + ".");
                                        context.succeed();
                                    } finally {
                                        jumper.setShiftKeyDown(false);
                                        MatchManager.stop(match);
                                        resetSettings(server, BOW_SPLEEF);
                                    }
                                });
                    } finally {
                        if (!waiting) {
                            MatchManager.stop(match);
                            resetSettings(server, BOW_SPLEEF);
                        }
                    }
                });
    }

    private static void checkMaps(
            GameTestHelper context, String gameId, Predicate<BlockState> floorBlock)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, gameId);
        assertTrue(maps.size() >= 2, "Expected two " + gameId + " maps, found " + maps + ".");
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                assertTrue(arena.teamSlots() >= 2, map + " needs at least two spawns.");
                List<MapArena.Region> floors = arena.regions("floor_");
                assertTrue(!floors.isEmpty(), map + " has no floor regions.");
                assertTrue(arena.region("floor_1").isPresent(), map + " lacks floor_1.");
                double lowestFloor = Double.MAX_VALUE;
                for (MapArena.Region floor : floors) {
                    lowestFloor = Math.min(lowestFloor, floor.box().minY);
                    find(arena, floor, List.of(), floorBlock);
                }
                assertTrue(
                        arena.voidY() < lowestFloor,
                        map + " needs its void below the lowest floor.");
                double top = arena.region("floor_1").orElseThrow().box().maxY;
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    for (var spawn : arena.spawnsOf(team)) {
                        assertTrue(
                                spawn.position().y() >= top
                                        && floorBlock.test(
                                                arena.level()
                                                        .getBlockState(
                                                                BlockPos.containing(
                                                                                spawn.position())
                                                                        .below())),
                                map + " spawn " + team + " is not on floor_1.");
                    }
                }
            } finally {
                arena.close();
            }
        }
    }

    /** A block of the region matching {@code test}, at least four blocks from every player. */
    private static BlockPos find(
            MapArena arena,
            MapArena.Region region,
            List<ServerPlayer> players,
            Predicate<BlockState> test) {
        AABB box = region.box();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX - 0.5, box.maxY - 0.5, box.maxZ - 0.5))) {
            Vec3 center = Vec3.atCenterOf(pos);
            if (test.test(arena.level().getBlockState(pos))
                    && players.stream()
                            .allMatch(player -> player.position().distanceTo(center) > 4.0)) {
                return pos.immutable();
            }
        }
        throw failure("No matching block in " + region.name() + " of " + arena.map() + ".");
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

    private static List<ServerPlayer> players(GameTestHelper context, int count) {
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ServerPlayer player = context.makeMockServerPlayerInLevel();
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            players.add(player);
        }
        return players;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static int slotOf(ServerPlayer player, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        throw failure("Expected " + item + " in the hotbar.");
    }

    private static void setCountdown(MinecraftServer server, Minigame game) {
        SettingsStorage.set(
                server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
    }

    private static void resetSettings(MinecraftServer server, Minigame game) {
        for (GameSetting setting : game.settings()) {
            SettingsStorage.reset(server, game, setting);
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
