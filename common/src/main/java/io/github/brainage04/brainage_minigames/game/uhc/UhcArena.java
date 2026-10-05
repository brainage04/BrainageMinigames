package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A generated UHC region and its coordinate-scaled Nether region, each with a match-local border.
 * Other matches in the same dimension pair keep independent borders and regions.
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

    private static final Set<UhcArena> OPEN = new HashSet<>();

    private final ServerLevel level;
    private final @Nullable ServerLevel nether;
    private final WorldBorder surfaceBorder = new WorldBorder();
    private final WorldBorder netherBorder = new WorldBorder();
    private boolean registered;
    private final int centerX;
    private final int centerZ;
    private final double startSize;
    private final Vec3 lobbyPosition;
    private final RandomSource spawnRandom;
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
            Vec3 lobbyPosition,
            RandomSource spawnRandom) {
        this.level = level;
        this.nether = nether;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.startSize = startSize;
        this.lobbyPosition = lobbyPosition;
        this.spawnRandom = spawnRandom;
        this.netherOpen = nether != null;
        this.badlion = UhcModeRules.badlion(level.getServer());
        setUpBorder(surfaceBorder, centerX, centerZ, startSize);
        if (nether != null) {
            setUpBorder(netherBorder, centerX * netherScale(), centerZ * netherScale(), startSize / 8);
        }
    }


    /** Holds dawn only while every open survival region is waiting for its first active tick. */
    static boolean waitingForStart(MinecraftServer server) {
        boolean waiting = false;
        for (UhcArena arena : OPEN) {
            if (arena.level.getServer() != server || arena.deathmatchStarted) continue;
            if (arena.clockStarted) return false;
            waiting = true;
        }
        return waiting;
    }

    void startClock() {
        clockStarted = true;
        UhcClock.tick(level.getServer());
    }

    /**
     * Opens a random region of the UHC dimension; with {@code withNether}, the UHC nether is part
     * of the arena and open until {@link #closeNether}.
     */
    static UhcArena open(MinecraftServer server, int borderSize, boolean withNether, GameSettings settings)
            throws MatchException {
        ServerLevel level = NaturalTerrain.level(server, ModDimensions.UHC);
        ServerLevel nether = withNether ? NaturalTerrain.level(server, ModDimensions.UHC_NETHER) : null;
        int divisor = server.getGameRules().get(UhcModeRules.NETHER_BORDER_SCALE);
        if (!UhcWorldCleanup.markForReset(server)) {
            throw new MatchException("The UHC-style dimensions could not be scheduled for regeneration.");
        }
        // The region inside the border with the most land wins (read from biomes, so trying one
        // costs nothing): an ocean-heavy region spawns players in the water, far from trees and
        // ore. A dry centre for the lobby comes first; without one anywhere, the lobby is on the
        // water's surface.
        int centerX = 0;
        int centerZ = 0;
        Optional<Vec3> lobby = Optional.empty();
        double bestShare = -1.0;
        RandomSource regionRandom = NaturalTerrain.regionRandom(level, settings);
        for (int attempt = 0, probes = 0; attempt < 4096 && probes < CENTER_ATTEMPTS; attempt++) {
            int[] center = NaturalTerrain.randomRegionCenter(regionRandom);
            if (!NaturalTerrain.free(level, center[0], center[1], borderSize)
                    || nether != null && !NaturalTerrain.free(nether, center[0] / 8.0, center[1] / 8.0,
                            (double) borderSize / divisor)) continue;
            probes++;
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
        if (bestShare < 0) throw new MatchException("No free UHC region was found; try again.");
        UhcArena arena =
                new UhcArena(
                        level,
                        nether,
                        centerX,
                        centerZ,
                        borderSize,
                        lobby.orElse(NaturalTerrain.surface(level, centerX + 0.5, centerZ + 0.5)),
                        NaturalTerrain.spawnRandom(level, settings));
        NaturalTerrain.reserve(level, centerX, centerZ, borderSize);
        if (nether != null) {
            NaturalTerrain.reserve(nether, centerX / 8.0, centerZ / 8.0, (double) borderSize / divisor);
        }
        arena.registered = true;
        arena.configureNetherBorderScale(divisor);
        OPEN.add(arena);
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
                NaturalTerrain.onGround(level, centerX + 0.5, centerZ + 0.5),
                level.getRandom());
    }

    @Override
    public ServerLevel level() {
        return deathmatchStarted ? deathmatchArena.level() : level;
    }

    /** The active match border, including the deathmatch arena's independent border. */
    public WorldBorder border() {
        return deathmatchStarted ? deathmatchBorder : surfaceBorder;
    }

    /** Border in this match's surface, Nether or deathmatch level; null outside its current levels. */
    public @Nullable WorldBorder border(ServerLevel current) {
        if (current == level()) return border();
        return !deathmatchStarted && current == nether ? netherBorder : null;
    }

    public void sendBorder(ServerPlayer player) {
        WorldBorder border = border(player.level());
        if (border != null) NaturalArena.sendBorder(player.level(), border, player);
    }

    void tickBorder(Match match) {
        List<ServerPlayer> viewers = match.onlineMembers(), alive = match.alivePlayers();
        NaturalArena.tickBorder(level, surfaceBorder, viewers, alive);
        if (nether != null) {
            NaturalArena.tickBorder(nether, netherBorder, viewers, alive);
        }
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
        if (level.getGameTime() % 20 == 0) sendBorder(player);
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
                    level, centerX + 0.5, centerZ + 0.5, radius, startSize, teamCount, spawnRandom);
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
        if (nether != null) netherBorder.setSize(surfaceBorder.getSize() / divisor);
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
        UhcClock.tick(level.getServer());
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
        surfaceBorder.setSize(targetSize);
        if (nether != null) {
            netherBorder.setSize(targetSize / netherBorderDivisor);
        }
        for (ServerPlayer player : players) {
            ServerLevel world = inNether(player) ? nether : level;
            WorldBorder border = world == nether ? netherBorder : surfaceBorder;
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
            sendBorder(player);
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
        return !deathmatchFrozen() && border().isWithinBounds(pos)
                && (!deathmatchStarted || deathmatchArena.canBuild(pos));
    }

    boolean canBuild(ServerLevel current, BlockPos pos) {
        WorldBorder border = border(current);
        return border != null && border.isWithinBounds(pos)
                && (!deathmatchStarted || canBuild(pos));
    }

    /** Shrinks the active border; only the natural phase also shrinks the nether's border. */
    void shrinkBorder(double targetSize, long durationTicks) {
        WorldBorder border = border();
        border.lerpSizeBetween(border.getSize(), targetSize, durationTicks, level().getGameTime());
        if (!deathmatchStarted && nether != null) {
            WorldBorder netherBorder = this.netherBorder;
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
        sendBorder(player);
    }
    /** Resolves a safe UHC surface position without adding or teleporting a disconnected player. */
    public Vec3 surfaceReturnPosition(ServerLevel source, double x, double z) {
        double scale = DimensionType.getTeleportationScale(source.dimensionType(), level.dimensionType());
        return groundInsideBorder(x * scale, z * scale);
    }


    private Vec3 groundInsideBorder(double x, double z) {
        WorldBorder border = surfaceBorder;
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
        if (registered) {
            NaturalTerrain.release(level, centerX, centerZ);
            if (nether != null) NaturalTerrain.release(nether, centerX * netherScale(), centerZ * netherScale());
            OPEN.remove(this);
            UhcClock.tick(level.getServer());
        }
    }

}
