"""Generates the Bow Spleef maps (run from the repository root: python3 tools/maps/bow_spleef.py).

Sized after the Duels Bow Spleef arena: a round TNT floor about 41 blocks across, walls seven
blocks high, an invisible barrier ceiling fourteen blocks above the floor and the void eight blocks
below it. Every TNT layer has a `region floor_<n>` marker (floor_1 is the one players start on);
arrows only remove TNT inside those regions, so the regions may cover the walls.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/bow_spleef"

# Team order around a ring: the first two spawns are opposite each other, as are the next two.
RING_ORDER = [0, 4, 2, 6, 1, 5, 3, 7]


def ring_spawns(s, radius, y):
    for team, slot in enumerate(RING_ORDER, start=1):
        angle = slot * math.pi / 4
        x = round(math.cos(angle) * radius)
        z = round(math.sin(angle) * radius)
        s.marker((x, y, z), f"spawn {team}")


def disc(s, radius, y, block):
    for x in range(-radius, radius + 1):
        for z in range(-radius, radius + 1):
            if math.hypot(x, z) <= radius + 0.5:
                s.set((x, y, z), block)


def ring_wall(s, inner, outer, bottom, top, pick):
    reach = math.ceil(outer)
    for x in range(-reach, reach + 1):
        for z in range(-reach, reach + 1):
            distance = math.hypot(x, z)
            if inner < distance <= outer:
                angle = (math.degrees(math.atan2(z, x)) + 360) % 360
                for y in range(bottom, top + 1):
                    s.set((x, y, z), *pick(x, y, z, angle))


def ember_court():
    """One TNT floor inside a blackstone and nether brick ring."""
    s = Structure()
    radius, floor = 20, 8
    disc(s, radius, floor, "tnt")

    def pick(x, y, z, angle):
        light = min(angle % 30, 30 - angle % 30) < 2.5
        if y < floor:
            return ("polished_blackstone_bricks" if y % 3 else "gilded_blackstone",)
        if y == floor + 7:
            return ("red_nether_bricks",)
        if light and y in (floor + 2, floor + 5):
            return ("shroomlight",)
        if light:
            return ("crimson_stem", )
        return ("nether_bricks" if y % 2 else "chiseled_nether_bricks",)

    ring_wall(s, radius + 0.5, radius + 2, 0, floor + 7, pick)
    disc(s, radius + 2, floor + 14, "barrier")
    s.marker((-radius - 3, floor, -radius - 3), f"region floor_1 {2 * radius + 6} 0 {2 * radius + 6}")
    ring_spawns(s, 14, floor + 1)
    s.marker((radius + 3, 0, radius + 3), "void")
    return s


def twin_decks():
    """A small upper TNT deck above a wide lower one, ringed by prismarine."""
    s = Structure()
    lower, upper = 8, 16
    lower_radius, upper_radius = 19, 13
    disc(s, lower_radius, lower, "tnt")
    disc(s, upper_radius, upper, "tnt")
    top = upper + 7

    def pick(x, y, z, angle):
        light = min(angle % 45, 45 - angle % 45) < 3
        if light and y in (lower + 3, upper + 3):
            return ("sea_lantern",)
        if y < lower:
            return ("dark_prismarine",)
        if y == lower + 7 or y == top:
            return ("prismarine_bricks",)
        return ("prismarine",)

    ring_wall(s, lower_radius + 0.5, lower_radius + 2, 0, top, pick)
    disc(s, lower_radius + 2, upper + 14, "barrier")
    size = 2 * lower_radius + 6
    corner = -lower_radius - 3
    s.marker((corner, upper, corner), f"region floor_1 {size} 0 {size}")
    s.marker((corner, lower, corner), f"region floor_2 {size} 0 {size}")
    ring_spawns(s, 9, upper + 1)
    s.marker((lower_radius + 3, 0, lower_radius + 3), "void")
    return s


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in (("ember_court", ember_court), ("twin_decks", twin_decks)):
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size}")


if __name__ == "__main__":
    main()
