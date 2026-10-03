package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.HashSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.phys.Vec3;

/** Fresh-chunk regressions; never warm the spread with blocking getChunk calls. */
public final class UhcSpawnGameTestFunctions {
    private UhcSpawnGameTestFunctions() {}

    // GameTest servers tick without the normal 50 ms pause. Loader metadata therefore gives
    // worker-thread chunk generation many test ticks instead of warming chunks synchronously.
    public static void awaitReady(GameTestHelper context, Match match, Runnable action) {
        context.startSequence().thenWaitUntil(() -> {
            context.assertFalse(match.preparingSpawns(), "Spawn terrain is still preparing");
            context.assertTrue(match.phase() == MatchPhase.COUNTDOWN || match.phase() == MatchPhase.ACTIVE,
                    "Match did not reach its countdown");
        }).thenExecute(action);
    }

    public static void ticketedSpread(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        double cx = 950_000.5, cz = 900_000.5;
        var preparation = new NaturalSpawnPreparation(level, cx, cz, 400, 1000, 50);
        context.runBeforeTestEnd(preparation::close);
        long started = System.nanoTime();
        context.assertFalse(preparation.tick(), "An unloaded fifty-player spread completed in one tick");
        context.assertTrue(System.nanoTime() - started < 1_000_000_000L,
                "Scheduling spawn tickets blocked the main thread for over a second");
        context.assertTrue(preparation.ticketCount() > 0
                        && preparation.ticketCount() <= NaturalSpawnPreparation.TICKETS_PER_TICK,
                "Spawn loading was not ticketed/bounded");
        int[] previous = {preparation.ticketCount()};
        boolean[] done = {false};
        context.onEachTick(() -> {
            if (done[0]) return;
            long before = System.nanoTime();
            boolean ready = preparation.tick();
            context.assertTrue(System.nanoTime() - before < 1_000_000_000L,
                    "A spawn preparation step blocked the main thread for over a second");
            context.assertTrue(preparation.ticketCount() - previous[0]
                            <= NaturalSpawnPreparation.TICKETS_PER_TICK,
                    "A spawn tick scheduled too many new chunk neighbourhoods");
            previous[0] = preparation.ticketCount();
            if (!ready) return;
            done[0] = true;
            var spawns = preparation.spawns();
            context.assertValueEqual(50, spawns.size(), "Fifty-player spawn count");
            var unique = new HashSet<Vec3>();
            TicketStorage storage = level.getChunkSource().getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (Arena.Spawn spawn : spawns) {
                Vec3 position = spawn.position();
                context.assertTrue(unique.add(position), "Two teams shared a flat-terrain spawn");
                context.assertTrue(NaturalTerrain.isDry(level, position), "Spawn was not solid dry land");
                context.assertTrue(Math.abs(position.x() - cx) < 500 && Math.abs(position.z() - cz) < 500,
                        "Spawn escaped the starting border");
                double distance = Math.hypot(position.x() - cx, position.z() - cz);
                context.assertTrue(distance >= 399 && distance <= 401, "The original ring spread changed");
                long key = ChunkPos.pack(net.minecraft.core.BlockPos.containing(position));
                context.assertTrue(storage.getTickets(key).stream()
                                .anyMatch(ticket -> ticket.getType() == NaturalSpawnPreparation.TICKET),
                        "Spawn was used without its preparation ticket");
                ChunkPos pos = ChunkPos.unpack(key);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    context.assertTrue(level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz) != null,
                            "Spawn neighbourhood was not FULL before use");
                }
            }
            // A completed async load must not drop the preparation's retained ticket.
            // Other teams may still be searching before the match can place anyone.
            context.runAfterDelay(40, () -> {
                for (Arena.Spawn spawn : spawns) {
                    long key = ChunkPos.pack(net.minecraft.core.BlockPos.containing(spawn.position()));
                    context.assertTrue(storage.getTickets(key).stream()
                                    .anyMatch(ticket -> ticket.getType() == NaturalSpawnPreparation.TICKET),
                            "Completed spawn loading discarded its retained ticket");
                    ChunkPos pos = ChunkPos.unpack(key);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        context.assertTrue(level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz) != null,
                                "A prepared spawn neighbourhood unloaded before placement");
                    }
                }
                preparation.close();
                for (Arena.Spawn spawn : spawns) {
                    long key = ChunkPos.pack(net.minecraft.core.BlockPos.containing(spawn.position()));
                    context.assertFalse(storage.getTickets(key).stream()
                                    .anyMatch(ticket -> ticket.getType() == NaturalSpawnPreparation.TICKET),
                            "Spawn preparation leaked a ticket");
                }
                context.succeed();
            });
        });
    }
}
