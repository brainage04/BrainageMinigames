"""Generates the Bed Wars maps (original layouts): python3 tools/maps/bedwars.py from the repo root.

Like Hypixel's, the maps are islands in the void that players bridge between with the blocks they
buy: one base island per team (bed at the front, spawn in the middle, the island generator at the
back, the Item Shop and Team Upgrades shopkeepers either side), diamond islands near the bases and
emerald generators on the middle island. Hypixel's counts: eight-team maps (Solo, Doubles) have four
diamond and four emerald generators, four-team maps (3v3v3v3, 4v4v4v4) four diamond and two emerald
generators, and two-team maps (4v4) two of each. The voidless maps are the same layouts raised over
a solid ground, as Hypixel's Voidless.

Markers (see the README's Bed Wars section): `spawn <team> <yaw>`, `point bed_<team> <yaw>` (the
bed's foot; its head lies the way the yaw faces), `point shop_<team> <yaw>`, `point upgrades_<team>
<yaw>`, `point forge_<team>`, `point diamond_<n>`, `point emerald_<n>`, `region base_<team>`,
`region build`, `void` and `lobby`. Team numbers follow the mod's colours: red, blue, green, yellow,
aqua, pink, gold and white.

The script refuses a map whose islands touch (every island must be bridged to).
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/{}/{}.nbt"

S = 16
# The mod's team colours as dyes: red, blue, green, yellow, aqua, pink, gold, white.
DYES = {1: "red", 2: "blue", 3: "lime", 4: "yellow", 5: "light_blue", 6: "magenta", 7: "orange", 8: "white"}
DIRECTIONS = [(1, 0), (0, 1), (-1, 0), (0, -1)]


def yaw(dx, dz):
    """The marker yaw facing along (dx, dz): 0 south (+z), 90 west, 180 north, -90 east."""
    return int(round(math.degrees(math.atan2(-dx, dz))))


class Base:
    """Local (a, b) coordinates of an island: `a` along `direction` (away from the middle for a base),
    `b` across it, centred on (cx, cz)."""

    def __init__(self, cx, cz, direction):
        self.cx, self.cz = cx, cz
        self.dx, self.dz = direction

    def xz(self, a, b):
        return (self.cx + a * self.dx - b * self.dz, self.cz + a * self.dz + b * self.dx)

    def pos(self, a, y, b):
        x, z = self.xz(a, b)
        return (x, y, z)

    def facing(self, da, db):
        """The yaw facing local direction (da, db)."""
        return yaw(da * self.dx - db * self.dz, da * self.dz + db * self.dx)


class Islands:
    """Records each island's footprint so that touching islands are refused."""

    def __init__(self):
        self.cells = {}

    def claim(self, name, xz):
        for x, z in xz:
            for nx in range(x - 1, x + 2):
                for nz in range(z - 1, z + 2):
                    other = self.cells.get((nx, nz))
                    if other is not None and other != name:
                        raise ValueError(f"islands {name} and {other} touch at {(x, z)}")
        for cell in xz:
            self.cells[cell] = name


def disc(s, islands, name, cells, top, fill, depth, surface=S, underside="stone"):
    """An island over `cells` (a set of (x, z)), `depth` deep at its heart and thinner to the rim."""
    islands.claim(name, cells)
    for x, z in cells:
        rim = min(abs(x - ox) + abs(z - oz) for ox, oz in _outside(cells, x, z))
        bottom = surface - 1 - min(depth, 1 + rim)
        for y in range(bottom, surface):
            s.set((x, y, z), underside if y < surface - 2 else fill)
        s.set((x, surface, z), top(x, z) if callable(top) else top)


def _outside(cells, x, z):
    """Nearby cells outside the island, to measure how far in (x, z) lies."""
    found = []
    for r in range(1, 8):
        for dx in range(-r, r + 1):
            for dz in (-r, r):
                if (x + dx, z + dz) not in cells:
                    found.append((x + dx, z + dz))
            for dz in range(-r + 1, r):
                for ddx in (-r, r):
                    if (x + ddx, z + dz) not in cells:
                        found.append((x + ddx, z + dz))
        if found:
            return found
    return [(x + 8, z)]


