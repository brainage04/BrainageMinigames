"""Generates the Bridge maps (original layouts): python3 tools/maps/bridge.py from the repo root.

Every map has, per team: a `spawn <team>` marker inside a glass cage above the island, a
`region cage_<team>` covering the cage (the game removes it when a round starts), a
`region goal_<team>` covering the goal hole, plus shared `build` regions, a `void` marker and a
`lobby` platform high above the middle. Team 1 is red, 2 blue, 3 green (lime blocks) and 4
yellow, matching the colours the mod gives teams.

Battle Rush reuses the island, goal and cage helpers (tools/maps/battle_rush.py).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/{}/{}.nbt"

COLORS = {1: "red", 2: "blue", 3: "lime", 4: "yellow"}

# Surface height of the islands inside the template.
S = 16

# Outward directions of the teams: (dx, dz). A team's island lies along its direction.
EAST_WEST = {1: (-1, 0), 2: (1, 0)}
FOUR_WAY = {1: (-1, 0), 2: (1, 0), 3: (0, -1), 4: (0, 1)}


class Frame:
    """Maps a team's local (u, v) coordinates, u outward from the centre and v sideways, to x, z."""

    def __init__(self, direction):
        self.dx, self.dz = direction

    def xz(self, u, v):
        return (u * self.dx - v * self.dz, u * self.dz + v * self.dx)

    def pos(self, u, y, v):
        x, z = self.xz(u, v)
        return (x, y, z)

    def box(self, u1, y1, v1, u2, y2, v2):
        """The min corner and the (dx, dy, dz) span of a local box, for region markers."""
        x1, z1 = self.xz(u1, v1)
        x2, z2 = self.xz(u2, v2)
        lo = (min(x1, x2), min(y1, y2), min(z1, z2))
        span = (abs(x2 - x1), abs(y2 - y1), abs(z2 - z1))
        return lo, span


def region(s, name, lo, span):
    s.marker(lo, "region {} {} {} {}".format(name, *span))


def blob(s, frame, u1, u2, half_width, top, fill, depth, taper=1.0, surface=S):
    """A rounded island between u1 and u2, `half_width` wide either side, tapering downwards."""
    mid = (u1 + u2) / 2
    half_len = (u2 - u1) / 2
    for u in range(u1, u2 + 1):
        for v in range(-half_width, half_width + 1):
            # Superellipse keeps the island boxy but with round corners.
            d = (abs(u - mid) / (half_len + 0.5)) ** 4 + (abs(v) / (half_width + 0.5)) ** 4
            if d > 1:
                continue
            # Deeper towards the middle of the island.
            rim = 1 - d
            bottom = surface - 1 - int(round(depth * (rim ** (0.5 * taper))))
            for y in range(bottom, surface):
                s.set(frame.pos(u, y, v), fill if y < surface - 2 else "dirt")
            s.set(frame.pos(u, surface, v), top)


def goal(s, frame, team, u_center, size=5, depth=4, floor="crying_obsidian", rim=None):
    """A square goal hole centred on (u_center, 0); returns nothing. Adds `region goal_<team>`."""
    color = COLORS[team]
    half = size // 2
    for u in range(u_center - half - 1, u_center + half + 2):
        for v in range(-half - 1, half + 2):
            inside = abs(u - u_center) <= half and abs(v) <= half
            if inside:
                for y in range(S - depth + 1, S + 1):
                    s.remove(frame.pos(u, y, v))
                s.set(frame.pos(u, S - depth, v), floor)
            else:
                s.set(frame.pos(u, S, v), rim or f"{color}_glazed_terracotta")
                for y in range(S - depth, S):
                    s.set(frame.pos(u, y, v), f"{color}_terracotta")
    lo, span = frame.box(u_center - half, S - depth + 1, -half, u_center + half, S, half)
    region(s, f"goal_{team}", lo, span)
    # Corner beacons so the goal is visible from across the gap.
    for cu, cv in ((u_center - half - 1, -half - 1), (u_center + half + 1, half + 1),
                   (u_center - half - 1, half + 1), (u_center + half + 1, -half - 1)):
        s.set(frame.pos(cu, S + 1, cv), f"{color}_stained_glass")
        s.set(frame.pos(cu, S + 2, cv), "end_rod", facing="up")


