package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lobby of a region of generated terrain: the nearest dry ground to the region's centre, found
 * over several ticks while its chunks generate off the server thread, then kept loaded while the
 * arena is open so players arriving there never wait for terrain.
 */
final class NaturalLobby implements AutoCloseable {
    /** Chunks kept loaded around the lobby, so arriving players stand on loaded ground. */
    private static final int HOLD_RADIUS = 2;

    private final @Nullable ServerLevel level;
    private final double x;
    private final double z;
    private final int reach;
    private final int step;
    private @Nullable NaturalSpawnPreparation search;
    private @Nullable Vec3 position;
    private @Nullable ChunkPos held;
    private boolean closed;

    /**
     * Searches dry ground at most {@code reach} blocks from the centre of a region of side {@code
     * size}, in steps of {@code step}.
     */
    NaturalLobby(ServerLevel level, int centerX, int centerZ, double size, int reach, int step) {
        this.level = level;
        this.x = centerX + 0.5;
        this.z = centerZ + 0.5;
        this.reach = reach;
        this.step = step;
        List<BlockPos> offsets = NaturalSpawnPreparation.searchOffsets(reach, step);
        search = new NaturalSpawnPreparation(level, x, z, size, List.of(new Arena.Spawn(new Vec3(x, 0, z), 0.0F)),
                point -> offsets);
    }

    /** A lobby already found, which holds no chunks: arenas placed directly use it. */
    static NaturalLobby found(Vec3 position) {
        return new NaturalLobby(position);
    }

    private NaturalLobby(Vec3 position) {
        this.level = null;
        this.x = position.x();
        this.z = position.z();
        this.reach = 0;
        this.step = 1;
        this.position = position;
        this.closed = true;
    }

    /** Advances the search by one server tick's worth of loaded columns. */
    void tick() {
        if (position == null && search != null && search.tick()) {
            settle(search.spawns().getFirst().position());
        }
    }

    boolean ready() {
        return position != null;
    }

    /** The lobby; when the search has not finished, it is finished now, loading what it needs. */
    Vec3 position() {
        if (position == null) {
            settle(NaturalTerrain.dryNear(level, x, z, reach, step)
                    .orElseGet(() -> NaturalTerrain.surface(level, x, z)));
        }
        return position;
    }

    private void settle(Vec3 found) {
        position = found;
        if (closed) return;
        held = new ChunkPos(Mth.floor(found.x()) >> 4, Mth.floor(found.z()) >> 4);
        // Hold the lobby's chunks before the search lets go of the ones it loaded.
        level.getChunkSource().addTicketWithRadius(NaturalSpawnPreparation.TICKET, held, HOLD_RADIUS);
        if (search != null) {
            search.close();
            search = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        if (search != null) {
            search.close();
            search = null;
        }
        if (held != null) {
            level.getChunkSource().removeTicketWithRadius(NaturalSpawnPreparation.TICKET, held, HOLD_RADIUS);
            held = null;
        }
    }
}
