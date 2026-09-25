package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

/**
 * A small square of generated terrain in the UHC dimension with a world border of its own. The
 * dimension's real border is left alone, so any number of these can be open at once, each in its
 * own far-apart region; the border is sent to the match's members alone, who see and collide with
 * it as usual, and players outside it are hurt the way vanilla hurts them. They cannot share the
 * dimension with a UHC, whose real border would reach them.
 */
public final class NaturalArena implements Arena {
    /** Regions tried before settling for the one with the most dry ground. */
    private static final int CENTER_ATTEMPTS = 8;

    /** Blocks between spawns and the border, and the least any spawn keeps from it. */
    private static final double SPAWN_MARGIN = 20.0;

    private static final double EDGE_MARGIN = 2.0;
    private static final double DAMAGE_PER_BLOCK = 0.5;
    private static final double SAFE_ZONE = 1.0;
    private static final int BORDER_RESEND_TICKS = 20;

    /** How far, and in what steps, spawns move to find dry ground; the border limits it too. */
    private static final int DRY_SEARCH_REACH = 32;

    private static final int DRY_SEARCH_STEP = 4;

    /** Region centres in use, as {@link #regionKey} values. */
    private static final Set<Long> OPEN = new HashSet<>();

    private final ServerLevel level;
    private final int centerX;
    private final int centerZ;
    private final WorldBorder border = new WorldBorder();
    private final Vec3 lobbyPosition;
    private final boolean registered;
    private boolean closed;

