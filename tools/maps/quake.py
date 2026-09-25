"""Generates the Quake maps (run from the repository root: python3 tools/maps/quake.py).

A Quake map is an enclosed arena with cover, several levels and many respawn points. It needs a
`spawn <n>` marker per player a free-for-all may hold (the match starts players there; teams use
the first spawns) and `point respawn_<n>` markers, where killed players respawn (the game picks one
away from their opponents).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/quake"

ROTATIONS = [(1, 1), (-1, -1), (1, -1), (-1, 1)]


def rotations(x, z):
    """(x, z) under the arena's four-fold rotational symmetry, starting with itself."""
    return [(x, z), (-x, -z), (z, -x), (-z, x)]


def check_free(s, pos):
    x, y, z = pos
    for dy in (0, 1):
        if s.get((x, y + dy, z)) is not None:
            raise ValueError(f"marker at {pos} is not in open air ({s.get((x, y + dy, z))})")
    if s.get((x, y - 1, z)) is None:
        raise ValueError(f"marker at {pos} has nothing to stand on")


def stairs(s, x_range, z_range, y, facing, block, support):
    """One row of stairs with solid support underneath."""
    for x in x_range:
        for z in z_range:
            s.set((x, y, z), block, facing=facing, half="bottom", shape="straight")
            if y > 1:
                s.fill((x, 1, z), (x, y - 1, z), support)


