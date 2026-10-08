"""Generates the Grinch Simulator maps (original layouts): python3 tools/maps/grinch_simulator.py
from the repo root.

A snowy village on a 3 x 3 grid of plots split by roads: the middle plot is the plaza, where
everyone spawns around a big Christmas tree, and the eight plots around it hold twelve houses.
Each house is built from a seeded template, as Hypixel's first Grinch Simulator pasted random
houses into its village:

- an open cottage has a doorway and one room; a climbing tree behind it (a spruce with a spiral
  of green terracotta steps inside its leaves) leads onto its roof;
- a locked house has an iron door that never opens and closed ground-floor windows: players get in
  through an open upper window at the back, reached by leaf steps up the back wall, or drop down
  its chimney from the roof, which a climbing tree behind it reaches; stairs inside lead down.

Markers (see the README's Grinch Simulator section): `spawn <n>` around the plaza tree, `lobby`
in the plaza, and `point present` at every spot a present may be put (the game picks some of them
at random each match).

The script refuses a map where a present spot has nothing under it, or cannot be reached on foot
from the spawns and left again (walking, jumping one block up and dropping at most three blocks,
with every other present spot taken), so bots and players can get every present.
"""

import math
import os
import random
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/grinch_simulator/{}.nbt"

# Ground: stone at 0, dirt at 1, snow (or road) at G; players walk at G + 1.
G = 2
PLOT = 30
ROAD = 3
EDGE = 4
SIZE = EDGE * 2 + PLOT * 3 + ROAD * 2
SPAWNS = 6

FACINGS = ["north", "east", "south", "west"]
# Steps of a climbing tree, in order round the trunk; consecutive cells are side by side.
RING = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]


def axis_props(name, axis):
    return {"axis": axis} if name.endswith("_log") or name.endswith("_wood") else {}


