"""Generates the SkyWars maps (run from the repository root: python3 tools/maps/skywars.py).

Every map is original: floating islands around a larger mid island, sized like Hypixel/Minemen
SkyWars (islands 20-30 blocks of void apart). Each island is its own team (`spawn <n>` above it;
the mod builds a glass cage there for the countdown and removes it at the start), has three
`skywars/island` chests, and the mid island has `skywars/mid` chests. Team numbers alternate
around the ring so that small team counts are spread across the map.

Insane and Lucky Block SkyWars play the maps in `maps/skywars/`; Mini SkyWars has its own small
four-island maps in `maps/skywars_mini/`, and Mega SkyWars a large map of two-player islands in
`maps/skywars_mega/`, whose mid is ringed by smaller islands that also hold mid chests.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

MAPS = "common/src/main/resources/data/brainage_minigames/structure/maps/"
OUT = MAPS + "skywars"
MINI_OUT = MAPS + "skywars_mini"
MEGA_OUT = MAPS + "skywars_mega"
ISLAND_LOOT = {"LootTable": "brainage_minigames:skywars/island"}
MID_LOOT = {"LootTable": "brainage_minigames:skywars/mid"}

# Surface (top block) height of islands; the template's y=0 holds the void marker.
SURFACE = 16


class Theme:
    def __init__(self, top, under, rock, log, leaves, accent, ores, wall, prop):
        self.top, self.under, self.rock = top, under, rock
        self.log, self.leaves, self.accent, self.ores = log, leaves, accent, ores
        # `wall` builds the low cover walls; `prop` is the island landmark (see `landmark`).
        self.wall, self.prop = wall, prop


MEADOW = Theme("grass_block", "dirt", "stone", "oak_log", "oak_leaves", "oak_planks",
               ["coal_ore", "iron_ore", "coal_ore", "iron_ore", "gold_ore"],
               wall="mossy_cobblestone", prop="ruin")
MESA = Theme("red_sand", "terracotta", "orange_terracotta", "acacia_log", "acacia_leaves",
             "cut_red_sandstone", ["iron_ore", "gold_ore", "coal_ore", "iron_ore"],
             wall="smooth_red_sandstone", prop="hoodoo")
TUNDRA = Theme("snow_block", "packed_ice", "stone", "spruce_log", "spruce_leaves",
               "spruce_planks", ["iron_ore", "coal_ore", "diamond_ore", "iron_ore"],
               wall="stone_bricks", prop="ice_spike")
BLOSSOM = Theme("grass_block", "dirt", "tuff", "cherry_log", "cherry_leaves", "cherry_planks",
                ["coal_ore", "iron_ore", "copper_ore", "iron_ore"],
                wall="mud_bricks", prop="ruin")
OASIS = Theme("sand", "sandstone", "sandstone", "jungle_log", "jungle_leaves", "smooth_sandstone",
              ["iron_ore", "gold_ore", "coal_ore", "iron_ore"],
              wall="cut_sandstone", prop="hoodoo")
HIGHLANDS = Theme("podzol", "dirt", "andesite", "dark_oak_log", "dark_oak_leaves", "dark_oak_planks",
                  ["coal_ore", "iron_ore", "iron_ore", "gold_ore", "diamond_ore"],
                  wall="cobblestone", prop="ruin")


def blob(s, rng, cx, cz, radius, depth, theme, surface=SURFACE):
    """A floating island: a flat top disc tapering to a point below."""
    for x in range(cx - radius - 1, cx + radius + 2):
        for z in range(cz - radius - 1, cz + radius + 2):
            distance = math.hypot(x - cx, z - cz) + rng.uniform(-0.4, 0.4)
            if distance > radius + 0.3:
                continue
            # The deeper the column, the closer to the centre it has to be. A sand top always has a
            # block under it, or it would fall at its first update.
            shallowest = 2 if theme.top.endswith("sand") else 1
            column = max(shallowest, int(round(depth * (1.0 - distance / (radius + 1.0)) + rng.uniform(0, 1.5))))
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


def ground(s, x, z):
    """The height of the highest block of the column near the surface (the island's top)."""
    for y in range(SURFACE + 2, SURFACE - 3, -1):
        if s.get((x, y, z)) is not None:
            return y
    return None


def landmark(s, x, z, theme):
    """The theme's landmark standing on the column at (x, z): an ice spike, a banded terracotta
    hoodoo with a sandstone cap, or a broken mossy ruin with a bush."""
    g = ground(s, x, z)
    if g is None:
        return
    if theme.prop == "ice_spike":
        for (dx, dz), height in (((0, 0), 6), ((1, 0), 3), ((-1, 0), 2), ((0, 1), 3), ((0, -1), 2),
                                 ((1, 1), 1), ((-1, -1), 1)):
            for dy in range(1, height + 1):
                s.set((x + dx, g + dy, z + dz), "blue_ice" if dy == 1 else "packed_ice")
    elif theme.prop == "hoodoo":
        bands = ["terracotta", "orange_terracotta", "white_terracotta", "red_terracotta",
                 "orange_terracotta"]
        for dy, band in enumerate(bands, start=1):
            s.set((x, g + dy, z), band)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s.set((x + dx, g + 1, z + dz), "terracotta")
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) < 2:
                    s.set((x + dx, g + 6, z + dz), "smooth_red_sandstone" if (dx, dz) == (0, 0)
                          else "smooth_red_sandstone_slab", **({} if (dx, dz) == (0, 0)
                                                               else {"type": "bottom"}))
    elif theme.prop == "ruin":
        for i, height in enumerate((3, 2, 1)):
            for dy in range(1, height + 1):
                s.set((x + i, g + dy, z), "mossy_cobblestone" if (i + dy) % 2 else "mossy_stone_bricks")
        s.set((x, g + 4, z), "mossy_cobblestone_wall", up="true")
        for dx, dz, dy in ((0, 1, 1), (1, 1, 1), (0, 1, 2)):
            s.set((x + dx, g + dy, z + dz), "azalea_leaves", persistent="true")