def cage(s, frame, team, u_center, floor_y=S + 3):
    """A glass cage with a 3x3 inside; the spawn stands on its floor. Adds `region cage_<team>`
    (removed by the game when the round starts) and `spawn <team>` facing the middle."""
    glass = f"{COLORS[team]}_stained_glass"
    for u in range(u_center - 2, u_center + 3):
        for v in range(-2, 3):
            s.set(frame.pos(u, floor_y, v), glass)
            s.set(frame.pos(u, floor_y + 4, v), glass)
            if abs(u - u_center) == 2 or abs(v) == 2:
                for y in range(floor_y + 1, floor_y + 4):
                    s.set(frame.pos(u, y, v), glass)
    # One block larger than the cage, so the marker (at a corner) does not replace a glass block.
    lo, span = frame.box(u_center - 3, floor_y - 1, -3, u_center + 3, floor_y + 5, 3)
    region(s, f"cage_{team}", lo, span)
    s.marker(frame.pos(u_center, floor_y + 1, 0), f"spawn {team}")


def tree(s, pos, log="oak_log", leaves="oak_leaves", height=4):
    x, y, z = pos
    for dy in range(height):
        s.set((x, y + dy, z), log)
    top = y + height
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (-2, -1):
                if abs(dx) + abs(dz) < 4 and (dx, dz) != (0, 0):
                    s.set((x + dx, top + dy, z + dz), leaves, persistent=True)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if abs(dx) + abs(dz) < 2:
                s.set((x + dx, top, z + dz), leaves, persistent=True)


def lobby(s, y=S + 22, size=4, block="white_stained_glass"):
    for x in range(-size, size + 1):
        for z in range(-size, size + 1):
            s.set((x, y, z), block)
            if max(abs(x), abs(z)) == size:
                s.set((x, y + 1, z), "barrier")
    s.marker((0, y + 1, 0), "lobby")


def bridge_line(s, frames, reach, block, width=0, y=S):
    """The pre-built bridge: a line of `block` from the middle out to `reach` along every frame."""
    for frame in frames:
        for u in range(0, reach + 1):
            for v in range(-width, width + 1):
                s.set(frame.pos(u, y, v), block)


def build_regions(s, frames, reach, below=6, above=6, half_width=12):
    """Placing is allowed within `reach` of the middle (keeping clear of the goals, which sit
    further out) between `below` and `above` blocks of the surface."""
    done = set()
    for frame in frames:
        axis = frame.dx != 0
        if axis in done:
            continue
        done.add(axis)
        lo, span = frame.box(-reach, S - below, -half_width, reach, S + above, half_width)
        region(s, "build", lo, span)


def team_island(s, frame, team, u1, u2, half_width, top, fill, goal_u, cage_u, depth=9,
                decorate=None):
    blob(s, frame, u1, u2, half_width, top, fill, depth)
    # A team-coloured path from the island edge to the goal.
    for u in range(u1, goal_u):
        for v in (-1, 0, 1):
            if s.get(frame.pos(u, S, v)):
                s.set(frame.pos(u, S, v), f"{COLORS[team]}_terracotta")
    goal(s, frame, team, goal_u)
    cage(s, frame, team, cage_u)
    if decorate:
        decorate(s, frame, team)