def rounded(base, a1, a2, b1, b2, corner):
    """World cells of a rectangle in a base's local coordinates with rounded corners."""
    cells = set()
    for a in range(a1, a2 + 1):
        for b in range(b1, b2 + 1):
            ca = max(a1 + corner - a, a - (a2 - corner), 0)
            cb = max(b1 + corner - b, b - (b2 - corner), 0)
            if ca * ca + cb * cb <= corner * corner:
                cells.add(base.xz(a, b))
    return cells


def circle(cx, cz, radius):
    return {(x, z) for x in range(cx - radius, cx + radius + 1) for z in range(cz - radius, cz + radius + 1)
            if (x - cx) ** 2 + (z - cz) ** 2 <= radius * radius + radius}


def tree(s, pos, log, leaves, height=4):
    x, y, z = pos
    for dy in range(height):
        s.set((x, y + dy, z), log, axis="y")
    top = y + height
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (-2, -1):
                if abs(dx) + abs(dz) < 4 and (dx, dz) != (0, 0):
                    s.set((x + dx, top + dy, z + dz), leaves, persistent=True)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if abs(dx) + abs(dz) < 2:
                s.set((x + dx, top, z + dz), leaves, persistent=True)


def lantern_post(s, pos, post="oak_fence", height=2):
    x, y, z = pos
    for dy in range(height):
        s.set((x, y + dy, z), post)
    s.set((x, y + height, z), "lantern")


def team_base(s, islands, team, base, theme):
    """A team's island: a plaza of the team's colour with the bed at the front (towards the middle),
    the spawn behind it, the island generator at the back and the two shopkeepers at the sides."""
    dye = DYES[team]
    cells = rounded(base, -8, 8, -9, 9, 3)
    trim = f"{dye}_concrete"

    def top(x, z):
        return theme["top"]

    disc(s, islands, f"base {team}", cells, top, theme["fill"], 7)
    # The plaza and its coloured border.
    for a in range(-6, 7):
        for b in range(-5, 6):
            edge = a in (-6, 6) or b in (-5, 5)
            s.set(base.pos(a, S, b), trim if edge else theme["plaza"])
    for a in (-6, 6):
        for b in (-5, 5):
            lantern_post(s, base.pos(a, S + 1, b), theme["post"])
    # Bed platform at the front: the bed lies along a, its head towards the middle.
    for a in range(-6, -2):
        for b in range(-1, 2):
            s.set(base.pos(a, S, b), f"{dye}_terracotta")
    s.marker(base.pos(-4, S + 1, 0), f"point bed_{team} {base.facing(-1, 0)}")
    # Spawn, facing the middle.
    s.marker(base.pos(1, S + 1, 0), f"spawn {team} {base.facing(-1, 0)}")
    # Island generator at the back under a little roof.
    for b in range(-1, 2):
        s.set(base.pos(6, S, b), "iron_block")
    s.set(base.pos(7, S, 0), "gold_block")
    for b in (-2, 2):
        for dy in range(1, 4):
            s.set(base.pos(6, S + dy, b), theme["post"])
    for b in range(-2, 3):
        s.set(base.pos(6, S + 4, b), theme["slab"], type="bottom")
    s.marker(base.pos(6, S + 1, 0), f"point forge_{team}")
    # Shopkeepers either side, facing the island's axis; a team chest beside the item shop.
    s.marker(base.pos(2, S + 1, -4), f"point shop_{team} {base.facing(0, 1)}")
    s.marker(base.pos(2, S + 1, 4), f"point upgrades_{team} {base.facing(0, -1)}")
    s.set(base.pos(4, S, -4), theme["plaza_alt"])
    s.set(base.pos(4, S, 4), theme["plaza_alt"])
    s.set(base.pos(4, S + 1, -4), "chest", facing=_dir_name(base, 0, 1))
    # Trees on the grass either side of the plaza.
    for a, b in ((-3, -8), (4, 8)):
        tree(s, base.pos(a, S + 1, b), theme["log"], theme["leaves"])
    for a, b in ((-3, 8), (4, -8)):
        s.set(base.pos(a, S + 1, b), theme["bush"], persistent=True)
    lo, hi = base.pos(-10, S - 6, -11), base.pos(10, S + 16, 11)
    s.marker(_min(lo, hi), "region base_{} {} {} {}".format(team, *_span(lo, hi)))


