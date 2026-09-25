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
    """Four home platforms around a central island, with stepping stones between them."""
    s = Structure()
    # Home platforms (radius 3) 17 blocks from the centre: south/north for teams 1 and 2,
    # east/west for teams 3 and 4.
    homes = [(0, -17), (0, 17), (17, 0), (-17, 0)]
    wool = ["red_concrete", "blue_concrete", "lime_concrete", "yellow_concrete"]
    for index, (x, z) in enumerate(homes):
        island(s, x, 0, z, 3, "smooth_quartz", "calcite", 4, index)
        # A ring of team colour around the edge of the home platform.
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                d = math.hypot(dx, dz)
                if 2.4 < d <= 3.4:
                    s.set((x + dx, 0, z + dz), wool[index])
    # Central island (radius 5) raised by one, with a small mound in the middle.
    island(s, 0, 1, 0, 5, "grass_block", "dirt", 6, 7)
    s.fill((-1, 2, -1), (1, 2, 1), "moss_block")
    s.set((0, 3, 0), "moss_carpet")
    for x, z in [(4, 0), (-4, 0), (0, 4), (0, -4)]:
        s.set((x, 2, z), "short_grass")
    # Diagonal stepping stones (radius 1) one below the homes' level.
    for x, z in [(9, 9), (-9, 9), (9, -9), (-9, -9)]:
        island(s, x, -1, z, 1, "end_stone_bricks", "end_stone", 2, x + z)
        lamp(s, x, 0, z, "purpur_pillar", 2)
    # Single-block perches between the homes and the centre, for pearls to land on.
    for x, z in [(0, -10), (0, 10), (10, 0), (-10, 0)]:
        s.set((x, 0, z), "amethyst_block")
    # Tall lamps on the central island mark the middle and give the build region its height.
    for x, z in [(3, 3), (-3, -3)]:
        lamp(s, x, 2, z, "stripped_birch_log", 6)

    for team, (x, z) in enumerate(homes, start=1):
        s.marker((x, 1, z), f"spawn {team}")
    s.marker((0, 3, -2), "lobby")
    # Players may build anywhere between the islands, up to the top of the lamps.
    s.marker((-20, -3, -20), "region build 40 12 40")
    return s


def twin_peaks():
    """A duel map: two long home platforms joined by a broken bridge of floating slabs over a
    wide gap, with a high island to either side for pearling around."""
    s = Structure()
    for sign, colour in ((-1, "red_terracotta"), (1, "blue_terracotta")):
        z0 = sign * 14
        for x in range(-4, 5):
            for z in range(z0 - 2, z0 + 3):
                s.set((x, 0, z), "polished_andesite")
                s.set((x, -1, z), "andesite")
                if abs(x) <= 2 and abs(z - z0) <= 1:
                    s.set((x, -2, z), "andesite")
        for x in range(-4, 5):
            s.set((x, 0, z0 + sign * 2), colour)
        lamp(s, 4, 1, z0 + sign * 2, "polished_andesite_wall", 2)
        lamp(s, -4, 1, z0 + sign * 2, "polished_andesite_wall", 2)
    # Broken bridge: pairs of slabs with gaps.
    for z in range(-10, 11):
        if z % 4 in (0, 1):
            s.set((0, 0, z), "smooth_stone_slab", type="top")
            s.set((1, 0, z), "smooth_stone_slab", type="top")
    # Side islands, higher up.
    island(s, 11, 3, 0, 3, "grass_block", "dirt", 4, 3)
    island(s, -11, 3, 0, 3, "grass_block", "dirt", 4, 5)
    lamp(s, 11, 4, 0, "oak_fence", 3)
    lamp(s, -11, 4, 0, "oak_fence", 3)
    s.marker((0, 1, -14), "spawn 1")
    s.marker((0, 1, 14), "spawn 2")
    s.marker((0, 1, -13), "lobby")
    s.marker((-15, -3, -17), "region build 30 11 34")
    return s


MAPS = {"skyreach": skyreach, "twin_peaks": twin_peaks}


if __name__ == "__main__":
    for name, build in MAPS.items():
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}")
