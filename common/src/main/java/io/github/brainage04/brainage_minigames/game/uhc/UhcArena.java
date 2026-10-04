package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
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
    /** Regions tried for the one with the most land and dry ground at its centre. */
    private static final int CENTER_ATTEMPTS = 16;

    /** A region at least this much land, with dry ground at its centre, is taken at once. */
    private static final double GOOD_LAND_SHARE = 0.85;

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
    private boolean clockStarted;
    private final boolean badlion;
    private @Nullable MapArena deathmatchArena;
    private boolean deathmatchStarted;
    private final WorldBorder deathmatchBorder = new WorldBorder();
    private int deathmatchStartWidth = 113;
    private int deathmatchFinalWidth = 56;
    private int netherBorderDivisor = 8;
    private final Map<UUID, Spawn> frozenSpawns = new HashMap<>();
    private @Nullable NaturalSpawnPreparation spawnPreparation;
    private List<Spawn> preparedSpawns = List.of();

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
        this.badlion = UhcModeRules.badlion(level.getServer());
    }

    /** Whether a UHC holds the UHC dimension's world border. */
    static boolean inUse() {
        return active != null;
    }

    /** The running UHC, whose borders are those of the UHC dimension and its nether. */
    static Optional<UhcArena> active() {
        return Optional.ofNullable(active);
    }

    /** The lobby and countdown hold dawn until the match enters its active/grace phase. */
    static boolean waitingForStart(MinecraftServer server) {
        return active != null && active.level.getServer() == server && !active.clockStarted;
    }

    void startClock() {
        clockStarted = true;
        UhcClock.tick(level.getServer());
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
        // The region inside the border with the most land wins (read from biomes, so trying one
        // costs nothing): an ocean-heavy region spawns players in the water, far from trees and
        // ore. A dry centre for the lobby comes first; without one anywhere, the lobby is on the
        // water's surface.
        int centerX = 0;
        int centerZ = 0;
        Optional<Vec3> lobby = Optional.empty();
        double bestShare = -1.0;
        for (int attempt = 0; attempt < CENTER_ATTEMPTS; attempt++) {
            int[] center = NaturalTerrain.randomRegionCenter(level);
            double share = NaturalTerrain.landShare(level, center[0], center[1], borderSize);
            if (lobby.isPresent() && share <= bestShare) {
                continue;
            }
            Optional<Vec3> dry = NaturalTerrain.dryNear(level, center[0] + 0.5, center[1] + 0.5);
            if (lobby.isPresent() ? dry.isEmpty() : dry.isEmpty() && share <= bestShare) {
                continue;
            }
            centerX = center[0];
            centerZ = center[1];
            lobby = dry;
            bestShare = share;
            if (lobby.isPresent() && share >= GOOD_LAND_SHARE) {
                break;
            }
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
        UhcClock.tick(server);
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
        return deathmatchStarted ? deathmatchArena.level() : level;
    }

    /** Match-local in deathmatch; the minigames dimension's global border is never changed. */
    public WorldBorder border() {
        return deathmatchStarted ? deathmatchBorder : level.getWorldBorder();
    }

    @Override
    public double voidY() {
        return deathmatchStarted ? deathmatchArena.voidY() : Double.NEGATIVE_INFINITY;
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
        return deathmatchStarted ? deathmatchArena.lobbyPosition() : lobbyPosition;
    }

    @Override
    public void holdInLobby(ServerPlayer player) {
        NaturalTerrain.holdNear(player, level, lobbyPosition);
    }

    @Override
    public boolean prepareSpawns(int teamCount) {
        if (preparedSpawns.size() == teamCount) return true;
        if (spawnPreparation == null) {
            double radius =
                    Math.min(
                            Math.clamp(teamCount * 20.0, 80.0, 400.0),
                            Math.max(8.0, startSize / 2.0 - 16.0));
            spawnPreparation = new NaturalSpawnPreparation(
                    level, centerX + 0.5, centerZ + 0.5, radius, startSize, teamCount);
        }
        if (!spawnPreparation.tick()) return false;
        preparedSpawns = spawnPreparation.spawns();
        return true;
    }

    @Override
    public List<Spawn> spawns(int teamCount) {
        if (preparedSpawns.size() != teamCount) {
            throw new IllegalStateException("UHC spawn terrain has not been prepared");
        }
        return preparedSpawns;
    }

    @Override
    public void releaseSpawns() {
        if (spawnPreparation != null) {
            spawnPreparation.close();
            spawnPreparation = null;
        }
    }

    boolean badlion() {
        return badlion;
    }

    boolean deathmatchEnabled() {
        return deathmatchArena != null;
    }

    public boolean inDeathmatch() {
        return deathmatchStarted;
    }

    boolean deathmatchFrozen() {
        return !frozenSpawns.isEmpty();
    }
    /** Arrival pad is available for every alive UUID, including offline participants, during freeze. */
    public @Nullable Spawn deathmatchSpawn(UUID participant) {
        return frozenSpawns.get(participant);
    }


    /** Widths are captured when the match opens, independently of other arena slots. */
    public void configureDeathmatchBorder(int startWidth, int finalWidth) {
        deathmatchStartWidth = startWidth;
        deathmatchFinalWidth = finalWidth;
    }

    int deathmatchFinalWidth() {
        return deathmatchFinalWidth;
    }

    /** Portal coordinate scaling remains vanilla; only the Nether border width is configurable. */
    public void configureNetherBorderScale(int divisor) {
        netherBorderDivisor = divisor;
        if (nether != null) nether.getWorldBorder().setSize(level.getWorldBorder().getSize() / divisor);
    }

    void prepareDeathmatch() throws MatchException {
        ServerLevel arenaLevel = level.getServer().getLevel(ModDimensions.MINIGAMES);
        if (arenaLevel == null) throw new MatchException("The minigames dimension is unavailable.");
        deathmatchArena = MapArena.open(arenaLevel, BrainageMinigames.id("maps/uhc_deathmatch/colosseum"));
    }

    void startDeathmatch(Match match, int freezeTicks) {
        MapArena map = java.util.Objects.requireNonNull(deathmatchArena);
        netherOpen = false;
        deathmatchStarted = true;
        var bounds = map.bounds();
        setUpBorder(deathmatchBorder,
                (bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minZ() + bounds.maxZ() + 1) / 2.0, deathmatchStartWidth);
        map.level().getEntities((net.minecraft.world.entity.Entity) null,
                net.minecraft.world.phys.AABB.of(bounds).inflate(1),
                entity -> entity instanceof net.minecraft.world.entity.Mob
                        && !entity.entityTags().contains("brainage_minigames:combat_logger"))
                .forEach(net.minecraft.world.entity.Entity::discard);
        int teamCount = match.teams().size();
        for (var team : match.teams()) {
            int room = (team.number() - 1) * map.teamSlots() / teamCount + 1;
            Spawn spawn = map.spawnsOf(room).getFirst();
            for (UUID participant : team.members()) {
                if (match.isAlive(participant)) frozenSpawns.put(participant, spawn);
            }
        }
        for (ServerPlayer player : match.alivePlayers()) {
            Spawn spawn = frozenSpawns.get(player.getUUID());
            player.stopRiding();
            PlayerUtils.teleport(player, map.level(), spawn.position(), spawn.yaw());
            NaturalArena.sendBorder(map.level(), deathmatchBorder, player);
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            match.freeze(player, freezeTicks);
        }
        for (ServerPlayer spectator : match.onlineMembers()) {
            if (!match.isAlive(spectator.getUUID())) {
                PlayerUtils.teleport(spectator, map.level(), map.lobbyPosition(), spectator.getYRot());
                NaturalArena.sendBorder(map.level(), deathmatchBorder, spectator);
            }
        }
    }

    void holdDeathmatchSpawns(Match match) {
        for (ServerPlayer player : match.alivePlayers()) {
            Spawn spawn = frozenSpawns.get(player.getUUID());
            if (spawn != null) {
                player.setDeltaMovement(Vec3.ZERO);
                if (player.position().distanceToSqr(spawn.position()) > 0.001) {
                    PlayerUtils.teleport(player, level(), spawn.position(), player.getYRot());
                }
            }
        }
    }

    void releaseDeathmatch(Match match) {
        for (ServerPlayer player : match.alivePlayers()) {
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.MINING_FATIGUE);
            player.removeEffect(MobEffects.WEAKNESS);
            player.removeEffect(MobEffects.SLOW_FALLING);
        }
        frozenSpawns.clear();
    }

    void tickDeathmatchBorder(Match match) {
        NaturalArena.tickBorder(level(), deathmatchBorder, match.onlineMembers(), match.alivePlayers());
    }

    /** An instant Badlion-style shrink moves only players beyond the new edge, never insiders. */
    void instantShrink(double targetSize, Collection<ServerPlayer> players) {
        level.getWorldBorder().setSize(targetSize);
        if (nether != null) {
            nether.getWorldBorder().setSize(targetSize / netherBorderDivisor);
        }
        for (ServerPlayer player : players) {
            ServerLevel world = inNether(player) ? nether : level;
            WorldBorder border = world.getWorldBorder();
            double half = border.getSize() / 2.0;
            if (Math.abs(player.getX() - border.getCenterX()) <= half
                    && Math.abs(player.getZ() - border.getCenterZ()) <= half) {
                continue;
            }
            double margin = world == level ? 5.0 : 5.0 * netherScale();
            Vec3 target = nearestInside(
                    player.getX(), player.getZ(), border.getCenterX(), border.getCenterZ(),
                    border.getSize(), margin);
            // Nether players are returned on the surface at their scaled UHC coordinates.
            if (world != level) {
                double scale = 1.0 / netherScale();
                target = NaturalTerrain.surface(level, target.x() * scale, target.z() * scale);
            } else {
                target = NaturalTerrain.surface(level, target.x(), target.z());
            }
            PlayerUtils.teleport(player, level, target, player.getYRot());
            player.resetFallDistance();
        }
    }

    static Vec3 nearestInside(
            double x, double z, double centerX, double centerZ, double width, double margin) {
        double half = Math.max(0.0, width / 2.0 - margin);
        return new Vec3(Math.clamp(x, centerX - half, centerX + half), 0,
                Math.clamp(z, centerZ - half, centerZ + half));
    }

    @Override
    public boolean canBuild(BlockPos pos) {
        return !deathmatchStarted || !deathmatchFrozen()
                && deathmatchArena.canBuild(pos) && deathmatchBorder.isWithinBounds(pos);
    }

    /** Shrinks the active border; only the natural phase also shrinks the nether's border. */
    void shrinkBorder(double targetSize, long durationTicks) {
        WorldBorder border = border();
        border.lerpSizeBetween(border.getSize(), targetSize, durationTicks, level().getGameTime());
        if (!deathmatchStarted && nether != null) {
            WorldBorder netherBorder = nether.getWorldBorder();
            netherBorder.lerpSizeBetween(
                    netherBorder.getSize(),
                    targetSize / netherBorderDivisor,
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
        if (deathmatchStarted) {
            Vec3 target = nearestInside(player.getX(), player.getZ(), deathmatchBorder.getCenterX(),
                    deathmatchBorder.getCenterZ(), deathmatchBorder.getSize(), RETURN_MARGIN);
            PlayerUtils.teleport(player, level(), new Vec3(target.x(), deathmatchArena.bounds().minY() + 3, target.z()), player.getYRot());
            return;
        }
        if (player.level() == level) {
            NaturalTerrain.moveToSurface(player, level);
            return;
        }
        PlayerUtils.teleport(
                player,
                level,
                surfaceReturnPosition(player.level(), player.getX(), player.getZ()),
                player.getYRot());
    }
    /** Resolves a safe UHC surface position without adding or teleporting a disconnected player. */
    public Vec3 surfaceReturnPosition(ServerLevel source, double x, double z) {
        double scale = DimensionType.getTeleportationScale(source.dimensionType(), level.dimensionType());
        return groundInsideBorder(x * scale, z * scale);
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
        releaseSpawns();
        netherOpen = false;
        frozenSpawns.clear();
        if (deathmatchArena != null) {
            deathmatchArena.close();
            deathmatchArena = null;
        }
        resetBorder(level.getWorldBorder());
        if (nether != null) {
            resetBorder(nether.getWorldBorder());
        }
        if (active == this) {
            active = null;
            UhcClock.tick(level.getServer());
        }
    }

    private static void resetBorder(WorldBorder border) {
        border.setCenter(0.0, 0.0);
        border.setSize(WorldBorder.MAX_SIZE);
    }
}
