package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules.Ore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Timber and Vein Miner: breaking one natural log or ore also breaks the connected logs of the same
 * kind, or the connected ore of the same resource, as if the player broke each with the same tool.
 * Every block therefore costs durability and rolls its own loot, with Fortune and the drop rules;
 * the drops land where the player broke the first block.
 */
public final class UhcChainBreaks {
    /** Logs in one felled tree, the broken one included: more than the largest vanilla trees have. */
    public static final int TIMBER_LIMIT = 256;
    /** Ore blocks in one mined vein, the broken one included. */
    public static final int VEIN_LIMIT = 64;

    private static @Nullable BlockPos dropOrigin;

    private UhcChainBreaks() {}

    /** Where a block dropping at {@code pos} drops its items: the first block's position during a chain. */
    public static BlockPos dropPosition(BlockPos pos) {
        return dropOrigin == null ? pos : dropOrigin;
    }

    /**
     * After {@code player} broke {@code state} at {@code origin}, holding {@code tool} ({@code held}
     * if it was a real item) that could {@code harvest} it.
     */
    static void broken(ServerPlayer player, BlockPos origin, BlockState state, ItemStack tool, boolean held,
            boolean harvested) {
        if (dropOrigin != null) return;
        ServerLevel level = player.level();
        Predicate<BlockState> member;
        int limit;
        if (state.is(BlockTags.LOGS)) {
            if (!UhcResourceScenarios.timber(level, player)) return;
            Block block = state.getBlock();
            member = candidate -> candidate.is(block);
            limit = TIMBER_LIMIT;
        } else {
            Ore ore = UhcResourceRules.ore(state);
            // Ore the tool cannot harvest drops nothing, so its vein stays.
            if (ore == null || !harvested || !UhcResourceScenarios.on(level, UhcResourceScenarios.VEIN_MINER)) return;
            member = candidate -> UhcResourceRules.ore(candidate) == ore;
            limit = VEIN_LIMIT;
        }
        if (UhcResourceRules.placed(level, origin)) return;
        List<BlockPos> targets = connected(level, origin, member, limit - 1);
        if (targets.isEmpty()) return;
        dropOrigin = origin.immutable();
        try {
            for (BlockPos pos : targets) {
                // A broken tool ends the chain, like a player whose pickaxe breaks mid-vein.
                if (!player.isAlive() || held && (tool.isEmpty() || player.getMainHandItem() != tool)) break;
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            dropOrigin = null;
        }
    }

    /**
     * Up to {@code limit} natural blocks matching {@code member} connected to {@code origin}, also
     * diagonally, nearest first. Only blocks in loaded chunks are read; nothing is loaded.
     */
    public static List<BlockPos> connected(ServerLevel level, BlockPos origin, Predicate<BlockState> member, int limit) {
        List<BlockPos> found = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin.asLong());
        queue.add(origin);
        LevelChunk chunk = null;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                BlockPos next = current.offset(x, y, z);
                if (!seen.add(next.asLong()) || level.isOutsideBuildHeight(next)) continue;
                int chunkX = SectionPos.blockToSectionCoord(next.getX()), chunkZ = SectionPos.blockToSectionCoord(next.getZ());
                if (chunk == null || chunk.getPos().x() != chunkX || chunk.getPos().z() != chunkZ) {
                    chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                }
                if (!member.test(chunk.getBlockState(next)) || UhcResourceRules.placed(level, next)) continue;
                found.add(next);
                if (found.size() >= limit) return found;
                queue.addLast(next);
            }
        }
        return found;
    }
}
