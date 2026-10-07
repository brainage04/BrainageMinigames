package io.github.brainage04.brainage_minigames.game.arena;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.MatchException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A map built from a structure template under {@code structure/maps/<game>/}, pasted into its own
 * slot of the minigames dimension. DATA structure blocks in the template are markers (spawns,
 * points, regions, the void height) that are read and then replaced by air; see the README's "Maps"
 * section for their grammar.
 */
public final class MapArena implements Arena {
    /** The template's minimum corner is pasted at this height. */
    public static final int BASE_Y = 64;

    /** Space around the template that is cleared too, for fluids and blocks that spread out. */
    private static final int MARGIN = 8;

    private static final String MAPS_DIRECTORY = "maps/";

    /**
     * How arenas remove their blocks, so that clearing never drops an item. Removing a block
     * normally updates its neighbours' shapes, and a neighbour that loses its support (a lantern, a
     * flower, a lily pad) then breaks with drops, whatever the removal's own flags say; and removing
     * a container spills it, rolling its loot table first if nobody opened it. Arenas remove every
     * block anyway, so they skip both.
     */
    static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    /** Chunks a gradually pasted or closed map pastes or clears per server tick. */
    private static final int CHUNKS_PER_PREPARE = 2;

    /** Maps closing gradually, until they are clear; see {@link #closeGradually}. */
    private static final List<MapArena> CLOSING = new ArrayList<>();

    /** The map a player picked for the match being opened, while {@link #withChosenMap} runs. */
    private static final ScopedValue<Identifier> CHOSEN_MAP = ScopedValue.newInstance();

    private final ServerLevel level;
    private final Identifier map;
    private final StructureTemplate template;
    private final BlockPos origin;
    private final BoundingBox bounds;
    private final BoundingBox clearArea;
    private final int firstSlot;
    private final int slotCount;
    private final Markers markers;
    private final List<Runnable> resetListeners = new ArrayList<>();
    private final List<ChunkPos> forcedChunks = new ArrayList<>();

    /** Chunks of a map opened with {@link #reserve} that are not pasted yet. */
    private final ArrayDeque<ChunkPos> unpasted = new ArrayDeque<>();

    /** Chunks anything of the map was pasted into, which closing and resetting must clear. */
    private final Set<ChunkPos> pastedChunks = new HashSet<>();

    /** What to do once a map opened with {@link #reserve} is pasted; see {@link #whenPasted}. */
    private final List<Runnable> pastedActions = new ArrayList<>();

    /** Chunks of a map closing gradually that are not cleared yet. */
    private final ArrayDeque<ChunkPos> uncleared = new ArrayDeque<>();
    private boolean closed;

    public record Point(String name, Vec3 position, float yaw) {}

    public record Region(String name, AABB box) {
        public boolean contains(Vec3 position) {
            return box.contains(position);
        }

        public boolean contains(BlockPos pos) {
            return box.contains(Vec3.atCenterOf(pos));
        }
    }

    private MapArena(
            ServerLevel level,
            Identifier map,
            StructureTemplate template,
            int firstSlot,
            int slotCount,
            BlockPos origin,
            Markers markers) {
        this.level = level;
        this.map = map;
        this.template = template;
        this.firstSlot = firstSlot;
        this.slotCount = slotCount;
        this.origin = origin;
        this.markers = markers;
        this.bounds = template.getBoundingBox(placeSettings(), origin);
        this.clearArea =
                new BoundingBox(
                        bounds.minX() - MARGIN,
                        Math.max(level.getMinY(), bounds.minY() - MARGIN * 2),
                        bounds.minZ() - MARGIN,
                        bounds.maxX() + MARGIN,
                        Math.min(level.getMaxY(), bounds.maxY() + MARGIN),
                        bounds.maxZ() + MARGIN);
    }

    /**
     * Pastes {@code template} into a free slot of {@code level}, its minimum corner at {@link
     * #BASE_Y}.
     */
    public static MapArena open(ServerLevel level, Identifier template) throws MatchException {
        MapArena arena = allocate(level, template);
        try {
            arena.forceChunks();
            arena.paste();
        } catch (RuntimeException exception) {
            arena.close();
            throw exception;
        }
        return arena;
    }

