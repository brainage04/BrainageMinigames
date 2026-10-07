"""Generates the Pearl Fight maps (run from the repository root: python3 tools/maps/pearl_fight.py).

A Pearl Fight map is a set of floating platforms over the void. It needs a `spawn <team>` marker on
each team's home platform (teams 1 and 2 face each other; 3 and 4 are for four-player free-for-alls)
and a `region build ...` marker covering the air players may bridge through with wool. Everything
below the platforms is void.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/pearl_fight"


def island(s, cx, cy, cz, radius, top, body, depth, seed):
    """A round floating island: a flat top at y=cy tapering underneath into a rough point."""
    for x in range(-radius, radius + 1):
        for z in range(-radius, radius + 1):
            distance = math.hypot(x, z)
            if distance > radius + 0.4:
                continue
            s.set((cx + x, cy, cz + z), top)
            # Deterministic ragged underside.
            wobble = ((x * 7 + z * 13 + seed) % 3) - 1
            below = max(0, round((radius + 0.4 - distance) * depth / radius) + wobble)
            for dy in range(1, below + 1):
                s.set((cx + x, cy - dy, cz + z), body)


def lamp(s, x, y, z, post, height=3):
    s.fill((x, y, z), (x, y + height - 1, z), post)
    s.set((x, y + height, z), "end_rod", facing="up")


def skyreach():
    """Four shrine terraces 17 blocks around a central crystal spire. Each home is a quartz rock
    with a team-coloured rim and a pillared shrine at its back; between neighbouring homes a pair
    of stepping rocks sits at different heights, and single amethyst perches lie between each home
    and the middle. The spire and the low walls around it give cover in the middle; its ledge is
    the high ground."""
    s = Structure()
    homes = [(0, -1), (0, 1), (1, 0), (-1, 0)]  # direction from the centre: teams 1 to 4
    colours = ["red", "blue", "lime", "yellow"]
    for team, ((dx, dz), colour) in enumerate(zip(homes, colours), start=1):
        def p(u, y, v):
            """Home-local coordinates: u outwards from the centre, v to the side."""
            return (u * dx - v * dz, y, u * dz + v * dx)

        hx, _, hz = p(17, 0, 0)
        rock(s, hx, hz, top_y=0, radius=3, depth=6, seed=team, top="smooth_quartz", body="calcite",
             rim=f"{colour}_concrete")
        # The shrine behind the spawn: two pillars, a lintel in team colour, lanterns on top.
        for v in (-2, 2):
            s.fill(p(19, 1, v), p(19, 3, v), "quartz_pillar", axis="y")
            s.set(p(19, 5, v), "lantern")
        for v in range(-2, 3):
            s.set(p(19, 4, v), f"{colour}_glazed_terracotta" if v == 0 else "chiseled_quartz_block")
        s.set(p(19, 1, 0), "quartz_stairs", facing=facing_of(p(-1, 0, 0)), half="bottom")
        s.marker(p(17, 1, 0), f"spawn {team}")
    # Stepping rocks on the diagonals, one low and one high between each pair of homes.
    for index, (x, z) in enumerate([(9, 9), (-9, 9), (9, -9), (-9, -9)]):
        low = index % 2 == 0
        rock(s, x, z, top_y=-1 if low else 2, radius=1, depth=3, seed=index + 5,
             top="end_stone_bricks", body="end_stone")
        top_y = -1 if low else 2
        s.set((x, top_y + 1, z), "amethyst_cluster", facing="up")
        rx, rz = (x - (1 if x > 0 else -1), z)
        s.fill((rx, top_y + 1, rz), (rx, top_y + 2, rz), "purpur_pillar", axis="y")
        s.set((rx, top_y + 3, rz), "end_rod", facing="up")
    # Single perches between the homes and the middle, hanging calcite under them.
    for dx, dz in homes:
        x, z = dx * 10, dz * 10
        s.set((x, 0, z), "amethyst_block")
        s.set((x, -1, z), "calcite")
    # The middle: a mossy rock with low broken walls around a stepped crystal spire.
    rock(s, 0, 0, top_y=1, radius=5, depth=8, seed=11, top="moss_block", body="stone",
         rim="mossy_stone_bricks")
    s.fill((-1, 2, -1), (1, 3, 1), "calcite")
    for x, z in ((-1, -1), (1, 1), (1, -1), (-1, 1)):
        s.set((x, 3, z), "amethyst_block")
    s.fill((0, 4, 0), (0, 6, 0), "amethyst_block")
    s.set((0, 7, 0), "amethyst_cluster", facing="up")
    for x, z in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        s.set((x, 4, z), "amethyst_block")
    # Low walls a quarter turn off each home's line, so nobody has a straight run at the spire.
    for dx, dz in homes:
        for side in (-1, 0, 1):
            x, z = dx * 3 - dz * (side + 1), dz * 3 + dx * (side + 1)
            s.set((x, 2, z), "mossy_cobblestone_wall", up="true" if side == 1 else "false")
        cx, cz = dx * 3 - dz * 2, dz * 3 + dx * 2
        s.set((cx, 3, cz), "lantern")
    s.marker((2, 2, 2), "lobby")
    # Players may build anywhere between the islands, up to two blocks over the spire.
    s.marker((-20, -3, -20), "region build 40 12 40")
    s.marker((0, -9, 0), "void")
    return s


def facing_of(vector):
    x, _, z = vector
    return {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(x, z)]


def rock(s, cx, cz, top_y, radius, depth, seed, top="stone", body="stone", rim=None):
    """A rugged rock: a flat top at `top_y` within `radius`, ledged sides and a body tapering down
    `depth` blocks, with tuff and calcite bands."""
    for x in range(-radius - 1, radius + 2):
        for z in range(-radius - 1, radius + 2):
            d = math.hypot(x, z)
            wobble = ((x * 7 + z * 13 + seed) % 5) / 5.0 - 0.3
            if d > radius + 0.4 + wobble * 0.6:
                continue
            edge = d > radius - 0.8
            s.set((cx + x, top_y, cz + z), rim if rim and edge else top)
            below = max(1, round((radius + 0.6 - d) * depth / radius + wobble * 2))
            for dy in range(1, below + 1):
                band = (top_y - dy + seed) % 5
                block = "tuff" if band == 0 else "calcite" if band == 3 else body
                s.set((cx + x, top_y - dy, cz + z), block)


def spruce(s, x, y, z, height=4):
    """A small spruce standing on (x, y - 1, z)."""
    s.fill((x, y, z), (x, y + height - 1, z), "spruce_log")
    for level, radius in ((height - 3, 2), (height - 2, 1), (height - 1, 1), (height, 0)):
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if abs(dx) + abs(dz) <= radius + (1 if radius == 2 else 0) and (dx, dz) != (0, 0):
                    s.set((x + dx, y + level, z + dz), "spruce_leaves", persistent=True)
    s.set((x, y + height, z), "spruce_leaves", persistent=True)


def twin_peaks():
    """A duel map: each team's home terrace sits under its own snow-capped peak, 30 blocks from
    the other. Between them a ruined arch stands on a low middle rock, and a high crag and a low
    ledge on either flank give pearls somewhere to land and fights a height advantage. The map
    is point-symmetric, so neither side has a better route."""
    s = Structure()
    colours = {1: "red", 2: "blue"}
    for team, sign in ((1, -1), (2, 1)):
        def p(x, y, z):
            """Team-local coordinates: +z points from this home towards the middle."""
            return (-sign * x, y, -sign * z)

        z0 = -15
        colour = colours[team]
        # The home terrace: polished andesite with a team-coloured back edge.
        for x in range(-4, 5):
            for z in range(z0 - 3, z0 + 4):
                block = f"{colour}_terracotta" if z == z0 - 3 else "polished_andesite"
                s.set(p(x, 0, z), block)
                for dy in range(1, 3 + (abs(x) < 3) + (abs(z - z0) < 2) * 2):
                    s.set(p(x, -dy, z), "andesite" if dy < 3 else "stone")
        # The front step a block lower, towards the middle.
        for x in range(-2, 3):
            for z in range(z0 + 4, z0 + 6):
                s.set(p(x, -1, z), "polished_andesite")
                s.set(p(x, -2, z), "andesite")
        # The peak behind the terrace: stone rising in ledges to a snow cap.
        for level, radius, back in ((1, 5, 5), (2, 4, 6), (3, 3, 6), (4, 3, 7), (5, 2, 7), (6, 1, 8)):
            for x in range(-radius - 1, radius + 2):
                for z in range(-2, 3):
                    if math.hypot(x / (radius + 0.6), z / 2.6) > 1.0:
                        continue
                    zz = z0 - back + z
                    cap = level >= 5 or (level == 4 and abs(x) >= 3)
                    s.set(p(x, level, zz), "snow_block" if cap else ("tuff" if level % 3 == 0 else "stone"))
        # Steps up the left side of the peak to a lookout ledge.
        for i, (x, z, y) in enumerate(((-4, z0 - 3, 1), (-5, z0 - 4, 2), (-5, z0 - 5, 3))):
            s.set(p(x, y, z), "cobblestone_slab", type="bottom" if i == 0 else "top")
        spruce(s, *p(4, 3, z0 - 6))
        # Lantern posts on the terrace's front corners.
        for x in (-4, 4):
            s.fill(p(x, 1, z0 + 3), p(x, 2, z0 + 3), "spruce_fence")
            s.set(p(x, 3, z0 + 3), "lantern")
        s.marker(p(0, 1, z0), f"spawn {team}")
        # Flanks: a high crag on this team's left, a low ledge on its right (and the reverse for
        # the other team, by symmetry).
        rock(s, *p(-10, 0, -6)[::2], top_y=3, radius=2, depth=5, seed=team, top="grass_block",
             body="stone")
        spruce(s, *p(-10, 4, -6), height=3)
        rock(s, *p(9, 0, -3)[::2], top_y=-2, radius=2, depth=3, seed=team + 2, top="moss_block",
             body="andesite")
        # A single stepping stone between the front step and the middle rock.
        s.set(p(0, -1, -7), "mossy_stone_bricks")
        s.set(p(0, -2, -7), "andesite")

    # The middle: a low rock under a broken arch, which blocks a straight pearl down the middle.
    rock(s, 0, 0, top_y=-1, radius=3, depth=5, seed=7, top="mossy_stone_bricks", body="stone",
         rim="stone_bricks")
    for side in (-1, 1):
        s.fill((side * 3, 0, -side), (side * 3, 4, -side), "stone_bricks")
        s.set((side * 3, 5, -side), "chiseled_stone_bricks")
    for x in range(-2, 2):
        s.set((x, 5, 1 if x < 0 else -1), "stone_brick_slab", type="top")
    s.set((0, 0, 0), "mossy_cobblestone_wall", up="true")
    s.set((0, 1, 0), "lantern")

    s.marker((0, 1, -14), "lobby")
    # Falls are short: the void is six blocks under the lowest ledge.
    s.marker((0, -8, 0), "void")
    s.marker((-15, -6, -20), "region build 30 15 40")
    return s


MAPS = {"skyreach": skyreach, "twin_peaks": twin_peaks}


if __name__ == "__main__":
    for name, build in MAPS.items():
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}")
