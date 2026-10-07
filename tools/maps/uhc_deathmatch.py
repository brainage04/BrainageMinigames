"""Original circular UHC deathmatch arena, inspired by Hypixel's rim rooms and central loot.

Run from the repository root: python3 tools/maps/uhc_deathmatch.py.
24 open rim spawn pads surround a 90-block grassy combat floor. More than 24 teams
share pads evenly; team members stay together. Chests are ordinary datapack-controlled loot.

The floor has modest cover in three rings: broken walls near the dais, pillars and low mounds in
the middle ring, and longer broken walls further out. Every piece stands alone with open ground
around it, so nothing encloses a player and the shrinking border never pushes anyone against a
closed wall; players put back inside the border land on top of any cover at their spot.
"""

import math
import random

from structure import Structure

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/uhc_deathmatch/colosseum.nbt"
ROOMS = 24
RADIUS = 45

# Team colours of the pads' banners, repeated around the rim.
COLOURS = ["red", "blue", "lime", "yellow", "purple", "orange", "cyan", "magenta"]


def floor_block(rng, x, z, d):
    """The surface of the combat floor: grass with packed-mud paths along two rings and the spokes
    from the pads to the dais, and patches of moss and coarse dirt. Every floor block is a full
    block, so the floor is level everywhere."""
    angle = math.degrees(math.atan2(z, x)) % (360 / ROOMS)
    spoke = d > 6 and min(angle, 360 / ROOMS - angle) * math.pi / 180 * d < 1.0
    if spoke or abs(d - 22) < 1.0 or abs(d - 38) < 0.6:
        return "packed_mud"
    patch = math.sin(x * 0.31) * math.cos(z * 0.27) + math.sin((x + z) * 0.13)
    if patch > 1.25:
        return "moss_block"
    if patch < -1.35:
        return "coarse_dirt"
    if rng.random() < 0.02:
        return "podzol"
    return "grass_block"


def arena():
    s = Structure()
    rng = random.Random(4040)
    for x in range(-56, 57):
        for z in range(-56, 57):
            d = math.hypot(x, z)
            if d <= 54:
                s.set((x, 0, z), "bedrock")
                s.set((x, 1, z), "dirt")
                s.set((x, 2, z), floor_block(rng, x, z, d) if d < RADIUS else "stone_bricks")
            if 53 <= d <= 54:
                # The rim wall: stone brick with mossy and cracked courses, a darker band at eye
                # height and a crenellated top.
                for y in range(3, 10):
                    if y == 5:
                        block = "polished_andesite"
                    elif (x * 3 + y * 7 + z) % 9 == 0:
                        block = "mossy_stone_bricks"
                    elif (x + y * 5 + z * 3) % 11 == 0:
                        block = "cracked_stone_bricks"
                    else:
                        block = "stone_bricks"
                    s.set((x, y, z), block)
                if d >= 53.5 and (x + z) % 2 == 0:
                    s.set((x, 10, z), "stone_brick_wall")
            if d > 54:
                s.fill((x, 0, z), (x, 12, z), "barrier")
    for room in range(ROOMS):
        angle = 2 * math.pi * room / ROOMS
        ux, uz = math.cos(angle), math.sin(angle)
        x, z = round(ux * 49), round(uz * 49)
        yaw = math.degrees(angle) + 90
        s.marker((x, 3, z), f"spawn {room + 1} {yaw:.2f}")
        s.set((round(ux * 51), 2, round(uz * 51)), "sea_lantern")
        # Each pad gets a polished floor, and a coloured banner of wool on the wall behind it.
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (x + dx, 2, z + dz) != (round(ux * 51), 2, round(uz * 51)):
                    s.set((x + dx, 2, z + dz), "polished_andesite")
        wx, wz = round(ux * 53), round(uz * 53)
        colour = COLOURS[room % len(COLOURS)]
        for y in range(6, 9):
            s.set((wx, y, wz), f"{colour}_wool")
    # Pillars between the pads rise above the wall, each with a lantern.
    for room in range(ROOMS):
        angle = 2 * math.pi * (room + 0.5) / ROOMS
        px, pz = round(math.cos(angle) * 52), round(math.sin(angle) * 52)
        for y in range(3, 13):
            s.set((px, y, pz), "chiseled_stone_bricks" if y % 4 == 0 else "stone_brick_wall"
                  if y == 12 else "polished_andesite")
        s.set((px, 12, pz), "stone_brick_wall", up="true")
        s.set((px, 13, pz), "lantern")
    # Low central dais: eight chests, anvils and an enchanting table, not an obstructing tower.
    s.fill((-4, 2, -4), (4, 2, 4), "smooth_stone")
    for x in range(-4, 5):
        for z in range(-4, 5):
            if max(abs(x), abs(z)) == 4:
                s.set((x, 2, z), "polished_andesite")
    for x, z in [(0, -4), (4, 0), (0, 4), (-4, 0), (-3, -3), (3, -3), (-3, 3), (3, 3)]:
        s.set((x, 3, z), "chest", nbt={"LootTable": "brainage_minigames:uhc/deathmatch"})
    s.set((0, 3, 0), "enchanting_table")
    s.set((-1, 3, 0), "anvil")
    s.set((1, 3, 0), "crafting_table")
    s.marker((0, 4, 1), "lobby")
    cover(s)
    # Blocks may be placed only up to five blocks above the floor, so nobody can tower onto the
    # rim wall: a higher pillar reaches its top and the barrier ring above it, which leads out of
    # the arena. The marker's corner lies in the barrier filler outside the circle.
    s.marker((-52, 3, -52), "region build 104 4 104")
    return s