def _dir_name(base, da, db):
    dx = da * base.dx - db * base.dz
    dz = da * base.dz + db * base.dx
    return {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)]


def _min(p, q):
    return tuple(min(a, b) for a, b in zip(p, q))


def _span(p, q):
    return tuple(abs(a - b) for a, b in zip(p, q))


def diamond_island(s, islands, index, cx, cz, theme):
    cells = circle(cx, cz, 3)
    disc(s, islands, f"diamond {index}", cells, theme["diamond_top"], "stone", 4)
    s.set((cx, S, cz), "diamond_block")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set((cx + dx, S, cz + dz), "polished_andesite")
    s.marker((cx, S + 1, cz), f"point diamond_{index}")
    lantern_post(s, (cx + 2, S + 1, cz + 2), theme["post"])
    s.set((cx - 2, S + 1, cz - 2), theme["diamond_wall"])
    s.set((cx - 2, S + 1, cz + 2), theme["diamond_wall"])


def middle(s, islands, radius, emeralds, theme):
    cells = circle(0, 0, radius)
    disc(s, islands, "middle", cells, theme["middle_top"], theme["fill"], 8)
    for x, z in cells:
        if (x + z) % 2 == 0 and x * x + z * z <= (radius - 3) ** 2:
            s.set((x, S, z), theme["middle_pattern"])
    for index, (x, z) in enumerate(emeralds, start=1):
        s.set((x, S, z), "emerald_block")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s.set((x + dx, S, z + dz), "polished_diorite")
        s.marker((x, S + 1, z), f"point emerald_{index}")
    # A tower in the middle, striped in the theme's colours, with a light on top.
    for dy in range(1, 11):
        block = theme["tower"][(dy // 2) % 2]
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if (dx, dz) != (0, 0) or dy == 10:
                    s.set((dx, S + dy, dz), block)
    s.set((0, S + 11, 0), theme["beacon"])
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set((dx, S + 11, dz), "lantern")
    # Four pillars with lanterns around the middle.
    for x, z in ((radius - 2, 0), (-radius + 2, 0), (0, radius - 2), (0, -radius + 2)):
        for dy in range(1, 5):
            s.set((x, S + dy, z), theme["pillar"], axis="y")
        s.set((x, S + 5, z), "lantern")


def frame_markers(s, reach, lobby_y):
    """The waiting box above the middle, the void and the build region."""
    for x in range(-5, 6):
        for z in range(-5, 6):
            s.set((x, lobby_y, z), "white_stained_glass")
            if max(abs(x), abs(z)) == 5:
                s.set((x, lobby_y + 1, z), "barrier")
                s.set((x, lobby_y + 2, z), "barrier")
    s.marker((0, lobby_y + 1, 0), "lobby")
    # The void marker and the build region's marker sit in opposite corners, so the map's bounds (where
    # blocks can be placed) reach as far out on every side.
    s.marker((reach, S - 24, reach), "void")
    s.marker((-reach, S - 22, -reach), f"region build {2 * reach} {44} {2 * reach}")


def ground(s, reach, y, theme):
    """Voidless: a solid surface under the whole map, the islands standing above it."""
    for x in range(-reach, reach + 1):
        for z in range(-reach, reach + 1):
            if s.get((x, y, z)) is None:
                s.set((x, y, z), theme["ground"])
                s.set((x, y - 1, z), "dirt")
                s.set((x, y - 2, z), "bedrock")


THEMES = {
    "lighthouse": {"top": "grass_block", "fill": "dirt", "plaza": "smooth_stone", "plaza_alt": "stone_bricks",
                   "post": "spruce_fence", "slab": "spruce_slab", "diamond_top": "andesite",
                   "middle_top": "grass_block", "middle_pattern": "stone_bricks", "pillar": "quartz_pillar",
                   "ground": "grass_block", "log": "birch_log", "leaves": "birch_leaves", "bush": "azalea_leaves",
                   "diamond_wall": "andesite_wall", "tower": ("white_concrete", "red_concrete"), "beacon": "sea_lantern"},
    "quarry": {"top": "coarse_dirt", "fill": "dirt", "plaza": "polished_andesite", "plaza_alt": "cobblestone",
               "post": "oak_fence", "slab": "oak_slab", "diamond_top": "cobblestone",
               "middle_top": "gravel", "middle_pattern": "cobblestone", "pillar": "oak_log",
               "ground": "coarse_dirt", "log": "oak_log", "leaves": "oak_leaves", "bush": "oak_leaves",
               "diamond_wall": "cobblestone_wall", "tower": ("stone_bricks", "polished_andesite"), "beacon": "glowstone"},
    "outpost": {"top": "podzol", "fill": "dirt", "plaza": "deepslate_tiles", "plaza_alt": "polished_deepslate",
                "post": "dark_oak_fence", "slab": "dark_oak_slab", "diamond_top": "tuff",
                "middle_top": "moss_block", "middle_pattern": "mossy_stone_bricks", "pillar": "stripped_dark_oak_log",
                "ground": "moss_block", "log": "dark_oak_log", "leaves": "dark_oak_leaves", "bush": "azalea_leaves",
                "diamond_wall": "tuff_wall", "tower": ("deepslate_bricks", "stripped_dark_oak_log"), "beacon": "shroomlight"},
}


def eight_teams(theme_name, voidless=False):
    """Solo/Doubles: two bases on each side, a diamond island at each corner, four emeralds in the middle."""
    theme = THEMES[theme_name]
    s, islands = Structure(), Islands()
    team = 1
    for direction in DIRECTIONS:
        for side in (-14, 14):
            dx, dz = direction
            cx, cz = 44 * dx - side * dz, 44 * dz + side * dx
            team_base(s, islands, team, Base(cx, cz, direction), theme)
            team += 1
    for index, (x, z) in enumerate(((34, 34), (-34, 34), (-34, -34), (34, -34)), start=1):
        diamond_island(s, islands, index, x, z, theme)
    middle(s, islands, 11, [(4, 4), (-4, -4), (4, -4), (-4, 4)], theme)
    reach = 72
    frame_markers(s, reach, S + 30)
    if voidless:
        ground(s, reach, S - 14, theme)
    return s


def four_teams(theme_name, voidless=False):
    """3v3v3v3/4v4v4v4: a base on each side with its own diamond island beside it, two emeralds in the middle."""
    theme = THEMES[theme_name]
    s, islands = Structure(), Islands()
    for team, direction in enumerate(DIRECTIONS, start=1):
        dx, dz = direction
        team_base(s, islands, team, Base(40 * dx, 40 * dz, direction), theme)
        # The diamond island to the base's left, as seen from its spawn looking at the middle.
        diamond_island(s, islands, team, 34 * dx + 20 * dz, 34 * dz - 20 * dx, theme)
    middle(s, islands, 9, [(3, 3), (-3, -3)], theme)
    reach = 64
    frame_markers(s, reach, S + 30)
    if voidless:
        ground(s, reach, S - 14, theme)
    return s


def two_teams(theme_name, voidless=False):
    """4v4: two bases facing each other along x, each with a diamond island, two emeralds in the middle."""
    theme = THEMES[theme_name]
    s, islands = Structure(), Islands()
    for team, direction in ((1, (1, 0)), (2, (-1, 0))):
        dx, dz = direction
        team_base(s, islands, team, Base(36 * dx, 0, direction), theme)
        diamond_island(s, islands, team, 22 * dx, 18 * dx, theme)
    middle(s, islands, 8, [(0, 3), (0, -3)], theme)
    reach = 60
    frame_markers(s, reach, S + 30)
    if voidless:
        ground(s, reach, S - 14, theme)
    return s


def one_block():
    """One Block: eight islands of two wool blocks and a bed in a ring, nothing else."""
    s, islands = Structure(), Islands()
    for team in range(1, 9):
        angle = math.radians(45 * (team - 1))
        dx, dz = math.cos(angle), math.sin(angle)
        cx, cz = int(round(24 * dx)), int(round(24 * dz))
        # The island runs across the ring: two wool blocks then the bed, its head away from the wool.
        ax, az = (0, 1) if abs(dx) >= abs(dz) else (1, 0)
        cells = {(cx - ax, cz - az), (cx, cz), (cx + ax, cz + az), (cx + 2 * ax, cz + 2 * az)}
        islands.claim(f"island {team}", cells)
        for i, (x, z) in enumerate(sorted(cells, key=lambda c: (c[0] - cx) * ax + (c[1] - cz) * az)):
            s.set((x, S, z), f"{DYES[team]}_wool" if i < 2 else "smooth_stone")
        s.marker((cx - ax, S + 1, cz - az), f"spawn {team} {yaw(-cx, -cz)}")
        s.marker((cx + ax, S + 1, cz + az), f"point bed_{team} {yaw(ax, az)}")
        s.marker((cx - 4, S - 3, cz - 4), f"region base_{team} 8 10 8")
    frame_markers(s, 34, S + 30)
    return s


def castle_keep(s, team, cx, facing):
    """A team's castle: a walled keep 17 wide with two generators inside, ladders up to the roof and the
    team's main bed on the roof under a beacon."""
    base = Base(cx, 0, facing)
    dye = DYES[team]
    wall = "stone_bricks"
    for a in range(-8, 9):
        for b in range(-8, 9):
            edge = abs(a) == 8 or abs(b) == 8
            s.set(base.pos(a, S, b), "polished_andesite" if not edge else wall)
            if edge:
                door = a == -8 and abs(b) <= 1
                for y in range(S + 1, S + 11):
                    if door and y <= S + 3:
                        continue
                    s.set(base.pos(a, y, b), f"{dye}_wool" if y == S + 6 else wall)
            s.set(base.pos(a, S + 10, b), wall if edge else "spruce_planks")
        # Crenellations.
    for a in range(-8, 9):
        for b in range(-8, 9):
            if (abs(a) == 8 or abs(b) == 8) and (a + b) % 2 == 0:
                s.set(base.pos(a, S + 11, b), wall)
    # Ladders up the back wall to a hatch in the roof.
    for y in range(S + 1, S + 11):
        s.set(base.pos(7, y, 0), "ladder", facing=_dir_name(base, -1, 0))
    s.set(base.pos(7, S + 10, 0), "air")
    s.marker(base.pos(0, S + 11, 0), f"point bed_{team}_keep {base.facing(-1, 0)}")
    # The beacon on a corner turret.
    for y in range(S + 11, S + 14):
        s.set(base.pos(6, y, 6), wall)
    for da in (-1, 0, 1):
        for db in (-1, 0, 1):
            s.set(base.pos(6 + da, S + 14, 6 + db), "iron_block")
    s.set(base.pos(6, S + 15, 6), "beacon")
    s.marker(base.pos(6, S + 16, 6), f"point beacon_{team}_keep")
    # Two generators inside, each with its four shopkeepers.
    for index, b in enumerate((-5, 5), start=1):
        generator_corner(s, base, team, index, 1, b, 1 if b < 0 else -1)
    for index in range(8):
        a, b = -4 + (index % 4) * 2, -2 + (index // 4) * 4
        s.marker(base.pos(a, S + 1, b), f"spawn {team} {base.facing(-1, 0)}")
    # Launch pads either side of the keep, towards the towers.
    for n, side in enumerate((-1, 1), start=1):
        s.set(base.pos(0, S, side * 11), "slime_block")
        s.set(base.pos(0, S + 1, side * 11), "heavy_weighted_pressure_plate")
        s.marker(base.pos(0, S + 1, side * 11), f"point pad_{team}_{n} {base.facing(-0.5, side)}")
    lo, hi = base.pos(-10, S - 2, -10), base.pos(10, S + 18, 10)
    s.marker(_min(lo, hi), "region base_{}_keep {} {} {}".format(team, *_span(lo, hi)))


def generator_corner(s, base, team, index, a, b, inward):
    """An island generator at (a, b) with the item shop, team upgrades, banker and streak powers by it."""
    for da in (-1, 0, 1):
        s.set(base.pos(a + da, S, b), "iron_block")
    s.marker(base.pos(a, S + 1, b), f"point forge_{team}_{index}")
    s.marker(base.pos(a - 2, S + 1, b), f"point shop_{team}_{index} {base.facing(0, inward)}")
    s.marker(base.pos(a + 2, S + 1, b), f"point upgrades_{team}_{index} {base.facing(0, inward)}")
    s.marker(base.pos(a - 2, S + 1, b + inward * 2), f"point banker_{team}_{index} {base.facing(0, -inward)}")
    s.marker(base.pos(a + 2, S + 1, b + inward * 2), f"point streak_{team}_{index} {base.facing(0, -inward)}")


def castle_tower(s, team, name, index, cx, cz, facing, side):
    """A tower nine wide and sixteen high with a generator at its foot and a bed on top under a beacon."""
    base = Base(cx, cz, facing)
    dye = DYES[team]
    wall = "cobblestone"
    for a in range(-5, 6):
        for b in range(-5, 6):
            edge = abs(a) == 5 or abs(b) == 5
            s.set(base.pos(a, S, b), wall if edge else "polished_andesite")
            if edge:
                door = a == -5 and abs(b) <= 1
                for y in range(S + 1, S + 16):
                    if door and y <= S + 3:
                        continue
                    s.set(base.pos(a, y, b), f"{dye}_wool" if y in (S + 5, S + 10) else wall)
            s.set(base.pos(a, S + 15, b), wall if edge else "oak_planks")
    for y in range(S + 1, S + 16):
        s.set(base.pos(4, y, 0), "ladder", facing=_dir_name(base, -1, 0))
    s.set(base.pos(4, S + 15, 0), "air")
    for a in range(-5, 6):
        for b in range(-5, 6):
            if (abs(a) == 5 or abs(b) == 5) and (a + b) % 2 == 0:
                s.set(base.pos(a, S + 16, b), wall)
    s.marker(base.pos(0, S + 16, 0), f"point bed_{team}_{name} {base.facing(-1, 0)}")
    for da in (-1, 0, 1):
        for db in (-1, 0, 1):
            s.set(base.pos(-3 + da, S + 16, 3 * side + db), "iron_block")
    s.set(base.pos(-3, S + 17, 3 * side), "beacon")
    s.marker(base.pos(-3, S + 18, 3 * side), f"point beacon_{team}_{name}")
    generator_corner(s, base, team, index, 1, -2 * side, side)
    s.marker(base.pos(-2, S + 1, 2 * side), f"spawn {team} {base.facing(-1, 0)}")
    # A launch pad behind the tower, back towards the keep.
    s.set(base.pos(7, S, 0), "slime_block")
    s.set(base.pos(7, S + 1, 0), "heavy_weighted_pressure_plate")
    s.marker(base.pos(7, S + 1, 0), f"point pad_{team}_{index + 1} {base.facing(0.6, -side)}")
    lo, hi = base.pos(-7, S - 2, -7), base.pos(9, S + 20, 7)
    s.marker(_min(lo, hi), "region base_{}_{} {} {} {}".format(team, name, *_span(lo, hi)))


def tunnel(s, tz):
    """A tunnel three wide under the wall at z = tz: ramps down from the surface either side, then three
    blocks of headroom under a stone-brick ceiling that the wall stands on."""
    for x in range(-12, 13):
        drop = min(4, 13 - abs(x))
        for z in range(tz - 1, tz + 2):
            if abs(x) >= 10:
                for y in range(S - drop + 1, S + 1):
                    s.set((x, y, z), "air")
                s.set((x, S - drop, z), "stone_bricks")
            else:
                s.set((x, S - 4, z), "stone_bricks")
                for y in range(S - 3, S):
                    s.set((x, y, z), "air")
                s.set((x, S, z), "stone_bricks")
        for z in (tz - 2, tz + 2):
            for y in range(S - 4, S + 1):
                s.set((x, y, z), "stone_bricks")
        if abs(x) < 10 and x % 4 == 0:
            s.set((x, S - 1, tz), "lantern", hanging=True)


def castle():
    """Castle: two halves of one ground split by a wall with tunnels under it; each team's keep and two
    towers hold its three beds; three diamond generators a side and three emerald islands."""
    s = Structure()
    width, depth = 96, 62
    for x in range(-width, width + 1):
        for z in range(-depth, depth + 1):
            rim = min(width - abs(x), depth - abs(z))
            if rim < 0:
                continue
            top = "grass_block" if (x * 7 + z * 13) % 11 else "coarse_dirt"
            s.set((x, S, z), top)
            for y in range(S - 6 + min(rim, 2), S):
                s.set((x, y, z), "dirt" if y >= S - 2 else "stone")
    # The wall across the middle, with three tunnels under it.
    for z in range(-depth, depth + 1):
        for x in (-1, 0, 1):
            for y in range(S + 1, S + 14):
                s.set((x, y, z), "deepslate_bricks")
            s.set((x, S + 14, z), "deepslate_brick_wall" if z % 2 == 0 else "air")
    for tz in (-36, 0, 36):
        tunnel(s, tz)
    for team, sign in ((1, -1), (2, 1)):
        facing = (sign, 0)
        castle_keep(s, team, sign * 74, facing)
        castle_tower(s, team, "north_tower", 3, sign * 56, -42, facing, -1)
        castle_tower(s, team, "south_tower", 4, sign * 56, 42, facing, 1)
        for n, (x, z) in enumerate(((30, -48), (30, 48), (36, 0)), start=1):
            gx = sign * x
            s.set((gx, S, z), "diamond_block")
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                s.set((gx + dx, S, z + dz), "polished_andesite")
            s.marker((gx, S + 1, z), f"point diamond_{(team - 1) * 3 + n}")
    # Emerald islands off both ends of the wall and one high over its middle.
    islands = Islands()
    for index, (x, y, z) in enumerate(((0, S, -78), (0, S, 78), (0, S + 22, 0)), start=1):
        cells = circle(x, z, 4)
        if y == S:
            islands.claim(f"emerald {index}", cells)
        for cx, cz in cells:
            for yy in range(y - 3, y):
                s.set((cx, yy, cz), "stone")
            s.set((cx, y, cz), "moss_block")
        s.set((x, y, z), "emerald_block")
        s.marker((x, y + 1, z), f"point emerald_{index}")
    for x in range(-5, 6):
        for z in range(-5, 6):
            s.set((x, S + 40, z), "white_stained_glass")
            if max(abs(x), abs(z)) == 5:
                s.set((x, S + 41, z), "barrier")
                s.set((x, S + 42, z), "barrier")
    s.marker((0, S + 41, 0), "lobby")
    s.marker((width, S - 24, 90), "void")
    s.marker((-width, S - 22, -90), f"region build {2 * width} {56} {180}")
    return s


def main():
    maps = [("bedwars", "lighthouse", lambda: eight_teams("lighthouse")), ("bedwars", "quarry", lambda: four_teams("quarry")),
            ("bedwars", "outpost", lambda: two_teams("outpost")),
            ("bedwars_voidless", "lighthouse", lambda: eight_teams("lighthouse", voidless=True)),
            ("bedwars_voidless", "quarry", lambda: four_teams("quarry", voidless=True)),
            ("bedwars_one_block", "drift", one_block), ("bedwars_castle", "castle", castle)]
    for directory, name, build in maps:
        os.makedirs(os.path.dirname(OUT.format(directory, name)), exist_ok=True)
        s = build()
        print(directory, name, s.save(OUT.format(directory, name)))


if __name__ == "__main__":
    main()
