package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Placement on generated terrain: finding dry ground, spreading teams over it, reserving separate
 * match regions and keeping lobby players nearby.
 */
final class NaturalTerrain {
    /** Candidate centres use this grid; reservations keep clearance based on the actual widths. */
    static final int REGION_SPACING = 2_000;

    private static final int REGION_RANGE = 2_000;

    private record Region(double x, double z, double width) {}
    private static final Map<ServerLevel, List<Region>> OPEN = new HashMap<>();

    static boolean inUse(MinecraftServer server) {
        for (ServerLevel level : OPEN.keySet()) if (level.getServer() == server) return true;
        return false;
    }

    static boolean free(ServerLevel level, double x, double z, double width) {
        for (Region region : OPEN.getOrDefault(level, List.of())) {
            double separation = (width + region.width) / 2.0 + 256;
            if (Math.abs(x - region.x) < separation && Math.abs(z - region.z) < separation) {
                return false;
            }
        }
        return true;
    }

    static void reserve(ServerLevel level, double x, double z, double width) {
        OPEN.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new Region(x, z, width));
    }

    static void release(ServerLevel level, double x, double z) {
        List<Region> regions = OPEN.get(level);
        if (regions == null) return;
        regions.removeIf(region -> region.x == x && region.z == z);
        if (regions.isEmpty()) OPEN.remove(level);
    }

    /**
     * How far a lobby, spawn or surface position may move to find dry ground, and the spacing of
     * the columns searched.
     */
    static final int DRY_SEARCH_RADIUS = 48;

    static final int DRY_SEARCH_STEP = 8;

    /** Lobby players who wander further than this, or drop this far below it, are brought back. */
    private static final double LOBBY_HOLD_DISTANCE = 16.0;

    /** Columns per side of the grid {@link #landShare} reads biomes on. */
    private static final int LAND_SAMPLES = 16;

    private NaturalTerrain() {}

    static ServerLevel level(MinecraftServer server, ResourceKey<net.minecraft.world.level.Level> dimension)
            throws MatchException {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            throw new MatchException("The " + dimension.identifier() + " dimension is unavailable.");
        }
        return level;
    }

    /** One source for the entire candidate search; an unset seed retains the world's live RNG. */
    static RandomSource regionRandom(ServerLevel level, GameSettings settings) {
        return settings.isOverridden(UhcGame.REGION_SEED)
                ? RandomSource.create(settings.get(UhcGame.REGION_SEED))
                : level.getRandom();
    }

    /** Spawn rotation has a separate stream, independent of how many regions were probed. */
    static RandomSource spawnRandom(ServerLevel level, GameSettings settings) {
        return settings.isOverridden(UhcGame.REGION_SEED)
                ? RandomSource.create(settings.get(UhcGame.REGION_SEED) ^ 0x535041574eL)
                : level.getRandom();
    }

    /** A random region centre on the shared placement grid, as x and z. */
    static int[] randomRegionCenter(RandomSource random) {
        return new int[] {
            (random.nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE) * REGION_SPACING,
            (random.nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE) * REGION_SPACING
        };
    }

    /**
     * The share of the square of side {@code size} around x, z that is neither ocean nor river,
     * read from the biome source at sea level, so nothing is generated or loaded.
     */
    static double landShare(ServerLevel level, int x, int z, double size) {
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int quartY = QuartPos.fromBlock(level.getSeaLevel());
        double step = size / LAND_SAMPLES;
        int land = 0;
        for (int i = 0; i < LAND_SAMPLES; i++) {
            for (int j = 0; j < LAND_SAMPLES; j++) {
                int sampleX = Mth.floor(x - size / 2.0 + (i + 0.5) * step);
                int sampleZ = Mth.floor(z - size / 2.0 + (j + 0.5) * step);
                Holder<Biome> biome =
                        biomes.getNoiseBiome(
                                QuartPos.fromBlock(sampleX),
                                quartY,
                                QuartPos.fromBlock(sampleZ),
                                sampler);
                if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER)) {
                    land++;
                }
            }
        }
        return land / (double) (LAND_SAMPLES * LAND_SAMPLES);
    }

    /**
     * Whether the biome at x, z at sea level is neither ocean nor river, read from the biome source
     * so nothing is generated or loaded; region choice uses it to expect dry ground there.
     */
    static boolean landAt(ServerLevel level, double x, double z) {
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(Mth.floor(x)),
                QuartPos.fromBlock(level.getSeaLevel()),
                QuartPos.fromBlock(Mth.floor(z)),
                level.getChunkSource().randomState().sampler());
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
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
