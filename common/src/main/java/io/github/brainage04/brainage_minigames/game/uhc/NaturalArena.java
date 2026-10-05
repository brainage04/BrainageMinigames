package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

/**
 * A square of generated terrain with a match-local border. The dimension border is left alone;
 * each match occupies a separate region, sends its border only to its members and applies vanilla
 * outside-border damage.
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


    private final ServerLevel level;
    private final int centerX;
    private final int centerZ;
    private final WorldBorder border = new WorldBorder();
    private final Vec3 lobbyPosition;
    private final RandomSource spawnRandom;
    private final boolean registered;
    private boolean closed;

    private NaturalArena(
            ServerLevel level, int centerX, int centerZ, int size, boolean registered,
            RandomSource spawnRandom) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.registered = registered;
        this.spawnRandom = spawnRandom;
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamagePerBlock(DAMAGE_PER_BLOCK);
        border.setSafeZone(SAFE_ZONE);
        border.setWarningBlocks(5);
        border.setWarningTime(15);
        this.lobbyPosition = ground(centerX + 0.5, centerZ + 0.5);
    }


    /**
     * Opens a region whose middle is mostly dry land: up to {@link #CENTER_ATTEMPTS} regions are
     * probed at their centre and four points around it, and the first with all five dry, or else
     * the driest, is used.
     */
    static NaturalArena open(MinecraftServer server, ResourceKey<Level> dimension,
            int size, GameSettings settings) throws MatchException {
        ServerLevel level = NaturalTerrain.level(server, dimension);
        if (!UhcWorldCleanup.markForReset(server)) {
            throw new MatchException("The UHC-style dimensions could not be scheduled for regeneration.");
        }
        int[] best = null;
        int bestDry = -1;
        RandomSource regionRandom = NaturalTerrain.regionRandom(level, settings);
        for (int attempt = 0, probes = 0; attempt < 4096 && probes < CENTER_ATTEMPTS && bestDry < 5; attempt++) {
            int[] center = NaturalTerrain.randomRegionCenter(regionRandom);
            if (!NaturalTerrain.free(level, center[0], center[1], size)) continue;
            probes++;
            int dry = dryProbes(level, center[0], center[1], size / 4);
            if (dry > bestDry) {
                best = center;
                bestDry = dry;
            }
        }
        if (best == null) {
            throw new MatchException("No free region of " + dimension.identifier() + " was found; try again.");
        }
        NaturalTerrain.reserve(level, best[0], best[1], size);
        return new NaturalArena(level, best[0], best[1], size, true,
                NaturalTerrain.spawnRandom(level, settings));
    }

    /**
     * An unregistered arena around a given centre, for tests and externally managed regions.
     */
    public static NaturalArena at(ServerLevel level, int centerX, int centerZ, int size) {
        return new NaturalArena(level, centerX, centerZ, size, false, level.getRandom());
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
                        spawnRandom.nextDouble() * Math.PI * 2.0,
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
        tickBorder(level, border, viewers, alive);
    }

    /** Shared local-border ticking for generated arenas and the UHC deathmatch arena slot. */
    static void tickBorder(ServerLevel level, WorldBorder border,
            Collection<ServerPlayer> viewers, Collection<ServerPlayer> alive) {
        border.tick();
        if (level.getGameTime() % BORDER_RESEND_TICKS == 0) {
            viewers.forEach(player -> sendBorder(level, border, player));
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
        sendBorder(level, border, player);
    }

    static void sendBorder(ServerLevel level, WorldBorder border, ServerPlayer player) {
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
            NaturalTerrain.release(level, centerX, centerZ);
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
