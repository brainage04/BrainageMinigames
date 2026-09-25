"""Generates the Parkour maps: python3 tools/maps/parkour.py (from the repository root).

Every course is a chain of jumps built by a small turtle: each jump names where the next block is
relative to the block the player stands on (forward, up, right). Checkpoints are 3x3 platforms
with a `region checkpoint_<n>` above them and a `point checkpoint_<n> <yaw>` marking where and which
way a player is put back; the course ends on a `region finish` platform. A course never descends
more than one block below its previous checkpoint, because the game sends anyone who drops five
blocks below their last checkpoint back to it. Water under the course catches everyone else inside
a `region fail`.

Jumps stay within what a sprinting player makes reliably: a 3-block gap on the level, a 2-block gap
one block up, fence posts and ladders. There are no neo, head-hitter or momentum jumps.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/parkour"

HEADINGS = {(1, 0): "east", (0, 1): "south", (-1, 0): "west", (0, -1): "north"}


def yaw_of(heading):
    hx, hz = heading
    return round(math.degrees(math.atan2(-hx, hz)))


class Course:
    def __init__(self, structure, start, heading):
        self.s = structure
        self.pos = start
        self.h = heading
        self.checkpoints = 0

    def offset(self, forward, up, right, origin=None):
        x, y, z = origin or self.pos
        hx, hz = self.h
        rx, rz = -hz, hx
        return (x + forward * hx + right * rx, y + up, z + forward * hz + right * rz)

    def facing_back(self):
        return HEADINGS[(-self.h[0], -self.h[1])]

    def turn(self, direction):
        hx, hz = self.h
        self.h = (-hz, hx) if direction == "right" else (hz, -hx)

    def jump(self, forward, up, right, block, **props):
        self.pos = self.offset(forward, up, right)
        self.s.set(self.pos, block, **props)
        return self.pos

    def jumps(self, steps, block, **props):
        for forward, up, right in steps:
            self.jump(forward, up, right, block, **props)

    def ladder(self, distance, height, wall, gap_below=False):
        """A ladder `distance` blocks ahead on a wall; the player climbs it and stands on top."""
        for level in range(1, height + 1):
            self.s.set(self.offset(distance, level, 0), "ladder", facing=self.facing_back())
            self.s.set(self.offset(distance + 1, level, 0), wall)
        if not gap_below:
            self.s.set(self.offset(distance, 0, 0), wall)
        self.s.set(self.offset(distance + 1, 0, 0), wall)
        self.pos = self.offset(distance + 1, height, 0)

    def platform(self, center, block, rim, radius=1):
        cx, y, cz = center
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                edge = abs(dx) == radius or abs(dz) == radius
                self.s.set((cx + dx, y, cz + dz), rim if edge else block)

    def checkpoint(self, forward, up, right, block, rim, turn=None):
        self.checkpoints += 1
        n = self.checkpoints
        center = self.offset(forward, up, right)
        self.platform(center, block, rim)
        self.pos = center
        if turn:
            self.turn(turn)
        cx, y, cz = center
        self.s.marker((cx - 1, y + 1, cz - 1), f"region checkpoint_{n} 2 2 2")
        self.s.marker((cx, y + 1, cz), f"point checkpoint_{n} {yaw_of(self.h)}")
        # A lantern post on the outer corner makes checkpoints visible from afar.
        post = (cx + 1, y + 1, cz + 1)
        self.s.set(post, "oak_fence")
        self.s.set((post[0], y + 2, post[2]), "lantern", hanging=False)

    def finish(self, forward, up, right):
        center = self.offset(forward, up, right)
        cx, y, cz = center
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                checker = "gold_block" if (dx + dz) % 2 == 0 else "yellow_glazed_terracotta"
                self.s.set((cx + dx, y, cz + dz), checker)
        self.s.marker((cx - 2, y + 1, cz - 2), "region finish 4 2 4")
        self.s.marker((cx, y + 1, cz), f"point finish {yaw_of(self.h)}")
        for dx, dz in ((-3, -3), (-3, 3), (3, -3), (3, 3)):
            for level in range(0, 4):
                self.s.set((cx + dx, y + level, cz + dz), "quartz_pillar")
            self.s.set((cx + dx, y + 4, cz + dz), "sea_lantern")
        self.pos = center


def start_pad(course, block, rim, width=8):
    """Eight spawns side by side on a pad three blocks deep; the first jump leaves from the middle."""
    left = -(width // 2) + 1
    yaw = yaw_of(course.h)
    for forward in (-2, -1, 0):
        for right in range(left - 1, left + width + 1):
            edge = forward == -2 or right in (left - 1, left + width)
            course.s.set(course.offset(forward, 0, right), rim if edge else block)
    for team in range(1, width + 1):
        course.s.marker(course.offset(0, 1, left + team - 1), f"spawn {team} {yaw}")
    course.s.marker(course.offset(-1, 1, 0), f"lobby {yaw}")
    # A low rail behind and beside the pad keeps waiting players from wandering off.
    for right in range(left - 1, left + width + 1):
        course.s.set(course.offset(-2, 1, right), "spruce_fence")
    for forward in (-1, 0):
        course.s.set(course.offset(forward, 1, left - 1), "spruce_fence")
        course.s.set(course.offset(forward, 1, left + width), "spruce_fence")


def water_floor(s, margin=4):
    """A shallow pool under the whole course with a stone rim; touching it counts as a fall.
    Returns the pool's floor height."""
    xs = [p[0] for p in s.blocks]
    zs = [p[2] for p in s.blocks]
    x1, x2 = min(xs) - margin, max(xs) + margin
    z1, z2 = min(zs) - margin, max(zs) + margin
    floor_y = min(p[1] for p in s.blocks) - 8
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            rim = x in (x1, x2) or z in (z1, z2)
            s.set((x, floor_y, z), "prismarine_bricks" if rim else "dark_prismarine")
            s.set((x, floor_y + 1, z), "prismarine_bricks" if rim else "water")
    # The corner of the rim holds the marker; a missing corner lets no water out.
    s.marker((x1, floor_y + 1, z1), f"region fail {x2 - x1} 3 {z2 - z1}")
    return floor_y


