package io.github.brainage04.brainage_minigames.game.arena;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.MatchException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        StructureTemplate structure = load(level.getServer(), template);
        Vec3i size = structure.getSize();
        int slotCount = ArenaSlots.slotsFor(size.getX());
        int firstSlot = ArenaSlots.allocate(slotCount);
        MapArena arena;
        try {
            BlockPos origin =
                    new BlockPos(
                            ArenaSlots.centerX(firstSlot, slotCount) - size.getX() / 2,
                            BASE_Y,
                            -size.getZ() / 2);
            Markers markers = Markers.parse(template, structure, origin);
            arena = new MapArena(level, template, structure, firstSlot, slotCount, origin, markers);
        } catch (MatchException | RuntimeException exception) {
            ArenaSlots.release(firstSlot, slotCount);
            throw exception;
        }
        try {
            arena.forceChunks();
            arena.paste();
        } catch (RuntimeException exception) {
            arena.close();
            throw exception;
        }
        return arena;
    }

    /** Opens a random map of the game in the minigames dimension. */
    public static MapArena openRandom(MinecraftServer server, String gameId) throws MatchException {
        return openRandom(server, gameId, 1);
    }

    /**
     * Opens a random map of the game that has spawns for at least {@code minTeams} teams, in the
     * minigames dimension.
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
        return open(level, fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
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

    /** Runs {@code listener} after every {@link #reset}. */
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
        paste();
        resetListeners.forEach(Runnable::run);
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
        clear();
        for (ChunkPos chunk : forcedChunks) {
            level.setChunkForced(chunk.x(), chunk.z(), false);
        }
        forcedChunks.clear();
        ArenaSlots.release(firstSlot, slotCount);
    }

    private void paste() {
        clear();
        template.placeInWorld(
                level, origin, origin, placeSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos marker : markers.positions()) {
            level.setBlock(marker, air, Block.UPDATE_CLIENTS);
        }
    }

    /** Removes every block and non-player entity in the area, skipping empty chunk sections. */
    private void clear() {
        level.getEntities(
                        (Entity) null,
                        AABB.of(clearArea).inflate(1.0),
                        entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = SectionPos.blockToSectionCoord(clearArea.minX());
                chunkX <= SectionPos.blockToSectionCoord(clearArea.maxX());
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(clearArea.minZ());
                    chunkZ <= SectionPos.blockToSectionCoord(clearArea.maxZ());
                    chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                int minX = Math.max(clearArea.minX(), SectionPos.sectionToBlockCoord(chunkX));
                int maxX = Math.min(clearArea.maxX(), SectionPos.sectionToBlockCoord(chunkX, 15));
                int minZ = Math.max(clearArea.minZ(), SectionPos.sectionToBlockCoord(chunkZ));
                int maxZ = Math.min(clearArea.maxZ(), SectionPos.sectionToBlockCoord(chunkZ, 15));
                for (int sectionY = SectionPos.blockToSectionCoord(clearArea.minY());
                        sectionY <= SectionPos.blockToSectionCoord(clearArea.maxY());
                        sectionY++) {
                    LevelChunkSection section =
                            chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int minY = Math.max(clearArea.minY(), SectionPos.sectionToBlockCoord(sectionY));
                    int maxY =
                            Math.min(
                                    clearArea.maxY(), SectionPos.sectionToBlockCoord(sectionY, 15));
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
                                level.setBlock(
                                        pos,
                                        air,
                                        Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                            }
                        }
                    }
                }
            }
        }
        // Clearing can drop items (such as a torch losing its support) before they are removed.
        level.getEntities(
                        (Entity) null,
                        AABB.of(clearArea).inflate(1.0),
                        entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }

    /**
     * Keeps the area's chunks loaded and ticking while the arena is open, so pasting, resets and
     * projectiles work anywhere on the map, however far from the nearest player.
     */
    private void forceChunks() {
        for (int chunkX = SectionPos.blockToSectionCoord(clearArea.minX());
                chunkX <= SectionPos.blockToSectionCoord(clearArea.maxX());
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(clearArea.minZ());
                    chunkZ <= SectionPos.blockToSectionCoord(clearArea.maxZ());
                    chunkZ++) {
                // Chunks someone else already forced stay theirs to release.
                if (level.setChunkForced(chunkX, chunkZ, true)) {
                    forcedChunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
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
