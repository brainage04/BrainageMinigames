"""Generates the Sumo maps (original layouts): python3 tools/maps/sumo.py from the repo root.

A Sumo map is one raised platform. Its walking surface is the layer at y = 0, so players stand at
y = 1, and the `void` marker sits at y = 0: a player whose feet drop a whole block below the
surface is out, whether into water, onto the ground around the platform or into the void. So that
nobody can land on something and stay in, every block outside the platform's footprint between
y = -1 (whose top is level with the void height) and y = 3 (as high as a knocked-up player rises)
keeps REACH blocks away from it; the GameTest `sumo_every_map_keeps_its_platform_clear` checks the
pasted maps the same way. Teams spawn on the platform (`spawn <team>`, facing the middle), and the
`lobby` marker stands on a viewing gallery, where players wait for the match and players who were
knocked off watch the rest of the round.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/sumo"

# How far from the platform anything a knocked-off player could land on must be.
REACH = 6
# The heights a knocked-off player can land on: from level with the void height to a jump above
# the surface.
LOW, HIGH = -1, 3


def check_clear(s, platform):
    """Refuses a map where a block outside the platform's footprint is within REACH of it at a
    height a knocked-off player could land on."""
    footprint = set(platform)
    for (x, y, z), (name, _, _) in s.blocks.items():
        if not LOW <= y <= HIGH or (x, z) in footprint or name == "minecraft:structure_block":
            continue
        nearest = min(math.hypot(x - px, z - pz) for px, pz in footprint)
        if nearest < REACH:
            raise ValueError(f"{name} at {(x, y, z)} is {nearest:.1f} blocks from the platform")


def gallery(s, z0, direction, width, floor, rows, seat="spruce_planks", step="spruce_stairs"):
    """Stepped benches facing the platform along z, starting at z0 and rising away from it by one
    block a row; returns the top row's z and floor height."""
    # A stair's tall half is on its facing side: the back of each bench, away from the platform.
    facing = "north" if direction < 0 else "south"
    for row in range(rows):
        z = z0 + direction * row
        y = floor + row
        for x in range(-width, width + 1):
            s.fill((x, floor - 1, z), (x, y - 1, z), "stone_bricks")
            s.set((x, y, z), step if row < rows - 1 else seat,
                  **({"facing": facing} if row < rows - 1 else {}))
    return z0 + direction * (rows - 1), floor + rows - 1


def dohyo():
    """A round clay ring, 15 blocks across, edged with flush straw bales and raised two blocks on
    a square earthen mound in a sandstone hall. Stepping off the ring onto the mound is out. A
    shrine roof hangs high above with a coloured tassel at each corner, and benches on two sides
    look on. Two teams start behind white lines."""
    s = Structure()
    radius = 7.4
    platform = []
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = math.hypot(x, z)
            if d > radius:
                continue
            platform.append((x, z))
            s.set((x, 0, z), "hay_block" if d > radius - 1.0 else "packed_mud")
            s.set((x, -1, z), "hay_block" if d > radius - 1.0 else "mud_bricks")
    # The start lines, and a lighter centre where the wrestlers meet.
    for x in (-1, 0, 1):
        for z in (-2, 2):
            s.set((x, 0, z), "white_concrete")
    for x, z in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set((x, 0, z), "smooth_sandstone")
    # The mound: two blocks under the ring, edged with mud bricks.
    for x in range(-10, 11):
        for z in range(-10, 11):
            if (x, z) in platform:
                s.fill((x, -4, z), (x, -2, z), "packed_mud")
                continue
            edge = max(abs(x), abs(z)) == 10
            s.set((x, -2, z), "mud_bricks" if edge else "coarse_dirt")
            s.fill((x, -4, z), (x, -3, z), "mud_bricks" if edge else "packed_mud")
    # The hall floor, a step below the mound, with lanterns on posts in its corners.
    for x in range(-19, 20):
        for z in range(-19, 20):
            if max(abs(x), abs(z)) > 10:
                tile = (x + z) % 4 == 0
                s.set((x, -5, z), "chiseled_sandstone" if tile else "smooth_sandstone")
            s.set((x, -6, z), "sandstone")
    for x in (-15, 15):
        for z in (-15, 15):
            s.fill((x, -4, z), (x, -1, z), "stripped_dark_oak_log")
            s.set((x, 0, z), "lantern")
    # A low wall round the hall, with pillars every four blocks.
    for x in range(-19, 20):
        for z in range(-19, 20):
            if max(abs(x), abs(z)) != 19:
                continue
            pillar = x % 4 == 0 and z % 4 == 0 or abs(x) == abs(z)
            s.fill((x, -4, z), (x, -2 if pillar else -3, z), "cut_sandstone")
            if pillar:
                s.set((x, -1, z), "chiseled_sandstone")
    # Benches on the north and south sides; the north one holds the lobby.
    top_z, top_y = gallery(s, -13, -1, 9, -4, 6)
    gallery(s, 13, 1, 9, -4, 6)
    s.marker((0, top_y + 1, top_z), "lobby")
    # The roof: a dark oak frame on four beams with stepped slabs, hanging well out of reach.
    roof = 12
    for x in range(-8, 9):
        for z in range(-8, 9):
            if max(abs(x), abs(z)) == 8:
                s.set((x, roof, z), "dark_oak_log", axis="x" if abs(z) == 8 else "z")
    for layer in range(4):
        half = 9 - layer * 2
        block = "dark_oak_slab" if layer < 3 else "dark_oak_planks"
        for x in range(-half, half + 1):
            for z in range(-half, half + 1):
                s.set((x, roof + 1 + layer, z), block, **({"type": "bottom"} if layer < 3 else {}))
    s.set((0, roof + 5, 0), "gold_block")
    # Tassels in the four colours, hanging on chains from the roof's corners.
    for (x, z), colour in zip(((8, 8), (-8, 8), (-8, -8), (8, -8)), ("red", "white", "black", "green")):
        s.fill((x, roof - 3, z), (x, roof - 1, z), "iron_chain", axis="y")
        s.set((x, roof - 4, z), f"{colour}_wool")
    s.marker((0, 1, -4), "spawn 1")
    s.marker((0, 1, 4), "spawn 2")
    s.marker((0, 0, -11), "void")
    check_clear(s, platform)
    return s


