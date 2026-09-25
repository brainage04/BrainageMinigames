package io.github.brainage04.brainage_minigames.game.arena;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A square room in the void minigames dimension: a bedrock floor under a one-block surface,
 * enclosed by barrier walls and ceiling. Each open arena occupies its own slot, so matches never
 * share space.
 */
public final class BoxArena implements Arena {
    private static final int SURFACE_Y = 64;
    private static final int INTERIOR_HEIGHT = 24;

    private final ServerLevel level;
    private final int slot;
    private final int centerX;
    private final int halfWidth;
    private final BoundingBox bounds;
    private boolean closed;

    private BoxArena(ServerLevel level, int slot, int size) {
        this.level = level;
        this.slot = slot;
        this.centerX = ArenaSlots.centerX(slot, 1);
        this.halfWidth = size / 2;
        // One block of wall around the interior, bedrock below the surface and a ceiling above the
        // interior.
        this.bounds =
                new BoundingBox(
                        centerX - halfWidth - 1,
                        SURFACE_Y - 1,
                        -halfWidth - 1,
                        centerX + halfWidth + 1,
                        SURFACE_Y + INTERIOR_HEIGHT + 1,
                        halfWidth + 1);
    }

    /** Builds an arena with a {@code size} by {@code size} interior in the first free slot. */
    public static BoxArena open(ServerLevel level, int size, BlockState surface) {
        int slot = ArenaSlots.allocate(1);
        BoxArena arena = new BoxArena(level, slot, size);
        arena.build(surface);
        return arena;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    private void build(BlockState surface) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        fill(
                (x, y, z) -> {
                    boolean wall =
                            x == bounds.minX()
                                    || x == bounds.maxX()
                                    || z == bounds.minZ()
                                    || z == bounds.maxZ();
                    if (y == bounds.minY()) {
                        return bedrock;
                    }
                    if (wall || y == bounds.maxY()) {
                        return barrier;
                    }
                    // Clearing the interior also removes anything a crashed match left behind in
                    // this slot.
                    return y == SURFACE_Y ? surface : air;
                });
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 lobbyPosition() {
        return new Vec3(centerX + 0.5, SURFACE_Y + 1, 0.5);
    }

    @Override
    public List<Spawn> spawns(int teamCount) {
        double radius = Math.max(1, halfWidth - 3);
        return Arena.ring(centerX + 0.5, 0.5, radius, teamCount, Math.PI, (x, z) -> SURFACE_Y + 1);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Entity> leftovers =
                level.getEntities(
                        (Entity) null,
                        AABB.of(bounds).inflate(1.0),
                        entity -> !(entity instanceof Player));
        leftovers.forEach(Entity::discard);
        BlockState air = Blocks.AIR.defaultBlockState();
        fill((x, y, z) -> air);
        ArenaSlots.release(slot, 1);
    }

    private void fill(BlockSupplier supplier) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    level.setBlock(pos.set(x, y, z), supplier.get(x, y, z), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @FunctionalInterface
    private interface BlockSupplier {
        BlockState get(int x, int y, int z);
    }
}
