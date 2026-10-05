package io.github.brainage04.brainage_minigames.dimension;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Chunk, entity and point-of-interest writes that a stopping server skips because their files are
 * deleted right after: the UHC-style dimension pairs once they are marked for regeneration, and the
 * whole world of a GameTest server, which is recreated on every launch. Writes still queued when
 * the server begins to stop are dropped too. A running server writes everything as usual.
 */
public final class DiscardedWrites {
    private static final List<ResourceKey<Level>> RESET_DIMENSIONS = List.of(
            ModDimensions.UHC, ModDimensions.UHC_NETHER, ModDimensions.MEETUP,
            ModDimensions.MEETUP_NETHER, ModDimensions.FINAL_UHC, ModDimensions.FINAL_UHC_NETHER);

    private static volatile @Nullable MinecraftServer stopping;
    private static volatile boolean wholeWorld;

    private DiscardedWrites() {}

    /** The UHC-style dimension pairs, deleted together when they are regenerated. */
    public static List<ResourceKey<Level>> resetDimensions() {
        return RESET_DIMENSIONS;
    }

    /**
     * Called as {@code server} begins to stop.
     *
     * @param disposableWorld the whole world is deleted before the server next starts
     * @param resetPending the UHC-style dimension pairs are deleted once it has stopped
     */
    public static void serverStopping(MinecraftServer server, boolean disposableWorld, boolean resetPending) {
        wholeWorld = disposableWorld;
        stopping = disposableWorld || resetPending ? server : null;
    }

    /** Called when a server starts or has stopped: nothing is skipped until the next stop. */
    public static void clear() {
        stopping = null;
        wholeWorld = false;
    }

    /** Whether saving this level's chunks is skipped. */
    public static boolean chunks(ServerLevel level) {
        return stopping == level.getServer() && discarded(level.dimension());
    }

    /** Whether region-file writes for this dimension are skipped, including writes already queued. */
    public static boolean writes(ResourceKey<Level> dimension) {
        return stopping != null && discarded(dimension);
    }

    private static boolean discarded(ResourceKey<Level> dimension) {
        return wholeWorld || RESET_DIMENSIONS.contains(dimension);
    }
}
