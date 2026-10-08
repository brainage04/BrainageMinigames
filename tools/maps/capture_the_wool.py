"""Generates the Capture the Wool maps (original layouts): python3 tools/maps/capture_the_wool.py from
the repo root.

Two teams face each other along x; the map is point-symmetric, so neither side has an advantage.
Each team's base island holds, from the front: a wall with a gate for every lane, the monument
(a slot for each enemy wool: open at the front, glass of the wool's colour behind, beside and
above it, so nobody can stand in it and block it), the spawn, and at the back a courtyard between
two wool rooms, each with one wool on a pedestal. Lanes cross the void between the bases.

Markers (see the README's Capture the Wool section): `spawn <team>`, `point wool_<team>_<colour>`
(where the game puts the wool team <team> defends), `point monument_<team>_<colour>` (the slot
team <team> places that colour on), `region woolroom_<team>_<n>` (inside a wool room, which its
own team may not enter), `point guard_<team>` (where bots that defend stand), `region build`,
`void` and `lobby`. Team 1 is red and team 2 blue, as the mod colours teams.

The script refuses a map whose wools, slots or rooms do not add up, and one where a wool room
can only be reached by building.
"""

import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bridge import Frame, lobby, region, tree  # noqa: E402
from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/capture_the_wool/{}.nbt"

# Walking surface of the islands and lanes inside the template.
S = 16
TEAM_COLORS = {1: "red", 2: "blue"}
EAST_WEST = {1: (-1, 0), 2: (1, 0)}


