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

    /**
     * Where players wait before the match starts. An arena whose lobby is not {@link #lobbyReady}
     * yet finishes finding it on the spot, which may load terrain; matches wait for it instead.
     */
    Vec3 lobbyPosition();

    /**
     * Advances work the arena does over several ticks after opening, such as finding its lobby on
     * generated terrain without waiting for chunks, or pasting a map a few chunks at a time. The
     * match calls it once per server tick for as long as it is open.
     */
    default void prepare() {}

    /** Whether {@link #lobbyPosition} is known and loaded, so players can be moved there. */
    default boolean lobbyReady() {
        return true;
    }

    /**
     * Called every tick for each player waiting in the lobby once it is ready; arenas without
     * walls bring players who wandered off back to {@link #lobbyPosition}.
     */
    default void holdInLobby(ServerPlayer player) {}

    /** One spawn per team, spread out and facing the middle. */
    List<Spawn> spawns(int teamCount);

    /**
     * Advances nonblocking spawn preparation once per server tick. False keeps participants in
     * the lobby; {@link #spawns} is only called after this returns true.
     */
    default boolean prepareSpawns(int teamCount) {
        return true;
    }

    /** Releases temporary spawn-loading tickets after players have been placed. */
    default void releaseSpawns() {}

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