def lotus():
    """An octagonal cherry-wood deck 17 blocks across on stilts over a koi pond, with a lotus inlaid
    in the middle. Falls end in the water two blocks down. Cherry trees grow on islets in the
    pond's corners and a pavilion on its north bank is the gallery. Four teams start a quarter
    turn apart."""
    s = Structure()
    platform = []
    for x in range(-8, 9):
        for z in range(-8, 9):
            if abs(x) + abs(z) > 11:
                continue
            platform.append((x, z))
            rim = abs(x) == 8 or abs(z) == 8 or abs(x) + abs(z) == 11
            s.set((x, 0, z), "dark_oak_planks" if rim else "cherry_planks")
            s.set((x, -1, z), "dark_oak_planks")
    # The lotus: a yellow heart in pink petals, white petal tips and green leaves between.
    s.set((0, 0, 0), "yellow_concrete")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        s.set((dx, 0, dz), "pink_concrete")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2), (2, 2), (-2, -2), (2, -2), (-2, 2),
                   (3, 0), (-3, 0), (0, 3), (0, -3)):
        s.set((dx, 0, dz), "white_concrete")
    for dx, dz in ((2, 1), (1, 2), (-2, 1), (-1, 2), (2, -1), (1, -2), (-2, -1), (-1, -2)):
        s.set((dx, 0, dz), "lime_terracotta")
    # The stilts, from the deck down to the pond floor.
    for x, z in ((-6, -4), (6, -4), (-6, 4), (6, 4), (-4, -6), (4, -6), (-4, 6), (4, 6), (0, 0)):
        s.fill((x, -5, z), (x, -2, z), "stripped_cherry_log", axis="y")
    # The pond: two blocks of water in a stone-brick basin, lily pads and islets in the corners.
    for x in range(-16, 17):
        for z in range(-16, 17):
            wall = max(abs(x), abs(z)) == 16
            s.set((x, -5, z), "stone_bricks" if wall else "mud")
            if wall:
                s.fill((x, -4, z), (x, -2, z), "mossy_stone_bricks" if (x * 7 + z * 3) % 5 == 0
                       else "stone_bricks")
            elif s.get((x, -4, z)) is None:
                s.set((x, -4, z), "water")
                s.set((x, -3, z), "water")
    for x, z in ((-11, 3), (10, -6), (3, 11), (-5, -12), (12, 9), (-12, -9)):
        s.set((x, -2, z), "lily_pad")
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = 13 * sx, 13 * sz
            for x in range(cx - 2, cx + 3):
                for z in range(cz - 2, cz + 3):
                    if abs(x - cx) + abs(z - cz) <= 3:
                        s.fill((x, -4, z), (x, -3, z), "dirt")
                        s.set((x, -2, z), "grass_block")
            cherry(s, cx, -1, cz)
    # The pavilion on the north bank: a raised floor under a curved roof on red pillars.
    for x in range(-5, 6):
        for z in range(-23, -16):
            s.fill((x, -5, z), (x, -1, z), "stone_bricks")
            s.set((x, 0, z), "dark_oak_planks")
    for x in (-5, 5):
        for z in (-23, -17):
            s.fill((x, 1, z), (x, 3, z), "red_terracotta")
    for x in range(-6, 7):
        for z in range(-24, -15):
            edge = x in (-6, 6) or z in (-24, -16)
            s.set((x, 4, z), "dark_oak_slab" if edge else "dark_oak_planks", type="bottom")
    for x in range(-4, 5):
        for z in range(-22, -17):
            s.set((x, 5, z), "dark_oak_slab", type="bottom")
    for x in (-3, 3):
        s.set((x, 3, -20), "lantern", hanging="true")
    s.marker((0, 1, -18), "lobby")
    s.marker((0, 1, -5), "spawn 1")
    s.marker((0, 1, 5), "spawn 2")
    s.marker((5, 1, 0), "spawn 3")
    s.marker((-5, 1, 0), "spawn 4")
    s.marker((0, 0, -12), "void")
    check_clear(s, platform)
    return s