def foundry():
    """A walled foundry: a two-storey central tower with a lookout on top, high bridges to side
    balconies over the east and west, raised corner balconies and scattered cover."""
    s = Structure()
    half = 32
    wall_top = 16

    # Floor: polished andesite with smooth stone lines every 8 blocks and lights at crossings.
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            on_x, on_z = x % 8 == 0, z % 8 == 0
            if on_x and on_z:
                block = "sea_lantern"
            elif on_x or on_z:
                block = "smooth_stone"
            else:
                block = "polished_andesite"
            s.set((x, 0, z), block)
    s.fill((-half, -1, -half), (half, -1, half), "stone")

    # Outer walls with a deepslate band and lamps.
    for x in range(-half, half + 1):
        for z in (-half, half):
            for y in range(1, wall_top + 1):
                block = "stone_bricks"
                if y in (4, 10):
                    block = "polished_deepslate"
                if y == 7 and x % 6 == 0:
                    block = "glowstone"
                s.set((x, y, z), block)
                s.set((z, y, x), block)
    s.fill((-half, wall_top + 1, -half), (half, wall_top + 1, -half), "barrier")
    s.fill((-half, wall_top + 1, half), (half, wall_top + 1, half), "barrier")
    s.fill((-half, wall_top + 1, -half), (-half, wall_top + 1, half), "barrier")
    s.fill((half, wall_top + 1, -half), (half, wall_top + 1, half), "barrier")

    # Central tower: hollow ground storey with archways, second storey floor at y=6.
    t = 7
    for x in range(-t, t + 1):
        for z in range(-t, t + 1):
            if abs(x) == t or abs(z) == t:
                for y in range(1, 6):
                    s.set((x, y, z), "bricks" if y != 3 else "polished_granite")
            s.set((x, 6, z), "polished_blackstone")
    # Archways: centred east and west, offset on the north and south sides (ramps are centred there).
    for a in range(-1, 2):
        for y in range(1, 4):
            s.remove((t, y, a))
            s.remove((-t, y, a))
            s.remove((4 + a, y, -t))
            s.remove((-4 + a, y, t))
    # Drop hole in the middle of the second storey.
    for x in range(-1, 2):
        for z in range(-1, 2):
            s.remove((x, 6, z))
    # Railing around the second storey with gaps where the ramps and bridges arrive.
    for x in range(-t, t + 1):
        for z in range(-t, t + 1):
            if abs(x) != t and abs(z) != t:
                continue
            if abs(x) <= 1 or abs(z) <= 1:
                continue
            if (x + z) % 3 == 0:
                continue
            s.set((x, 7, z), "polished_blackstone_wall")
    # Lookout on top: four pillars and a platform at y=11, reached by a ladder.
    for sx, sz in ROTATIONS:
        s.fill((3 * sx, 7, 3 * sz), (3 * sx, 10, 3 * sz), "polished_blackstone_bricks")
    s.fill((-3, 11, -3), (3, 11, 3), "polished_blackstone_bricks")
    s.set((0, 11, 0), "glowstone")
    s.fill((0, 7, -3), (0, 10, -3), "polished_blackstone_bricks")
    for y in range(7, 12):
        s.set((0, y, -4), "ladder", facing="north")
    for x in range(-3, 4):
        for z in (-3, 3):
            if x not in (0,) and (x + z) % 2 == 0:
                s.set((x, 12, z), "blackstone_slab", type="bottom")
                s.set((z, 12, x), "blackstone_slab", type="bottom")

    # Ramps up to the second storey, centred on the north and south sides.
    for step in range(6):
        y = 1 + step
        stairs(s, range(-1, 2), [-13 + step], y, "south", "brick_stairs", "bricks")
        stairs(s, range(-1, 2), [13 - step], y, "north", "brick_stairs", "bricks")

    # High bridges east and west to side balconies at the same level.
    for sign in (1, -1):
        for x in range(8, 24):
            for z in range(-1, 2):
                s.set((sign * x, 6, z), "spruce_planks")
            s.set((sign * x, 7, -2), "spruce_fence")
            s.set((sign * x, 7, 2), "spruce_fence")
            if x % 5 == 0:
                s.fill((sign * x, 1, 0), (sign * x, 5, 0), "stripped_spruce_log", axis="y")
        for x in range(24, half):
            for z in range(-5, 6):
                s.set((sign * x, 6, z), "spruce_planks")
        for z in range(-5, 6):
            if abs(z) > 1:
                s.set((sign * 24, 7, z), "spruce_fence")
        for z in (-5, 5):
            for x in range(24, half):
                s.set((sign * x, 7, z), "spruce_fence")
        for z in (-5, 5):
            s.fill((sign * 24, 1, z), (sign * 24, 5, z), "stripped_spruce_log", axis="y")
        s.set((sign * 28, 7, 0), "lantern")
        # Crates on the balcony for cover.
        s.set((sign * 27, 7, -3), "barrel", facing="up")
        s.set((sign * 27, 7, 3), "barrel", facing="up")
        s.set((sign * 27, 8, 3), "barrel", facing="up")

    # Raised corner balconies at y=5, reached by stairs along the east and west walls.
    for sx, sz in ROTATIONS:
        for x in range(20, half):
            for z in range(20, half):
                s.set((sx * x, 5, sz * z), "cut_copper")
        for i in range(20, half):
            if i % 3 != 0:
                s.set((sx * 20, 6, sz * i), "waxed_cut_copper_slab", type="bottom")
                s.set((sx * i, 6, sz * 20), "waxed_cut_copper_slab", type="bottom")
        s.fill((sx * 20, 1, sz * 20), (sx * 20, 4, sz * 20), "copper_block")
        s.fill((sx * 25, 1, sz * 20), (sx * 25, 4, sz * 20), "copper_block")
        s.fill((sx * 20, 1, sz * 25), (sx * 20, 4, sz * 25), "copper_block")
        # Stairs along the wall at |x| = 29..31, climbing towards the balcony.
        facing = "south" if sz > 0 else "north"
        for step in range(5):
            y = 1 + step
            z = sz * (15 + step)
            stairs(s, [sx * 29, sx * 30, sx * 31], [z], y, facing, "cut_copper_stairs", "cut_copper")
        s.set((sx * 26, 6, sz * 26), "lantern")

    # Ground cover, rotated four ways so every quadrant plays the same.
    pillars = [(12, 6), (6, 18), (18, 12)]
    low_walls = [((10, -18), (14, -18)), ((22, 4), (22, 8)), ((-14, 22), (-10, 22))]
    crates = [(15, -8), (16, -8), (15, -9), (26, 12), (-4, 26), (-5, 26)]
    for px, pz in pillars:
        for rx, rz in rotations(px, pz):
            s.fill((rx, 1, rz), (rx + 1, 4, rz + 1), "polished_diorite")
            s.set((rx, 5, rz), "diorite_slab", type="bottom")
            s.set((rx + 1, 5, rz + 1), "diorite_slab", type="bottom")
    for (ax, az), (bx, bz) in low_walls:
        for (rax, raz), (rbx, rbz) in zip(rotations(ax, az), rotations(bx, bz)):
            s.fill((rax, 1, raz), (rbx, 2, rbz), "mossy_stone_bricks")
            s.fill((rax, 3, raz), (rbx, 3, rbz), "mossy_stone_brick_slab", type="bottom")
    for cx, cz in crates:
        for rx, rz in rotations(cx, cz):
            s.set((rx, 1, rz), "barrel", facing="up")
    # Stacked crates beside the tower entrances.
    for rx, rz in rotations(9, 4):
        s.set((rx, 1, rz), "barrel", facing="up")
        s.set((rx, 2, rz), "barrel", facing="up")

    # Start spawns around the ground floor; opposite spawns are consecutive so duels start apart.
    ground = [(24, -12), (10, 26), (-26, 16), (16, -26)]
    starts = []
    for gx, gz in ground:
        starts.extend(rotations(gx, gz)[:2])
    for gx, gz in ground[:2]:
        starts.extend(rotations(gx, gz)[2:])
    for team, (x, z) in enumerate(starts, start=1):
        check_free(s, (x, 1, z))
        s.marker((x, 1, z), f"spawn {team}")

    # Respawn points on every level.
    respawns = []
    for x, z in [(4, -20), (26, -2), (12, 28), (-18, -6), (3, 3)]:
        for rx, rz in rotations(x, z):
            respawns.append((rx, 1, rz))
    for x, z in [(4, 4), (-4, -4), (5, -4), (-5, 4)]:
        respawns.append((x, 7, z))
    for sign in (1, -1):
        respawns.append((sign * 29, 7, 0))
    for sx, sz in ROTATIONS:
        respawns.append((sx * 27, 6, sz * 23))
    respawns.append((2, 12, 2))
    respawns.append((-2, 12, -2))
    seen = set()
    for index, pos in enumerate(respawns, start=1):
        if pos in seen:
            raise ValueError(f"duplicate respawn {pos}")
        seen.add(pos)
        check_free(s, pos)
        s.marker(pos, f"point respawn_{index}")

    s.marker((0, 1, 3), "lobby")
    return s


