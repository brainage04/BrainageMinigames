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
    """A walled monastery cloister for duels, on three levels. On the ground, a garden with
    hedges, azalea trees and a fountain under an open pavilion, ringed by an arcade along the
    walls. The arcade roof is a walkway, reached by four stairways, from which four bridges cross
    the garden to the pavilion roof, with a hole over the fountain to drop through. Stairs along
    the walls climb from the walkway to a lookout platform in each corner. Outside the walls,
    buttresses, turrets with spruce spires and a bell tower make the skyline."""
    s = Structure()
    half = 20
    wall_top = 13
    arcade = 15  # the arcade's inner edge: its roof covers 15 <= |c| <= 19
    roof = 5
    lookout = 10

    # Ground: flagstones in the cloister walk, a mossy garden crossed by two paths.
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            ring = max(abs(x), abs(z))
            if ring <= 13:
                path = abs(x) <= 1 or abs(z) <= 1 or ring in (12, 13)
                block = "polished_andesite" if path else ("moss_block" if (x * z) % 7 else "grass_block")
            elif ring < half:
                block = "stone_bricks" if (x + z) % 2 else "polished_andesite"
            else:
                block = "stone_bricks"
            s.set((x, 0, z), block)
    s.fill((-half, -1, -half), (half, -1, half), "stone")

    # Outer wall: a deepslate plinth, a chiselled band at the walkway, arched glass windows
    # above it and a barrier on top.
    for i in range(-half, half + 1):
        for y in range(1, wall_top + 1):
            if y <= 2:
                block = "polished_deepslate" if y == 1 else "deepslate_bricks"
            elif y == 6:
                block = "chiseled_stone_bricks" if i % 4 == 0 else "stone_bricks"
            elif y == wall_top:
                block = "polished_andesite"
            elif 8 <= y <= 10 and i % 6 == 3 and abs(i) < 17:
                block = "glass_pane"
            else:
                block = "stone_bricks" if (i * 7 + y * 3) % 11 else "cracked_stone_bricks"
            for pos, along_x in (((i, y, -half), True), ((i, y, half), True),
                                 ((-half, y, i), False), ((half, y, i), False)):
                if block == "glass_pane":
                    s.set(pos, block, **({"east": "true", "west": "true"} if along_x
                                         else {"north": "true", "south": "true"}))
                else:
                    s.set(pos, block)
        for pos in [(i, wall_top + 1, -half), (i, wall_top + 1, half),
                    (-half, wall_top + 1, i), (half, wall_top + 1, i)]:
            s.set(pos, "barrier")

    # Arcade: a deepslate-tiled roof over the cloister walk, carried on the inside by pillars
    # every four blocks (an opening where each garden path enters) with lanterns hanging between
    # them. Its parapet is added once the bridges and stairways are in place.
    for x in range(-half + 1, half):
        for z in range(-half + 1, half):
            ring = max(abs(x), abs(z))
            if arcade <= ring:
                s.set((x, roof, z), "deepslate_tiles")
            if ring != arcade:
                continue
            along = x if abs(z) == arcade else z
            if abs(x) == arcade and abs(z) == arcade or along % 4 == 2:
                s.set((x, 1, z), "polished_deepslate")
                s.fill((x, 2, z), (x, 3, z), "deepslate_brick_wall", up="true")
                s.set((x, 4, z), "polished_deepslate")
            elif along % 4 == 0:
                s.set((x, roof - 1, z), "lantern", hanging="true")

    # Pavilion over the fountain: four corner piers, a roof with a hole over the fountain and
    # a parapet broken where the bridges arrive.
    for x in range(-4, 5):
        for z in range(-4, 5):
            ring = max(abs(x), abs(z))
            if ring >= 2:
                s.set((x, roof, z), "polished_blackstone_bricks")
            if ring == 4 and abs(x) == abs(z):
                s.set((x, 1, z), "polished_blackstone")
                s.fill((x, 2, z), (x, roof - 1, z), "polished_blackstone_wall", up="true")
                s.set((x, roof + 1, z), "polished_blackstone_bricks")
                s.set((x, roof + 2, z), "lantern")
            elif ring == 4 and abs(x) > 2 and abs(z) > 2:
                s.set((x, roof + 1, z), "polished_blackstone_brick_slab", type="bottom")
    # The fountain: a basin with a spout, water inside.
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2:
                s.set((x, 1, z), "stone_brick_wall", up="true")
            else:
                s.set((x, 0, z), "water")
    s.set((0, 1, 0), "chiseled_stone_bricks")
    s.set((0, 0, 0), "chiseled_stone_bricks")

    # Bridges from the walkway to the pavilion along the paths, five wide, with posts along
    # both edges.
    for sx, sz in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
        for along in range(5, arcade):
            for across in range(-2, 3):
                x = sx * along + (across if sx == 0 else 0)
                z = sz * along + (across if sz == 0 else 0)
                s.set((x, roof, z), "stone_brick_slab", type="top")
                if abs(across) == 2 and along % 3 == 0:
                    s.set((x, roof + 1, z), "stone_brick_wall", up="true")

    # Stairways from the garden up to the walkway: one per side, along the inner edge of the
    # arcade, climbing clockwise.
    for rx, rz in rotations(1, 0):
        for step in range(roof):
            y = 1 + step
            # The stairway lies at |along-axis| 6..10 next to the arcade, two wide.
            for depth in (13, 14):
                cell = (6 + step, depth)
                x, z = rotate(cell, (rx, rz))
                block_facing = facing_of(rotate((1, 0), (rx, rz)))
                s.set((x, y, z), "stone_brick_stairs", facing=block_facing, half="bottom", shape="straight")
                if y > 1:
                    s.fill((x, 1, z), (x, y - 1, z), "stone_bricks")
        for depth in (13, 14):
            for along in (11, 12, 13, 14):
                x, z = rotate((along, depth), (rx, rz))
                s.fill((x, 1, z), (x, roof, z), "stone_bricks")

    # Corner lookouts: a platform over each corner of the walkway on four piers, a parapet of
    # walls with a lantern post, and stairs up to it along the wall.
    for rx, rz in rotations(1, 0):
        for a in range(15, half):
            for b in range(15, half):
                x, z = rotate((a, b), (rx, rz))
                s.set((x, lookout, z), "spruce_planks")
                edge = a == 15 or b == 15
                if edge and not (b == 15 and a in (17, 18)):
                    s.set((x, lookout + 1, z), "spruce_fence")
        for a, b in ((15, 15), (15, 19), (19, 15)):
            x, z = rotate((a, b), (rx, rz))
            s.fill((x, roof + 1, z), (x, lookout - 1, z), "stripped_spruce_log", axis="y")
        x, z = rotate((19, 19), (rx, rz))
        s.set((x, lookout + 1, z), "spruce_fence")
        s.set((x, lookout + 2, z), "lantern")
        # Stairs along the wall, from the walkway (feet at roof + 1) up to the platform.
        for step in range(lookout - roof):
            y = roof + 1 + step
            for a in (17, 18):
                x, z = rotate((a, 10 + step), (rx, rz))
                s.set((x, y, z), "spruce_stairs", facing=facing_of(rotate((0, 1), (rx, rz))),
                      half="bottom", shape="straight")
                if y > roof + 1:
                    s.fill((x, roof + 1, z), (x, y - 1, z), "spruce_planks")

    # The walkway's parapet along the arcade's inner edge: wall posts over the pillars and tile
    # slabs between them, open wherever a bridge or stairway meets the walkway.
    for x in range(-arcade, arcade + 1):
        for z in range(-arcade, arcade + 1):
            if max(abs(x), abs(z)) != arcade:
                continue
            inward = (x - (x > 0) + (x < 0) if abs(x) == arcade else x,
                      z - (z > 0) + (z < 0) if abs(z) == arcade else z)
            if s.get((inward[0], roof, inward[1])) is not None:
                continue
            along = x if abs(z) == arcade else z
            if abs(x) == arcade and abs(z) == arcade or along % 4 == 2:
                s.set((x, roof + 1, z), "deepslate_tile_wall", up="true")
            else:
                s.set((x, roof + 1, z), "deepslate_tile_slab", type="bottom")

    # Garden cover: hedges, azalea trees and benches, the same in every quarter.
    for rx, rz in rotations(1, 0):
        for cells, height in (([(5, 8), (6, 8), (7, 8), (8, 8), (8, 7), (8, 6)], 2),
                              ([(10, 4), (10, 5)], 2), ([(4, 10), (5, 10)], 1)):
            for cell in cells:
                x, z = rotate(cell, (rx, rz))
                s.fill((x, 1, z), (x, height, z), "azalea_leaves", persistent="true")
        x, z = rotate((10, 9), (rx, rz))
        s.fill((x, 1, z), (x, 3, z), "oak_log", axis="y")
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                s.set((x + dx, 4, z + dz), "flowering_azalea_leaves" if (dx + dz) % 2 else "azalea_leaves",
                      persistent="true")
        s.set((x, 5, z), "azalea_leaves", persistent="true")
        bx, bz = rotate((6, 4), (rx, rz))
        s.set((bx, 1, bz), "stone_brick_stairs", facing=facing_of(rotate((0, -1), (rx, rz))),
              half="bottom", shape="straight")
        bx, bz = rotate((7, 4), (rx, rz))
        s.set((bx, 1, bz), "stone_brick_stairs", facing=facing_of(rotate((0, -1), (rx, rz))),
              half="bottom", shape="straight")
        # Barrels stacked in the cloister walk.
        for cell, height in (((17, -6), 2), ((18, -6), 1), ((17, 4), 1)):
            x, z = rotate(cell, (rx, rz))
            s.fill((x, 1, z), (x, height, z), "barrel", facing="up")

    outside(s, half, wall_top)

    starts = [(0, 17), (0, -17), (17, 12), (-17, -12), (-12, 17), (12, -17), (10, 0), (-10, 0)]
    for team, (x, z) in enumerate(starts, start=1):
        check_free(s, (x, 1, z))
        s.marker((x, 1, z), f"spawn {team}")
    respawns = []
    for x, z in [(17, 2), (5, 11), (11, -11), (3, 6)]:
        respawns.extend((rx, 1, rz) for rx, rz in rotations(x, z))
    for x, z in [(17, -8), (0, 9)]:
        respawns.extend((rx, roof + 1, rz) for rx, rz in rotations(x, z))
    for x, z in [(17, 17)]:
        respawns.extend((rx, lookout + 1, rz) for rx, rz in rotations(x, z))
    respawns.extend([(3, roof + 1, -3), (-3, roof + 1, 3)])
    seen = set()
    for index, pos in enumerate(respawns, start=1):
        if pos in seen:
            raise ValueError(f"duplicate respawn {pos}")
        seen.add(pos)
        check_free(s, pos)
        s.marker(pos, f"point respawn_{index}")
    s.marker((0, 1, 6), "lobby")
    return s


