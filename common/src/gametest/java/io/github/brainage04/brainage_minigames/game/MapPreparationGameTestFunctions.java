package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Map games open and close their maps over several ticks, as the game menu and {@code /minigames
 * open} open them: players who join wait where they are until the map is pasted, a start waits for
 * it too, and a stopped match's map is cleared over the following ticks.
 */
public final class MapPreparationGameTestFunctions {
    private MapPreparationGameTestFunctions() {}

    /** The largest race map: opened and joined at once, it is pasted before anyone is moved or started. */
    public static void parkourMapPastedBeforeJoinAndStart(GameTestHelper context) {
        Opened opened = open(context, Minigames.PARKOUR, "maps/parkour/canopy");
        MapArena arena = opened.arena();
        ServerPlayer opener = opened.opener();
        ServerPlayer second = TestPlayers.connect(context, "map_second");
        GameTestLifecycle.afterTest(context, () -> TestPlayers.disconnect(second));
        ServerLevel before = opener.level();
        context.assertFalse(arena.prepared(), "Opening parkour pasted the whole canopy map at once");
        context.assertFalse(arena.lobbyReady(), "The lobby was ready before its map was pasted");
        context.assertTrue(opener.level() == before, "The opener was moved onto a map that is not pasted yet");
        try {
            MatchManager.join(second, opened.match(), 0);
            opened.match().start();
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
        context.assertTrue(opened.match().preparingSpawns(), "The match started on a map that is not pasted yet");
        GameTestLifecycle.awaitPreparation(context, () -> !opened.match().preparingSpawns(), () -> {
            context.assertTrue(arena.prepared(), "The match started before its map was pasted");
            for (ServerPlayer player : List.of(opener, second)) {
                BlockPos below = player.blockPosition().below();
                context.assertTrue(player.level() == arena.level() && !arena.level().getBlockState(below).isAir(),
                        player.getScoreboardName() + " was not placed on the map: " + player.position());
            }
            context.succeed();
        });
    }

    /** SkyWars finds its island chests and builds its cages on the pasted map. */
    public static void skyWarsPreparesItsPastedMap(GameTestHelper context) {
        Opened opened = open(context, Minigames.SKYWARS, "maps/skywars/archipelago");
        MapArena arena = opened.arena();
        context.assertFalse(arena.prepared(), "Opening SkyWars pasted the whole archipelago map at once");
        GameTestLifecycle.awaitPreparation(context, arena::lobbyReady, () -> {
            context.assertFalse(Minigames.SKYWARS.chests(arena).isEmpty(), "SkyWars found no chests on its pasted map");
            BlockPos spawn = BlockPos.containing(arena.spawnsOf(1).getFirst().position());
            context.assertTrue(arena.level().getBlockState(spawn.above(3)).is(net.minecraft.world.level.block.Blocks.GLASS),
                    "SkyWars built no cage roof at its first spawn");
            context.succeed();
        });
    }

    /** A stopped match's map is cleared over the following ticks, then its chunks are freed. */
    public static void stoppedMapClearedOverTicks(GameTestHelper context) {
        Opened opened = open(context, Minigames.PARKOUR, "maps/parkour/canopy");
        MapArena arena = opened.arena();
        GameTestLifecycle.awaitPreparation(context, arena::lobbyReady, () -> {
            ServerLevel level = arena.level();
            BlockPos floor = BlockPos.containing(arena.spawnsOf(1).getFirst().position()).below();
            long chunk = ChunkPos.pack(floor);
            context.assertFalse(level.getBlockState(floor).isAir(), "The canopy map has no floor under its first spawn");
            MatchManager.stop(opened.match());
            context.assertFalse(level.getBlockState(floor).isAir(), "Stopping the match cleared its whole map at once");
            context.assertTrue(level.getForceLoadedChunks().contains(chunk), "The map's chunks were freed before it was clear");
            context.startSequence()
                    .thenWaitUntil(() -> context.assertTrue(level.getBlockState(floor).isAir()
                                    && !level.getForceLoadedChunks().contains(chunk),
                            "The stopped match's map is not cleared and freed yet"))
                    .thenSucceed();
        });
    }

    private record Opened(Match match, MapArena arena, ServerPlayer opener) {}

    /** Opens the map as a player's match and joins them, as the game menu does. */
    private static Opened open(GameTestHelper context, Minigame game, String map) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer opener = TestPlayers.connect(context, "map_opener");
        Match[] match = {null};
        GameTestLifecycle.afterTest(context, () -> {
            if (match[0] != null && !match[0].isClosed()) MatchManager.stop(match[0]);
            TestPlayers.disconnect(opener);
        });
        Identifier id = BrainageMinigames.id(map);
        try {
            match[0] = MatchService.open(opener, game, TeamLayout.FREE_FOR_ALL, null,
                    (unused, settings) -> MapArena.withChosenMap(id,
                            () -> game.openArena(server, settings, TeamLayout.FREE_FOR_ALL)));
            MatchManager.join(opener, match[0], 0);
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
        Arena arena = match[0].arena();
        if (!(arena instanceof MapArena mapArena)) throw context.assertionException(game.id() + " opened no map");
        return new Opened(match[0], mapArena, opener);
    }
}