def cloister():
    """A small sunken courtyard for duels: a cross-shaped cloister around an open garden, with a
    raised walkway on the cloister roof."""
    s = Structure()
    half = 18
    wall_top = 12
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            garden = abs(x) <= 6 and abs(z) <= 6
            s.set((x, 0, z), "moss_block" if garden else "stone_bricks")
    s.fill((-half, -1, -half), (half, -1, half), "stone")
    for i in range(-half, half + 1):
        for y in range(1, wall_top + 1):
            block = "chiseled_stone_bricks" if y == 6 and i % 4 == 0 else "stone_bricks"
            for pos in [(i, y, -half), (i, y, half), (-half, y, i), (half, y, i)]:
                s.set(pos, block)
    for i in range(-half, half + 1):
        for pos in [(i, wall_top + 1, -half), (i, wall_top + 1, half),
                    (-half, wall_top + 1, i), (half, wall_top + 1, i)]:
            s.set(pos, "barrier")

    # Cloister ring: roof at y=5 between |c| = 10 and 13, arches every 4 blocks on the inside.
    for x in range(-13, 14):
        for z in range(-13, 14):
            ring = max(abs(x), abs(z))
            if 10 <= ring <= 13:
                s.set((x, 5, z), "deepslate_tiles")
            if ring == 10:
                on_pillar = (x % 4 == 0) if abs(z) == 10 else (z % 4 == 0)
                if on_pillar or (abs(x) == 10 and abs(z) == 10):
                    s.fill((x, 1, z), (x, 4, z), "deepslate_brick_wall")
                    s.set((x, 1, z), "polished_deepslate")
                    s.set((x, 4, z), "polished_deepslate")
                elif (x + z) % 2 == 0:
                    s.set((x, 6, z), "deepslate_tile_slab", type="bottom")
    # Stairs up to the roof in two opposite corners.
    for sign in (1, -1):
        for step in range(5):
            y = 1 + step
            stairs(s, [sign * 15, sign * 16, sign * 17], [sign * (9 - step)], y,
                   "north" if sign > 0 else "south", "stone_brick_stairs", "stone_bricks")
        # Landing joining the top step to the cloister roof.
        for x in range(14, 18):
            for z in range(1, 5):
                s.set((sign * x, 5, sign * z), "stone_bricks")
        s.fill((sign * 17, 1, sign * 1), (sign * 17, 4, sign * 1), "stone_bricks")
    # Garden: a fountain and hedges for cover.
    s.fill((-1, 1, -1), (1, 1, 1), "stone_brick_wall")
    s.set((0, 1, 0), "water")
    for x, z in rotations(4, 2) + rotations(2, -4):
        s.fill((x, 1, z), (x, 2, z), "azalea_leaves", persistent=True)
    for x, z in rotations(16, -3):
        s.fill((x, 1, z), (x, 3, z), "barrel", facing="up")
    for x, z in rotations(12, 12):
        s.set((x, 6, z), "lantern")
    for x, z in rotations(8, 8):
        s.set((x, 1, z), "lantern")

    starts = [(0, 15), (0, -15), (15, 12), (-15, -12), (-12, 15), (12, -15), (8, 0), (-8, 0)]
    for team, (x, z) in enumerate(starts, start=1):
        check_free(s, (x, 1, z))
        s.marker((x, 1, z), f"spawn {team}")
    respawns = []
    for x, z in [(12, 0), (4, 8), (15, -16), (7, 7)]:
        respawns.extend((rx, 1, rz) for rx, rz in rotations(x, z))
    for x, z in [(11, -11), (12, 0)]:
        respawns.extend((rx, 6, rz) for rx, rz in rotations(x, z))
    for index, pos in enumerate(respawns, start=1):
        check_free(s, pos)
        s.marker(pos, f"point respawn_{index}")
    s.marker((0, 1, 4), "lobby")
    return s


MAPS = {"foundry": foundry, "cloister": cloister}


if __name__ == "__main__":
    for name, build in MAPS.items():
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}")
