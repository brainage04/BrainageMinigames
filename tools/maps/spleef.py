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
    """A round pit melted into a glacier. Three snow floors six blocks apart, each patterned in clay
    (a snowflake on the top floor, broken rings on the middle one, a two-armed spiral on the bottom
    one), inside a jagged wall of packed ice with wavy blue-ice strata, sea lanterns and a frozen
    icefall on the west side. Outside, the glacier rises to ice crags along the rim and snow-capped
    stone peaks to the west, drops to a low lip with spruces to the north-east, and tapers below
    like an iceberg."""
    s = Structure()
    radius = 13  # floor radius: floors are 27 blocks across
    floors = [16, 10, 4]  # floor_1 (start) is the top floor
    top = floors[0] + 6  # the rim's lowest height
    # Peaks (angle in degrees from +X towards +Z, distance from the middle, height above the rim,
    # base radius): stone mountains to the west, where the first spawn looks, and ice crags
    # around the rest of the rim.
    peaks = [(133, 25, 17, 9, "stone"), (165, 23, 22, 10, "stone"), (195, 25, 15, 8, "stone"),
             (109, 21, 9, 5, "stone"), (225, 21, 8, 5, "stone")]
    for angle in (40, 75, 260, 300):
        peaks.append((angle, 16.5, 5 + angle % 4, 2.6, "ice"))
    peaks = [(math.cos(math.radians(a)) * d, math.sin(math.radians(a)) * d, h, r, kind)
             for a, d, h, r, kind in peaks]

    def footprint(angle):
        """The glacier's outer edge: 18 blocks out on the north-east, 30 under the peaks."""
        towards = max(0.0, math.cos(math.radians(angle - 165)))
        return 18 + 12 * towards ** 1.5 + 0.8 * math.sin(math.radians(angle * 5))

    def height(x, z, distance, angle):
        """The terrain's top and whether stone shows there."""
        rim = top + 1 + round(1.2 * math.sin(math.radians(angle * 7)) + 0.8 * math.sin(math.radians(angle * 3)))
        best, kind = rim, "ice"
        for px, pz, h, r, peak_kind in peaks:
            rise = top + h * (1 - math.hypot(x - px, z - pz) / r)
            if rise > best:
                best, kind = rise, peak_kind
        # The lip falls away towards the outer edge.
        edge = footprint(angle) - distance
        if edge < 3:
            best -= 3 - edge
        return int(round(best)), kind

    heights = {}
    reach = 31
    for x in range(-reach, reach + 1):
        for z in range(-reach, reach + 1):
            distance = math.hypot(x, z)
            angle = (math.degrees(math.atan2(z, x)) + 360) % 360
            if radius + 0.5 < distance <= footprint(angle):
                heights[(x, z)] = height(x, z, distance, angle) + (angle, distance)

    for (x, z), (h, kind, angle, distance) in heights.items():
        # Iceberg underside: thickest at the pit, thinning outwards, never more than ten deep
        # under the outer terrain.
        bottom = max(int(-6 + 1.6 * (distance - radius)), h - 10)
        if distance <= radius + 3.5:
            bottom = min(bottom, 0)
        neighbours = [heights.get((x + dx, z + dz), (top - 4,))[0]
                      for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        steep = h - min(neighbours) >= 2
        inner_face = distance <= radius + 1.5
        icefall = inner_face and 172 <= angle <= 188
        for y in range(bottom, h + 1):
            if icefall:
                block = "blue_ice"
            elif y == h and (not steep or y >= top + 10):
                block = "snow_block"
            elif kind == "stone" and y > top:
                grain = (x * 73856093 ^ y * 19349663 ^ z * 83492791) % 9
                block = ("andesite", "tuff", "calcite")[grain - 6] if grain >= 6 else "stone"
            elif (y + round(2.5 * math.sin(math.radians(angle * 4)) + distance * 0.4)) % 6 == 0:
                block = "blue_ice"
            else:
                block = "packed_ice"
            if inner_face and y in (floor + 3 for floor in floors) and round(angle) % 45 == 22:
                block = "sea_lantern"
            s.set((x, y, z), block)
        # The icefall freezes into an icicle under the glacier.
        if icefall:
            for dy in range(1, 4 if distance <= radius + 1 else 2):
                s.set((x, bottom - dy, z), "blue_ice" if dy < 3 else "packed_ice")

    # Small spruces on the low north-eastern lip.
    for x, z in ((14, -8), (16, -3), (15, 4)):
        h = heights[(x, z)][0]
        for dy in range(1, 5):
            s.set((x, h + dy, z), "spruce_log", axis="y")
        for dy, spread in ((2, 2), (3, 1), (4, 1), (5, 0)):
            for dx in range(-spread, spread + 1):
                for dz in range(-spread, spread + 1):
                    if (dx, dz) != (0, 0) or dy == 5:
                        if abs(dx) + abs(dz) <= spread + (1 if spread == 2 else 0):
                            s.set((x + dx, h + dy, z + dz), "spruce_leaves", persistent="true")

    # The floors: snow, patterned with clay, which breaks the same way. Spawns stand on snow.
    spawn_cells = set()
    for slot in range(8):
        a = slot * math.pi / 4
        spawn_cells.add((round(math.cos(a) * 9), round(math.sin(a) * 9)))
    for x in range(-radius, radius + 1):
        for z in range(-radius, radius + 1):
            distance = math.hypot(x, z)
            if distance > radius + 0.5:
                continue
            angle = math.atan2(z, x)
            for number, y in enumerate(floors, start=1):
                if number == 1:
                    clay = distance < 1.5 or snowflake(distance, angle)
                elif number == 2:
                    broken = math.degrees(angle) % 60 < 9
                    clay = not broken and any(abs(distance - ring) < 0.5 for ring in (4.5, 8.5, 12.5))
                else:
                    turn = (distance / 4.0 - angle / math.pi) % 1.0
                    clay = distance > 1.5 and turn < 0.2
                if (x, z) in spawn_cells:
                    clay = False
                s.set((x, y, z), "clay" if clay else "snow_block")

    reach_region = radius + 3
    for number, y in enumerate(floors, start=1):
        s.marker((-reach_region, y, -reach_region),
                 f"region floor_{number} {2 * reach_region} 0 {2 * reach_region}")
    ring_spawns(s, 9, floors[0] + 1)
    s.marker((0, 0, 0), "void")
    return s


def snowflake(distance, angle):
    """Whether a cell at polar (distance, angle) lies on a six-armed snowflake, its arms between
    the spawns' eight directions, each with two pairs of side branches."""
    for arm in range(6):
        arm_angle = math.radians(30 + 60 * arm)
        along = distance * math.cos(angle - arm_angle)
        across = distance * math.sin(angle - arm_angle)
        if 1.0 < along < 11.5 and abs(across) < 0.55:
            return True
        for root, length in ((5.0, 2.6), (8.0, 2.0)):
            for side in (-1, 1):
                # A branch leaves the arm at `root`, 60 degrees off it, outwards.
                bx = along - root
                by = across * side
                b_along = bx * math.cos(math.radians(60)) + by * math.sin(math.radians(60))
                b_across = -bx * math.sin(math.radians(60)) + by * math.cos(math.radians(60))
                if 0.4 < b_along < length and abs(b_across) < 0.55:
                    return True
    return False


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
