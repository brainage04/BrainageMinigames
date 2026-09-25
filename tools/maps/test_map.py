"""Builds the GameTest maps for MapFrameworkGameTest and the NeoForge map GameTest.

`test_map/basic` is a 64-block-long strip, wider than a structure block can save, with one marker
of every kind. Coordinates below are template coordinates before saving: the void marker is the
lowest block, so after saving the floor is 5 blocks above the template's minimum corner.
`test_map_invalid/*` are maps the mod must refuse with a clear message.

Run from the repository root: python3 tools/maps/test_map.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

ROOTS = [
    "fabric/src/gametest/resources/data/brainage_minigames/structure/maps",
    "neoforge/src/gametest/resources/data/brainage_minigames/structure/maps",
]


def basic():
    s = Structure()
    # Floor: x 0..63, z 0..8 at y 0.
    s.fill((0, 0, 0), (63, 0, 8), "minecraft:stone")
    # A block to break and restore.
    s.set((32, 1, 8), "minecraft:glass")
    s.marker((32, 1, 4), "lobby")
    s.marker((2, 1, 4), "spawn 1 -90")
    s.marker((4, 1, 6), "spawn 1")
    s.marker((61, 1, 4), "spawn 2 90")
    s.marker((32, 1, 2), "point chest_mid")
    s.marker((10, 1, 6), "point boat_1 -90")
    s.marker((12, 1, 6), "point boat_2")
    # Build region: x 20..43, y 1..11, z 0..8.
    s.marker((20, 1, 0), "region build 23 10 8")
    s.marker((0, 1, 0), "region goal_1 1 1 1")
    s.marker((32, -5, 4), "void")
    return s


def missing_team():
    s = Structure()
    s.fill((0, 0, 0), (4, 0, 4), "minecraft:stone")
    s.marker((1, 1, 1), "spawn 2")
    return s


def bad_marker():
    s = Structure()
    s.fill((0, 0, 0), (4, 0, 4), "minecraft:stone")
    s.marker((1, 1, 1), "spawn 1")
    s.marker((3, 1, 3), "teleporter 3")
    return s


MAPS = {
    "test_map/basic": basic,
    "test_map_invalid/missing_team": missing_team,
    "test_map_invalid/bad_marker": bad_marker,
}

if __name__ == "__main__":
    for root in ROOTS:
        for name, build in MAPS.items():
            if root.startswith("neoforge") and name != "test_map/basic":
                continue
            path = f"{root}/{name}.nbt"
            os.makedirs(os.path.dirname(path), exist_ok=True)
            print(path, build().save(path))