def canopy():
    """A jungle course heading east with a detour north: warm-up, ladders, fences, climbs."""
    s = Structure()
    c = Course(s, (0, 12, 0), (1, 0))
    start_pad(c, "jungle_planks", "stripped_jungle_log")

    # 1: warm-up on jungle logs.
    c.jumps([(3, 0, 0), (3, 0, 0), (3, 0, -1), (4, 0, 0), (3, 1, 0), (3, 0, 1), (3, 1, 0)],
            "jungle_log")
    c.checkpoint(3, 0, 0, "moss_block", "mossy_stone_bricks")

    # 2: a ladder wall, then a jump onto a ladder hanging in the air.
    c.jump(3, 0, 0, "mossy_cobblestone")
    c.ladder(1, 5, "mossy_stone_bricks")
    c.jumps([(3, 0, 0), (3, 0, 2)], "mossy_cobblestone")
    c.ladder(2, 4, "mossy_stone_bricks", gap_below=True)
    c.jumps([(3, 0, 0), (4, 0, 0)], "mossy_cobblestone")
    c.checkpoint(3, 0, 0, "moss_block", "mossy_stone_bricks")

    # 3: fence posts (their tops are half a block higher than a block's).
    for step in [(3, 0, 0), (3, 0, 0), (3, 0, 1), (3, 0, 0)]:
        c.jump(*step, "jungle_fence")
    c.jumps([(3, 1, 0), (3, 0, -1)], "jungle_planks")
    c.checkpoint(3, 0, 0, "moss_block", "mossy_stone_bricks", turn="left")

    # 4: heading north: a long gap, then a staircase of single blocks.
    c.jumps([(4, 0, 0), (3, 1, 0), (3, 1, 0), (3, 1, 0), (2, 1, 1), (4, 0, 0), (4, 0, -1)],
            "jungle_leaves", persistent=True)
    c.checkpoint(3, 0, 0, "moss_block", "mossy_stone_bricks", turn="right")

    # 5: heading east again: a tall ladder and the last run to the finish.
    c.jump(3, 0, 0, "mossy_cobblestone")
    c.ladder(1, 6, "mossy_stone_bricks")
    c.jumps([(3, 0, 0), (3, -1, 1), (4, 0, 0), (3, 1, -1), (3, 0, 0)], "jungle_log")
    c.finish(4, 0, 0)

    water_floor(s)
    return s, c.checkpoints


