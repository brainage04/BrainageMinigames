package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Opening a UHC-style match and joining it at once, as the game menu does, never waits for the
 * region's terrain on the server thread: the lobby is found while its chunks generate, the opener
 * is moved there once it is ready, the UHC deathmatch arena is pasted, and cleared after a stop,
 * over later ticks and a Meetup's spawns are found the same way when it starts.
 */
public final class UhcLobbyPreparationGameTestFunctions {
    private UhcLobbyPreparationGameTestFunctions() {}

    public static void uhc(GameTestHelper context) {
        openAndJoin(context, Minigames.UHC, "lobby_uhc");
    }

    /**
     * Stopping a UHC clears its deathmatch arena a few chunks per tick, not all at once, and frees
     * the arena's chunks once it is clear.
     */
    public static void uhcStopClearsDeathmatchOverTicks(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        Match[] match = {null};
        GameTestLifecycle.afterTest(context, () -> {
            if (match[0] != null) MatchManager.stop(match[0]);
        });
        try {
            match[0] = MatchManager.open(server, Minigames.UHC, TeamLayout.FREE_FOR_ALL, null);
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
        UhcArena arena = (UhcArena) match[0].arena();
        MapArena deathmatch = arena.deathmatchArena();
        context.assertTrue(deathmatch != null, "UHC opened without a deathmatch arena");
        GameTestLifecycle.awaitPreparation(context, () -> arena.lobbyReady() && deathmatch.prepared(), () -> {
            ServerLevel level = deathmatch.level();
            BlockPos floor = BlockPos.containing(deathmatch.spawnsOf(1).getFirst().position()).below();
            long chunk = net.minecraft.world.level.ChunkPos.pack(floor);
            context.assertFalse(level.getBlockState(floor).isAir(), "The deathmatch arena has no floor under its first spawn");
            MatchManager.stop(match[0]);
            match[0] = null;
            context.assertFalse(level.getBlockState(floor).isAir(), "Stopping the UHC cleared its whole deathmatch arena at once");
            context.assertTrue(level.getForceLoadedChunks().contains(chunk), "The deathmatch arena's chunks were freed before it was clear");
            context.startSequence()
                    .thenWaitUntil(() -> context.assertTrue(level.getBlockState(floor).isAir()
                                    && !level.getForceLoadedChunks().contains(chunk),
                            "The closed deathmatch arena is not cleared and freed yet"))
                    .thenSucceed();
        });
    }

    /**
     * As for UHC, and a started Meetup finds its spawns while their chunks generate: with a wide
     * border the spawns are far from the lobby, so placing everyone at once would generate them
     * on the server thread.
     */
    public static void meetup(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int border = SettingsStorage.resolve(server, Minigames.MEETUP).get(MeetupGame.BORDER_START_SIZE);
        GameTestLifecycle.afterTest(context, () -> SettingsStorage.set(server, Minigames.MEETUP, MeetupGame.BORDER_START_SIZE, border));
        SettingsStorage.set(server, Minigames.MEETUP, MeetupGame.BORDER_START_SIZE, 1_000);
        openAndJoin(context, Minigames.MEETUP, "lobby_meetup");
    }

    private static void openAndJoin(GameTestHelper context, Minigame game, String name) {
        ServerPlayer player = TestPlayers.connect(context, name);
        ServerPlayer second = TestPlayers.connect(context, name + "_2");
        Match[] match = {null};
        GameTestLifecycle.afterTest(context, () -> {
            if (match[0] != null) MatchManager.stop(match[0]);
            TestPlayers.disconnect(player, second);
        });
        ServerLevel before = player.level();
        try {
            match[0] = MatchService.open(player, game, TeamLayout.FREE_FOR_ALL, null);
            MatchManager.join(player, match[0], 0);
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
        Arena arena = match[0].arena();
        ServerLevel level = arena.level();
        BlockPos centre = centre(arena);
        context.assertTrue(level.getChunkSource().getChunkNow(centre.getX() >> 4, centre.getZ() >> 4) == null,
                game.id() + ": opening loaded the region's centre on the server thread");
        context.assertFalse(arena.lobbyReady(), game.id() + ": opening waited for the lobby's terrain");
        context.assertTrue(player.level() == before,
                game.id() + ": the opener was moved before the lobby was ready");
        context.assertTrue(match[0].isWaiting(player.getUUID()), game.id() + ": the opener is not in the lobby");
        if (arena instanceof UhcArena uhc) {
            MapArena deathmatch = uhc.deathmatchArena();
            context.assertTrue(deathmatch != null && !deathmatch.prepared(),
                    "Opening UHC pasted its whole deathmatch arena at once");
        }
        GameTestLifecycle.awaitPreparation(context,
                () -> arena.lobbyReady() && player.level() == level && deathmatchPasted(arena),
                () -> {
                    Vec3 lobby = arena.lobbyPosition();
                    context.assertTrue(player.position().distanceTo(lobby) < 1.0,
                            game.id() + ": the opener is at " + player.position() + ", not the lobby " + lobby);
                    context.assertTrue(level.getChunkSource().getChunkNow(
                                    BlockPos.containing(lobby).getX() >> 4, BlockPos.containing(lobby).getZ() >> 4) != null,
                            game.id() + ": the lobby's chunk is not loaded");
                    if (arena instanceof UhcArena uhc) {
                        // The ticked search finds the lobby a synchronous search finds.
                        Vec3 expected = NaturalTerrain.onGround(level, centre.getX() + 0.5, centre.getZ() + 0.5);
                        context.assertTrue(expected.equals(lobby),
                                "The UHC lobby " + lobby + " differs from the synchronous search's " + expected);
                        MapArena deathmatch = uhc.deathmatchArena();
                        Arena.Spawn pad = deathmatch.spawnsOf(1).getFirst();
                        context.assertFalse(deathmatch.level().getBlockState(
                                        BlockPos.containing(pad.position()).below()).isAir(),
                                "The deathmatch arena has no floor under its first spawn");
                        context.succeed();
                        return;
                    }
                    startWhileSpawnsLoad(context, match[0], player, second);
                });
    }

    /** Starts the match and checks its players reach dry spawns only once they have loaded. */
    private static void startWhileSpawnsLoad(GameTestHelper context, Match match, ServerPlayer player, ServerPlayer second) {
        try {
            MatchManager.join(second, match, 0);
            match.start();
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
        context.assertTrue(match.preparingSpawns(),
                match.game().id() + ": starting placed everyone at once instead of preparing the spawns' terrain");
        GameTestLifecycle.awaitPreparation(context, () -> !match.preparingSpawns(), () -> {
            for (ServerPlayer placed : List.of(player, second)) {
                context.assertTrue(placed.level() == match.arena().level() && NaturalTerrain.isDry(placed.level(), placed.position()),
                        match.game().id() + ": " + placed.getScoreboardName() + " was not placed on dry ground: " + placed.position());
            }
            context.succeed();
        });
    }

    private static boolean deathmatchPasted(Arena arena) {
        return !(arena instanceof UhcArena uhc) || uhc.deathmatchArena() == null || uhc.deathmatchArena().prepared();
    }

    private static BlockPos centre(Arena arena) {
        if (arena instanceof UhcArena uhc) {
            return BlockPos.containing(uhc.border().getCenterX(), 0, uhc.border().getCenterZ());
        }
        NaturalArena natural = (NaturalArena) arena;
        return new BlockPos(natural.centerX(), 0, natural.centerZ());
    }
}