def wall(s, radius, degrees, length):
    """A broken wall two blocks high across the radius at `degrees`, `length` blocks long; its
    ends drop to one block, so it can be climbed there."""
    angle = math.radians(degrees)
    cx, cz = math.cos(angle) * radius, math.sin(angle) * radius
    tx, tz = -math.sin(angle), math.cos(angle)
    half = (length - 1) / 2
    placed = set()
    for step in range(length * 2 + 1):
        t = -half + step / 2
        x, z = round(cx + tx * t), round(cz + tz * t)
        if (x, z) in placed:
            continue
        placed.add((x, z))
        end = abs(t) > half - 0.6
        s.set((x, 3, z), "mossy_stone_bricks" if (x + z) % 3 == 0 else "stone_bricks")
        if not end:
            s.set((x, 4, z), "stone_brick_wall")


def pillar(s, radius, degrees):
    """A 2x2 pillar four blocks high with a lantern on top."""
    angle = math.radians(degrees)
    x, z = round(math.cos(angle) * radius), round(math.sin(angle) * radius)
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(3, 7):
                s.set((x + dx, y, z + dz), "chiseled_stone_bricks" if y == 6 else
                      "mossy_stone_bricks" if (dx + dz + y) % 3 == 0 else "stone_bricks")
    s.set((x, 7, z), "lantern")


def mound(s, radius, degrees):
    """A low raised spot: a 5x5 mossy rise one block up with a 3x3 top a block higher, which a
    player steps onto from any side."""
    angle = math.radians(degrees)
    x, z = round(math.cos(angle) * radius), round(math.sin(angle) * radius)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) == 2 and abs(dz) == 2:
                continue
            s.set((x + dx, 3, z + dz), "moss_block")
            if abs(dx) <= 1 and abs(dz) <= 1:
                s.set((x + dx, 4, z + dz), "mossy_cobblestone" if (dx, dz) != (0, 0) else "stone_bricks")


def cover(s):
    """Cover on the combat floor, set between the pads' spokes (every 15 degrees) so each piece
    stands apart and every pad keeps an open path to the dais."""
    for i in range(6):
        wall(s, 14, 37.5 + 60 * i, 4)
    for i in range(8):
        pillar(s, 28, 7.5 + 45 * i)
    for i in range(4):
        mound(s, 31, 30 + 90 * i)
    for i in range(6):
        wall(s, 39, 7.5 + 60 * i, 5)


if __name__ == "__main__":
    print(f"{OUT}: {arena().save(OUT)}")