    /**
     * As {@link #open}, but nothing is loaded or placed yet: the map's chunks are forced, to load
     * off the server thread, and each {@link #prepare} clears and pastes up to {@link
     * #CHUNKS_PER_PREPARE} of those that have loaded, so a large map never holds up one tick. Its
     * lobby is {@link #lobbyReady ready} once the whole map is pasted. {@link #finishPreparing}
     * pastes whatever is left at once.
     */
    public static MapArena reserve(ServerLevel level, Identifier template) throws MatchException {
        MapArena arena = allocate(level, template);
        for (ChunkPos chunk : arena.areaChunks()) {
            // Chunks someone else already forced stay theirs to release.
            if (level.getChunkSource().updateChunkForced(chunk, true)) arena.forcedChunks.add(chunk);
            arena.unpasted.add(chunk);
        }
        return arena;
    }

    private static MapArena allocate(ServerLevel level, Identifier template) throws MatchException {
        StructureTemplate structure = load(level.getServer(), template);
        Vec3i size = structure.getSize();
        int slotCount = ArenaSlots.slotsFor(size.getX());
        int firstSlot = ArenaSlots.allocate(slotCount);
        try {
            BlockPos origin =
                    new BlockPos(
                            ArenaSlots.centerX(firstSlot, slotCount) - size.getX() / 2,
                            BASE_Y,
                            -size.getZ() / 2);
            Markers markers = Markers.parse(template, structure, origin);
            return new MapArena(level, template, structure, firstSlot, slotCount, origin, markers);
        } catch (MatchException | RuntimeException exception) {
            ArenaSlots.release(firstSlot, slotCount);
            throw exception;
        }
    }

    /** Pastes the next few chunks of a map opened with {@link #reserve} that have loaded. */
    @Override
    public void prepare() {
        if (unpasted.isEmpty()) return;
        int pasted = 0;
        for (var chunks = unpasted.iterator(); pasted < CHUNKS_PER_PREPARE && chunks.hasNext(); ) {
            ChunkPos chunk = chunks.next();
            if (!loadedAround(chunk)) continue;
            chunks.remove();
            pasteChunk(chunk);
            pasted++;
        }
        if (unpasted.isEmpty()) runPastedActions();
    }

