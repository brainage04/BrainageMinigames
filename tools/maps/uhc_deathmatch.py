"""Original circular UHC deathmatch arena, inspired by Hypixel's rim rooms and central loot.

Run from the repository root: python3 tools/maps/uhc_deathmatch.py.
24 open rim spawn pads surround a 90-block grassy combat floor. More than 24 teams
share pads evenly; team members stay together. Chests are ordinary datapack-controlled loot.

The combat floor stays level: players returned from outside the shrinking border are put on it
at a fixed height, so it only varies in texture (worn paths, moss and coarse dirt patches) and
everything raised stands on the rim.
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
    return s


if __name__ == "__main__":
    print(f"{OUT}: {arena().save(OUT)}")
