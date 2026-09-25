package io.github.brainage04.brainage_minigames.game.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** The space one match is played in. */
public interface Arena {
    ServerLevel level();

    /** Where players wait before the match starts. */
    Vec3 lobbyPosition();

    /**
     * Called every tick for each player waiting in the lobby; arenas without walls bring players
     * who wandered off back to {@link #lobbyPosition}.
     */
    default void holdInLobby(ServerPlayer player) {}

    /** One spawn per team, spread out and facing the middle. */
    List<Spawn> spawns(int teamCount);

    /**
     * The most teams {@link #spawns} can place; matches refuse layouts and free-for-all joins
     * beyond it.
     */
    default int maxTeams() {
        return Integer.MAX_VALUE;
    }

    /** Alive participants below this height die at once, as if they fell into the void. */
    default double voidY() {
        return Double.NEGATIVE_INFINITY;
    }

    /** Whether participants may place blocks at {@code pos}; the default for games' place rules. */
    default boolean canBuild(BlockPos pos) {
        return true;
    }

    /** Releases the space so another match can use it. */
    void close();

    record Spawn(Vec3 position, float yaw) {}

    /**
     * Spawns evenly spaced on a circle around the centre, each facing inwards; {@code surfaceY}
     * maps x, z to y.
     */
    static List<Spawn> ring(
            double centerX,
            double centerZ,
            double radius,
            int count,
            double startAngle,
            DoubleBinaryOperator surfaceY) {
        List<Spawn> spawns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = startAngle + Math.PI * 2.0 * index / count;
            double x = Math.floor(centerX + Math.cos(angle) * radius) + 0.5;
            double z = Math.floor(centerZ + Math.sin(angle) * radius) + 0.5;
            // Minecraft faces (-sin(yaw), cos(yaw)); point that at the centre.
            float yaw = (float) Math.toDegrees(Math.atan2(x - centerX, -(z - centerZ)));
            spawns.add(new Spawn(new Vec3(x, surfaceY.applyAsDouble(x, z), z), yaw));
        }
        return spawns;
    }
}
