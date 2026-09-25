"""Generates the SkyWars maps (run from the repository root: python3 tools/maps/skywars.py).

Every map is original: floating islands around a larger mid island, sized like Hypixel/Minemen
SkyWars (islands 20-30 blocks of void apart). Each island is its own team (`spawn <n>` above it;
the mod builds a glass cage there for the countdown and removes it at the start), has three
`skywars/island` chests, and the mid island has `skywars/mid` chests. Team numbers alternate
around the ring so that small team counts are spread across the map.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/skywars"
ISLAND_LOOT = {"LootTable": "brainage_minigames:skywars/island"}
MID_LOOT = {"LootTable": "brainage_minigames:skywars/mid"}

# Surface (top block) height of islands; the template's y=0 holds the void marker.
SURFACE = 16


class Theme:
    def __init__(self, top, under, rock, log, leaves, accent, ores):
        self.top, self.under, self.rock = top, under, rock
        self.log, self.leaves, self.accent, self.ores = log, leaves, accent, ores


MEADOW = Theme("grass_block", "dirt", "stone", "oak_log", "oak_leaves", "oak_planks",
               ["coal_ore", "iron_ore", "coal_ore", "iron_ore", "gold_ore"])
MESA = Theme("red_sand", "terracotta", "orange_terracotta", "acacia_log", "acacia_leaves",
             "cut_red_sandstone", ["iron_ore", "gold_ore", "coal_ore", "iron_ore"])
TUNDRA = Theme("snow_block", "packed_ice", "stone", "spruce_log", "spruce_leaves",
               "spruce_planks", ["iron_ore", "coal_ore", "diamond_ore", "iron_ore"])


def blob(s, rng, cx, cz, radius, depth, theme, surface=SURFACE):
    """A floating island: a flat top disc tapering to a point below."""
    for x in range(cx - radius - 1, cx + radius + 2):
        for z in range(cz - radius - 1, cz + radius + 2):
            distance = math.hypot(x - cx, z - cz) + rng.uniform(-0.4, 0.4)
            if distance > radius + 0.3:
                continue
            # The deeper the column, the closer to the centre it has to be.
            column = max(1, int(round(depth * (1.0 - distance / (radius + 1.0)) + rng.uniform(0, 1.5))))
            for dy in range(column):
                y = surface - dy
                if dy == 0:
                    block = theme.top
                elif dy <= 2:
                    block = theme.under
                elif rng.random() < 0.07:
                    block = rng.choice(theme.ores)
                else:
                    block = theme.rock
                s.set((x, y, z), block)


def tree(s, x, z, theme, height=4, surface=SURFACE):
    for dy in range(1, height + 1):
        s.set((x, surface + dy, z), theme.log, axis="y")
    top = surface + height
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (-1, 0):
                if abs(dx) == 2 and abs(dz) == 2:
                    continue
                if (dx, dz) != (0, 0):
                    s.set((x + dx, top + dy, z + dz), theme.leaves, persistent="true")
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set((x + dx, top + 1, z + dz), theme.leaves, persistent="true")


def facing_towards(dx, dz):
    """The horizontal direction name closest to (dx, dz)."""
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def spawn_island(s, rng, team, cx, cz, theme, centre):
    """A team island: three chests, a tree, and a spawn four blocks above its middle."""
    blob(s, rng, cx, cz, 5, 7, theme)
    # Unit vector towards the mid island, and its perpendicular.
    vx, vz = centre[0] - cx, centre[1] - cz
    length = math.hypot(vx, vz)
    ux, uz = vx / length, vz / length
    px, pz = -uz, ux
    # Chests face the island's middle so they open towards the player.
    for along, side in ((2.6, 0.0), (-1.0, 3.2), (-1.0, -3.2)):
        x = cx + int(round(ux * along + px * side))
        z = cz + int(round(uz * along + pz * side))
        s.set((x, SURFACE + 1, z), "chest", nbt=ISLAND_LOOT,
              facing=facing_towards(cx - x, cz - z))
    tx = cx + int(round(-ux * 4.0))
    tz = cz + int(round(-uz * 4.0))
    tree(s, tx, tz, theme)
    s.set((cx + int(round(px * 2)), SURFACE + 1, cz + int(round(pz * 2))), "crafting_table")
    # The cage floor is built at SURFACE+3, so players drop three blocks (no fall damage).
    s.marker((cx, SURFACE + 4, cz), f"spawn {team}")


def mid_island(s, rng, cx, cz, radius, theme, chests):
    blob(s, rng, cx, cz, radius, 11, theme)
    blob(s, rng, cx, cz, 4, 3, theme, surface=SURFACE + 1)
    top = SURFACE + 1
    # A stone-brick altar with a lantern pillar; the mid chests sit around it.
    s.fill((cx - 2, top, cz - 2), (cx + 2, top, cz + 2), "stone_bricks")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        s.set((cx + dx, top, cz + dz), "chiseled_stone_bricks")
    s.fill((cx, top + 1, cz), (cx, top + 3, cz), "stone_brick_wall", up="true")
    s.set((cx, top + 4, cz), "lantern", hanging="false")
    positions = [(0, -2), (2, 0), (0, 2), (-2, 0), (-2, -2), (2, 2)][:chests]
    for dx, dz in positions:
        x, z = cx + dx, cz + dz
        s.set((x, top + 1, z), "chest", nbt=MID_LOOT, facing=facing_towards(dx, dz))
    s.marker((cx + 1, top + 1, cz + 1), "lobby")
    # A few decorative pillars at the rim.
    for angle in range(0, 360, 90):
        x = cx + int(round(math.cos(math.radians(angle + 45)) * (radius - 2)))
        z = cz + int(round(math.sin(math.radians(angle + 45)) * (radius - 2)))
        s.fill((x, SURFACE + 1, z), (x, SURFACE + 2, z), theme.accent)


# Angles in ring order for team numbers 1..n, spread so few teams never start as neighbours.
def spread_angles(count):
    if count == 2:
        return [0, 180]
    if count == 4:
        return [0, 180, 90, 270]
    if count == 8:
        return [0, 180, 90, 270, 45, 225, 135, 315]
    raise ValueError(count)


def build(name, seed, islands, ring, mid_radius, mid_chests, theme):
    rng = random.Random(seed)
    s = Structure()
    for team, angle in enumerate(spread_angles(islands), start=1):
        x = int(round(math.cos(math.radians(angle)) * ring))
        z = int(round(math.sin(math.radians(angle)) * ring))
        spawn_island(s, rng, team, x, z, theme, (0, 0))
    mid_island(s, rng, 0, 0, mid_radius, theme, mid_chests)
    # Buildable space: the map's bounds, from the void marker up to 24 blocks above the islands
    # and six blocks beyond the outermost island.
    extent = ring + 5 + 6
    s.marker((0, 0, 0), "void")
    s.marker((-extent, SURFACE + 24, -extent), "point bounds_min")
    s.marker((extent, SURFACE + 24, extent), "point bounds_max")
    size = s.save(f"{OUT}/{name}.nbt")
    print(f"{name}: {size[0]}x{size[1]}x{size[2]}, {islands} islands")


def main():
    os.makedirs(OUT, exist_ok=True)
    # Minemen-style duel map: two islands facing each other over a mid.
    build("frostbite", 2, islands=2, ring=24, mid_radius=7, mid_chests=4, theme=TUNDRA)
    # Four islands: 1v1 to 1v1v1v1 or 2v2.
    build("mesa", 4, islands=4, ring=26, mid_radius=8, mid_chests=4, theme=MESA)
    # Eight islands: free-for-all or team modes up to eight teams.
    build("archipelago", 8, islands=8, ring=34, mid_radius=9, mid_chests=6, theme=MEADOW)


if __name__ == "__main__":
    main()