def deck(s, frame, u1, u2, v1, v2, top, fill, depth=5, corner=4, edge=None):
    """A flat island between u1..u2 and v1..v2 with rounded corners, `depth` blocks thick in the
    middle and thinner at the rim; `edge` paints the outermost ring of the top."""
    for u in range(u1, u2 + 1):
        for v in range(v1, v2 + 1):
            cu = max(u1 + corner - u, u - (u2 - corner), 0)
            cv = max(v1 + corner - v, v - (v2 - corner), 0)
            if cu * cu + cv * cv > corner * corner:
                continue
            rim = min(u - u1, u2 - u, v - v1, v2 - v)
            bottom = S - 1 - min(depth, 1 + rim // 2)
            for y in range(bottom, S):
                s.set(frame.pos(u, y, v), fill)
            on_edge = edge and (rim == 0 or cu * cu + cv * cv > (corner - 1) ** 2)
            s.set(frame.pos(u, S, v), edge if on_edge else top)


def wall_rail(block):
    """The wall block matching a brick block: stone_bricks -> stone_brick_wall."""
    return block[:-1] + "_wall" if block.endswith("bricks") else block + "_wall"


def bridge(s, frame, u1, u2, v_center, top, rail, half=2, light=None):
    """A bridge along u at v_center: a top 2*half+1 wide over a beam, walls of `rail` on both
    edges with a lantern on a post every eight blocks."""
    wall = wall_rail(rail)
    for u in range(min(u1, u2), max(u1, u2) + 1):
        for v in range(v_center - half, v_center + half + 1):
            s.set(frame.pos(u, S, v), top)
        for v in range(v_center - half + 1, v_center + half):
            s.set(frame.pos(u, S - 1, v), rail)
        for side in (-half - 1, half + 1):
            s.set(frame.pos(u, S, v_center + side), rail)
            s.set(frame.pos(u, S + 1, v_center + side), wall)
            if light and u % 8 == 0:
                s.set(frame.pos(u, S + 2, v_center + side), light)


def cross_bridge(s, frame, u_center, v1, v2, top, rail, half=1):
    """A bridge along v at u_center, between v1 and v2."""
    wall = wall_rail(rail)
    for v in range(min(v1, v2), max(v1, v2) + 1):
        for u in range(u_center - half, u_center + half + 1):
            s.set(frame.pos(u, S, v), top)
        s.set(frame.pos(u_center, S - 1, v), rail)
        for side in (-half - 1, half + 1):
            s.set(frame.pos(u_center + side, S, v), rail)
            s.set(frame.pos(u_center + side, S + 1, v), wall)


def gate_wall(s, frame, u, v1, v2, gates, wall, trim, height=3):
    """A crenellated wall along v at u with a three-wide, three-high opening at each v in gates."""
    for v in range(v1, v2 + 1):
        opening = any(abs(v - g) <= 1 for g in gates)
        for y in range(S + 1, S + 1 + height):
            if opening and y < S + 4:
                continue
            s.set(frame.pos(u, y, v), wall)
        if not opening or height > 3:
            if v % 2 == 0:
                s.set(frame.pos(u, S + 1 + height, v), trim)


def monument(s, frame, team, u, slots, plinth, cap, frame_block, light):
    """The team's monument at u: a one-step plinth with a slot for each enemy wool at v in
    `slots` (colour -> v). Each slot is open towards the middle of the map and closed by glass
    of the wool's colour behind, beside and above it, so nobody can stand in it."""
    lo = min(slots.values()) - 3
    hi = max(slots.values()) + 3
    for du in (-1, 0, 1):
        for v in range(lo, hi + 1):
            s.set(frame.pos(u + du, S + 1, v), plinth)
    for v in (lo, hi):
        for y in range(S + 2, S + 6):
            s.set(frame.pos(u + 1, y, v), frame_block)
        s.set(frame.pos(u + 1, S + 6, v), light)
    for v in range(lo, hi + 1):
        s.set(frame.pos(u + 1, S + 5, v), frame_block)
    for colour, v in slots.items():
        glass = f"{colour}_stained_glass"
        s.set(frame.pos(u, S + 1, v), cap)
        s.set(frame.pos(u, S + 3, v), glass)
        s.set(frame.pos(u + 1, S + 2, v), glass)
        s.set(frame.pos(u + 1, S + 3, v), glass)
        s.set(frame.pos(u, S + 2, v - 1), glass)
        s.set(frame.pos(u, S + 2, v + 1), glass)
        s.set(frame.pos(u, S + 4, v), glass)
        s.marker(frame.pos(u, S + 2, v), f"point monument_{team}_{colour}")


def wool_room(s, frame, team, index, u1, u2, v1, v2, door_v_side, colour, wall, floor, roof, pillar):
    """An enclosed wool room between u1..u2 and v1..v2 (walls included) with a three-wide door in
    the wall at v = door_v_side (v1 or v2), the wool on a pedestal in the middle and windows of
    its colour. Adds `region woolroom_<team>_<index>` over the inside and the wool's point."""
    top = S + 6
    for u in range(u1, u2 + 1):
        for v in range(v1, v2 + 1):
            s.set(frame.pos(u, S, v), floor)
            edge = u in (u1, u2) or v in (v1, v2)
            for y in range(S + 1, top):
                if edge:
                    corner = u in (u1, u2) and v in (v1, v2)
                    s.set(frame.pos(u, y, v), pillar if corner else wall,
                          **({"axis": "y"} if corner and pillar.endswith(("log", "pillar", "basalt")) else {}))
                else:
                    s.remove(frame.pos(u, y, v))
            s.set(frame.pos(u, top, v), roof)
    mid_u = (u1 + u2) // 2
    mid_v = (v1 + v2) // 2
    # The door, facing the courtyard.
    for du in (-1, 0, 1):
        for y in range(S + 1, S + 4):
            s.remove(frame.pos(mid_u + du, y, door_v_side))
    # Windows of the wool's colour in the outer walls.
    glass = f"{colour}_stained_glass"
    far_v = v2 if door_v_side == v1 else v1
    for du in (-2, 2):
        for y in (S + 3, S + 4):
            s.set(frame.pos(mid_u + du, y, far_v), glass)
    for dv in (-1, 1):
        for y in (S + 3, S + 4):
            s.set(frame.pos(u2, y, mid_v + dv), glass)
    # A beacon of the wool's colour on the roof, seen from across the map.
    s.set(frame.pos(mid_u, top + 1, mid_v), glass)
    s.set(frame.pos(mid_u, top + 2, mid_v), "end_rod", facing="up")
    # The pedestal; the game puts the wool on it.
    s.set(frame.pos(mid_u, S + 1, mid_v), "chiseled_quartz_block")
    s.marker(frame.pos(mid_u, S + 2, mid_v), f"point wool_{team}_{colour}")
    lo, span = frame.box(u1 + 1, S + 1, v1 + 1, u2 - 1, top - 1, v2 - 1)
    region(s, f"woolroom_{team}_{index}", lo, span)


def spawn_pad(s, frame, team, u, pad, roof, posts):
    """Four spawn points under an open pavilion, facing the middle."""
    for du in range(-2, 3):
        for v in range(-3, 4):
            s.set(frame.pos(u + du, S, v), pad)
    for du in (-2, 2):
        for v in (-3, 3):
            for y in range(S + 1, S + 5):
                s.set(frame.pos(u + du, y, v), posts)
    for du in range(-2, 3):
        for v in range(-3, 4):
            s.set(frame.pos(u + du, S + 5, v), roof)
    s.set(frame.pos(u, S + 5, 0), f"{TEAM_COLORS[team]}_stained_glass")
    for du, v in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
        s.marker(frame.pos(u + du, S + 1, v), f"spawn {team}")


def check(s, wools, frames):
    """Refuses a map whose wools, slots or rooms do not add up, or whose wools cannot be walked to
    from their capturer's spawn."""
    markers = {}
    for pos, (name, _, nbt) in s.blocks.items():
        if name == "minecraft:structure_block":
            markers[nbt["metadata"]] = pos
    for owner, colours in wools.items():
        capturer = 3 - owner
        for colour in colours:
            assert f"point wool_{owner}_{colour}" in markers, colour
            assert f"point monument_{capturer}_{colour}" in markers, colour
            start = next(pos for meta, pos in markers.items() if meta == f"spawn {capturer}")
            goal = markers[f"point wool_{owner}_{colour}"]
            if not walkable(s, start, goal):
                raise ValueError(f"the {colour} wool cannot be walked to from spawn {capturer}")
            slot = markers[f"point monument_{capturer}_{colour}"]
            if not walkable(s, start, slot):
                raise ValueError(f"the {colour} slot cannot be walked to from spawn {capturer}")


def solid(s, pos):
    name = s.get(pos)
    return name is not None and name != "minecraft:structure_block" and not name.endswith(
        ("_wall", "end_rod", "lantern"))


def passable(s, pos):
    name = s.get(pos)
    return name is None or name == "minecraft:structure_block"


def walkable(s, start, goal):
    """Whether a player can walk (stepping up or down a block) from start to within reach of goal."""
    seen = {start}
    todo = deque([start])
    while todo:
        x, y, z = todo.popleft()
        if abs(x - goal[0]) + abs(z - goal[2]) <= 3 and abs(y - goal[1]) <= 2:
            return True
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                nxt = (x + dx, y + dy, z + dz)
                if nxt in seen:
                    continue
                if not (passable(s, nxt) and passable(s, (nxt[0], nxt[1] + 1, nxt[2]))
                        and solid(s, (nxt[0], nxt[1] - 1, nxt[2]))):
                    continue
                if dy == 1 and not passable(s, (x, y + 2, z)):
                    continue
                seen.add(nxt)
                todo.append(nxt)
    return False


def base(s, frame, team, wools, palette, gates=(-20, 0, 20), cover=((42, -9), (42, 9), (45, -14), (45, 14))):
    """One team's base island with its gate wall (an opening at each v in `gates`, under a lintel
    marked in the team's colour, between two corner towers), monument, spawn, courtyard and wool
    rooms. The ground is speckled with `ground_mix` and lamp posts line the path to the spawn."""
    p = palette
    colour = TEAM_COLORS[team]
    deck(s, frame, 34, 72, -26, 26, p["ground"], p["fill"], edge=p["edge"])
    for u in range(35, 72):
        for v in range(-25, 26):
            if s.get(frame.pos(u, S, v)) == "minecraft:" + p["ground"] and (u * 7 + abs(v) * 13) % 9 == 0:
                s.set(frame.pos(u, S, v), p["ground_mix"])
    # A path of the team's colour from each gate to the spawn.
    for u in range(44, 58):
        for v in (-1, 0, 1):
            s.set(frame.pos(u, S, v), p["path"])
    for gate in gates:
        for u in range(34, 46):
            for v in (gate - 1, gate, gate + 1):
                s.set(frame.pos(u, S, v), p["path"])
        for v in range(min(gate, 0), max(gate, 0) + 1):
            for u in (43, 44, 45):
                s.set(frame.pos(u, S, v), p["path"])
    gate_wall(s, frame, 37, -25, 25, gates, p["wall"], p["trim"], height=4)
    for gate in gates:
        for dv in (-1, 0, 1):
            s.set(frame.pos(37, S + 4, gate + dv), f"{colour}_glazed_terracotta" if dv == 0 else p["wall"])
    for v in (-25, 25):
        for u in range(38, 45):
            for y in range(S + 1, S + 4):
                s.set(frame.pos(u, y, v), p["wall"])
    # Corner towers at both ends of the gate wall, with a light and the team's colour on top.
    for sign in (-1, 1):
        for u in range(36, 39):
            for dv in range(3):
                v = sign * (23 + dv)
                for y in range(S + 1, S + 7):
                    s.set(frame.pos(u, y, v), p["post"] if (u - 36) % 2 == 0 and dv % 2 == 0 else p["wall"],
                          **({"axis": "y"} if (u - 36) % 2 == 0 and dv % 2 == 0 and p["post"].endswith("log") else {}))
                s.set(frame.pos(u, S + 7, v), f"{colour}_concrete" if u == 37 and dv == 1 else p["trim"])
        s.set(frame.pos(37, S + 8, sign * 24), p["light"])
    # Lamp posts either side of the path to the spawn.
    for u in (40, 52):
        for v in (-3, 3):
            s.set(frame.pos(u, S + 1, v), p["trim"])
            s.set(frame.pos(u, S + 2, v), p["trim"])
            s.set(frame.pos(u, S + 3, v), "lantern")
    # The enemy wools' slots on the monument, to the team's left and right of the path.
    enemy = wools[3 - team]
    monument(s, frame, team, 48, {enemy[0]: -4, enemy[1]: 4}, p["plinth"], p["cap"], p["wall"], p["light"])
    spawn_pad(s, frame, team, 56, p["pad"], p["roof"], p["post"])
    # Cover between the gates and the monument.
    for u, v in cover:
        for dv in (-1, 0, 1):
            for y in range(S + 1, S + 3):
                s.set(frame.pos(u, y, v + dv), p["wall"])
        s.set(frame.pos(u, S + 3, v), p["trim"])
    # The wool rooms either side of the courtyard behind the spawn; doors face the courtyard.
    own = wools[team]
    wool_room(s, frame, team, 1, 62, 71, -19, -9, -9, own[0], p["room"], p["room_floor"], p["roof"], p["post"])
    wool_room(s, frame, team, 2, 62, 71, 9, 19, 9, own[1], p["room"], p["room_floor"], p["roof"], p["post"])
    for u in range(62, 72):
        for v in range(-8, 9):
            s.set(frame.pos(u, S, v), p["court"])
    for v in range(-8, 9):
        for y in range(S + 1, S + 4):
            s.set(frame.pos(72, y, v), p["wall"])
        if v % 2 == 0:
            s.set(frame.pos(72, S + 4, v), p["trim"])
    s.marker(frame.pos(64, S + 1, 0), f"point guard_{team}")


def bastions():
    """Two stone-brick bases on floating islands, 68 blocks apart. A five-wide mid bridge runs
    from gate to gate through a ruined watchtower on the middle island; two side bridges along
    the flanks lead past the middle on small platforms with broken walls, linked to the middle
    island by narrow cross bridges."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}
    wools = {1: ("orange", "yellow"), 2: ("lime", "cyan")}
    for team, frame in frames.items():
        colour = TEAM_COLORS[team]
        base(s, frame, team, wools, {
            "ground": "stone_bricks", "ground_mix": "mossy_stone_bricks", "fill": "stone", "edge": "chiseled_stone_bricks",
            "path": f"{colour}_terracotta", "wall": "stone_bricks", "trim": "stone_brick_wall",
            "plinth": "polished_andesite", "cap": "chiseled_stone_bricks", "light": "sea_lantern",
            "pad": "polished_andesite", "roof": "spruce_planks", "post": "spruce_log",
            "room": "cracked_stone_bricks", "room_floor": "polished_andesite", "court": "andesite",
        })
        # The mid bridge, gate to the middle island; the side bridges run past the middle, each
        # half drawn from its own base.
        bridge(s, frame, 11, 34, 0, "polished_andesite", "stone_bricks", light="lantern")
        for sign in (-1, 1):
            bridge(s, frame, 0, 34, 20 * sign, "spruce_planks", "stone_bricks", light="lantern")
    # The side platforms halfway, with broken walls as cover, linked to the middle island.
    for sign in (-1, 1):
        for x in range(-5, 6):
            for z in range(14, 27):
                s.set((x, S, sign * z), "mossy_stone_bricks")
                s.set((x, S - 1, sign * z), "stone")
                for y in (S + 1, S + 2):
                    s.remove((x, y, sign * z))
        for x, z in ((-3 * sign, 16), (3 * sign, 24)):
            for dx in (-1, 0, 1):
                for y in range(S + 1, S + 2 + (dx + 1) % 2):
                    s.set((x + dx, y, sign * z), "mossy_stone_bricks")
        cross_bridge(s, frames[2], 0, sign * 8, sign * 13, "spruce_planks", "stone_bricks")
    # The middle island and its ruined watchtower.
    for x in range(-11, 12):
        for z in range(-8, 9):
            if (abs(x) / 11.5) ** 4 + (abs(z) / 8.5) ** 4 > 1:
                continue
            s.set((x, S, z), "mossy_stone_bricks" if (x * 7 + z * 3) % 5 == 0 else "stone_bricks")
            for y in range(S - 4 + max(abs(x) // 4, abs(z) // 3), S):
                s.set((x, y, z), "stone")
    for x in range(-3, 4):
        for z in range(-3, 4):
            if max(abs(x), abs(z)) != 3:
                continue
            # A broken ring: higher on one diagonal, open on the lane axis.
            if z == 0 or x == 0:
                continue
            height = 2 + (abs(x + z) % 3 == 0) + (x * z > 0) * 2
            for y in range(S + 1, S + 1 + height):
                s.set((x, y, z), "mossy_stone_bricks" if y % 2 else "stone_bricks")
    s.set((0, S + 1, 0), "chiseled_stone_bricks")
    s.set((0, S + 2, 0), "lantern")
    whole_map(s)
    check(s, wools, frames)
    return s


def timberline():
    """Two timber forts on grassy islands. There is no straight way between them: each fort's
    gates open onto two flank bridges that land on a wide orchard in the middle, where hedges,
    trees and a stone well give cover, so every crossing passes through the orchard."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}
    wools = {1: ("pink", "light_blue"), 2: ("lime", "purple")}
    for team, frame in frames.items():
        colour = TEAM_COLORS[team]
        base(s, frame, team, wools, {
            "ground": "grass_block", "ground_mix": "moss_block", "fill": "dirt", "edge": "coarse_dirt",
            "path": f"{colour}_terracotta", "wall": "stripped_spruce_wood", "trim": "spruce_fence",
            "plinth": "mossy_cobblestone", "cap": "polished_granite", "light": "glowstone",
            "pad": "spruce_planks", "roof": "dark_oak_planks", "post": "spruce_log",
            "room": "spruce_planks", "room_floor": "stripped_oak_wood", "court": "packed_mud",
        }, gates=(-14, 14), cover=((42, -5), (42, 5), (45, -21), (45, 21)))
        for sign in (-1, 1):
            bridge(s, frame, 14, 34, 14 * sign, "spruce_planks", "mossy_cobblestone", light="lantern")
            # An oak in each back corner of the fort and flowers along its flanks.
            tree(s, frame.pos(59, S + 1, 23 * sign), log="oak_log", leaves="oak_leaves", height=5)
            for u in (41, 47, 53):
                s.set(frame.pos(u, S + 1, 24 * sign), "cornflower" if team == 2 else "poppy")
    # The orchard in the middle.
    for x in range(-15, 16):
        for z in range(-21, 22):
            if (abs(x) / 15.5) ** 4 + (abs(z) / 21.5) ** 4 > 1:
                continue
            s.set((x, S, z), "grass_block")
            depth = 3 + (abs(x) < 10) + (abs(z) < 14) * 2
            for y in range(S - depth, S):
                s.set((x, y, z), "dirt" if y > S - 3 else "stone")
    # Hedges across the orchard, broken where the paths run.
    for z in range(-18, 19):
        if abs(z) in (13, 14, 15) or abs(z) <= 1:
            continue
        for x in (-7, 7):
            for y in (S + 1, S + 2):
                s.set((x, y, z), "azalea_leaves", persistent=True)
    for x, z in ((-11, -7), (11, 7), (-3, 10), (3, -10), (-11, 19), (11, -19), (4, 4), (-4, -4)):
        tree(s, (x, S + 1, z), log="oak_log", leaves="flowering_azalea_leaves" if (x * z) % 2 else "oak_leaves")
    for x, z in ((-12, 2), (12, -2), (0, 17), (0, -17), (-5, -15), (5, 15)):
        s.set((x, S + 1, z), "poppy" if x > 0 or (x == 0 and z > 0) else "dandelion")
    # The well in the middle: a ring of cobblestone around water, a roof on four posts.
    for x in range(-2, 3):
        for z in range(-2, 3):
            edge = max(abs(x), abs(z)) == 2
            s.set((x, S, z), "cobblestone" if edge else "water")
            if edge:
                s.set((x, S + 1, z), "cobblestone_wall")
            else:
                s.set((x, S - 1, z), "cobblestone")
    for x, z in ((-2, -2), (2, 2), (-2, 2), (2, -2)):
        for y in (S + 2, S + 3):
            s.set((x, y, z), "spruce_fence")
    for x in range(-3, 4):
        for z in range(-3, 4):
            s.set((x, S + 4, z), "spruce_slab", type="bottom")
    s.set((0, S + 3, 0), "lantern", hanging=True)
    s.set((0, S + 4, 0), "spruce_planks")
    whole_map(s)
    check(s, wools, frames)
    return s


def whole_map(s):
    """Building is allowed over the whole map up to twelve blocks above the surface; the void
    is twelve below it, and the lobby high above the middle."""
    xs = [p[0] for p in s.blocks]
    zs = [p[2] for p in s.blocks]
    lo = (min(xs), S - 10, min(zs))
    region(s, "build", lo, (max(xs) - lo[0], 22, max(zs) - lo[2]))
    s.marker((0, S - 12, 0), "void")
    lobby(s, y=S + 30, size=4, block="white_stained_glass")


def main():
    for name, build in (("bastions", bastions), ("timberline", timberline)):
        s = build()
        size = s.save(OUT.format(name))
        print(name, size)


if __name__ == "__main__":
    main()
