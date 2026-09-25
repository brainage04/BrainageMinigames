package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Placement on generated terrain, shared by every arena in the UHC dimension: finding dry ground,
 * spreading teams over it and keeping lobby players nearby.
 */
final class NaturalTerrain {
    /** Region centres are multiples of this, far enough apart that regions never meet. */
    static final int REGION_SPACING = 2_000;

    private static final int REGION_RANGE = 2_000;

    /**
     * How far a lobby, spawn or surface position may move to find dry ground, and the spacing of
     * the columns searched.
     */
    private static final int DRY_SEARCH_RADIUS = 48;

    private static final int DRY_SEARCH_STEP = 8;

    /** Lobby players who wander further than this, or drop this far below it, are brought back. */
    private static final double LOBBY_HOLD_DISTANCE = 16.0;

    private NaturalTerrain() {}

    static ServerLevel uhcLevel(MinecraftServer server) throws MatchException {
        ServerLevel level = server.getLevel(ModDimensions.UHC);
        if (level == null) {
            throw new MatchException("The UHC dimension is unavailable.");
        }
        return level;
    }

    /** A random region centre of the UHC dimension, as x and z. */
    static int[] randomRegionCenter(ServerLevel level) {
        return new int[] {
            (level.getRandom().nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE) * REGION_SPACING,
            (level.getRandom().nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE) * REGION_SPACING
        };
    }

    /**
     * {@code count} spawns evenly spaced on a circle around the centre, each facing inwards and
     * moved to the nearest dry ground; the ring only picks the spots, so their chunks load once.
     */
    static List<Arena.Spawn> spreadOnGround(
            ServerLevel level, double centerX, double centerZ, double radius, int count) {
        double startAngle = level.getRandom().nextDouble() * Math.PI * 2.0;
        return Arena.ring(centerX, centerZ, radius, count, startAngle, (x, z) -> 0).stream()
                .map(
                        spawn ->
                                new Arena.Spawn(
                                        onGround(level, spawn.position().x(), spawn.position().z()),
                                        spawn.yaw()))
                .toList();
    }

    /** Brings a lobby player who wandered off, fell or left the level back to the lobby. */
    static void holdNear(ServerPlayer player, ServerLevel level, Vec3 lobby) {
        double dx = player.getX() - lobby.x();
        double dz = player.getZ() - lobby.z();
        if (player.level() != level
                || dx * dx + dz * dz > LOBBY_HOLD_DISTANCE * LOBBY_HOLD_DISTANCE
                || player.getY() < lobby.y() - LOBBY_HOLD_DISTANCE) {
            PlayerUtils.teleport(player, level, lobby, player.getYRot());
        }
    }

    /** Moves the player straight up or down to the nearest dry ground. */
    static void moveToSurface(ServerPlayer player, ServerLevel level) {
        PlayerUtils.teleport(
                player, level, onGround(level, player.getX(), player.getZ()), player.getYRot());
    }

    /** The nearest dry ground to x, z, or the surface there, even water, if there is none. */
    static Vec3 onGround(ServerLevel level, double x, double z) {
        return dryNear(level, x, z).orElseGet(() -> surface(level, x, z));
    }

    /**
     * The closest surface to x, z, searched in growing squares, that stands on solid ground rather
     * than water or lava.
     */
    static Optional<Vec3> dryNear(ServerLevel level, double x, double z) {
        return dryNear(level, x, z, DRY_SEARCH_RADIUS, DRY_SEARCH_STEP);
    }

    /** As {@link #dryNear(ServerLevel, double, double)}, at most {@code reach} blocks away. */
    static Optional<Vec3> dryNear(ServerLevel level, double x, double z, int reach, int step) {
        for (int radius = 0; radius <= reach; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    Vec3 candidate = surface(level, x + dx, z + dz);
                    if (isDry(level, candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a surface position stands on a solid, fluid-free block. The heightmap ignores leaves,
     * so the ground is never a tree top; it does count fluids, so an ocean's surface is its water.
     */
    static boolean isDry(ServerLevel level, Vec3 position) {
        BlockPos ground = BlockPos.containing(position).below();
        return ground.getY() >= level.getMinY()
                && level.getFluidState(ground).isEmpty()
                && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP);
    }

    /**
     * The top of the ground at x, z. The chunk is loaded, and generated if it has to be, first: the
     * heightmap of a chunk that is not loaded reads as the bottom of the world.
     */
    static Vec3 surface(ServerLevel level, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        level.getChunk(
                SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        return new Vec3(x, y, z);
    }
}
