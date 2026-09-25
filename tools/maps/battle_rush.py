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
    """Two mossy islands 20 blocks apart, each with a small pond behind its cage."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        for u in range(17, 20):
            for v in range(4, 7):
                s.set(frame.pos(u, S, v), "water")
                s.set(frame.pos(u, S - 1, v), "clay")
        s.set(frame.pos(18, S + 1, 5), "lily_pad")
        s.set(frame.pos(17, S + 1, 6), "lily_pad")
        tree(s, frame.pos(23, S + 1, -5), height=3)
        for u, v in ((12, -4), (15, 4), (20, -2)):
            s.set(frame.pos(u, S + 1, v), "fern")

    for team, frame in frames.items():
        island(s, frame, team, 10, 26, 7, "moss_block", "stone", goal_u=22, cage_u=14,
               decorate=decorate)
    build_regions(s, frames.values(), 19, below=4, above=5, half_width=9)
    s.marker((0, S - 10, 0), "void")
    lobby(s, y=S + 18, size=3)
    return s


def driftwood():
    """Two sandy islands 24 blocks apart with a lone driftwood post in the middle of the gap."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        for u, v in ((13, 5), (21, -5)):
            s.set(frame.pos(u, S + 1, v), "dead_bush")
        for v in (-5, 5):
            for y in range(S + 1, S + 3):
                s.set(frame.pos(24, y, v), "stripped_oak_log", axis="y")

    for team, frame in frames.items():
        island(s, frame, team, 12, 28, 6, "sand", "sandstone", goal_u=24, cage_u=16,
               decorate=decorate)
    for y in range(S - 6, S - 1):
        s.set((0, y, 3), "stripped_spruce_log", axis="y")
    build_regions(s, frames.values(), 21, below=4, above=5, half_width=8)
    s.marker((0, S - 10, 0), "void")
    lobby(s, y=S + 18, size=3, block="light_blue_stained_glass")
    return s


def main():
    for name, make in (("lilypond", lilypond), ("driftwood", driftwood)):
        size = make().save(OUT.format("battle_rush", name))
        print(f"battle_rush/{name}: {size}")


if __name__ == "__main__":
    main()
