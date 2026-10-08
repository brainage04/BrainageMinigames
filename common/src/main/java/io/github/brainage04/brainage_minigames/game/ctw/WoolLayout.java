package io.github.brainage04.brainage_minigames.game.ctw;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Capture the Wool map keeps its wools, read from its markers: {@code point wool_<team>_<colour>}
 * is a wool's place in team {@code <team>}'s wool room, {@code point monument_<team>_<colour>} the
 * slot on team {@code <team>}'s monument where that colour is placed, {@code region woolroom_<team>_<n>}
 * a wool room team {@code <team>} may not enter and the optional {@code point guard_<team>} where the
 * team's defenders stand.
 */
record WoolLayout(List<Spec> wools, Map<Integer, List<AABB>> woolRooms, Map<Integer, Vec3> guards) {
    /** One wool: kept in {@code owner}'s room at {@code source} and placed by {@code capturer} at {@code slot}. */
    record Spec(DyeColor colour, int owner, int capturer, BlockPos source, BlockPos slot) {}

    private static final String WOOL = "wool_";
    private static final String MONUMENT = "monument_";
    private static final String WOOL_ROOM = "woolroom_";
    private static final String GUARD = "guard_";

    /** The wools teams 1 to {@code teams} play for; refuses a map whose markers do not add up. */
    static WoolLayout read(MapArena arena, int teams) throws MatchException {
        String map = arena.mapName();
        Map<DyeColor, Owned> sources = new LinkedHashMap<>();
        for (MapArena.Point point : arena.points(WOOL)) {
            Owned owned = owned(map, point.name().substring(WOOL.length()), point.name());
            if (sources.put(owned.colour(), owned.at(point.position())) != null) {
                throw new MatchException("Map %s keeps the %s wool twice.".formatted(map, name(owned.colour())));
            }
        }
        Map<DyeColor, Owned> slots = new HashMap<>();
        for (MapArena.Point point : arena.points(MONUMENT)) {
            Owned owned = owned(map, point.name().substring(MONUMENT.length()), point.name());
            if (slots.put(owned.colour(), owned.at(point.position())) != null) {
                throw new MatchException("Map %s has two monument slots for %s wool.".formatted(map, name(owned.colour())));
            }
        }
        Map<Integer, List<AABB>> rooms = new HashMap<>();
        for (MapArena.Region region : arena.regions(WOOL_ROOM)) {
            String rest = region.name().substring(WOOL_ROOM.length());
            int team = team(map, rest.contains("_") ? rest.substring(0, rest.indexOf('_')) : rest, region.name());
            rooms.computeIfAbsent(team, ignored -> new ArrayList<>()).add(region.box());
        }
        Map<Integer, Vec3> guards = new HashMap<>();
        for (MapArena.Point point : arena.points(GUARD)) {
            guards.put(team(map, point.name().substring(GUARD.length()), point.name()), point.position());
        }
        List<Spec> wools = new ArrayList<>();
        for (Map.Entry<DyeColor, Owned> entry : sources.entrySet()) {
            DyeColor colour = entry.getKey();
            Owned source = entry.getValue();
            Owned slot = slots.remove(colour);
            if (slot == null) {
                throw new MatchException("Map %s has no monument slot for %s wool.".formatted(map, name(colour)));
            }
            if (slot.team() == source.team()) {
                throw new MatchException("Map %s has team %d place its own %s wool.".formatted(map, slot.team(), name(colour)));
            }
            BlockPos at = BlockPos.containing(source.position());
            if (rooms.getOrDefault(source.team(), List.of()).stream().noneMatch(room -> room.contains(Vec3.atCenterOf(at)))) {
                throw new MatchException("Map %s keeps %s wool outside team %d's wool rooms.".formatted(map, name(colour), source.team()));
            }
            wools.add(new Spec(colour, source.team(), slot.team(), at, BlockPos.containing(slot.position())));
        }
        if (!slots.isEmpty()) {
            throw new MatchException("Map %s has monument slots for %s wool, which it does not keep."
                    .formatted(map, name(slots.keySet().iterator().next())));
        }
        for (Spec wool : wools) {
            if (wool.owner() > teams || wool.capturer() > teams) {
                throw new MatchException("Map %s is for %d teams, not %d.".formatted(map, arena.teamSlots(), teams));
            }
        }
        for (int team = 1; team <= teams; team++) {
            int number = team;
            if (wools.stream().noneMatch(wool -> wool.capturer() == number)) {
                throw new MatchException("Map %s has no monument for team %d.".formatted(map, team));
            }
            if (wools.stream().noneMatch(wool -> wool.owner() == number)) {
                throw new MatchException("Map %s keeps no wool for team %d to defend.".formatted(map, team));
            }
        }
        return new WoolLayout(List.copyOf(wools), Map.copyOf(rooms), Map.copyOf(guards));
    }

    /** The monument slots of team {@code team}, in the map's order. */
    List<Spec> monument(int team) {
        return wools.stream().filter(wool -> wool.capturer() == team).toList();
    }

    /** Whether {@code position} is inside one of team {@code team}'s wool rooms. */
    boolean inWoolRoom(int team, Vec3 position) {
        return woolRooms.getOrDefault(team, List.of()).stream().anyMatch(room -> room.contains(position));
    }

    /** Whether {@code position} is inside any team's wool room. */
    boolean inAnyWoolRoom(Vec3 position) {
        return woolRooms.values().stream().flatMap(List::stream).anyMatch(room -> room.contains(position));
    }

    Optional<Vec3> guard(int team) {
        return Optional.ofNullable(guards.get(team));
    }

    /** A wool's colour as players read it: "Light Blue". */
    static String name(DyeColor colour) {
        StringBuilder name = new StringBuilder();
        for (String word : colour.getName().split("_")) {
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private record Owned(int team, DyeColor colour, Vec3 position) {
        Owned at(Vec3 position) {
            return new Owned(team, colour, position);
        }
    }

    /** Reads {@code <team>_<colour>}, such as {@code 1_light_blue}. */
    private static Owned owned(String map, String rest, String marker) throws MatchException {
        int split = rest.indexOf('_');
        if (split < 0) {
            throw new MatchException("Map %s: marker %s needs a team and a colour.".formatted(map, marker));
        }
        int team = team(map, rest.substring(0, split), marker);
        DyeColor colour = DyeColor.byName(rest.substring(split + 1).toLowerCase(Locale.ROOT), null);
        if (colour == null) {
            throw new MatchException("Map %s: marker %s names no dye colour.".formatted(map, marker));
        }
        return new Owned(team, colour, Vec3.ZERO);
    }

    private static int team(String map, String word, String marker) throws MatchException {
        try {
            int team = Integer.parseInt(word);
            if (team >= 1) return team;
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        throw new MatchException("Map %s: marker %s needs a team number from 1.".formatted(map, marker));
    }
}
