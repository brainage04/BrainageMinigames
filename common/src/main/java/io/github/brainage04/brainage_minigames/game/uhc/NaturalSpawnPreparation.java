package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.arena.Arena;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Ticket-driven terrain searches. All world reads happen on the server thread, without waiting. */
final class NaturalSpawnPreparation implements AutoCloseable {
    static final int TICKETS_PER_TICK = 4;
    private static final int MAX_PENDING_CHUNKS = 8;
    private static final int COLUMNS_PER_TICK = 128;
    private static final int LOAD_RADIUS = 1;
    // Transient, loading-only tickets: do not simulate terrain or persist across a restart.
    // This flag combination is distinct from vanilla player/portal/forced tickets.
    static final TicketType TICKET = new TicketType(
            TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final List<BlockPos> OFFSETS = searchOffsets();

    private final ServerLevel level;
    private final double centerX;
    private final double centerZ;
    private final double halfSize;
    private final List<Search> searches;
    private final LongSet tickets = new LongOpenHashSet();
    private final LongSet pending = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
    private int lastTick = Integer.MIN_VALUE;
    private int cursor;
    private int remaining;
    private boolean closed;

    NaturalSpawnPreparation(ServerLevel level, double centerX, double centerZ,
            double radius, double size, int count) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.halfSize = size / 2.0;
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        searches = Arena.ring(centerX, centerZ, radius, count, angle, (x, z) -> 0).stream()
                .map(Search::new).toList();
        remaining = count;
    }

    boolean tick() {
        if (closed) throw new IllegalStateException("Spawn preparation is closed");
        if (remaining == 0) return true;
        int tick = level.getServer().getTickCount();
        if (lastTick == tick) return false;
        lastTick = tick;
        pending.removeIf((long key) -> loaded(ChunkPos.getX(key), ChunkPos.getZ(key)));
        int added = 0;
        int inspected = 0;
        int idle = 0;
        while (remaining > 0 && inspected < COLUMNS_PER_TICK && idle < searches.size()) {
            Search search = searches.get(cursor);
            cursor = (cursor + 1) % searches.size();
            if (search.result != null) { idle++; continue; }
            BlockPos offset = OFFSETS.get(search.column);
            double x = search.ring.position().x() + offset.getX();
            double z = search.ring.position().z() + offset.getZ();
            if (Math.abs(x - centerX + 0.5) >= halfSize || Math.abs(z - centerZ + 0.5) >= halfSize) {
                search.column++;
                inspected++;
                idle = 0;
            } else {
                int chunkX = Mth.floor(x) >> 4;
                int chunkZ = Mth.floor(z) >> 4;
                long key = ChunkPos.pack(chunkX, chunkZ);
                if (!tickets.contains(key)) {
                    if (added >= TICKETS_PER_TICK || pending.size() >= MAX_PENDING_CHUNKS) {
                        idle++;
                        continue;
                    }
                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                    // Retain our ticket independently: replacement chunk systems may only
                    // hold temporary tickets for addTicketAndLoadWithRadius's async load.
                    level.getChunkSource().addTicketWithRadius(TICKET, pos, LOAD_RADIUS);
                    tickets.add(key);
                    // Schedule FULL generation without waiting on the server thread.
                    level.getChunkSource().addTicketAndLoadWithRadius(TICKET, pos, LOAD_RADIUS);
                    pending.add(key);
                    added++;
                }
                if (!loaded(chunkX, chunkZ)) { idle++; continue; }
                pending.remove(key);
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Mth.floor(x) & 15, Mth.floor(z) & 15) + 1;
                if (search.column == 0) search.fallback = new Vec3(x, y, z);
                ground.set(Mth.floor(x), y - 1, Mth.floor(z));
                if (ground.getY() >= level.getMinY() && chunk.getFluidState(ground).isEmpty()
                        && chunk.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                    search.result = new Arena.Spawn(new Vec3(x, y, z), search.ring.yaw());
                    remaining--;
                } else {
                    search.column++;
                }
                inspected++;
                idle = 0;
            }
            if (search.result == null && search.column == OFFSETS.size()) {
                search.result = new Arena.Spawn(search.fallback, search.ring.yaw());
                remaining--;
            }
        }
        return remaining == 0;
    }

    List<Arena.Spawn> spawns() {
        if (remaining != 0 || closed) throw new IllegalStateException("Spawn terrain is not ready");
        return searches.stream().map(search -> search.result).toList();
    }

    private boolean loaded(int chunkX, int chunkZ) {
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                if (level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) return false;
            }
        }
        return true;
    }

    int ticketCount() { return tickets.size(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (long key : tickets) {
            level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.unpack(key), LOAD_RADIUS);
        }
        tickets.clear();
        pending.clear();
    }

    private static List<BlockPos> searchOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int radius = 0; radius <= NaturalTerrain.DRY_SEARCH_RADIUS;
                radius += NaturalTerrain.DRY_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += NaturalTerrain.DRY_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += NaturalTerrain.DRY_SEARCH_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static final class Search {
        private final Arena.Spawn ring;
        private int column;
        private Vec3 fallback;
        private Arena.Spawn result;
        private Search(Arena.Spawn ring) { this.ring = ring; }
    }
}