def cherry(s, x, y, z):
    """A small cherry tree standing on (x, y - 1, z)."""
    s.fill((x, y, z), (x, y + 3, z), "cherry_log", axis="y")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy, reach in ((3, 2), (4, 2), (5, 1)):
                if abs(dx) + abs(dz) <= reach + (dy == 4) and (dx, dz) != (0, 0) or dy == 5 and (dx, dz) == (0, 0):
                    s.set((x + dx, y + dy, z + dz), "cherry_leaves", persistent="true")


def crucible():
    """A deepslate foundry floor 19 blocks across with cut corners, hanging over the void. Hazard
    stripes mark its edge and copper rails cross it to a copper hearth in the middle. Beneath it
    the furnace narrows into the dark; four soul-lit pylons stand off its corners and a copper
    catwalk on the north side is the gallery. Four teams start a quarter turn apart."""
    s = Structure()
    platform = []
    for x in range(-9, 10):
        for z in range(-9, 10):
            if abs(x) + abs(z) > 15:
                continue
            platform.append((x, z))
            edge = abs(x) == 9 or abs(z) == 9 or abs(x) + abs(z) == 15
            if edge:
                block = "yellow_concrete" if (x + z) % 2 == 0 else "black_concrete"
            elif max(abs(x), abs(z)) <= 1:
                block = "waxed_copper_block"
            elif x == 0 or z == 0:
                block = "waxed_cut_copper"
            else:
                block = "polished_deepslate"
            s.set((x, 0, z), block)
            s.set((x, -1, z), "deepslate_tiles")
    # The furnace under the floor, narrowing with depth, with a glowing core.
    for depth in range(2, 9):
        half = 9 - depth
        for x in range(-half, half + 1):
            for z in range(-half, half + 1):
                if abs(x) + abs(z) > half + 5:
                    continue
                outer = max(abs(x), abs(z)) == half or abs(x) + abs(z) == half + 5
                s.set((x, -depth, z), "deepslate_bricks" if outer else "magma_block")
    s.set((0, -9, 0), "deepslate_bricks")
    # Pylons off the corners, soul lanterns on top.
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = 15 * sx, 15 * sz
            s.fill((x, -12, z), (x, 4, z), "polished_basalt", axis="y")
            for y in (-8, -4, 0):
                s.set((x, y, z), "chiseled_polished_blackstone")
            s.set((x, 5, z), "soul_lantern")
            s.set((x, -13, z), "deepslate_bricks")
    # The catwalk: a copper deck with iron bars along it, on the north side.
    for x in range(-6, 7):
        for z in range(-21, -17):
            s.set((x, 2, z), "waxed_cut_copper")
            if z in (-18, -21):
                s.set((x, 3, z), "iron_bars", **bars(x, -6, 6))
    for z in (-20, -19):
        for x in (-6, 6):
            s.set((x, 3, z), "iron_bars", north="true", south="true")
    for x in (-6, 6):
        for z in (-21, -18):
            s.set((x, 3, z), "waxed_copper_block")
            s.set((x, 4, z), "copper_lantern")
    for x in (-5, 5):
        s.fill((x, -12, -20), (x, 1, -20), "polished_basalt", axis="y")
    s.marker((0, 3, -20), "lobby")
    s.marker((0, 1, -5), "spawn 1")
    s.marker((0, 1, 5), "spawn 2")
    s.marker((5, 1, 0), "spawn 3")
    s.marker((-5, 1, 0), "spawn 4")
    s.marker((0, 0, -13), "void")
    check_clear(s, platform)
    return s


def bars(x, low, high):
    """Iron bar connections along a straight east-west rail."""
    return {"east": str(x < high).lower(), "west": str(x > low).lower()}


MAPS = {"dohyo": dohyo, "lotus": lotus, "crucible": crucible}


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, build in MAPS.items():
        size = build().save(f"{OUT}/{name}.nbt")
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}")
