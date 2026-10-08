package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Bed Wars map keeps each team's beds, shopkeepers and island generator, and its diamond and
 * emerald generators, read from its markers:
 *
 * <ul>
 *   <li>{@code point bed_<team>[_<name>] <yaw>}: the foot of one of the team's beds, its head lying the
 *       way the yaw faces; Castle maps give each team several, named;
 *   <li>{@code point shop_<team>[_<n>] <yaw>} and {@code point upgrades_<team>[_<n>] <yaw>}: where the
 *       item shopkeeper and the team upgrades shopkeeper stand;
 *   <li>{@code point forge_<team>[_<n>]}: the team's island generator (iron and gold);
 *   <li>{@code point diamond_<n>} and {@code point emerald_<n>}: the shared generators;
 *   <li>{@code region base_<team>[_<name>]}: the team's base, where its traps and Heal Pool work; Castle
 *       maps give a region to each building, named after the bed on it.
 * </ul>
 */
public record BedWarsLayout(
        Map<Integer, List<Bed>> beds,
        Map<Integer, List<MapArena.Point>> shops,
        Map<Integer, List<MapArena.Point>> upgrades,
        Map<Integer, List<Vec3>> forges,
        List<Vec3> diamonds,
        List<Vec3> emeralds,
        Map<Integer, List<MapArena.Region>> bases) {

    /** One bed: its foot block, the way its head lies from the foot, and its name (empty for a team's only bed). */
    public record Bed(int team, String name, BlockPos foot, Direction facing) {
        public BlockPos head() {
            return foot.relative(facing);
        }

        public boolean covers(BlockPos pos) {
            return foot.equals(pos) || head().equals(pos);
        }
    }

    static final String BED = "bed_";
    static final String SHOP = "shop_";
    static final String UPGRADES = "upgrades_";
    static final String FORGE = "forge_";
    static final String DIAMOND = "diamond_";
    static final String EMERALD = "emerald_";
    static final String BASE = "base_";

    /**
     * The layout for teams 1 to {@code teams}; refuses a map that lacks a bed or base for one of them,
     * or its shopkeepers when {@code shops} (One Block maps have none).
     */
    public static BedWarsLayout read(MapArena arena, int teams, boolean shops) throws MatchException {
        String map = arena.mapName();
        Map<Integer, List<Bed>> beds = new HashMap<>();
        for (MapArena.Point point : arena.points(BED)) {
            String rest = point.name().substring(BED.length());
            int split = rest.indexOf('_');
            int team = team(map, split < 0 ? rest : rest.substring(0, split), point.name());
            String name = split < 0 ? "" : rest.substring(split + 1);
            BlockPos foot = BlockPos.containing(point.position());
            Direction facing = Direction.fromYRot(point.yaw());
            beds.computeIfAbsent(team, ignored -> new ArrayList<>()).add(new Bed(team, name, foot, facing));
        }
        Map<Integer, List<MapArena.Point>> shopkeepers = byTeam(arena, map, SHOP);
        Map<Integer, List<MapArena.Point>> upgrades = byTeam(arena, map, UPGRADES);
        Map<Integer, List<Vec3>> forges = new HashMap<>();
        byTeam(arena, map, FORGE).forEach((team, points) ->
                forges.put(team, points.stream().map(MapArena.Point::position).toList()));
        Map<Integer, List<MapArena.Region>> bases = new HashMap<>();
        for (MapArena.Region region : arena.regions(BASE)) {
            String rest = region.name().substring(BASE.length());
            int split = rest.indexOf('_');
            int team = team(map, split < 0 ? rest : rest.substring(0, split), region.name());
            bases.computeIfAbsent(team, ignored -> new ArrayList<>()).add(region);
        }
        for (int team = 1; team <= teams; team++) {
            if (!beds.containsKey(team)) throw new MatchException("Map %s has no bed for team %d.".formatted(map, team));
            if (shops && (!shopkeepers.containsKey(team) || !upgrades.containsKey(team))) {
                throw new MatchException("Map %s has no shopkeepers for team %d.".formatted(map, team));
            }
            if (!bases.containsKey(team)) throw new MatchException("Map %s has no base region for team %d.".formatted(map, team));
        }
        List<Vec3> diamonds = arena.points(DIAMOND).stream().map(MapArena.Point::position).toList();
        List<Vec3> emeralds = arena.points(EMERALD).stream().map(MapArena.Point::position).toList();
        return new BedWarsLayout(Map.copyOf(beds), Map.copyOf(shopkeepers), Map.copyOf(upgrades), Map.copyOf(forges),
                diamonds, emeralds, Map.copyOf(bases));
    }

    private static Map<Integer, List<MapArena.Point>> byTeam(MapArena arena, String map, String prefix)
            throws MatchException {
        Map<Integer, List<MapArena.Point>> points = new HashMap<>();
        for (MapArena.Point point : arena.points(prefix)) {
            String rest = point.name().substring(prefix.length());
            int split = rest.indexOf('_');
            int team = team(map, split < 0 ? rest : rest.substring(0, split), point.name());
            points.computeIfAbsent(team, ignored -> new ArrayList<>()).add(point);
        }
        return points;
    }

    private static int team(String map, String word, String marker) throws MatchException {
        try {
            int team = Integer.parseInt(word);
            if (team >= 1) return team;
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        throw new MatchException("Map %s has marker %s without a team number.".formatted(map, marker));
    }

    /** Whether {@code position} is in one of team {@code team}'s base regions. */
    public boolean inBase(int team, Vec3 position) {
        return bases.getOrDefault(team, List.of()).stream().anyMatch(region -> region.contains(position));
    }

    /** Every bed of every team. */
    public List<Bed> allBeds() {
        return beds.values().stream().flatMap(List::stream).toList();
    }
}
