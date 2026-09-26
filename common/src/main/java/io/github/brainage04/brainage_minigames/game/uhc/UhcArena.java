package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A fresh, unexplored region of the dedicated UHC dimension, bounded by that dimension's world
 * border, and the matching region of the UHC nether, bounded by the same border scaled the way
 * portals scale coordinates. The borders belong to the whole dimensions, so only one UHC can run at
 * a time, and not while a {@link NaturalArena} is open there.
 */
public final class UhcArena implements Arena {
    /** Regions tried before settling for one whose centre has no dry ground nearby. */
    private static final int CENTER_ATTEMPTS = 8;

    /** Blocks kept between players brought back from the nether and the border. */
    private static final double RETURN_MARGIN = 8.0;

    /** How far, and in what steps, a player brought back from the nether looks for dry ground. */
    private static final int RETURN_SEARCH_REACH = 48;

    private static final int RETURN_SEARCH_STEP = 8;

    /** The UHC holding the borders of the UHC dimension and its nether, if one is running. */
    private static @Nullable UhcArena active;

    private final ServerLevel level;
    private final @Nullable ServerLevel nether;
    private final int centerX;
    private final int centerZ;
    private final double startSize;
    private final Vec3 lobbyPosition;
    private boolean netherOpen;
    private boolean closed;

    private UhcArena(
            ServerLevel level,
            @Nullable ServerLevel nether,
            int centerX,
            int centerZ,
            double startSize,
            Vec3 lobbyPosition) {
        this.level = level;
        this.nether = nether;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.startSize = startSize;
        this.lobbyPosition = lobbyPosition;
        this.netherOpen = nether != null;
    }

    /** Whether a UHC holds the UHC dimension's world border. */
    static boolean inUse() {
        return active != null;
    }

    /** The running UHC, whose borders are those of the UHC dimension and its nether. */
    static Optional<UhcArena> active() {
        return Optional.ofNullable(active);
    }

    /**
     * Opens a random region of the UHC dimension; with {@code withNether}, the UHC nether is part
     * of the arena and open until {@link #closeNether}.
     */
    static UhcArena open(MinecraftServer server, int borderSize, boolean withNether)
            throws MatchException {
        ServerLevel level = NaturalTerrain.uhcLevel(server);
        if (active != null) {
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
        ServerLevel nether = withNether ? server.getLevel(ModDimensions.UHC_NETHER) : null;
        UhcArena arena =
                new UhcArena(
                        level,
                        nether,
                        centerX,
                        centerZ,
                        borderSize,
                        lobby.orElse(NaturalTerrain.surface(level, centerX + 0.5, centerZ + 0.5)));
        setUpBorder(level.getWorldBorder(), centerX, centerZ, borderSize);
        if (nether != null) {
            double scale = arena.netherScale();
            setUpBorder(
                    nether.getWorldBorder(), centerX * scale, centerZ * scale, borderSize * scale);
        }
        active = arena;
        return arena;
    }

    private static void setUpBorder(
            WorldBorder border, double centerX, double centerZ, double size) {
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamagePerBlock(0.2);
        border.setSafeZone(5.0);
        border.setWarningBlocks(10);
        border.setWarningTime(15);
    }

    /**
     * An arena around a given centre of any level that leaves the level's world border alone, has
     * no nether and need not be closed. GameTests use it in the Overworld when they need no UHC
     * dimension.
     */
    public static UhcArena at(ServerLevel level, int centerX, int centerZ, int borderSize) {
        return new UhcArena(
                level,
                null,
                centerX,
                centerZ,
                borderSize,
                NaturalTerrain.onGround(level, centerX + 0.5, centerZ + 0.5));
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    /** The UHC nether, while this match's players may travel to it. */
    public Optional<ServerLevel> openNether() {
        return netherOpen ? Optional.ofNullable(nether) : Optional.empty();
    }

    /** Whether this arena has a nether, open or closed. */
    public boolean hasNether() {
        return nether != null;
    }

    /** Whether the player is in this arena's nether. */
    public boolean inNether(ServerPlayer player) {
        return nether != null && player.level() == nether;
    }

    /** How the nether scales coordinates of the UHC dimension: 1/8, as vanilla portals do. */
    private double netherScale() {
        return nether == null
                ? 1.0
                : DimensionType.getTeleportationScale(
                        level.dimensionType(), nether.dimensionType());
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

    /** Shrinks the border, and the nether's border in proportion. */
    void shrinkBorder(double targetSize, long durationTicks) {
        WorldBorder border = level.getWorldBorder();
        border.lerpSizeBetween(border.getSize(), targetSize, durationTicks, level.getGameTime());
        if (nether != null) {
            WorldBorder netherBorder = nether.getWorldBorder();
            netherBorder.lerpSizeBetween(
                    netherBorder.getSize(),
                    targetSize * netherScale(),
                    durationTicks,
                    nether.getGameTime());
        }
    }

    /**
     * Stops portals leading into the nether and brings every given player who is still there back
     * with {@link #moveToSurface}.
     */
    void closeNether(Collection<ServerPlayer> players) {
        netherOpen = false;
        for (ServerPlayer player : players) {
            if (inNether(player)) {
                moveToSurface(player);
            }
        }
    }

    /**
     * Moves a player in the arena straight up or down to the nearest dry ground; a player in the
     * nether goes to dry ground at the matching overworld position, pulled inside the border and
     * the size it is shrinking to.
     */
    public void moveToSurface(ServerPlayer player) {
        if (player.level() == level) {
            NaturalTerrain.moveToSurface(player, level);
            return;
        }
        double scale =
                DimensionType.getTeleportationScale(
                        player.level().dimensionType(), level.dimensionType());
        PlayerUtils.teleport(
                player,
                level,
                groundInsideBorder(player.getX() * scale, player.getZ() * scale),
                player.getYRot());
    }

    private Vec3 groundInsideBorder(double x, double z) {
        WorldBorder border = level.getWorldBorder();
        double half =
                Math.max(
                        0.0,
                        Math.min(border.getSize(), border.getLerpTarget()) / 2.0 - RETURN_MARGIN);
        double insideX = Math.clamp(x, border.getCenterX() - half, border.getCenterX() + half);
        double insideZ = Math.clamp(z, border.getCenterZ() - half, border.getCenterZ() + half);
        double used =
                Math.max(
                        Math.abs(insideX - border.getCenterX()),
                        Math.abs(insideZ - border.getCenterZ()));
        int reach = Math.min(RETURN_SEARCH_REACH, Mth.floor(half - used));
        return NaturalTerrain.dryNear(level, insideX, insideZ, reach, RETURN_SEARCH_STEP)
                .orElseGet(() -> NaturalTerrain.surface(level, insideX, insideZ));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        netherOpen = false;
        resetBorder(level.getWorldBorder());
        if (nether != null) {
            resetBorder(nether.getWorldBorder());
        }
        if (active == this) {
            active = null;
        }
    }

    private static void resetBorder(WorldBorder border) {
        border.setCenter(0.0, 0.0);
        border.setSize(WorldBorder.MAX_SIZE);
    }
}