def cover_wall(s, x, z, tx, tz, theme, length=3, height=2):
    """A low wall of the theme's cover block centred on (x, z), running along (tx, tz); its ends
    are a block lower, so it reads as broken."""
    half = length // 2
    for i in range(-half, half + 1):
        wx, wz = x + int(round(tx * i)), z + int(round(tz * i))
        g = ground(s, wx, wz)
        if g is None:
            continue
        top = height if abs(i) < half else height - 1
        for dy in range(1, top + 1):
            s.set((wx, g + dy, wz), theme.wall)


def spawn_island(s, rng, team, cx, cz, theme, centre, spawns=1):
    """A team island: three chests, a tree, a cover wall facing the mid, the theme's landmark,
    and a spawn four blocks above its middle, or, for two-player teams, two spawns side by side."""
    blob(s, rng, cx, cz, 6 if spawns == 1 else 7, 7, theme)
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
    # The crafting table stands beside the spawns, never under a cage.
    table = 2 if spawns == 1 else 0
    s.set((cx + int(round(px * table - ux * 1.5 * (spawns - 1))), SURFACE + 1,
           cz + int(round(pz * table - uz * 1.5 * (spawns - 1)))), "crafting_table")
    # A low wall in front of the front chest shields it from the mid.
    cover_wall(s, cx + int(round(ux * 4.4)), cz + int(round(uz * 4.4)), px, pz, theme)
    landmark(s, cx + int(round(ux * 2.0 - px * 4.0)), cz + int(round(uz * 2.0 - pz * 4.0)), theme)
    # The cage floor is built at SURFACE+3, so players drop three blocks (no fall damage). Two
    # spawns stand four blocks apart, so their cages share no glass.
    sides = (0.0,) if spawns == 1 else (2.0, -2.0)
    for side in sides:
        s.marker((cx + int(round(px * side)), SURFACE + 4, cz + int(round(pz * side))), f"spawn {team}")