    private NaturalArena(
            ServerLevel level, int centerX, int centerZ, int size, boolean registered) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.registered = registered;
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamagePerBlock(DAMAGE_PER_BLOCK);
        border.setSafeZone(SAFE_ZONE);
        border.setWarningBlocks(5);
        border.setWarningTime(15);
        this.lobbyPosition = ground(centerX + 0.5, centerZ + 0.5);
    }

    /** Arenas of this kind currently open in the UHC dimension. */
    static int openCount() {
        return OPEN.size();
    }

    /**
     * Opens a region whose middle is mostly dry land: up to {@link #CENTER_ATTEMPTS} regions are
     * probed at their centre and four points around it, and the first with all five dry, or else
     * the driest, is used.
     */
    static NaturalArena open(MinecraftServer server, int size) throws MatchException {
        ServerLevel level = NaturalTerrain.uhcLevel(server);
        if (UhcArena.inUse()) {
            throw new MatchException(
                    "A UHC is running; its world border covers the whole UHC dimension.");
        }
        if (!UhcWorldCleanup.markForReset(server)) {
            throw new MatchException("The UHC dimension could not be scheduled for regeneration.");
        }
        int[] best = null;
        int bestDry = -1;
        for (int attempt = 0; attempt < CENTER_ATTEMPTS && bestDry < 5; attempt++) {
            int[] center = NaturalTerrain.randomRegionCenter(level);
            if (OPEN.contains(regionKey(center[0], center[1]))) {
                continue;
            }
            int dry = dryProbes(level, center[0], center[1], size / 4);
            if (dry > bestDry) {
                best = center;
                bestDry = dry;
            }
        }
        if (best == null) {
            throw new MatchException("No free region of the UHC dimension was found; try again.");
        }
        OPEN.add(regionKey(best[0], best[1]));
        return new NaturalArena(level, best[0], best[1], size, true);
    }

    /**
     * An arena around a given centre of any level that is not counted as open in the UHC dimension.
     * GameTests use it in the Overworld, as the GameTest server has no UHC dimension.
     */
    public static NaturalArena at(ServerLevel level, int centerX, int centerZ, int size) {
        return new NaturalArena(level, centerX, centerZ, size, false);
    }

    private static int dryProbes(ServerLevel level, int centerX, int centerZ, int offset) {
        int dry = 0;
        int[][] probes = {{0, 0}, {offset, 0}, {-offset, 0}, {0, offset}, {0, -offset}};
        for (int[] probe : probes) {
            Vec3 surface =
                    NaturalTerrain.surface(
                            level, centerX + probe[0] + 0.5, centerZ + probe[1] + 0.5);
            if (NaturalTerrain.isDry(level, surface)) {
                dry++;
            }
        }
        return dry;
    }

    private static long regionKey(int centerX, int centerZ) {
        return ((long) (centerX / NaturalTerrain.REGION_SPACING) << 32)
                ^ (centerZ / NaturalTerrain.REGION_SPACING & 0xFFFFFFFFL);
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 lobbyPosition() {
        return lobbyPosition;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    /** This arena's own border; never added to a level. */
    public WorldBorder border() {
        return border;
    }

    @Override
    public void holdInLobby(ServerPlayer player) {
        NaturalTerrain.holdNear(player, level, lobbyPosition);
        if (level.getGameTime() % BORDER_RESEND_TICKS == 0) {
            sendBorder(player);
        }
    }

    /** Spawns on a ring {@link #SPAWN_MARGIN} blocks inside the border, on dry ground inside it. */
    @Override
    public List<Spawn> spawns(int teamCount) {
        double radius = Math.max(4.0, border.getSize() / 2.0 - SPAWN_MARGIN);
        return Arena.ring(
                        centerX + 0.5,
                        centerZ + 0.5,
                        radius,
                        teamCount,
                        level.getRandom().nextDouble() * Math.PI * 2.0,
                        (x, z) -> 0)
                .stream()
                .map(
                        spawn ->
                                new Spawn(
                                        ground(spawn.position().x(), spawn.position().z()),
                                        spawn.yaw()))
                .toList();
    }

    /** Players may only build inside the border. */
    @Override
    public boolean canBuild(BlockPos pos) {
        return border.isWithinBounds(pos);
    }

    /**
     * The nearest dry ground to x, z that is inside the border, or the surface at x, z if there is
     * none.
     */
    private Vec3 ground(double x, double z) {
        double half = border.getSize() / 2.0 - EDGE_MARGIN;
        double used =
                Math.max(Math.abs(x - border.getCenterX()), Math.abs(z - border.getCenterZ()));
        int reach = Math.min(DRY_SEARCH_REACH, Math.max(0, Mth.floor(half - used)));
        return NaturalTerrain.dryNear(level, x, z, reach, DRY_SEARCH_STEP)
                .orElseGet(() -> NaturalTerrain.surface(level, x, z));
    }

    /** Starts shrinking the border to {@code targetSize} over {@code durationTicks}. */
    public void shrink(double targetSize, long durationTicks, Collection<ServerPlayer> viewers) {
        if (durationTicks > 0) {
            border.lerpSizeBetween(
                    border.getSize(), targetSize, durationTicks, level.getGameTime());
        } else {
            border.setSize(targetSize);
        }
        viewers.forEach(this::sendBorder);
    }

    /**
     * Moves the border on by a tick, keeps it on the viewers' screens and hurts {@code alive}
     * players outside it as vanilla would.
     */
    public void tick(Collection<ServerPlayer> viewers, Collection<ServerPlayer> alive) {
        border.tick();
        if (level.getGameTime() % BORDER_RESEND_TICKS == 0) {
            viewers.forEach(this::sendBorder);
        }
        for (ServerPlayer player : alive) {
            if (player.level() != level || player.isSpectator()) {
                continue;
            }
            double inside = border.getDistanceToBorder(player) + border.getSafeZone();
            if (inside < 0.0) {
                player.hurtServer(
                        level,
                        player.damageSources().outOfBorder(),
                        Math.max(1, Mth.floor(-inside * border.getDamagePerBlock())));
            }
        }
    }

    /** Shows this arena's border to a player in its level. */
    public void sendBorder(ServerPlayer player) {
        if (player.level() == level) {
            player.connection.send(new ClientboundInitializeBorderPacket(border));
        }
    }

    /** Puts the border of the level the player is in back on their screen. */
    public static void restoreBorder(ServerPlayer player) {
        player.connection.send(
                new ClientboundInitializeBorderPacket(player.level().getWorldBorder()));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (registered) {
            OPEN.remove(regionKey(centerX, centerZ));
        }
    }

    /** The border's side length, rounded. */
    public int size() {
        return (int) Math.round(border.getSize());
    }

    /** The size the border is moving to, or its size if it is not moving. */
    public int targetSize() {
        return (int) Math.round(border.getLerpTarget());
    }
}