    /** Whether the chunk and its neighbours, which pasting may update, are loaded. */
    private boolean loadedAround(ChunkPos chunk) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(chunk.x() + dx, chunk.z() + dz) == null) return false;
            }
        }
        return true;
    }

    /** Whether the whole map has been pasted. */
    public boolean prepared() {
        return unpasted.isEmpty();
    }

    /** Players are moved onto the map, and its match may start, once it is pasted. */
    @Override
    public boolean lobbyReady() {
        return unpasted.isEmpty();
    }

    /** Pastes every chunk {@link #prepare} has not pasted yet. */
    public void finishPreparing() {
        if (unpasted.isEmpty()) return;
        while (!unpasted.isEmpty()) {
            pasteChunk(unpasted.poll());
        }
        runPastedActions();
    }

    /**
     * Runs {@code action} once the whole map is pasted: now if it is, otherwise right after the
     * last chunk is pasted, before the match can start. Games build what their map needs on it
     * this way, such as SkyWars' cages.
     */
    public void whenPasted(Runnable action) {
        if (unpasted.isEmpty()) {
            action.run();
        } else {
            pastedActions.add(action);
        }
    }

    private void runPastedActions() {
        List<Runnable> actions = List.copyOf(pastedActions);
        pastedActions.clear();
        actions.forEach(Runnable::run);
    }

    /**
     * Runs {@code open} so that every map the game would pick at random is {@code map} instead:
     * {@link #openRandom} opens it when it has room for the teams and refuses the match otherwise.
     * A null {@code map} leaves the choice to the game.
     */
    public static <T, X extends Throwable> T withChosenMap(
            @Nullable Identifier map, ScopedValue.CallableOp<T, X> open) throws X {
        return map == null ? open.call() : ScopedValue.where(CHOSEN_MAP, map).call(open);
    }

    /** Whether a player picked the map; games that choose maps themselves then defer to it. */
    public static boolean hasChosenMap() {
        return CHOSEN_MAP.isBound();
    }

    /** Opens a random map of the game in the minigames dimension; see {@link #reserve}. */
    public static MapArena openRandom(MinecraftServer server, String gameId) throws MatchException {
        return openRandom(server, gameId, 1);
    }

    /**
     * Opens a random map of the game that has spawns for at least {@code minTeams} teams, in the
     * minigames dimension; the chosen map instead while {@link #withChosenMap} runs. The map is
     * pasted over the following ticks, as {@link #reserve} describes.
     */
    public static MapArena openRandom(MinecraftServer server, String gameId, int minTeams)
            throws MatchException {
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES);
        if (level == null) {
            throw new MatchException("The minigames dimension is unavailable.");
        }
        List<Identifier> all = maps(server, gameId);
        if (all.isEmpty()) {
            throw new MatchException("There are no " + gameId + " maps.");
        }
        if (CHOSEN_MAP.isBound()) {
            Identifier chosen = CHOSEN_MAP.get();
            if (!all.contains(chosen)) {
                throw new MatchException("There is no " + gameId + " map " + chosen + ".");
            }
            int teams = teamSlots(server, chosen);
            if (teams < minTeams) {
                throw new MatchException(
                        "Map %s has room for %d teams; this layout needs %d."
                                .formatted(nameOf(chosen), teams, minTeams));
            }
            return reserve(level, chosen);
        }
        List<Identifier> fitting = new ArrayList<>();
        int most = 0;
        for (Identifier candidate : all) {
            int teams = teamSlots(server, candidate);
            most = Math.max(most, teams);
            if (teams >= minTeams) {
                fitting.add(candidate);
            }
        }
        if (fitting.isEmpty()) {
            throw new MatchException(
                    "No %s map has room for %d teams; the largest holds %d."
                            .formatted(gameId, minTeams, most));
        }
        return reserve(level, fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
    }

    /**
     * Reads every map from its file now, as the server starts, so opening a match never waits for
     * its map to be read.
     */
    public static void loadMaps(MinecraftServer server) {
        server.getStructureManager()
                .listTemplates()
                .filter(id -> id.getNamespace().equals(BrainageMinigames.MOD_ID)
                        && id.getPath().startsWith(MAPS_DIRECTORY))
                .distinct()
                .forEach(id -> server.getStructureManager().get(id));
    }

    /** Every map of the game: structure templates under {@code maps/<gameId>/}, sorted. */
    public static List<Identifier> maps(MinecraftServer server, String gameId) {
        String prefix = MAPS_DIRECTORY + gameId + "/";
        return server.getStructureManager()
                .listTemplates()
                .filter(
                        id ->
                                id.getNamespace().equals(BrainageMinigames.MOD_ID)
                                        && id.getPath().startsWith(prefix))
                .distinct()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    /** How many teams a map has spawns for, without pasting it; 0 when it is invalid. */
    public static int teamSlots(MinecraftServer server, Identifier template) {
        try {
            return Markers.parse(template, load(server, template), BlockPos.ZERO).teamSlots();
        } catch (MatchException exception) {
            BrainageMinigames.LOGGER.warn(exception.getMessage());
            return 0;
        }
    }

    private static StructureTemplate load(MinecraftServer server, Identifier template)
            throws MatchException {
        StructureTemplate structure =
                server.getStructureManager()
                        .get(template)
                        .orElseThrow(() -> new MatchException("Unknown map " + template + "."));
        if (structure.getSize().getX() < 1 || structure.getSize().getZ() < 1) {
            throw new MatchException("Map " + template + " is empty.");
        }
        return structure;
    }

    private static StructurePlaceSettings placeSettings() {
        return new StructurePlaceSettings();
    }

    public Identifier map() {
        return map;
    }

    /** The map's name: the last part of its template id. */
    public String mapName() {
        return nameOf(map);
    }

    /** A map's name: the last part of its template id. */
    public static String nameOf(Identifier map) {
        String path = map.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Every {@code spawn <team>} marker of the team, in the template's order. */
    public List<Spawn> spawnsOf(int team) {
        return markers.spawns().getOrDefault(team, List.of());
    }

    /** The highest team number with a spawn; every lower number has one too. */
    public int teamSlots() {
        return markers.teamSlots();
    }

    public Optional<Point> point(String name) {
        return markers.points().stream().filter(point -> point.name().equals(name)).findFirst();
    }

    /** Points whose name starts with {@code prefix}, in the template's order. */
    public List<Point> points(String prefix) {
        return markers.points().stream().filter(point -> point.name().startsWith(prefix)).toList();
    }

    public Optional<Region> region(String name) {
        return markers.regions().stream().filter(region -> region.name().equals(name)).findFirst();
    }

    /** Regions whose name starts with {@code prefix}, in the template's order. */
    public List<Region> regions(String prefix) {
        return markers.regions().stream()
                .filter(region -> region.name().startsWith(prefix))
                .toList();
    }

    /** The blocks the template covers. */
    public BoundingBox bounds() {
        return bounds;
    }

    /** Runs {@code listener} whenever {@link #reset} or {@link #resetGradually} starts. */
    public void onReset(Runnable listener) {
        resetListeners.add(listener);
    }

    /**
     * Puts the map back as it was pasted: clears the area and every non-player entity in it, then
     * pastes the template again.
     */
    public void reset() {
        if (closed) {
            return;
        }
        resetListeners.forEach(Runnable::run);
        paste();
    }

    /**
     * As {@link #reset}, but only the parts of the map inside {@code first}, such as the cages
     * players are about to be sent to, are rebuilt now; {@link #prepare} rebuilds every chunk of
     * the map {@link #CHUNKS_PER_PREPARE} per tick after that, so a large map never holds up one
     * tick. {@link #prepared} is false until the map is whole again.
     */
    public void resetGradually(Collection<AABB> first) {
        if (closed) {
            return;
        }
        resetListeners.forEach(Runnable::run);
        for (AABB box : first) {
            pasteBox(BoundingBox.fromCorners(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(Math.ceil(box.maxX) - 1, Math.ceil(box.maxY) - 1, Math.ceil(box.maxZ) - 1)));
        }
        unpasted.clear();
        unpasted.addAll(areaChunks());
    }

    /** Clears and pastes the part of the map inside {@code box}. */
    private void pasteBox(BoundingBox box) {
        int minX = Math.max(box.minX(), clearArea.minX()), maxX = Math.min(box.maxX(), clearArea.maxX());
        int minY = Math.max(box.minY(), clearArea.minY()), maxY = Math.min(box.maxY(), clearArea.maxY());
        int minZ = Math.max(box.minZ(), clearArea.minZ()), maxZ = Math.min(box.maxZ(), clearArea.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) return;
        BoundingBox inside = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        discardEntities(inside);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(inside.minX(), inside.minY(), inside.minZ(),
                inside.maxX(), inside.maxY(), inside.maxZ())) {
            if (level.getBlockState(pos).isAir()) continue;
            if (level.getBlockEntity(pos) instanceof Clearable clearable) clearable.clearContent();
            level.setBlock(pos, air, CLEAR_FLAGS);
        }
        template.placeInWorld(level, origin, origin, placeSettings().setBoundingBox(inside),
                level.getRandom(), Block.UPDATE_CLIENTS);
        for (BlockPos marker : markers.positions()) {
            if (inside.isInside(marker)) level.setBlock(marker, air, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 lobbyPosition() {
        return markers.lobby()
                .map(Point::position)
                .orElseGet(() -> spawnsOf(1).getFirst().position());
    }

    @Override
    public List<Spawn> spawns(int teamCount) {
        if (teamCount > teamSlots()) {
            throw new IllegalStateException(
                    "Map %s has spawns for %d teams, not %d."
                            .formatted(map, teamSlots(), teamCount));
        }
        List<Spawn> spawns = new ArrayList<>(teamCount);
        for (int team = 1; team <= teamCount; team++) {
            spawns.add(spawnsOf(team).getFirst());
        }
        return spawns;
    }

    @Override
    public int maxTeams() {
        return teamSlots();
    }

    /** The {@code void} marker's height, or 10 blocks below the map. */
    @Override
    public double voidY() {
        return markers.voidY().orElse(bounds.minY() - 10.0);
    }

    /** Inside the map, and inside a {@code build} region if the map has any. */
    @Override
    public boolean canBuild(BlockPos pos) {
        if (!bounds.isInside(pos)) {
            return false;
        }
        List<Region> build =
                markers.regions().stream().filter(region -> region.name().equals("build")).toList();
        return build.isEmpty() || build.stream().anyMatch(region -> region.contains(pos));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pastedActions.clear();
        clear();
        release();
    }

    /**
     * As {@link #close}, but the map is cleared {@link #CHUNKS_PER_PREPARE} chunks per server tick
     * by {@link #tickClosing}; its slot stays taken, and its chunks forced, until it is clear. Its
     * entities and block entities, such as chests, go at once, so nothing in it can still be used.
     */
    @Override
    public void closeGradually() {
        if (closed) {
            return;
        }
        closed = true;
        pastedActions.clear();
        discardEntities(clearArea);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (ChunkPos chunk : areaChunks()) {
            if (!pastedChunks.contains(chunk)) continue;
            uncleared.add(chunk);
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded == null) continue;
            for (BlockPos pos : List.copyOf(loaded.getBlockEntities().keySet())) {
                if (!clearArea.isInside(pos)) continue;
                if (loaded.getBlockEntity(pos) instanceof Clearable clearable) {
                    // Containers would otherwise spill their items.
                    clearable.clearContent();
                }
                level.setBlock(pos, air, CLEAR_FLAGS);
            }
        }
        unpasted.clear();
        CLOSING.add(this);
    }

    /** Clears the next few chunks of every map closing gradually; the server calls it every tick. */
    public static void tickClosing() {
        CLOSING.removeIf(arena -> arena.clearSome(CHUNKS_PER_PREPARE));
    }

    /** Finishes clearing every map closing gradually, as the server stops. */
    public static void finishClosing() {
        CLOSING.removeIf(arena -> arena.clearSome(Integer.MAX_VALUE));
    }

    /** Clears up to {@code chunks} more chunks; once the map is clear, releases it and returns true. */
    private boolean clearSome(int chunks) {
        for (int count = 0; count < chunks && !uncleared.isEmpty(); count++) {
            clearBlocks(uncleared.poll());
        }
        if (!uncleared.isEmpty()) return false;
        // Clearing can drop items (such as a torch losing its support) before they are removed.
        discardEntities(clearArea);
        release();
        return true;
    }

    /** Stops forcing the map's chunks and frees its slot. */
    private void release() {
        for (ChunkPos chunk : forcedChunks) {
            level.setChunkForced(chunk.x(), chunk.z(), false);
        }
        forcedChunks.clear();
        ArenaSlots.release(firstSlot, slotCount);
    }

    private void paste() {
        unpasted.clear();
        clear();
        template.placeInWorld(
                level, origin, origin, placeSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos marker : markers.positions()) {
            level.setBlock(marker, air, Block.UPDATE_CLIENTS);
        }
        pastedChunks.addAll(areaChunks());
        runPastedActions();
    }

    /** Clears and pastes the part of the map in one chunk. */
    private void pasteChunk(ChunkPos chunk) {
        BoundingBox column = new BoundingBox(
                Math.max(clearArea.minX(), chunk.getMinBlockX()), clearArea.minY(),
                Math.max(clearArea.minZ(), chunk.getMinBlockZ()),
                Math.min(clearArea.maxX(), chunk.getMaxBlockX()), clearArea.maxY(),
                Math.min(clearArea.maxZ(), chunk.getMaxBlockZ()));
        discardEntities(column);
        clearBlocks(chunk);
        template.placeInWorld(level, origin, origin, placeSettings().setBoundingBox(column),
                level.getRandom(), Block.UPDATE_CLIENTS);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos marker : markers.positions()) {
            if (column.isInside(marker)) level.setBlock(marker, air, Block.UPDATE_CLIENTS);
        }
        discardEntities(column);
        pastedChunks.add(chunk);
    }

    /**
     * Removes every block and non-player entity in the area, skipping empty chunk sections and the
     * chunks of a gradually pasted map that nothing was pasted into yet.
     */
    private void clear() {
        discardEntities(clearArea);
        for (ChunkPos chunk : areaChunks()) {
            if (pastedChunks.contains(chunk)) clearBlocks(chunk);
        }
        // Clearing can drop items (such as a torch losing its support) before they are removed.
        discardEntities(clearArea);
    }

    private void discardEntities(BoundingBox box) {
        level.getEntities((Entity) null, AABB.of(box).inflate(1.0), entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }

    /** Removes every block of the area inside one chunk, skipping empty chunk sections. */
    private void clearBlocks(ChunkPos chunkPos) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        int minX = Math.max(clearArea.minX(), chunkPos.getMinBlockX());
        int maxX = Math.min(clearArea.maxX(), chunkPos.getMaxBlockX());
        int minZ = Math.max(clearArea.minZ(), chunkPos.getMinBlockZ());
        int maxZ = Math.min(clearArea.maxZ(), chunkPos.getMaxBlockZ());
        for (int sectionY = SectionPos.blockToSectionCoord(clearArea.minY());
                sectionY <= SectionPos.blockToSectionCoord(clearArea.maxY());
                sectionY++) {
            LevelChunkSection section =
                    chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (section.hasOnlyAir()) {
                continue;
            }
            int minY = Math.max(clearArea.minY(), SectionPos.sectionToBlockCoord(sectionY));
            int maxY = Math.min(clearArea.maxY(), SectionPos.sectionToBlockCoord(sectionY, 15));
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        pos.set(x, y, z);
                        if (chunk.getBlockState(pos).isAir()) {
                            continue;
                        }
                        BlockEntity blockEntity = chunk.getBlockEntity(pos);
                        if (blockEntity instanceof Clearable clearable) {
                            // Containers would otherwise spill their items.
                            clearable.clearContent();
                        }
                        level.setBlock(pos, air, CLEAR_FLAGS);
                    }
                }
            }
        }
    }

    /** The chunks the area covers. */
    private List<ChunkPos> areaChunks() {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(clearArea.minX());
                chunkX <= SectionPos.blockToSectionCoord(clearArea.maxX());
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(clearArea.minZ());
                    chunkZ <= SectionPos.blockToSectionCoord(clearArea.maxZ());
                    chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    /**
     * Keeps the area's chunks loaded and ticking while the arena is open, so pasting, resets and
     * projectiles work anywhere on the map, however far from the nearest player.
     */
    private void forceChunks() {
        areaChunks().forEach(this::force);
    }

    private void force(ChunkPos chunk) {
        // Chunks someone else already forced stay theirs to release.
        if (level.setChunkForced(chunk.x(), chunk.z(), true)) {
            forcedChunks.add(chunk);
        }
    }

    /** The markers of one map, parsed from its DATA structure blocks. */
    private record Markers(
            List<BlockPos> positions,
            Optional<Point> lobby,
            TreeMap<Integer, List<Spawn>> spawns,
            List<Point> points,
            List<Region> regions,
            Optional<Double> voidY) {
        int teamSlots() {
            return spawns.isEmpty() ? 0 : spawns.lastKey();
        }

        static Markers parse(Identifier map, StructureTemplate template, BlockPos origin)
                throws MatchException {
            List<StructureTemplate.StructureBlockInfo> infos =
                    template.filterBlocks(origin, placeSettings(), Blocks.STRUCTURE_BLOCK);
            List<BlockPos> positions = new ArrayList<>();
            List<ParsedPoint> rawSpawns = new ArrayList<>();
            List<ParsedPoint> rawPoints = new ArrayList<>();
            ParsedPoint lobby = null;
            List<Region> regions = new ArrayList<>();
            Double voidY = null;
            for (StructureTemplate.StructureBlockInfo info : infos) {
                if (info.nbt() == null
                        || !info.nbt().getStringOr("mode", "").equalsIgnoreCase("data")) {
                    continue;
                }
                BlockPos pos = info.pos();
                positions.add(pos);
                String metadata = info.nbt().getStringOr("metadata", "").trim();
                String[] words = metadata.toLowerCase(Locale.ROOT).split("\\s+");
                String where =
                        "Map %s, marker '%s' at %d %d %d (template %d %d %d)"
                                .formatted(
                                        map,
                                        metadata,
                                        pos.getX(),
                                        pos.getY(),
                                        pos.getZ(),
                                        pos.getX() - origin.getX(),
                                        pos.getY() - origin.getY(),
                                        pos.getZ() - origin.getZ());
                switch (words[0]) {
                    case "lobby" -> {
                        arguments(words, 0, 1, where);
                        if (lobby != null) {
                            throw new MatchException(where + ": the map already has a lobby.");
                        }
                        lobby = new ParsedPoint("lobby", pos, yaw(words, 1, where));
                    }
                    case "spawn" -> {
                        arguments(words, 1, 2, where);
                        int team = integer(words[1], where);
                        if (team < 1) {
                            throw new MatchException(where + ": team numbers start at 1.");
                        }
                        rawSpawns.add(new ParsedPoint(words[1], pos, yaw(words, 2, where)));
                    }
                    case "point" -> {
                        arguments(words, 1, 2, where);
                        rawPoints.add(new ParsedPoint(words[1], pos, yaw(words, 2, where)));
                    }
                    case "region" -> {
                        arguments(words, 4, 4, where);
                        BlockPos other =
                                pos.offset(
                                        integer(words[2], where),
                                        integer(words[3], where),
                                        integer(words[4], where));
                        regions.add(
                                new Region(
                                        words[1],
                                        new AABB(
                                                Math.min(pos.getX(), other.getX()),
                                                Math.min(pos.getY(), other.getY()),
                                                Math.min(pos.getZ(), other.getZ()),
                                                Math.max(pos.getX(), other.getX()) + 1,
                                                Math.max(pos.getY(), other.getY()) + 1,
                                                Math.max(pos.getZ(), other.getZ()) + 1)));
                    }
                    case "void" -> {
                        arguments(words, 0, 0, where);
                        voidY = voidY == null ? pos.getY() : Math.max(voidY, pos.getY());
                    }
                    default ->
                            throw new MatchException(
                                    where
                                            + ": unknown marker; expected lobby, spawn, point,"
                                            + " region or void.");
                }
            }
            if (rawSpawns.isEmpty()) {
                throw new MatchException("Map " + map + " has no spawn markers.");
            }

            // Default yaws face the centre of the map.
            double centerX = origin.getX() + template.getSize().getX() / 2.0;
            double centerZ = origin.getZ() + template.getSize().getZ() / 2.0;
            TreeMap<Integer, List<Spawn>> spawns = new TreeMap<>();
            for (ParsedPoint raw : rawSpawns) {
                Point point = raw.toPoint(centerX, centerZ);
                spawns.computeIfAbsent(Integer.parseInt(raw.name()), team -> new ArrayList<>())
                        .add(new Spawn(point.position(), point.yaw()));
            }
            for (int team = 1; team <= spawns.lastKey(); team++) {
                if (!spawns.containsKey(team)) {
                    throw new MatchException(
                            "Map %s has spawns for team %d but none for team %d."
                                    .formatted(map, spawns.lastKey(), team));
                }
            }
            spawns.replaceAll((team, list) -> List.copyOf(list));
            List<Point> points = new ArrayList<>();
            for (ParsedPoint raw : rawPoints) {
                points.add(raw.toPoint(centerX, centerZ));
            }
            ParsedPoint lobbyMarker = lobby;
            return new Markers(
                    List.copyOf(positions),
                    Optional.ofNullable(lobbyMarker)
                            .map(marker -> marker.toPoint(centerX, centerZ)),
                    spawns,
                    List.copyOf(points),
                    List.copyOf(regions),
                    Optional.ofNullable(voidY));
        }

        private static void arguments(String[] words, int min, int max, String where)
                throws MatchException {
            int count = words.length - 1;
            if (count < min || count > max) {
                throw new MatchException(
                        where
                                + ": expected "
                                + (min == max ? String.valueOf(min) : min + " to " + max)
                                + " arguments, found "
                                + count
                                + ".");
            }
        }

        private static int integer(String word, String where) throws MatchException {
            try {
                return Integer.parseInt(word);
            } catch (NumberFormatException exception) {
                throw new MatchException(where + ": '" + word + "' is not a whole number.");
            }
        }

        /** The yaw argument at {@code index}, or NaN to face the map centre. */
        private static float yaw(String[] words, int index, String where) throws MatchException {
            if (words.length <= index) {
                return Float.NaN;
            }
            try {
                return Float.parseFloat(words[index]);
            } catch (NumberFormatException exception) {
                throw new MatchException(where + ": '" + words[index] + "' is not a yaw.");
            }
        }
    }

    private record ParsedPoint(String name, BlockPos pos, float yaw) {
        Point toPoint(double centerX, double centerZ) {
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            float facing =
                    Float.isNaN(yaw)
                            ? (float) Math.toDegrees(Math.atan2(x - centerX, -(z - centerZ)))
                            : yaw;
            return new Point(name, new Vec3(x, pos.getY(), z), facing);
        }
    }
}