def islet(s, rng, x, z, theme):
    """A small island between the spawn islands and the mid, with the theme's landmark."""
    blob(s, rng, x, z, 2, 5, theme)
    landmark(s, x, z, theme)


def sub_mid_island(s, rng, x, z, theme, centre):
    """A smaller island between the team islands and the mid with two mid chests and the theme's
    landmark (Mega maps)."""
    blob(s, rng, x, z, 5, 8, theme)
    vx, vz = centre[0] - x, centre[1] - z
    length = math.hypot(vx, vz)
    px, pz = -vz / length, vx / length
    for side in (2.0, -2.0):
        cx, cz = x + int(round(px * side)), z + int(round(pz * side))
        s.set((cx, SURFACE + 1, cz), "chest", nbt=MID_LOOT, facing=facing_towards(x - cx, z - cz))
    landmark(s, x + int(round(vx / length * -2.5)), z + int(round(vz / length * -2.5)), theme)


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
    # Rim pillars on the diagonals and broken walls on the axes give cover around the altar.
    for angle in range(0, 360, 90):
        x = cx + int(round(math.cos(math.radians(angle + 45)) * (radius - 2)))
        z = cz + int(round(math.sin(math.radians(angle + 45)) * (radius - 2)))
        s.fill((x, SURFACE + 1, z), (x, SURFACE + 3, z), theme.accent)
        s.set((x, SURFACE + 4, z), "lantern")
        ax, az = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        cover_wall(s, cx + int(round(ax * (radius - 3))), cz + int(round(az * (radius - 3))),
                   -az, ax, theme)


# Angles in ring order for team numbers 1..n, spread so few teams never start as neighbours.
def spread_angles(count):
    if count == 2:
        return [0, 180]
    if count == 4:
        return [0, 180, 90, 270]
    if count == 8:
        return [0, 180, 90, 270, 45, 225, 135, 315]
    if count == 12:
        return [step * 30 for step in (0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11)]
    raise ValueError(count)


def build(name, seed, islands, ring, mid_radius, mid_chests, theme, islets=(), out=OUT, spawns=1,
          sub_mids=()):
    """`islets` are (angle, distance) pairs of small landmark islands without chests, `sub_mids`
    (angle, distance) pairs of smaller islands with two mid chests each; `spawns` is the number of
    spawns (players) per team island."""
    rng = random.Random(seed)
    s = Structure()
    for team, angle in enumerate(spread_angles(islands), start=1):
        x = int(round(math.cos(math.radians(angle)) * ring))
        z = int(round(math.sin(math.radians(angle)) * ring))
        spawn_island(s, rng, team, x, z, theme, (0, 0), spawns)
    mid_island(s, rng, 0, 0, mid_radius, theme, mid_chests)
    for angle, distance in islets:
        islet(s, rng, int(round(math.cos(math.radians(angle)) * distance)),
              int(round(math.sin(math.radians(angle)) * distance)), theme)
    for angle, distance in sub_mids:
        sub_mid_island(s, rng, int(round(math.cos(math.radians(angle)) * distance)),
                       int(round(math.sin(math.radians(angle)) * distance)), theme, (0, 0))
    save(s, name, out, ring + (6 if spawns == 1 else 7) + 6, f"{islands} islands")


def save(s, name, out, extent, summary):
    """Adds the void marker and the buildable bounds (from the void marker up to 24 blocks above
    the islands, `extent` blocks either side of the middle) and writes the map."""
    s.marker((0, 0, 0), "void")
    s.marker((-extent, SURFACE + 24, -extent), "point bounds_min")
    s.marker((extent, SURFACE + 24, extent), "point bounds_max")
    os.makedirs(out, exist_ok=True)
    size = s.save(f"{out}/{name}.nbt")
    print(f"{name}: {size[0]}x{size[1]}x{size[2]}, {summary}")


