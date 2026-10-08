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
import io.github.brainage04.brainage_minigames.game.grinch.GrinchSimulatorGame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Grinch Simulator on its bundled village maps, pasted into the test level. */
public final class GrinchSimulatorGameTest {
    private static final GrinchSimulatorGame GRINCH = Minigames.GRINCH_SIMULATOR;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    /**
     * Every village holds six spawns that can be stood on and more present spots than a match puts
     * presents in by default, each on a floor block with room for the present, all within the
     * 128 blocks a map shows.
     */
    public void everyVillageHasSpawnsAndPresentSpots(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, GrinchSimulatorGame.ID);
        assertTrue(maps.size() >= 2, "Expected two Grinch Simulator maps, found " + maps + ".");
        int presents = GrinchSimulatorGame.PRESENTS.defaultValue();
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                ServerLevel level = arena.level();
                assertEquals(6, arena.teamSlots(), map + " spawns");
                for (int team = 1; team <= arena.teamSlots(); team++) {
                    for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                        BlockPos feet = BlockPos.containing(spawn.position());
                        assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                                        && solid(level, feet.below()),
                                map + ": spawn " + team + " at " + feet + " cannot be stood on.");
                    }
                }
                Set<BlockPos> spots = new HashSet<>();
                for (MapArena.Point point : arena.points("present")) {
                    BlockPos pos = BlockPos.containing(point.position());
                    assertTrue(spots.add(pos), map + ": two present spots at " + pos + ".");
                    assertTrue(level.getBlockState(pos).isAir(), map + ": present spot " + pos + " is not empty.");
                    assertTrue(solid(level, pos.below()), map + ": present spot " + pos + " has no floor.");
                }
                assertTrue(spots.size() > presents,
                        map + " has " + spots.size() + " present spots, not more than " + presents + ".");
                assertTrue(arena.bounds().getXSpan() <= 128 && arena.bounds().getZSpan() <= 128,
                        map + " is wider than a map shows: " + arena.bounds() + ".");
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    /**
     * The match puts the configured number of presents (player heads) on present spots and gives
     * every Grinch a map that marks them. Right-clicking a present steals it for a point and takes
     * it off the map; any other block, or a present already stolen, gives nothing. A map thrown
     * away comes back, nobody takes damage, and once every present is stolen the Grinch with the
     * most wins.
     */
    public void stealingPresentsScoresAndTheMostPresentsWin(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        SettingsStorage.set(server, GRINCH, GRINCH.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, GRINCH, GrinchSimulatorGame.PRESENTS, 3);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "hollyvale", "1v1");
        resetSettings(server);
        ServerPlayer red = players.get(0);
        ServerPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        List<BlockPos> presents = new ArrayList<>();
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    presents.addAll(GRINCH.presents(match));
                    assertEquals(3, presents.size(), "presents put down");
                    Set<BlockPos> spots = new HashSet<>();
                    ((MapArena) match.arena()).points("present").forEach(point -> spots.add(BlockPos.containing(point.position())));
                    for (BlockPos pos : presents) {
                        assertTrue(spots.contains(pos), "Expected the present at " + pos + " on a present spot.");
                        assertTrue(level.getBlockState(pos).is(Blocks.PLAYER_HEAD)
                                        && level.getBlockEntity(pos) instanceof SkullBlockEntity skull
                                        && skull.getOwnerProfile() != null,
                                "Expected a present head with a skin at " + pos + ".");
                    }
                    MapId id = GRINCH.mapId(match);
                    assertTrue(id != null && id.equals(red.getInventory().getItem(0).get(DataComponents.MAP_ID))
                                    && id.equals(blue.getInventory().getItem(0).get(DataComponents.MAP_ID)),
                            "Expected both Grinches to hold the village map in their first slot.");
                    MapItemSavedData map = level.getMapData(id);
                    assertEquals(3L, markers(map), "present markers on the map");
                    assertTrue(map.colors[64 * 128 + 64] != 0, "Expected the village drawn on the map.");
                    Map<String, Object> view = GrinchSimulatorGame.botView(red);
                    assertTrue(view != null && ((List<?>) view.get("presents")).size() == 3,
                            "Expected the bot view of the presents: " + view);
                    // A block that is not a present gives nothing.
                    assertEquals(InteractionResult.FAIL, use(red, presents.get(0).below()), "using the floor");
                    assertEquals(0, team(match, red).score(), "red's presents after using the floor");
                    assertEquals(InteractionResult.SUCCESS, use(red, presents.get(0)), "stealing a present");
                    assertTrue(level.getBlockState(presents.get(0)).isAir(), "Expected the stolen present gone.");
                    assertEquals(1, team(match, red).score(), "red's presents");
                    assertEquals(1, GRINCH.stolen(match, red.getUUID()), "red's stolen count");
                    assertEquals(2L, markers(map), "present markers after a theft");
                    assertEquals(" 1", GRINCH.sidebarTeamSuffix(match, team(match, red)).getString(), "red on the sidebar");
                    assertTrue(use(blue, presents.get(0)) instanceof InteractionResult.Fail, "Expected a stolen present to be gone for blue.");
                    assertEquals(0, team(match, blue).score(), "blue's presents");
                    // Nobody takes damage, and a thrown-away map comes back.
                    float health = blue.getHealth();
                    blue.invulnerableTime = 0;
                    blue.hurtServer(level, blue.damageSources().playerAttack(red), 5.0F);
                    assertEquals(health, blue.getHealth(), "blue's health after a hit");
                    red.getInventory().setItem(0, ItemStack.EMPTY);
                }))
                .thenExecuteAfter(21, () -> run(match, players, () -> {
                    assertTrue(GRINCH.mapId(match).equals(red.getInventory().getItem(0).get(DataComponents.MAP_ID)),
                            "Expected red's map back.");
                    assertEquals(InteractionResult.SUCCESS, use(blue, presents.get(1)), "blue stealing a present");
                    assertEquals(InteractionResult.SUCCESS, use(red, presents.get(2)), "red stealing the last present");
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase with every present stolen");
                    assertEquals(List.of(team(match, red)), match.winners(), "winners");
                    context.succeed();
                }, true));
    }

    private static long markers(MapItemSavedData map) {
        return StreamSupport.stream(map.getDecorations().spliterator(), false)
                .filter(decoration -> decoration.type().equals(MapDecorationTypes.TARGET_POINT))
                .count();
    }

    /** Right-clicks the top of the block with the main hand, as a client would. */
    private static InteractionResult use(ServerPlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Runs a step; stops the match and removes the players after a failure or the last step. */
    private static void run(Match match, List<? extends ServerPlayer> players, Runnable step) {
        run(match, players, step, false);
    }

    private static void run(Match match, List<? extends ServerPlayer> players, Runnable step, boolean last) {
        boolean failed = true;
        try {
            step.run();
            failed = false;
        } finally {
            if (failed || last) finish(match, players);
        }
    }

    private static Match open(GameTestHelper context, String map, String layout) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id = MapArena.maps(server, GrinchSimulatorGame.ID).stream()
                .filter(candidate -> candidate.getPath().endsWith("/" + map))
                .findFirst()
                .orElseThrow(() -> failure("Missing Grinch Simulator map " + map + "."));
        return MatchManager.open(server, GRINCH, TeamLayout.parse(layout).orElseThrow(), null,
                (unused, settings) -> MapArena.open(context.getLevel(), id));
    }

    private static List<TestPlayers.ChatPlayer> players(GameTestHelper context, int count) {
        List<TestPlayers.ChatPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            players.add(TestPlayers.chat(context, "grinch" + NEXT_NAME.incrementAndGet()));
        }
        return players;
    }

    private static void finish(Match match, List<? extends ServerPlayer> players) {
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) playerList.remove(player);
        }
    }

    private static void resetSettings(MinecraftServer server) {
        for (GameSetting setting : GRINCH.settings()) SettingsStorage.reset(server, GRINCH, setting);
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID())
                .orElseThrow(() -> failure("Expected " + player.getScoreboardName() + " on a team."));
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
