package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A player moved to another dimension, as a match moves its players into its lobby and back to the
 * hub, finds their footing where they land: their client's next ticks must not make the server load
 * the block they stood on before, at the same coordinates in the new dimension.
 */
public final class TeleportFootingGameTest {
    private TeleportFootingGameTest() {}

    public static void playerTeleportedToAnotherDimensionLoadsNothingWhereTheyStood(GameTestHelper context) {
        ServerLevel overworld = context.getLevel();
        ServerLevel end = overworld.getServer().getLevel(Level.END);
        ServerPlayer player = TestPlayers.connect(context, "footing_traveller");
        // Far from anything else in either dimension: a floor in the Overworld, then a pad in the End.
        ChunkPos stood = new ChunkPos(-61_000, 61_000);
        ChunkPos target = new ChunkPos(-61_064, 61_064);
        BlockPos floor = new BlockPos(stood.getMiddleBlockX(), 100, stood.getMiddleBlockZ());
        BlockPos pad = new BlockPos(target.getMiddleBlockX(), 100, target.getMiddleBlockZ());
        overworld.setChunkForced(stood.x(), stood.z(), true);
        end.setChunkForced(target.x(), target.z(), true);
        overworld.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        end.setBlockAndUpdate(pad, Blocks.OBSIDIAN.defaultBlockState());
        GameTestLifecycle.afterTest(context, () -> {
            overworld.setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
            end.setBlockAndUpdate(pad, Blocks.AIR.defaultBlockState());
            overworld.setChunkForced(stood.x(), stood.z(), false);
            end.setChunkForced(target.x(), target.z(), false);
            TestPlayers.disconnect(player);
        });
        player.setGameMode(GameType.SURVIVAL);
        PlayerUtils.teleport(player, overworld, Vec3.atBottomCenterOf(floor.above()), 0.0F);
        // A client ticks its player every server tick; the server runs that tick's movement.
        for (int tick = 0; tick < 5; tick++) player.doTick();
        context.assertTrue(player.onGround() && player.mainSupportingBlockPos.filter(floor::equals).isPresent(),
                "Expected the player to stand on the Overworld floor, they stand on " + player.mainSupportingBlockPos + ".");
        context.assertTrue(end.getChunkSource().getChunkNow(stood.x(), stood.z()) == null,
                "Expected nothing loaded in the End where the player stands in the Overworld.");

        PlayerUtils.teleport(player, end, Vec3.atBottomCenterOf(pad.above()), 0.0F);
        for (int tick = 0; tick < 5; tick++) player.doTick();
        context.assertTrue(player.level() == end && player.blockPosition().equals(pad.above()),
                "Expected the player on the End pad, they are at " + player.blockPosition() + ".");
        context.assertTrue(end.getChunkSource().getChunkNow(stood.x(), stood.z()) == null,
                "Expected nothing loaded in the End where the player stood in the Overworld.");
        context.succeed();
    }
}