def grove():
    """Two leafy islands 30 blocks apart joined by a one-wide stone bridge with a small resting
    platform in the middle."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        for u, v in ((20, -7), (26, 7), (37, -4)):
            tree(s, frame.pos(u, S + 1, v))
        for u, v in ((18, 4), (23, -4), (29, 5), (35, 3)):
            s.set(frame.pos(u, S + 1, v), "poppy" if (u + v) % 2 else "dandelion")
        for v in (-3, 3):
            s.set(frame.pos(16, S + 1, v), "lantern")

    for team, frame in frames.items():
        team_island(s, frame, team, 15, 40, 11, "grass_block", "stone", goal_u=33, cage_u=21,
                    decorate=decorate)
    bridge_line(s, frames.values(), 15, "polished_andesite")
    for x in range(-1, 2):
        for z in range(-2, 3):
            s.set((x, S, z), "smooth_stone")
    for z in (-2, 2):
        s.set((0, S + 1, z), "stone_brick_wall")
        s.set((0, S + 2, z), "lantern")
    build_regions(s, frames.values(), 29)
    s.marker((0, S - 12, 0), "void")
    lobby(s)
    return s


def basalt():
    """Two volcanic islands 34 blocks apart; the bridge crosses two basalt stepping pillars."""
    s = Structure()
    frames = {t: Frame(d) for t, d in EAST_WEST.items()}

    def decorate(s, frame, team):
        for u, v in ((22, -8), (22, 8), (36, -6), (36, 6)):
            for y in range(S + 1, S + 4):
                s.set(frame.pos(u, y, v), "basalt", axis="y")
            s.set(frame.pos(u, S + 4, v), "shroomlight")
        for u, v in ((19, 3), (27, -5), (30, 6)):
            s.set(frame.pos(u, S + 1, v), "crimson_fungus")
        # Stepping pillar in the gap.
        for u in (9, 10):
            for v in (-1, 0, 1):
                for y in range(S - 5, S):
                    s.set(frame.pos(u, y, v), "basalt", axis="y")
                s.set(frame.pos(u, S, v), "polished_basalt", axis="y")

    for team, frame in frames.items():
        team_island(s, frame, team, 17, 41, 10, "crimson_nylium", "blackstone", goal_u=35,
                    cage_u=23, decorate=decorate)
    bridge_line(s, frames.values(), 17, "polished_blackstone_bricks")
    s.set((0, S, 0), "gilded_blackstone")
    build_regions(s, frames.values(), 31, half_width=11)
    s.marker((0, S - 12, 0), "void")
    lobby(s, block="black_stained_glass")
    return s


def compass():
    """Four islands around a square plaza, one per team, each 13 blocks from the plaza."""
    s = Structure()
    frames = {t: Frame(d) for t, d in FOUR_WAY.items()}

    def decorate(s, frame, team):
        for u, v in ((19, -6), (31, 5)):
            tree(s, frame.pos(u, S + 1, v), log="birch_log", leaves="birch_leaves")
        s.set(frame.pos(17, S + 1, 5), "lantern")
        s.set(frame.pos(17, S + 1, -5), "lantern")

    for team, frame in frames.items():
        team_island(s, frame, team, 16, 36, 9, "grass_block", "stone", goal_u=30, cage_u=21,
                    depth=8, decorate=decorate)
    bridge_line(s, frames.values(), 16, "smooth_stone")
    for x in range(-3, 4):
        for z in range(-3, 4):
            s.set((x, S, z), "chiseled_stone_bricks" if (x, z) == (0, 0) else "stone_bricks")
            for y in range(S - 3, S):
                s.set((x, y, z), "stone")
    for x, z in ((-3, -3), (-3, 3), (3, -3), (3, 3)):
        s.set((x, S + 1, z), "stone_brick_wall")
        s.set((x, S + 2, z), "lantern")
    build_regions(s, frames.values(), 26, half_width=10)
    s.marker((0, S - 12, 0), "void")
    lobby(s)
    return s


def main():
    for name, make in (("grove", grove), ("basalt", basalt), ("compass", compass)):
        size = make().save(OUT.format("bridge", name))
        print(f"bridge/{name}: {size}")


if __name__ == "__main__":
    main()