def rotate(cell, turn):
    """(a, b) turned by the quarter turn that takes (1, 0) to `turn`."""
    a, b = cell
    tx, tz = turn
    return a * tx - b * tz, a * tz + b * tx


def facing_of(direction):
    return {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[direction]


def outside(s, half, wall_top):
    """The monastery seen over the walls: buttresses with sloped tops, a turret with a spruce
    spire over the middle of the east and west walls, a gatehouse on the south wall and a bell
    tower over the north wall. None of it can be reached."""
    for i in range(-half + 2, half - 1, 6):
        if i == 0:
            continue  # the turrets, gatehouse and bell tower stand there
        for rx, rz in rotations(1, 0):
            x, z = rotate((half + 1, i), (rx, rz))
            ox, oz = rotate((half + 2, i), (rx, rz))
            out = facing_of(rotate((1, 0), (rx, rz)))
            s.fill((x, -1, z), (x, wall_top - 3, z), "stone_bricks")
            s.set((x, wall_top - 2, z), "stone_brick_stairs", facing=opposite(out), half="bottom",
                  shape="straight")
            s.fill((ox, -1, oz), (ox, wall_top - 6, oz), "stone_bricks")
            s.set((ox, wall_top - 5, oz), "stone_brick_stairs", facing=opposite(out), half="bottom",
                  shape="straight")
    for cx in (half + 2, -half - 2):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if abs(dx) == 2 and abs(dz) == 2:
                    continue
                for y in range(-1, wall_top + 4):
                    s.set((cx + dx, y, dz), "stone_bricks" if y % 5 else "polished_andesite")
        for level, spread in enumerate((2, 2, 1, 1, 0, 0)):
            y = wall_top + 4 + level
            for dx in range(-spread, spread + 1):
                for dz in range(-spread, spread + 1):
                    if abs(dx) + abs(dz) <= spread + 1:
                        s.set((cx + dx, y, dz), "spruce_planks" if spread else "dark_oak_planks")
        s.set((cx, wall_top + 10, 0), "lightning_rod")
    # Gatehouse: two crenellated towers either side of a barred arch.
    for sign in (1, -1):
        for x in range(3 * sign, 6 * sign, sign):
            for z in range(half + 1, half + 4):
                for y in range(-1, wall_top + 6):
                    s.set((x, y, z), "stone_bricks" if y % 6 else "chiseled_stone_bricks")
                if (x + z) % 2 == 0:
                    s.set((x, wall_top + 6, z), "stone_brick_wall", up="true")
    for x in range(-2, 3):
        for z in range(half + 1, half + 3):
            for y in range(-1, wall_top + 2):
                arch = z == half + 2 and abs(x) <= 1 and 1 <= y <= 4 + (x == 0)
                s.set((x, y, z), "iron_bars" if arch else "stone_bricks")
            s.set((x, wall_top + 2, z), "stone_brick_slab", type="bottom")
    # Bell tower: a square tower over the middle of the north wall, open belfry, pyramid roof.
    for x in range(-3, 4):
        for z in range(-half - 6, -half + 1):
            edge = abs(x) == 3 or z in (-half - 6, -half)
            for y in range(-1, wall_top + 13):
                if not edge and y > 0:
                    continue
                if wall_top + 6 <= y <= wall_top + 9 and (abs(x) < 2 or abs(z + half + 3) < 2):
                    continue  # the belfry's openings
                if z == -half and y <= wall_top + 1:
                    continue
                s.set((x, y, z), "stone_bricks" if (y % 6) else "chiseled_stone_bricks")
        for z in range(-half - 5, -half):
            s.set((x, wall_top + 5, z), "stone_bricks")
    s.set((0, wall_top + 9, -half - 3), "bell", attachment="ceiling", facing="north")
    s.fill((0, wall_top + 10, -half - 3), (0, wall_top + 12, -half - 3), "stone_bricks")
    for level in range(4):
        y = wall_top + 13 + level
        spread = 3 - level
        for x in range(-spread, spread + 1):
            for z in range(-half - 3 - spread, -half - 3 + spread + 1):
                s.set((x, y, z), "deepslate_tiles")
    s.set((0, wall_top + 17, -half - 3), "lightning_rod")


def opposite(facing):
    return {"east": "west", "west": "east", "north": "south", "south": "north"}[facing]


MAPS = {"foundry": foundry, "cloister": cloister}


if __name__ == "__main__":
    for name, build in MAPS.items():
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}")
