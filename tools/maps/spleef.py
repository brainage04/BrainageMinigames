"""Generates the Spleef maps (run from the repository root: python3 tools/maps/spleef.py).

Every map has stacked floors of shovel-breakable blocks with open air between them, walls that are
not shovel-breakable, a `region floor_<n>` marker per floor (floor_1 is the floor players start on)
and a `void` marker below the lowest floor. The game only lets players break shovel-mineable blocks
inside a floor region, so the regions may cover the walls around the floors.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/spleef"

# Team order around a ring: the first two spawns are opposite each other, as are the next two.
RING_ORDER = [0, 4, 2, 6, 1, 5, 3, 7]


def ring_spawns(s, radius, y):
    for team, slot in enumerate(RING_ORDER, start=1):
        angle = slot * math.pi / 4
        x = round(math.cos(angle) * radius)
        z = round(math.sin(angle) * radius)
        s.marker((x, y, z), f"spawn {team}")


def glacier():
    """A round ice tower with three snow floors six blocks apart."""
    s = Structure()
    radius = 12
    wall_outer = 14.5
    floors = [16, 10, 4]  # floor_1 (start) is the top floor
    top = floors[0] + 6
    for x in range(-15, 16):
        for z in range(-15, 16):
            distance = math.hypot(x, z)
            if distance <= radius + 0.5:
                for number, y in enumerate(floors, start=1):
                    # A clay ring marks the edge of the middle floor; both break like snow.
                    edge = number == 2 and distance > radius - 0.5
                    s.set((x, y, z), "clay" if edge else "snow_block")
            elif distance <= wall_outer:
                angle = (math.degrees(math.atan2(z, x)) + 360) % 360
                pillar = min(angle % 45, 45 - angle % 45) < 4 and distance <= radius + 1.5
                for y in range(0, top + 1):
                    if pillar:
                        block = "stripped_spruce_log" if y % 6 else "spruce_log"
                    elif y in (floor + 3 for floor in floors) and round(angle) % 90 < 12:
                        block = "sea_lantern"
                    elif y % 6 == 1:
                        block = "blue_ice"
                    else:
                        block = "packed_ice"
                    s.set((x, y, z), block, **({"axis": "y"} if "log" in block else {}))
                # A snow cap; snow layers cannot lie on ice and would break as soon as pasted.
                s.set((x, top + 1, z), "snow_block")
    for number, y in enumerate(floors, start=1):
        s.marker((-15, y, -15), f"region floor_{number} 30 0 30")
    ring_spawns(s, 8, floors[0] + 1)
    s.marker((15, 0, 15), "void")
    return s


def lantern_pit():
    """A square deepslate pit with two snow floors seven blocks apart, sunk into a small arena:
    patterned floors (clay rings on the top floor, a clay star on the lower one), buttressed walls
    with froglight bands and a crenellated top, a tower with a lantern at every corner and stepped
    stands for spectators along two sides."""
    s = Structure()
    half = 10
    floors = [11, 4]
    top = floors[0] + 6
    for x in range(-half - 1, half + 2):
        for z in range(-half - 1, half + 2):
            ring = max(abs(x), abs(z))
            wall = ring == half + 1
            if not wall:
                # Clay breaks like snow; it only patterns the floors (spawns stand on snow).
                upper = ring == 4 or (ring == half and (x + z) % 2 == 0)
                lower = ring >= 2 and (abs(x) == abs(z) or x == 0 or z == 0) and ring % 3 != 0
                s.set((x, floors[0], z), "clay" if upper else "snow_block")
                s.set((x, floors[1], z), "clay" if lower else "snow_block")
                continue
            corner = abs(x) == half + 1 and abs(z) == half + 1
            for y in range(0, top + 1):
                if corner:
                    block = "polished_deepslate"
                elif y in (floor + 3 for floor in floors) and (x % 5 == 0 or z % 5 == 0):
                    block = "ochre_froglight"
                elif y % 7 == 0:
                    block = "chiseled_deepslate"
                else:
                    block = "deepslate_bricks" if (x + y + z) % 4 else "cracked_deepslate_bricks"
                s.set((x, y, z), block)
            if not corner:
                s.set((x, top + 1, z), "deepslate_brick_wall" if (x + z) % 2 else "polished_deepslate")
    # Buttresses down the outside of the walls, every five blocks.
    for i in range(-half, half + 1, 5):
        for outward, (ax, az) in ((half + 2, (1, 0)), (half + 2, (-1, 0)), (half + 2, (0, 1)),
                                  (half + 2, (0, -1))):
            bx, bz = (ax * outward, i) if ax else (i, az * outward)
            for y in range(0, top - 1):
                s.set((bx, y, bz), "polished_deepslate")
            s.set((bx, top - 1, bz), "deepslate_tile_slab", type="bottom")
    # Corner towers rise four blocks over the walls, each with a lantern on top.
    for sx in (-1, 1):
        for sz in (-1, 1):
            for dx in (0, 1):
                for dz in (0, 1):
                    x, z = sx * (half + 1 + dx), sz * (half + 1 + dz)
                    for y in range(0, top + 5):
                        s.set((x, y, z), "polished_deepslate" if dx == dz else "deepslate_tiles")
            s.set((sx * (half + 1), top + 5, sz * (half + 1)), "lantern")
            for dx, dz in ((1, 0), (0, 1)):
                s.set((sx * (half + 1 + dx), top + 5, sz * (half + 1 + dz)), "deepslate_tile_wall")
    # Stands on the east and west: four tiers of seats stepping up and away from the wall top.
    for side in (-1, 1):
        for tier in range(4):
            x = side * (half + 3 + tier)
            y = top - 2 + tier
            for z in range(-half + 1, half):
                for below in range(y - 2, y):
                    s.set((x, below, z), "deepslate_bricks")
                s.set((x, y, z), "polished_deepslate_stairs",
                      facing="west" if side > 0 else "east", half="bottom")
        # Coloured banners of wool hang under the front of the stands.
        for z in range(-half + 2, half - 1, 4):
            for dy in (1, 2):
                s.set((side * (half + 3), top - 2 - 2 - dy, z), "red_wool" if z % 8 else "blue_wool")
    for number, y in enumerate(floors, start=1):
        s.marker((-half - 2, y, -half - 2), f"region floor_{number} {2 * half + 4} 0 {2 * half + 4}")
    spawn_y = floors[0] + 1
    for team, (x, z) in enumerate(
        [(-7, -7), (7, 7), (7, -7), (-7, 7), (0, -7), (0, 7), (-7, 0), (7, 0)], start=1
    ):
        s.marker((x, spawn_y, z), f"spawn {team}")
    s.marker((half + 2, 0, half + 2), "void")
    return s


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in (("glacier", glacier), ("lantern_pit", lantern_pit)):
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size}")


if __name__ == "__main__":
    main()
