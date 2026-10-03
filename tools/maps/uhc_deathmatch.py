"""Original circular UHC deathmatch arena, inspired by Hypixel's rim rooms and central loot.

Run from the repository root: python3 tools/maps/uhc_deathmatch.py.
24 partially gated spawn rooms surround a 90-block grassy combat floor. More than 24 teams
share rooms evenly; team members stay together. Chests are ordinary datapack-controlled loot.
"""

import math
from structure import Structure

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/uhc_deathmatch/colosseum.nbt"
ROOMS = 24
RADIUS = 45


def arena():
    s = Structure()
    for x in range(-56, 57):
        for z in range(-56, 57):
            d = math.hypot(x, z)
            if d <= 54:
                s.set((x, 0, z), "bedrock")
                s.set((x, 1, z), "dirt")
                s.set((x, 2, z), "grass_block" if d < RADIUS else "stone_bricks")
            if 53 <= d <= 54:
                s.fill((x, 3, z), (x, 9, z), "stone_bricks")
            if d > 54:
                s.fill((x, 0, z), (x, 12, z), "barrier")
    for room in range(ROOMS):
        angle = 2 * math.pi * room / ROOMS
        ux, uz = math.cos(angle), math.sin(angle)
        px, pz = -uz, ux
        # Voxelised room walls at the rim; the front has an open two-block doorway.
        for along in range(44, 53):
            for side in range(-4, 5):
                x, z = round(ux * along + px * side), round(uz * along + pz * side)
                wall = abs(side) == 4 or along == 52
                gate = along == 44 and abs(side) >= 2
                if wall or gate:
                    s.fill((x, 3, z), (x, 6, z), "stone_bricks" if wall else "iron_bars")
                if along >= 45:
                    s.set((x, 7, z), "stone_brick_slab", type="bottom")
        x, z = round(ux * 49), round(uz * 49)
        yaw = math.degrees(angle) + 90
        s.marker((x, 3, z), f"spawn {room + 1} {yaw:.2f}")
        s.set((round(ux * 51), 6, round(uz * 51)), "sea_lantern")
    # Low central dais: eight chests, anvils and an enchanting table, not an obstructing tower.
    s.fill((-4, 2, -4), (4, 2, 4), "smooth_stone")
    for x, z in [(0, -4), (4, 0), (0, 4), (-4, 0), (-3, -3), (3, -3), (-3, 3), (3, 3)]:
        s.set((x, 3, z), "chest", nbt={"LootTable": "brainage_minigames:uhc/deathmatch"})
    s.set((0, 3, 0), "enchanting_table")
    s.set((-1, 3, 0), "anvil")
    s.set((1, 3, 0), "crafting_table")
    s.marker((0, 4, 1), "lobby")
    return s


if __name__ == "__main__":
    print(f"{OUT}: {arena().save(OUT)}")
