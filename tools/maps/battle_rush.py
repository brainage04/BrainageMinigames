"""Generates the Battle Rush maps (original layouts): python3 tools/maps/battle_rush.py from the
repo root.

Battle Rush islands are small and nothing connects them: players rush across the void with wool.
Markers are the same as Bridge's (see tools/maps/bridge.py): `spawn`, `region cage_<team>`,
`region goal_<team>`, `region build`, `void` and `lobby`.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bridge import (  # noqa: E402
    COLORS, EAST_WEST, OUT, S, Frame, blob, build_regions, cage, goal, lobby, tree)
from structure import Structure  # noqa: E402


def island(s, frame, team, u1, u2, half_width, top, fill, goal_u, cage_u, decorate):
    blob(s, frame, u1, u2, half_width, top, fill, depth=6)
    for u in range(u1, goal_u):
        if s.get(frame.pos(u, S, 0)):
            s.set(frame.pos(u, S, 0), f"{COLORS[team]}_wool")
    goal(s, frame, team, goal_u, size=3, depth=3)
    cage(s, frame, team, cage_u)
    decorate(s, frame, team)


def lilypond():
    """Two mossy water-garden islands 20 blocks apart: lily ponds either side of the cage, a ruined
    shrine wall with lantern pillars behind each goal, a flowering azalea and bushes for cover."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        def at(u, dy, v, block, **props):
            s.set(frame.pos(u, S + dy, v), block, **props)

        for sign in (-1, 1):
            for u in range(16, 20):
                for v in range(4, 7):
                    at(u, 0, sign * v, "water")
                    at(u, -1, sign * v, "clay")
            at(17, 1, sign * 5, "lily_pad")
            at(19, 1, sign * 4, "lily_pad")
            at(16, 0, sign * 6, "big_dripleaf_stem", facing=frame_facing(frame, -1, 0),
               waterlogged="true")
            at(16, 1, sign * 6, "big_dripleaf", facing=frame_facing(frame, -1, 0))
            # Azalea bushes near the edge of the island give some cover.
            for du, dy, dv in ((0, 1, 0), (1, 1, 0), (0, 1, 1), (0, 2, 0)):
                at(12 + du, dy, sign * (5 + dv), "azalea_leaves", persistent=True)
        # The shrine: a broken wall behind the goal between two lantern pillars.
        for v in range(-3, 4):
            for dy in (1, 2, 3):
                if dy == 3 and v in (-2, 1):
                    continue
                at(25, dy, v, "mossy_stone_bricks" if (v + dy) % 3 else "cracked_stone_bricks")
        for v in (-4, 4):
            for dy in range(1, 5):
                at(25, dy, v, "mossy_stone_brick_wall" if dy < 4 else "chiseled_stone_bricks")
            at(25, 5, v, "lantern")
        tree(s, frame.pos(22, S + 1, -6), height=3, leaves="flowering_azalea_leaves")
        for u, v in ((11, -2), (20, 3), (24, 5), (13, 3)):
            at(u, 1, v, "pink_petals", flower_amount=3, facing=frame_facing(frame, 1, 0))
        for u, v in ((12, 2), (21, -3), (18, -2)):
            at(u, 1, v, "fern")

    for team, frame in frames.items():
        island(s, frame, team, 10, 26, 7, "moss_block", "stone", goal_u=22, cage_u=14,
               decorate=decorate)
    build_regions(s, frames.values(), 19, below=4, above=5, half_width=9)
    s.marker((0, S - 10, 0), "void")
    lobby(s, y=S + 18, size=3)
    return s


def driftwood():
    """Two sandy beach islands 24 blocks apart. A boardwalk with a rail wraps the back of each
    goal, a beached boat with a sail gives cover on one side of the spawn and a leaning palm and
    a tide pool sit on the other."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        def at(u, dy, v, block, **props):
            s.set(frame.pos(u, S + dy, v), block, **props)

        # The boardwalk: raised planks around the back and sides of the goal, on posts.
        for u in range(22, 29):
            for v in range(-5, 6):
                beside = 22 <= u <= 26 and 3 <= abs(v) <= 4
                behind = u >= 27 and abs(v) <= 4
                if beside or behind:
                    at(u, 1, v, "spruce_planks")
                    if (u + v) % 3 == 0:
                        at(u, 0, v, "stripped_spruce_log", axis="y")
        for v in range(-4, 5):
            at(28, 2, v, "spruce_fence")
        for u, v in ((28, -4), (28, 4), (22, -4), (22, 4)):
            at(u, 2, v, "spruce_fence")
            at(u, 3, v, "lantern")
        # The beached boat: a short hull of spruce with a mast and a sail.
        for u in range(12, 18):
            if 13 <= u <= 16:
                at(u, 1, -5, "spruce_planks")
            else:
                at(u, 1, -5, "spruce_slab", type="bottom")
            if 13 <= u <= 16:
                at(u, 2, -6, "spruce_trapdoor", facing=frame_facing(frame, 0, 1), half="bottom",
                   open="true")
                at(u, 2, -4, "spruce_trapdoor", facing=frame_facing(frame, 0, -1), half="bottom",
                   open="true")
        for dy in range(2, 7):
            at(15, dy, -5, "stripped_dark_oak_log", axis="y")
        for dy in range(3, 7):
            for u in (14, 16):
                at(u, dy, -5, "white_wool")
        # A palm leaning over the island's edge, and a tide pool.
        trunk = [(20, 1, 5), (20, 2, 5), (20, 3, 6), (20, 4, 6), (20, 5, 7), (20, 6, 7)]
        for u, dy, v in trunk:
            at(u, dy, v, "jungle_log", axis="y")
        crown_u, crown_y, crown_v = 20, 7, 7
        at(crown_u, crown_y, crown_v, "jungle_leaves", persistent=True)
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for step in (1, 2, 3):
                droop = 1 if step == 3 else 0
                at(crown_u + du * step, crown_y - droop, crown_v + dv * step, "jungle_leaves",
                   persistent=True)
        at(20, 4, 7, "cocoa", facing=frame_facing(frame, 0, -1), age=2)
        for u in range(13, 16):
            for v in range(2, 5):
                at(u, -1, v, "sand")
                at(u, 0, v, "water")
        at(14, 0, 3, "sea_pickle", pickles=3, waterlogged="true")
        for u, v in ((19, -2), (23, -5), (18, 4)):
            at(u, 1, v, "dead_bush")

    for team, frame in frames.items():
        island(s, frame, team, 12, 28, 6, "sand", "sandstone", goal_u=24, cage_u=16,
               decorate=decorate)
    build_regions(s, frames.values(), 21, below=4, above=5, half_width=8)
    s.marker((0, S - 10, 0), "void")
    lobby(s, y=S + 18, size=3, block="light_blue_stained_glass")
    return s


def frame_facing(frame, du, dv):
    """The compass direction of the local (u, v) direction (du, dv)."""
    dx, dz = frame.xz(du, dv)
    return {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)]


def main():
    for name, make in (("lilypond", lilypond), ("driftwood", driftwood)):
        size = make().save(OUT.format("battle_rush", name))
        print(f"battle_rush/{name}: {size}")


if __name__ == "__main__":
    main()