def spruce(s, x, z, g, height=7):
    """A snowy spruce: a trunk with tiers of leaves narrowing to a point, standing on (x, g, z)."""
    for dy in range(1, height + 1):
        s.set((x, g + dy, z), "spruce_log", axis="y")
    for dy, spread in ((3, 2), (4, 1), (5, 2), (6, 1), (7, 1), (8, 0), (9, 0)):
        if dy > height + 2:
            break
        for dx in range(-spread, spread + 1):
            for dz in range(-spread, spread + 1):
                if (dx, dz) == (0, 0) and dy <= height:
                    continue
                if abs(dx) + abs(dz) > spread + (1 if spread == 2 else 0):
                    continue
                s.set((x + dx, g + dy, z + dz), "spruce_leaves", persistent="true")


def frostbite():
    """A duel and small-team map: two large snowy islands 30 blocks either side of a mid island,
    each holding a team of up to two (a cage per player). Each island has a spruce cabin with a
    chest on a raised terrace at the back, a front chest behind a broken wall facing the mid next
    to a frozen pond, a side chest under a tall spruce, and an ice spike on a rocky mound; the mid
    has an altar with four chests, ice spikes and broken walls; two ice-spike islets lie on the
    flanks and four small floes between the islands and the mid."""
    rng = random.Random(2)
    s = Structure()
    theme = TUNDRA
    ring, radius = 30, 10
    for team, sign in ((1, 1), (2, -1)):
        cx = sign * ring
        blob(s, rng, cx, 0, radius, 9, theme)

        def at(a, b):
            """Island coordinates: `a` towards the mid, `b` to the team's left."""
            return cx - sign * a, -sign * b

        # A raised terrace across the back of the island, with a stone face, where the cabin
        # stands; rocks show through the snow along the rim.
        for x in range(cx - radius - 1, cx + radius + 2):
            for z in range(-radius - 1, radius + 2):
                if ground(s, x, z) != SURFACE:
                    continue
                a, b = sign * (cx - x), -sign * z
                distance = math.hypot(a, b)
                if a <= -4 - (abs(b) > 6) and distance <= radius - 1:
                    s.set((x, SURFACE, z), "stone")
                    s.set((x, SURFACE + 1, z), "snow_block")
                elif distance > radius - 1.5 and (x * 7 + z * 13) % 5 == 0:
                    s.set((x, SURFACE, z), "stone")
        # A frozen pond by the front chest.
        for a in range(1, 6):
            for b in range(-7, -2):
                if math.hypot(a - 3, b + 5) <= 1.6:
                    x, z = at(a, b)
                    s.set((x, SURFACE, z), "packed_ice")
        # A rocky mound with an ice spike on the right flank.
        for a in range(-4, 2):
            for b in range(-9, -4):
                x, z = at(a, b)
                lift = 2 - (abs(a + 1.5) > 2) - (abs(b + 6.5) > 1.5)
                g = ground(s, x, z)
                if g is None or lift <= 0:
                    continue
                for dy in range(1, lift + 1):
                    s.set((x, g + dy, z), "snow_block" if dy == lift else "stone")
        landmark(s, *at(-1, -7), theme)
        # The spruce cabin on the terrace, its door towards the spawns, with a chest inside.
        g = SURFACE + 1
        for a in range(-8, -4):
            for b in range(-3, 4):
                x, z = at(a, b)
                wall = a in (-8, -5) or abs(b) == 3
                corner = a in (-8, -5) and abs(b) == 3
                s.set((x, g, z), "spruce_planks")
                for dy in (1, 2, 3):
                    if corner:
                        s.set((x, g + dy, z), "spruce_log", axis="y")
                    elif wall and not (a == -5 and abs(b) <= 1 and dy <= 2):
                        s.set((x, g + dy, z), "spruce_planks" if dy != 2 or abs(b) != 3 or a != -7
                              else "glass")
                s.set((x, g + 4, z), "spruce_slab", type="bottom")
        # A snowy ridge along the roof.
        for a in (-7, -6):
            for b in range(-3, 4):
                x, z = at(a, b)
                s.set((x, g + 4, z), "snow_block")
        x, z = at(-7, 2)
        s.set((x, g + 1, z), "chest", nbt=ISLAND_LOOT, facing="south" if sign > 0 else "north")
        x, z = at(-7, -2)
        s.set((x, g + 1, z), "crafting_table")
        s.set((at(-6, -2)[0], g + 3, at(-6, -2)[1]), "lantern", hanging="true")
        # The front chest behind a broken wall facing the mid; outdoor chests face the island's
        # middle so they open towards the player.
        g = SURFACE
        x, z = at(4, 0)
        s.set((x, g + 1, z), "chest", nbt=ISLAND_LOOT, facing=facing_towards(cx - x, -z))
        wx, wz = at(6, 0)
        cover_wall(s, wx, wz, 0, 1, theme, length=5)
        # A tall spruce on the left flank with a chest at its foot.
        x, z = at(-2, 7)
        spruce(s, x, z, ground(s, x, z), height=7)
        x, z = at(0, 6)
        s.set((x, g + 1, z), "chest", nbt=ISLAND_LOOT, facing=facing_towards(cx - x, -z))
        # Snowy boulders.
        for a, b in ((3, 7), (6, -4)):
            x, z = at(a, b)
            s.set((x, g + 1, z), "cobblestone")
            s.set((at(a + 1, b)[0], g + 1, at(a + 1, b)[1]), "snow_block")
        # Two spawns, four blocks apart, so each player of a two-player team has a cage.
        for b in (2, -2):
            x, z = at(0, b)
            s.marker((x, SURFACE + 4, z), f"spawn {team}")

    mid_island(s, rng, 0, 0, radius, theme, 4)
    for x, z in ((4, 7), (-4, -7)):
        landmark(s, x, z, theme)
    # Islets: an ice spike on each flank, and small floes on the diagonals between the islands
    # and the mid.
    for z in (18, -18):
        blob(s, rng, 0, z, 4, 6, theme)
        landmark(s, 0, z, theme)
    for x, z in ((17, 10), (-17, -10), (17, -10), (-17, 10)):
        blob(s, rng, x, z, 2, 4, theme)
    save(s, "frostbite", OUT, ring + radius + 6, "2 islands")


