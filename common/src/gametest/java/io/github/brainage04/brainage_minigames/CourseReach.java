package io.github.brainage04.brainage_minigames;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The places on a pasted map a runner can get to from a starting point with jumps a sprinting
 * player can make, as tools/maps/parkour.py builds courses: a gap of up to four blocks on the
 * level, three blocks one block up, nothing higher than a jump; ladders, vines and other climbable
 * blocks are climbed. A place is a block a runner fits on top of (or a climbable block they hold
 * on to), with the height of their feet.
 */
final class CourseReach {
    /** How high a jump lifts the feet. */
    private static final double JUMP = 1.25;

    private static final double PLAYER_HEIGHT = 1.8;

    /** The furthest jump between block centres considered; see {@link #legalJump}. */
    private static final int RANGE = 6;

    record Place(BlockPos block, double feet, boolean climbing) {
        Vec3 position() {
            return new Vec3(block.getX() + 0.5, feet, block.getZ() + 0.5);
        }

        boolean in(AABB box) {
            return box.contains(new Vec3(block.getX() + 0.5, feet + 0.1, block.getZ() + 0.5));
        }
    }

    private final ServerLevel level;
    private final Map<Long, List<Place>> columns = new HashMap<>();

    CourseReach(ServerLevel level, BoundingBox bounds) {
        this.level = level;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY() + 1; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.is(BlockTags.CLIMBABLE)) {
                        add(new Place(pos.immutable(), y, true));
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    double feet = y + shape.max(Direction.Axis.Y);
                    if (fits(x, feet, z, y)) add(new Place(pos.immutable(), feet, false));
                }
            }
        }
    }

    /** Whether a jump between block centres {@code distance} apart that rises {@code rise} works. */
    static boolean legalJump(double distance, double rise) {
        if (rise > JUMP) return false;
        if (rise > 0.5) return distance <= 4.1;
        if (rise > 0.0) return distance <= 4.6;
        return distance <= 5.1;
    }

    /** The place a player standing at {@code feet} stands on. */
    Place at(Vec3 feet) {
        BlockPos below = BlockPos.containing(feet.x(), feet.y() - 0.5, feet.z());
        List<Place> column = columns.getOrDefault(column(below.getX(), below.getZ()), List.of());
        return column.stream()
                .filter(place -> !place.climbing() && Math.abs(place.feet() - feet.y()) < 0.6)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nothing to stand on at " + feet + "."));
    }

    /** Where a player dropped at {@code position} lands: the highest place at or below it. */
    Place landing(Vec3 position) {
        BlockPos column = BlockPos.containing(position);
        return columns.getOrDefault(column(column.getX(), column.getZ()), List.of()).stream()
                .filter(place -> !place.climbing() && place.feet() <= position.y() + 1.0E-3)
                .max(java.util.Comparator.comparingDouble(Place::feet))
                .orElseThrow(() -> new AssertionError("Nothing to land on below " + position + "."));
    }

    /**
     * Every place reachable from {@code start} without the feet ever dropping below {@code lowest}
     * (the game counts that as a fall) and without entering a place {@code avoid} accepts.
     */
    Set<Place> from(Place start, double lowest, Predicate<Place> avoid) {
        Set<Place> seen = new HashSet<>();
        ArrayDeque<Place> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Place from = queue.poll();
            for (Place to : neighbours(from)) {
                if (to.feet() < lowest || avoid.test(to) || !seen.add(to)) continue;
                queue.add(to);
            }
        }
        return seen;
    }

    private List<Place> neighbours(Place from) {
        List<Place> result = new ArrayList<>();
        int x = from.block().getX();
        int z = from.block().getZ();
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                double distance = Math.hypot(dx, dz);
                for (Place to : columns.getOrDefault(column(x + dx, z + dz), List.of())) {
                    if (to.equals(from)) continue;
                    if (to.climbing()) {
                        // Climbing moves along a ladder one block at a time; a jump (or a fall)
                        // catches one where the feet pass, at most a jump above the take-off.
                        boolean along = from.climbing() && distance <= 1.0
                                && Math.abs(to.feet() - from.feet()) <= 1.0;
                        boolean caught = distance <= 4.1 && to.feet() <= from.feet() + JUMP;
                        if (along || caught) result.add(to);
                    } else if (legalJump(distance, to.feet() - from.feet()) && clear(from, to)) {
                        result.add(to);
                    }
                }
            }
        }
        return result;
    }

    /** Whether nothing solid stands in the way of the body at the height the jump passes. */
    private boolean clear(Place from, Place to) {
        Vec3 a = from.position();
        Vec3 b = to.position();
        double height = Math.max(a.y(), b.y()) + 0.1;
        int samples = (int) Math.ceil(Math.hypot(b.x() - a.x(), b.z() - a.z()) * 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 1; i < samples; i++) {
            double t = (double) i / samples;
            double x = a.x() + (b.x() - a.x()) * t;
            double z = a.z() + (b.z() - a.z()) * t;
            for (int k = (int) Math.floor(height); k <= (int) Math.floor(height + PLAYER_HEIGHT); k++) {
                pos.set(x, k, z);
                if (pos.equals(from.block()) || pos.equals(to.block())) continue;
                BlockState state = level.getBlockState(pos);
                if (!state.is(BlockTags.CLIMBABLE)
                        && blocks(state.getCollisionShape(level, pos), height - k)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a runner fits standing at {@code feet} on the block at height {@code y}. */
    private boolean fits(int x, double feet, int z, int y) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = (int) Math.floor(feet); k <= (int) Math.floor(feet + PLAYER_HEIGHT - 1.0E-3); k++) {
            if (k == y) continue;
            pos.set(x, k, z);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.CLIMBABLE)) continue;
            if (blocks(state.getCollisionShape(level, pos), feet - k)) return false;
        }
        return true;
    }

    /** Whether a collision shape reaches above {@code from} blocks into its cell. */
    private static boolean blocks(VoxelShape shape, double from) {
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > Math.max(0.0, from) + 1.0E-3
                && shape.min(Direction.Axis.Y) < from + PLAYER_HEIGHT;
    }

    private void add(Place place) {
        columns.computeIfAbsent(column(place.block().getX(), place.block().getZ()), key -> new ArrayList<>())
                .add(place);
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