def spire():
    """A square spiral climbing around a quartz tower, turning left at every checkpoint."""
    s = Structure()
    side = 20
    c = Course(s, (-side // 2, 12, side // 2), (1, 0))
    start_pad(c, "smooth_quartz", "chiseled_quartz_block")

    def leg(steps, block, finish=False, **props):
        """Jumps along one side; the checkpoint (or finish) closes the side at the corner."""
        run = 0
        for step in steps:
            if step[0] == "ladder":
                _, distance, height, wall = step
                c.ladder(distance, height, wall, gap_below=distance > 1)
                run += distance + 1
            else:
                c.jump(*step, block, **props)
                run += step[0]
        remaining = side - run
        assert 3 <= remaining <= 5, remaining
        if finish:
            c.finish(remaining, 0, 0)
        else:
            c.checkpoint(remaining, 0, 0, "prismarine_bricks", "dark_prismarine", turn="left")

    leg([(3, 0, 0), (3, 1, 0), (3, 0, 1), (3, 0, -1), (4, 1, 0)], "quartz_pillar")
    leg([(3, 0, 0), ("ladder", 1, 4, "quartz_bricks"), (3, 0, 0), (3, 1, 0), (4, 0, 0)],
        "quartz_block")
    leg([(3, 0, 0), (3, 0, 0), (3, 0, 1), (3, 0, -1), (3, 1, 0)], "nether_brick_fence")
    leg([(3, 0, 0), ("ladder", 2, 4, "quartz_bricks"), (3, 0, 0), (3, 1, 1), (3, 0, -1)],
        "quartz_block")
    leg([(3, 1, 0), (3, 1, 0), (3, 0, -1), (4, 0, 1), (2, 1, 0)], "smooth_quartz_slab", type="top")
    leg([(3, 0, 0), ("ladder", 1, 5, "quartz_bricks"), (4, 0, 0), (3, 0, 1), (3, 1, -1)],
        "purpur_block")
    leg([(3, 1, 0), (3, 0, 1), (4, 0, -1), (3, 1, 0), (3, 0, 0)], "purpur_pillar", finish=True)

    floor_y = water_floor(s)

    # The tower in the middle rises from the pool to the finish, lit every few blocks.
    top = c.pos[1]
    for y in range(floor_y + 1, top + 1):
        for x in range(-2, 3):
            for z in range(-2, 3):
                if abs(x) == 2 or abs(z) == 2:
                    corner = abs(x) == 2 and abs(z) == 2
                    block = "quartz_pillar" if corner else "quartz_bricks"
                    if not corner and y % 6 == 0 and (x == 0 or z == 0):
                        block = "sea_lantern"
                    s.set((x, y, z), block)
    for x in range(-2, 3):
        for z in range(-2, 3):
            s.set((x, top + 1, z), "chiseled_quartz_block")
    return s, c.checkpoints


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in (("canopy", canopy), ("spire", spire)):
        structure, checkpoints = build()
        size = structure.save(os.path.join(OUT, name + ".nbt"))
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}, {checkpoints} checkpoints")


if __name__ == "__main__":
    main()