def main():
    # Duel and small-team map: two large islands of two spawns each, facing each other over a mid.
    frostbite()
    # Four islands: 1v1 to 1v1v1v1 or 2v2.
    build("mesa", 4, islands=4, ring=26, mid_radius=8, mid_chests=4, theme=MESA,
          islets=((45, 17), (135, 17), (225, 17), (315, 17)))
    # Eight islands: free-for-all or team modes up to eight teams.
    build("archipelago", 8, islands=8, ring=34, mid_radius=9, mid_chests=6, theme=MEADOW,
          islets=((22.5, 21), (112.5, 21), (202.5, 21), (292.5, 21)))
    # Mini: four close islands around a small mid, for four-player games.
    build("blossom", 21, islands=4, ring=18, mid_radius=6, mid_chests=4, theme=BLOSSOM, out=MINI_OUT)
    build("oasis", 22, islands=4, ring=19, mid_radius=6, mid_chests=4, theme=OASIS, out=MINI_OUT,
          islets=((45, 12), (225, 12)))
    # Mega: twelve two-player islands, four sub-mid islands with mid chests, and a large mid.
    build("highlands", 31, islands=12, ring=72, mid_radius=12, mid_chests=6, theme=HIGHLANDS, out=MEGA_OUT,
          spawns=2, sub_mids=((45, 36), (135, 36), (225, 36), (315, 36)),
          islets=((15, 55), (75, 55), (105, 55), (165, 55), (195, 55), (255, 55), (285, 55), (345, 55)))


if __name__ == "__main__":
    main()
