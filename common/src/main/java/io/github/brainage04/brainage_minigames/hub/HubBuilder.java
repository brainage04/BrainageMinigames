package io.github.brainage04.brainage_minigames.hub;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a hub: the structure template {@value #TEMPLATE_PATH} when a data pack or the world's
 * {@code generated} folder provides one, otherwise a lit stone platform of radius {@value #RADIUS}
 * with a low wall and a sign listing the commands. Either is centred on the given position at the
 * height of the ground there.
 */
public final class HubBuilder {
    public static final String TEMPLATE_PATH = "brainage_minigames:hub";
    public static final int RADIUS = 12;

    /** Players arriving face north, towards the sign. */
    public static final float SPAWN_YAW = 180.0F;

    private static final Identifier TEMPLATE = BrainageMinigames.id("hub");
    private static final int HEADROOM = 10;
    private static final int FOUNDATION_DEPTH = 24;
    private static final int SIGN_DISTANCE = 3;

    private HubBuilder() {}

    /** Builds the hub around {@code center}'s column and returns where players should stand. */
    public static Vec3 build(ServerLevel level, BlockPos center) {
        loadChunks(level, center, RADIUS + 1);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        int floorY = Math.clamp(ground - 1, level.getMinY() + FOUNDATION_DEPTH, level.getMaxY() - HEADROOM - 1);
        BlockPos floorCenter = new BlockPos(center.getX(), floorY, center.getZ());
        Optional<StructureTemplate> template = level.getServer().getStructureManager().get(TEMPLATE);
        if (template.isPresent() && template.get().getSize().getX() > 0 && template.get().getSize().getZ() > 0) {
            return placeTemplate(level, template.get(), floorCenter);
        }
        buildPlatform(level, floorCenter);
        return Vec3.atBottomCenterOf(floorCenter.above());
    }

    private static Vec3 placeTemplate(ServerLevel level, StructureTemplate template, BlockPos floorCenter) {
        Vec3i size = template.getSize();
        loadChunks(level, floorCenter, Math.max(size.getX(), size.getZ()) / 2 + 1);
        BlockPos origin = floorCenter.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floorCenter.getX(), floorCenter.getZ());
        return new Vec3(floorCenter.getX() + 0.5, top, floorCenter.getZ() + 0.5);
    }

    private static void buildPlatform(ServerLevel level, BlockPos floorCenter) {
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState rim = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState foundation = Blocks.STONE.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICK_WALL.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        double inner = (RADIUS - 1.5) * (RADIUS - 1.5);
        double outer = (RADIUS + 0.5) * (RADIUS + 0.5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double distance = dx * dx + dz * dz;
                if (distance > outer) continue;
                int x = floorCenter.getX() + dx;
                int z = floorCenter.getZ() + dz;
                for (int y = floorCenter.getY() + 1; y <= floorCenter.getY() + HEADROOM; y++) {
                    level.setBlock(pos.set(x, y, z), air, Block.UPDATE_ALL);
                }
                boolean edge = distance > inner;
                boolean lamp = !edge && dx % 4 == 0 && dz % 4 == 0 && (dx != 0 || dz != 0);
                level.setBlock(pos.set(x, floorCenter.getY(), z), edge ? rim : lamp ? light : floor, Block.UPDATE_ALL);
                for (int y = floorCenter.getY() - 1; y >= floorCenter.getY() - FOUNDATION_DEPTH; y--) {
                    BlockState below = level.getBlockState(pos.set(x, y, z));
                    if (!below.canBeReplaced() && below.getFluidState().isEmpty()) break;
                    level.setBlock(pos, foundation, Block.UPDATE_ALL);
                }
                // A low wall around the rim, open at the four compass points.
                if (edge && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
                    level.setBlock(pos.set(x, floorCenter.getY() + 1, z), wall, Block.UPDATE_ALL);
                }
            }
        }
        // Connect the wall pieces now that all of them stand.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                pos.set(floorCenter.getX() + dx, floorCenter.getY() + 1, floorCenter.getZ() + dz);
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.STONE_BRICK_WALL)) {
                    level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_ALL);
                }
            }
        }
        placeSign(level, floorCenter.offset(0, 1, -SIGN_DISTANCE));
    }

    /** A sign facing south, towards players standing at the spawn, listing the commands. */
    private static void placeSign(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(0, Component.literal("Minigames Hub"))
                    .setMessage(1, Component.literal("/minigames: play"))
                    .setMessage(2, Component.literal("/feedback <msg>"))
                    .setMessage(3, Component.literal("/hub: come back"))
                    .setHasGlowingText(true);
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }

    private static void loadChunks(ServerLevel level, BlockPos center, int radius) {
        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
                chunkX <= SectionPos.blockToSectionCoord(center.getX() + radius); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
                    chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + radius); chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }
}