class Stamp:
    """Writes a house built in local coordinates (front wall along lz = 0, facing -z, the street)
    into the map, turned `turns` quarter turns clockwise and moved so that local (0, 0) lands at
    `origin`."""

    def __init__(self, s, origin, turns, presents):
        self.s = s
        self.ox, self.oz = origin
        self.turns = turns % 4
        self.presents = presents

    def xz(self, lx, lz):
        x, z = lx, lz
        for _ in range(self.turns):
            x, z = -z, x
        return self.ox + x, self.oz + z

    def pos(self, lx, y, lz):
        x, z = self.xz(lx, lz)
        return (x, y, z)

    def turn(self, props):
        props = dict(props)
        if props.get("facing") in FACINGS:
            props["facing"] = FACINGS[(FACINGS.index(props["facing"]) + self.turns) % 4]
        if props.get("axis") in ("x", "z") and self.turns % 2:
            props["axis"] = "z" if props["axis"] == "x" else "x"
        sides = {side: props.pop(side) for side in FACINGS if side in props}
        for side, value in sides.items():
            props[FACINGS[(FACINGS.index(side) + self.turns) % 4]] = value
        return props

    def set(self, lx, y, lz, name, **props):
        self.s.set(self.pos(lx, y, lz), name, **self.turn(props))

    def fill(self, lx1, y1, lz1, lx2, y2, lz2, name, **props):
        for lx in range(min(lx1, lx2), max(lx1, lx2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for lz in range(min(lz1, lz2), max(lz1, lz2) + 1):
                    self.set(lx, y, lz, name, **props)

    def air(self, lx1, y1, lz1, lx2, y2, lz2):
        for lx in range(min(lx1, lx2), max(lx1, lx2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for lz in range(min(lz1, lz2), max(lz1, lz2) + 1):
                    self.s.remove(self.pos(lx, y, lz))

    def get(self, lx, y, lz):
        return self.s.get(self.pos(lx, y, lz))

    def present(self, lx, y, lz):
        self.presents.append(self.pos(lx, y, lz))


def pane(st, lx, y, lz, along_x):
    if along_x:
        st.set(lx, y, lz, "glass_pane", east="true", west="true")
    else:
        st.set(lx, y, lz, "glass_pane", north="true", south="true")


def walls(st, w, d, y1, y2, material, trim):
    """Walls round 0..w-1 x 0..d-1 from y1 to y2, with `trim` columns at the corners."""
    for y in range(y1, y2 + 1):
        for lx in range(w):
            for lz in (0, d - 1):
                st.set(lx, y, lz, material)
        for lz in range(1, d - 1):
            for lx in (0, w - 1):
                st.set(lx, y, lz, material)
    for lx in (0, w - 1):
        for lz in (0, d - 1):
            st.fill(lx, y1, lz, lx, y2, lz, trim, **axis_props(trim, "y"))


def gable_roof(st, w, d, base_y, roof, hollow, gable):
    """A roof ridged along local x over walls 0..w-1 x 0..d-1 whose top is base_y - 1,
    overhanging a block on every side; layer k is at base_y + k and its slopes at lz = k - 1 and
    lz = d - k. A hollow roof is only its surface, so the room below reaches up into it; the gable
    walls under the slopes are `gable`. Returns the ridge's height."""
    k = 0
    while True:
        lo, hi = k - 1, d - k
        if lo > hi:
            return base_y + k - 1
        y = base_y + k
        for lx in range(-1, w + 1):
            if lo == hi:
                st.set(lx, y, lo, roof + "_planks")
                continue
            st.set(lx, y, lo, roof + "_stairs", facing="south", half="bottom")
            st.set(lx, y, hi, roof + "_stairs", facing="north", half="bottom")
            for lz in range(lo + 1, hi):
                if lx in (0, w - 1):
                    st.set(lx, y, lz, gable)
                elif not hollow or lx in (-1, w):
                    st.set(lx, y, lz, roof + "_planks")
        k += 1


def christmas_tree(st, lx, lz, height, top_step=None, exit_side=(0, -1)):
    """A spruce with its trunk at (lx, lz) and a glowstone star on top. With `top_step`, green
    terracotta steps climb round the trunk one block at a time from the ground to that height,
    the last one on `exit_side`: the leaves stay clear above the steps and on the exit side from
    the top step up, so whatever lies two blocks out that way (a roof's eave) can be stepped onto,
    and below the first step's outer side, where the climb starts."""
    trunk_top = G + height
    st.fill(lx, G + 1, lz, lx, trunk_top, lz, "spruce_log", axis="y")
    steps = []
    if top_step is not None:
        count = top_step - G
        end = RING.index(exit_side)
        for index in range(count):
            steps.append((RING[(end - (count - 1 - index)) % len(RING)], G + 1 + index))
    clear = set()
    for (dx, dz), y in steps:
        for above in range(1, 4):
            clear.add((dx, dz, y + above))
    if steps:
        # The climb starts from outside the first step: the outer ring beside it stays open.
        (fx, fz), _ = steps[0]
        for ox in range(-2, 3):
            for oz in range(-2, 3):
                if max(abs(ox), abs(oz)) == 2 and abs(ox - fx) <= 1 and abs(oz - fz) <= 1:
                    for y in range(G + 1, G + 4):
                        clear.add((ox, oz, y))
        for y in range(top_step, trunk_top + 1):
            for side in (-1, 0, 1):
                ex, ez = exit_side
                clear.add((ex * 2 + (side if ex == 0 else 0), ez * 2 + (side if ez == 0 else 0), y))
    step_cells = {(dx, dz, y) for (dx, dz), y in steps}
    span = max(1, trunk_top - (G + 2))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if (dx, dz) == (0, 0):
                continue
            for y in range(G + 2, trunk_top + 1):
                # A cone: wide and round at the bottom, a diamond higher up, then a thin top.
                part = (y - G - 2) / span
                reach = 3 if part < 0.4 else 2 if part < 0.7 else 1
                if abs(dx) + abs(dz) > reach or max(abs(dx), abs(dz)) > 2 \
                        or part >= 0.7 and max(abs(dx), abs(dz)) > 1:
                    continue
                if (dx, dz, y) in clear or (dx, dz, y) in step_cells:
                    continue
                bauble = abs(dx) + abs(dz) == reach and (lx * 7 + lz * 13 + y * 5 + dx * 3 + dz) % 9 == 0
                if bauble:
                    st.set(lx + dx, y, lz + dz, ("red_wool", "yellow_wool", "light_blue_wool")[(y + dx) % 3])
                else:
                    st.set(lx + dx, y, lz + dz, "spruce_leaves", persistent="true")
    for dx, dz, y in step_cells:
        st.set(lx + dx, y, lz + dz, "green_terracotta")
    st.set(lx, trunk_top + 1, lz, "spruce_leaves", persistent="true")
    st.set(lx, trunk_top + 2, lz, "glowstone")


def indoor_tree(st, lx, y, lz):
    """A small Christmas tree in a room: two leaves on a log with a glowstone star on top (leaves
    cannot hold a lantern); presents go round its foot."""
    st.set(lx, y, lz, "spruce_log", axis="y")
    st.set(lx, y + 1, lz, "spruce_leaves", persistent="true")
    st.set(lx, y + 2, lz, "spruce_leaves", persistent="true")
    st.set(lx, y + 3, lz, "glowstone")


def shelf(st, rng, lx, y, lz, facing):
    kind = rng.choice(("bookshelf", "chiseled_bookshelf", "barrel", "crafting_table"))
    if kind == "chiseled_bookshelf":
        st.set(lx, y, lz, kind, facing=facing)
    elif kind == "barrel":
        st.set(lx, y, lz, kind, facing="up", open="false")
    else:
        st.set(lx, y, lz, kind)


def cottage(st, rng, palette):
    """An open cottage, 9 x 9: a doorway in the front, one room under a solid roof with a chimney
    stack, and a climbing tree behind it up to the back eave."""
    w, d = 9, 9
    wall, trim, roof, floor = palette["wall"], palette["trim"], palette["roof"], palette["floor"]
    st.fill(0, G, 0, w - 1, G, d - 1, floor)
    walls(st, w, d, G + 1, G + 4, wall, trim)
    st.air(4, G + 1, 0, 4, G + 3, 0)
    for lx in (2, 6):
        for y in (G + 2, G + 3):
            pane(st, lx, y, 0, True)
            pane(st, lx, y, d - 1, True)
    for lz in (3, 5):
        for lx in (0, w - 1):
            for y in (G + 2, G + 3):
                pane(st, lx, y, lz, False)
    ridge = gable_roof(st, w, d, G + 5, roof, False, wall)
    # Ceiling lights set into the ceiling: a hanging lantern would drop when its ceiling is pasted after it.
    st.set(4, G + 5, 4, "glowstone")
    st.fill(6, ridge - 1, 4, 6, ridge + 1, 4, "bricks")
    # Inside: a small tree in a back corner with presents round it, shelves along a side wall.
    tree_x = rng.choice((2, 6))
    other = 1 if tree_x == 6 else 7
    indoor_tree(st, tree_x, G + 1, 6)
    for dx, dz in ((-1, 0), (1, 0), (0, -1)):
        st.present(tree_x + dx, G + 1, 6 + dz)
    st.present(4, G + 1, 4)
    for lz in (2, 3, 4):
        shelf(st, rng, other, G + 1, lz, "east" if other == 1 else "west")
        st.present(other, G + 2, lz)
    shelf(st, rng, other, G + 1, 7, "east" if other == 1 else "west")
    st.present(other, G + 2, 7)
    st.present(4, G + 1, 7)
    st.present(1, G + 1, 1)
    st.present(7, G + 1, 1)
    # Outside: by the doorway, on the front slope and on the ridge.
    st.present(2, G + 1, -1)
    st.present(6, G + 1, -1)
    for lx in (1, 4, 7):
        st.present(lx, G + 8, 1)
    st.present(2, ridge + 1, 4)
    # The climbing tree behind, its top step level with the back eave.
    tx = rng.choice((2, 6))
    christmas_tree(st, tx, d + 2, 9, top_step=G + 5)
    st.present(tx + 2, G + 1, d + 3)


def locked_house(st, rng, palette):
    """A locked house, 13 x 11, two floors under a hollow roof: an iron door that never opens and
    panes downstairs, an open window upstairs at the back reached by leaf steps, a chimney to drop
    down from the roof (a climbing tree behind reaches the back eave), and stairs inside."""
    w, d = 13, 11
    wall, trim, roof, floor = palette["wall"], palette["trim"], palette["roof"], palette["floor"]
    upper = palette["upper"]
    st.fill(0, G, 0, w - 1, G, d - 1, floor)
    walls(st, w, d, G + 1, G + 4, wall, trim)
    walls(st, w, d, G + 5, G + 9, upper, trim)
    st.fill(1, G + 5, 1, w - 2, G + 5, d - 2, floor)
    st.set(6, G + 1, 0, "iron_door", half="lower", facing="south", hinge="left", open="false", powered="false")
    st.set(6, G + 2, 0, "iron_door", half="upper", facing="south", hinge="left", open="false", powered="false")
    for lx in (2, 3, 9, 10):
        for y in (G + 2, G + 3):
            pane(st, lx, y, 0, True)
    for lx in (3, 9):
        for y in (G + 7, G + 8):
            pane(st, lx, y, 0, True)
    for lz in (3, 7):
        for lx in (0, w - 1):
            for y in (G + 2, G + 3, G + 7, G + 8):
                pane(st, lx, y, lz, False)
    # Which way round: the window and leaf steps on one side, the stairs on the other.
    win = rng.choice((3, 9))
    side = 11 if win == 3 else 1
    run = 1 if win == 3 else -1
    st.air(win, G + 6, d - 1, win, G + 7, d - 1)
    other_back = 12 - win
    for y in (G + 7, G + 8):
        pane(st, other_back, y, d - 1, True)
    for index in range(6):
        lx = win + run * index
        top = G + 5 - max(0, index - 1)
        st.fill(lx, G + 1, d, lx, top, d, palette["leaves"], persistent="true")
    # Stairs along the side wall, opening into the upper floor behind a rail.
    stairs = floor.replace("_planks", "_stairs")
    for index in range(4):
        lz = 6 - index
        for below in range(G + 1, G + 1 + index):
            st.set(side, below, lz, floor)
        st.set(side, G + 1 + index, lz, stairs, facing="north", half="bottom")
    st.air(side, G + 5, 3, side, G + 5, 6)
    rail = side - 1 if side == 11 else side + 1
    for lz in range(3, 8):
        st.set(rail, G + 6, lz, "spruce_fence", north="true" if lz > 3 else "false",
               south="true" if lz < 7 else "false")
    st.set(side, G + 6, 7, "spruce_fence", **({"west": "true"} if side == 11 else {"east": "true"}))
    # The roof and its chimney, on the window's side at the front; its shaft opens into the room.
    ridge = gable_roof(st, w, d, G + 10, roof, True, upper)
    st.set(6, ridge, 5, "glowstone")
    st.set(4, G + 5, 3, "glowstone")
    st.set(8, G + 5, 7, "glowstone")
    cx = win
    for y in range(G + 6, G + 15):
        for lx in range(cx - 1, cx + 2):
            st.set(lx, y, 2, "bricks")
            st.set(lx, y, 4, "bricks")
        st.set(cx - 1, y, 3, "bricks")
        st.set(cx + 1, y, 3, "bricks")
    st.air(cx, G + 6, 3, cx, G + 14, 3)
    st.air(cx, G + 6, 2, cx, G + 7, 2)
    # Downstairs: a tree with presents round it, shelves along the wall, a table.
    tree_x = 12 - win
    indoor_tree(st, tree_x, G + 1, 8)
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (-1, -1), (1, -1)):
        st.present(tree_x + dx, G + 1, 8 + dz)
    shelf_x = 12 - side
    for lz in (2, 3, 4, 5, 6):
        shelf(st, rng, shelf_x, G + 1, lz, "east" if shelf_x == 1 else "west")
        st.present(shelf_x, G + 2, lz)
    for lx in (5, 6, 7):
        st.set(lx, G + 1, 5, palette["table"])
        st.present(lx, G + 2, 5)
    st.present(1, G + 1, 1)
    st.present(11, G + 1, 1)
    st.present(6, G + 1, 1)
    st.present(6, G + 1, 9)
    # Upstairs: two beds, shelves at the front, presents in the corners.
    for bed_x in ((10, 8) if side == 11 else (2, 4)):
        st.set(bed_x, G + 6, 7, palette["bed"], facing="north", part="head", occupied="false")
        st.set(bed_x, G + 6, 8, palette["bed"], facing="north", part="foot", occupied="false")
        st.present(bed_x, G + 6, 6)
    for lx in (5, 6, 7):
        st.set(lx, G + 6, 1, "bookshelf")
        st.present(lx, G + 7, 1)
    for lx, lz in ((1, 9), (11, 9), (6, 9), (6, 5), (side, 1)):
        st.present(lx, G + 6, lz)
    # On the roof's front slope, and outside the front door.
    for lx in (1, 6, 11):
        st.present(lx, G + 13, 1)
    st.present(4, G + 1, -1)
    st.present(8, G + 1, -1)
    # The climbing tree behind, away from the leaf steps, its top step a block under the eave.
    christmas_tree(st, 11 if win == 3 else 1, d + 2, 12, top_step=G + 9)


def lamp(s, pos):
    x, y, z = pos
    s.fill((x, y, z), (x, y + 2, z), "spruce_fence")
    s.set((x, y + 3, z), "lantern", hanging="false")


def plaza(s, cx, cz, presents, style):
    """The plaza: a stone circle with the big tree in the middle and lamps; the spawns stand round
    the tree facing out to the village."""
    for x in range(cx - 11, cx + 12):
        for z in range(cz - 11, cz + 12):
            dist = math.hypot(x - cx, z - cz)
            if dist > 10.5:
                continue
            block = "polished_andesite" if dist > 9.5 else ("stone_bricks" if (x + z) % 2 else "andesite")
            if style == "pond" and 3.5 < dist <= 8.5:
                block = "packed_ice" if dist <= 7.5 else "polished_diorite"
            s.set((x, G, z), block)
    for y in range(G + 1, G + 16):
        s.set((cx, y, cz), "spruce_log", axis="y")
    ornaments = ["red_wool", "yellow_wool", "light_blue_wool", "white_wool"]
    rng = random.Random(7)
    for y in range(G + 3, G + 16):
        r = max(0, 4 - (y - G - 3) // 3)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if (dx, dz) != (0, 0) and abs(dx) + abs(dz) <= r + (1 if r > 1 else 0):
                    edge = abs(dx) + abs(dz) >= r
                    block = rng.choice(ornaments) if edge and rng.random() < 0.12 else "spruce_leaves"
                    if block == "spruce_leaves":
                        s.set((cx + dx, y, cz + dz), block, persistent="true")
                    else:
                        s.set((cx + dx, y, cz + dz), block)
    s.set((cx, G + 16, cz), "spruce_leaves", persistent="true")
    s.set((cx, G + 17, cz), "gold_block")
    for index in range(SPAWNS):
        angle = index * 2 * math.pi / SPAWNS
        dx, dz = round(math.sin(angle) * 7), -round(math.cos(angle) * 7)
        yaw = round(math.degrees(math.atan2(-dx, dz)))
        s.marker((cx + dx, G + 1, cz + dz), "spawn {} {}".format(index + 1, yaw))
    s.marker((cx + 9, G + 1, cz), "lobby 90")
    for dx, dz in ((5, 0), (-5, 0), (0, 5), (0, -5), (3, 3), (-3, -3)):
        presents.append((cx + dx, G + 1, cz + dz))
    for dx, dz in ((7, 7), (-7, 7), (7, -7), (-7, -7)):
        lamp(s, (cx + dx, G + 1, cz + dz))


PALETTES = [
    {"wall": "white_terracotta", "trim": "spruce_log", "roof": "dark_oak", "floor": "spruce_planks",
     "upper": "spruce_planks", "leaves": "spruce_leaves", "bed": "red_bed", "table": "spruce_planks"},
    {"wall": "stone_bricks", "trim": "dark_oak_log", "roof": "spruce", "floor": "oak_planks",
     "upper": "white_terracotta", "leaves": "oak_leaves", "bed": "green_bed", "table": "oak_planks"},
    {"wall": "bricks", "trim": "stripped_spruce_log", "roof": "spruce", "floor": "dark_oak_planks",
     "upper": "light_gray_terracotta", "leaves": "spruce_leaves", "bed": "blue_bed", "table": "spruce_planks"},
    {"wall": "spruce_planks", "trim": "stripped_dark_oak_log", "roof": "dark_oak", "floor": "birch_planks",
     "upper": "white_concrete", "leaves": "azalea_leaves", "bed": "yellow_bed", "table": "birch_planks"},
    {"wall": "light_gray_terracotta", "trim": "oak_log", "roof": "crimson", "floor": "spruce_planks",
     "upper": "white_terracotta", "leaves": "spruce_leaves", "bed": "white_bed", "table": "dark_oak_planks"},
]


def ground(s):
    s.fill((0, 0, 0), (SIZE - 1, 0, SIZE - 1), "stone")
    s.fill((0, 1, 0), (SIZE - 1, 1, SIZE - 1), "dirt")
    s.fill((0, G, 0), (SIZE - 1, G, SIZE - 1), "snow_block")
    # The edge: a spruce fence with barriers above it, so nobody walks or jumps off the map.
    last = SIZE - 1
    for i in range(SIZE):
        for x, z in ((i, 0), (i, last), (0, i), (last, i)):
            along_x = z in (0, last)
            along_z = x in (0, last)
            s.set((x, G + 1, z), "spruce_fence",
                  east=str(along_x and x < last).lower(), west=str(along_x and x > 0).lower(),
                  south=str(along_z and z < last).lower(), north=str(along_z and z > 0).lower())
            for y in range(G + 2, G + 26):
                s.set((x, y, z), "barrier")


def roads(s):
    """Two cobbled roads each way between the plots."""
    rng = random.Random(3)
    for start in (EDGE + PLOT, EDGE + PLOT * 2 + ROAD):
        for offset in range(ROAD):
            for i in range(1, SIZE - 1):
                for x, z in ((start + offset, i), (i, start + offset)):
                    s.set((x, G, z), rng.choice(("cobblestone", "stone_bricks", "andesite", "cobblestone")))


def plot_origin(col, row):
    return (EDGE + col * (PLOT + ROAD), EDGE + row * (PLOT + ROAD))


def place(s, presents, build, rng, palette, col, row, slot, facing):
    """Builds one house in a plot, facing `facing` (towards a road): one house in the middle
    (slot None) or two side by side (slot 0 or 1), three blocks back from the plot's front, with a
    plank path from the door to the road."""
    px, pz = plot_origin(col, row)
    turns = FACINGS.index(facing)
    w = 13 if build is locked_house else 9
    u = (PLOT - w) // 2 if slot is None else (3 if slot == 0 else PLOT - 3 - w)
    v = 3
    corners = {
        0: (px + u, pz + v),
        1: (px + PLOT - 1 - v, pz + u),
        2: (px + PLOT - 1 - u, pz + PLOT - 1 - v),
        3: (px + v, pz + PLOT - 1 - u),
    }
    st = Stamp(s, corners[turns], turns, presents)
    build(st, rng, palette)
    door = 4 if build is cottage else 6
    for step in range(1, v + 1):
        x, z = st.xz(door, -step)
        s.set((x, G, z), "spruce_planks")


def free(s, x, z, radius, height):
    """Whether nothing stands on the open snow within `radius` of (x, z), up to `height`."""
    return all(s.get((x + dx, G, z + dz)) == "minecraft:snow_block"
               and all(s.get((x + dx, G + dy, z + dz)) is None for dy in range(1, height + 1))
               for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1))


def scenery(s, rng):
    """Pines and snowmen in the gardens, and lamps at every corner where the roads cross."""
    pines = Stamp(s, (0, 0), 0, [])
    for col in range(3):
        for row in range(3):
            if (col, row) == (1, 1):
                continue
            px, pz = plot_origin(col, row)
            spots = [(px + u, pz + v) for u in range(3, PLOT - 3, 2) for v in range(3, PLOT - 3, 2)]
            rng.shuffle(spots)
            planted = 0
            for x, z in spots:
                if planted < 3 and free(s, x, z, 3, 10):
                    christmas_tree(pines, x, z, rng.choice((5, 6, 7)))
                    planted += 1
            for x, z in ((px + 1, pz + 1), (px + PLOT - 2, pz + PLOT - 2), (px + 1, pz + PLOT - 2)):
                if free(s, x, z, 1, 3):
                    s.set((x, G + 1, z), "snow_block")
                    # A wool middle: two snow blocks under a pumpkin would come alive as a snow golem.
                    s.set((x, G + 2, z), "white_wool")
                    s.set((x, G + 3, z), "carved_pumpkin", facing=rng.choice(FACINGS))
    for a in (EDGE + PLOT - 1, EDGE + PLOT * 2 + ROAD - 1):
        for b in (EDGE + PLOT - 1, EDGE + PLOT * 2 + ROAD - 1):
            for dx, dz in ((0, 0), (ROAD + 1, 0), (0, ROAD + 1), (ROAD + 1, ROAD + 1)):
                x, z = a + dx, b + dz
                if s.get((x, G + 1, z)) is None:
                    lamp(s, (x, G + 1, z))


def village(name, seed, layout, plaza_style):
    rng = random.Random(seed)
    s = Structure()
    ground(s)
    roads(s)
    presents = []
    centre = plot_origin(1, 1)
    plaza(s, centre[0] + PLOT // 2, centre[1] + PLOT // 2, presents, plaza_style)
    for col, row, kind, facing in layout:
        if kind == "locked":
            place(s, presents, locked_house, rng, rng.choice(PALETTES), col, row, None, facing)
        else:
            for slot in (0, 1):
                place(s, presents, cottage, rng, rng.choice(PALETTES), col, row, slot, facing)
    scenery(s, rng)
    spots = set(presents)
    for x in range(1, SIZE - 1):
        for z in range(1, SIZE - 1):
            if s.get((x, G, z)) == "minecraft:snow_block" and s.get((x, G + 1, z)) is None \
                    and (x, G + 1, z) not in spots and rng.random() < 0.18:
                s.set((x, G + 1, z), "snow", layers="1")
    for pos in presents:
        s.marker(pos, "point present")
    check(s, presents, name)
    return s


# ------------------------------------------------------------------ reachability

PASSABLE = {"minecraft:air", "minecraft:snow", "minecraft:structure_block"}
NOT_FLOOR_WORDS = ("fence", "_wall", "pane", "door", "lantern", "campfire", "carpet", "_bed", "barrier",
                   "_slab", "structure_block")


def block(s, pos):
    return s.get(pos) or "minecraft:air"


def passable(s, pos, spots):
    return pos not in spots and block(s, pos) in PASSABLE


def floor(s, pos, spots):
    if pos in spots:
        return False
    name = block(s, pos)
    return name not in PASSABLE and not any(word in name for word in NOT_FLOOR_WORDS)


def standable(s, pos, spots):
    x, y, z = pos
    return passable(s, pos, spots) and passable(s, (x, y + 1, z), spots) and floor(s, (x, y - 1, z), spots)


def moves(s, cell, spots):
    x, y, z = cell
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if standable(s, (nx, y, nz), spots):
            yield (nx, y, nz)
        elif standable(s, (nx, y + 1, nz), spots) and passable(s, (x, y + 2, z), spots):
            yield (nx, y + 1, nz)
        elif passable(s, (nx, y, nz), spots) and passable(s, (nx, y + 1, nz), spots):
            for drop in range(1, 4):
                below = (nx, y - drop, nz)
                if not passable(s, below, spots):
                    break
                if floor(s, (nx, y - drop - 1, nz), spots):
                    yield below
                    break


def check(s, presents, name):
    spots = set(presents)
    assert len(spots) == len(presents), name + ": two present spots share a block"
    for pos in presents:
        below = (pos[0], pos[1] - 1, pos[2])
        assert floor(s, below, set()), "{}: present spot {} stands on {}".format(name, pos, block(s, below))
    starts = [pos for pos, entry in s.blocks.items()
              if entry[0] == "minecraft:structure_block" and entry[2]["metadata"].startswith("spawn")]
    edges = {}
    seen = set(starts)
    todo = deque(starts)
    while todo:
        cell = todo.popleft()
        edges[cell] = list(moves(s, cell, spots))
        for nxt in edges[cell]:
            if nxt not in seen:
                seen.add(nxt)
                todo.append(nxt)
    back = {}
    for cell, nexts in edges.items():
        for nxt in nexts:
            back.setdefault(nxt, []).append(cell)
    home = set(starts)
    todo = deque(starts)
    while todo:
        for prev in back.get(todo.popleft(), ()):
            if prev not in home:
                home.add(prev)
                todo.append(prev)
    both = seen & home
    missing = [pos for pos in presents
               if not any((pos[0] + dx, pos[1] + dy, pos[2] + dz) in both
                          for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in (-1, 0, 1) if (dx, dz) != (0, 0))]
    assert not missing, "{}: {} present spots cannot be reached and left on foot, e.g. {}".format(
        name, len(missing), missing[:6])
    print("{}: {} present spots, all reachable; {} walkable cells".format(name, len(presents), len(both)))


def hollyvale():
    """Locked houses in the corners, two cottages on each edge plot, all facing the roads round
    the plaza."""
    layout = [
        (0, 0, "locked", "south"), (2, 0, "locked", "south"),
        (0, 2, "locked", "north"), (2, 2, "locked", "north"),
        (1, 0, "cottages", "south"), (1, 2, "cottages", "north"),
        (0, 1, "cottages", "east"), (2, 1, "cottages", "west"),
    ]
    return village("hollyvale", 2016, layout, "stone")


def frostmere():
    """Locked houses on the edge plots facing the plaza, cottages in the corners facing the roads
    across, and a frozen pond round the plaza tree."""
    layout = [
        (1, 0, "locked", "south"), (1, 2, "locked", "north"),
        (0, 1, "locked", "east"), (2, 1, "locked", "west"),
        (0, 0, "cottages", "east"), (2, 0, "cottages", "west"),
        (0, 2, "cottages", "east"), (2, 2, "cottages", "west"),
    ]
    return village("frostmere", 2020, layout, "pond")


def main():
    for name, build in (("hollyvale", hollyvale), ("frostmere", frostmere)):
        size = build().save(OUT.format(name))
        print(name, size)


if __name__ == "__main__":
    main()
