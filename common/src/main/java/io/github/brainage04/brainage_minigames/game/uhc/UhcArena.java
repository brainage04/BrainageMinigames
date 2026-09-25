package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

/**
 * A fresh, unexplored region of the dedicated UHC dimension, bounded by that dimension's world
 * border. The border belongs to the whole dimension, so only one UHC can run at a time, and not
 * while a {@link NaturalArena} is open there.
 */
public final class UhcArena implements Arena {
    /** Regions tried before settling for one whose centre has no dry ground nearby. */
    private static final int CENTER_ATTEMPTS = 8;

    private static boolean inUse;

    private final ServerLevel level;
    private final int centerX;
    private final int centerZ;
    private final double startSize;
    private final Vec3 lobbyPosition;
    private boolean closed;

    private UhcArena(
            ServerLevel level, int centerX, int centerZ, double startSize, Vec3 lobbyPosition) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.startSize = startSize;
        this.lobbyPosition = lobbyPosition;
    }

    /** Whether a UHC holds the UHC dimension's world border. */
    static boolean inUse() {
        return inUse;
    }

    static UhcArena open(MinecraftServer server, int borderSize) throws MatchException {
        ServerLevel level = NaturalTerrain.uhcLevel(server);
        if (inUse) {
            throw new MatchException(
                    "A UHC is already running; its dimension's world border can only host one at a time.");
        }
        if (NaturalArena.openCount() > 0) {
            throw new MatchException(
                    "A Meetup or FinalUHC match is using the UHC dimension; a UHC's world border would reach it.");
        }
        if (!UhcWorldCleanup.markForReset(server)) {
            throw new MatchException("The UHC dimension could not be scheduled for regeneration.");
        }
        // Regions centred in an ocean are skipped; after CENTER_ATTEMPTS the last one is used
        // anyway, with the lobby on the water's surface.
        int centerX = 0;
        int centerZ = 0;
        Optional<Vec3> lobby = Optional.empty();
        for (int attempt = 0; attempt < CENTER_ATTEMPTS && lobby.isEmpty(); attempt++) {
            int[] center = NaturalTerrain.randomRegionCenter(level);
            centerX = center[0];
            centerZ = center[1];
            lobby = NaturalTerrain.dryNear(level, centerX + 0.5, centerZ + 0.5);
        }
        UhcArena arena =
                new UhcArena(
                        level,
                        centerX,
                        centerZ,
                        borderSize,
                        lobby.orElse(NaturalTerrain.surface(level, centerX + 0.5, centerZ + 0.5)));
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setDamagePerBlock(0.2);
        border.setSafeZone(5.0);
        border.setWarningBlocks(10);
        border.setWarningTime(15);
        inUse = true;
        return arena;
    }

    /**
     * An arena around a given centre of any level that leaves the level's world border alone and
     * need not be closed. GameTests use it in the Overworld, as the GameTest server has no UHC
     * dimension.
     */
    public static UhcArena at(ServerLevel level, int centerX, int centerZ, int borderSize) {
        return new UhcArena(
                level,
                centerX,
                centerZ,
                borderSize,
                NaturalTerrain.onGround(level, centerX + 0.5, centerZ + 0.5));
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 lobbyPosition() {
        return lobbyPosition;
    }

    @Override
    public void holdInLobby(ServerPlayer player) {
        NaturalTerrain.holdNear(player, level, lobbyPosition);
    }

    @Override
    public List<Spawn> spawns(int teamCount) {
        double radius =
                Math.min(
                        Math.clamp(teamCount * 20.0, 80.0, 400.0),
                        Math.max(8.0, startSize / 2.0 - 16.0));
        return NaturalTerrain.spreadOnGround(
                level, centerX + 0.5, centerZ + 0.5, radius, teamCount);
    }

    void shrinkBorder(double targetSize, long durationTicks) {
        WorldBorder border = level.getWorldBorder();
        border.lerpSizeBetween(border.getSize(), targetSize, durationTicks, level.getGameTime());
    }

    public void moveToSurface(ServerPlayer player) {
        NaturalTerrain.moveToSurface(player, level);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(WorldBorder.MAX_SIZE);
        inUse = false;
    }
}
