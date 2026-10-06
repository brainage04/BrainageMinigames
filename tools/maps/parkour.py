"""Generates the Parkour maps: python3 tools/maps/parkour.py (from the repository root).

Every course is a chain of jumps built by a small turtle: each jump names where the next block is
relative to the block the player stands on (forward, up, right), and the turtle refuses a jump a
sprinting player cannot make (see `legal_jump`). Checkpoints are 3x3 platforms with a
`region checkpoint_<n>` above them and a `point checkpoint_<n> <yaw>` marking where and which way
a player is put back; the course ends on a `region finish` platform. A course never descends more
than one block below its previous checkpoint, because the game sends anyone who drops five blocks
below their last checkpoint back to it; that rule is also what catches a missed jump, so a course
needs nothing underneath it.

Scenery is added after the course and never touches it: `Course.reserve` keeps the air a runner
needs above every block and along every jump free, and scenery a runner could land on from the
course is removed again, so the route stays the only way through.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/parkour"

HEADINGS = {(1, 0): "east", (0, 1): "south", (-1, 0): "west", (0, -1): "north"}

# Air kept free above a runner's block: a jump lifts the head 1.25 blocks above standing height.
HEADROOM = 4


def yaw_of(heading):
    hx, hz = heading
    return round(math.degrees(math.atan2(-hx, hz)))


def surface(block, props):
    """How far above the bottom of its block a runner stands on it."""
    if block.endswith(("_fence", "_wall")):
        return 1.5
    if block.endswith("_slab") and props.get("type", "bottom") == "bottom":
        return 0.5
    return 1.0


def legal_jump(distance, rise):
    """Whether a sprint jump covers `distance` blocks between block centres while rising `rise`.
    The longest are a 4-block gap on the level and a 3-block gap one block up."""
    if rise > 1.25:
        return False
    if rise > 0.5:
        return distance <= 4.1
    if rise > 0:
        return distance <= 4.6
    return distance <= 5.1


class Course:
    def __init__(self, structure, start, heading):
        self.s = structure
        self.pos = start
        self.h = heading
        self.top = start[1] + 1.0
        self.lowest = self.top - 1.0
        self.checkpoints = 0
        self.reserved = set()
        # Blocks of the sections before the last checkpoint, and of the current section.
        self.passed = []
        self.section = []

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

    def reserve(self, start, end, height=HEADROOM):
        """Keeps the air above both blocks and along the jump between them free of scenery."""
        (x1, y1, z1), (x2, y2, z2) = start, end
        steps = max(1, int(math.hypot(x2 - x1, z2 - z1) * 4))
        for i in range(steps + 1):
            t = i / steps
            x, z = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
            for cx in {math.floor(x + 0.5 - 0.4), math.floor(x + 0.5 + 0.4)}:
                for cz in {math.floor(z + 0.5 - 0.4), math.floor(z + 0.5 + 0.4)}:
                    for y in range(min(y1, y2) + 1, max(y1, y2) + height + 1):
                        self.reserved.add((cx, y, cz))

    def land(self, pos, block, props):
        """Moves onto `pos`, checking that the jump there is one a runner can make and that no
        block before the last checkpoint is close enough to jump to `pos` past it."""
        top = pos[1] + surface(block, props)
        distance = math.hypot(pos[0] - self.pos[0], pos[2] - self.pos[2])
        rise = top - self.top
        assert legal_jump(distance, rise), f"impossible jump to {pos}: {distance:.2f} far, {rise} up"
        assert top >= self.lowest - 1.0, f"{pos} is more than a block below the last checkpoint"
        for before, before_top in self.passed:
            assert not legal_jump(math.hypot(pos[0] - before[0], pos[2] - before[2]), top - before_top), \
                f"{pos} can be reached from {before}, skipping checkpoint {self.checkpoints}"
        self.reserve(self.pos, pos)
        self.pos, self.top = pos, top
        self.section.append((pos, top))

    def jump(self, forward, rise, right, block, **props):
        pos = self.offset(forward, rise, right)
        self.s.set(pos, block, **props)
        self.land(pos, block, props)
        return pos

    def jumps(self, steps, block, **props):
        for forward, up, right in steps:
            self.jump(forward, up, right, block, **props)

    def beam(self, length, block, **props):
        """A walk along `length` blocks straight ahead."""
        for _ in range(length):
            self.jump(1, 0, 0, block, **props)

    def ladder(self, distance, height, wall, gap_below=False, **wall_props):
        """A ladder `distance` blocks ahead on a wall; the player climbs it and stands on top.
        With `gap_below` it hangs in the air and has to be caught with a jump."""
        assert distance <= 3, "a ladder further away cannot be caught"
        start = self.pos
        for level in range(1, height + 1):
            self.s.set(self.offset(distance, level, 0), "ladder", facing=self.facing_back())
            self.s.set(self.offset(distance + 1, level, 0), wall, **wall_props)
        if not gap_below:
            self.s.set(self.offset(distance, 0, 0), wall, **wall_props)
        self.s.set(self.offset(distance + 1, 0, 0), wall, **wall_props)
        self.pos = self.offset(distance + 1, height, 0)
        self.top = self.pos[1] + 1.0
        self.reserve(start, self.offset(-1, -height, 0), height + HEADROOM)
        self.reserve(self.offset(-1, -height, 0), self.pos)

    def platform(self, center, block, rim, radius=1):
        cx, y, cz = center
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                edge = abs(dx) == radius or abs(dz) == radius
                self.s.set((cx + dx, y, cz + dz), rim if edge else block)

    def checkpoint(self, forward, up, right, block, rim, turn=None, post="oak_fence",
                   light="lantern"):
        self.checkpoints += 1
        n = self.checkpoints
        center = self.offset(forward, up, right)
        self.platform(center, block, rim)
        self.land(center, block, {})
        self.passed += self.section[:-1]
        self.section = []
        self.lowest = self.top - 1.0
        cx, y, cz = center
        self.reserve((cx - 1, y, cz - 1), (cx + 1, y, cz + 1))
        self.reserve((cx - 1, y, cz + 1), (cx + 1, y, cz - 1))
        if turn:
            self.turn(turn)
        self.s.marker((cx - 1, y + 1, cz - 1), f"region checkpoint_{n} 2 2 2")
        self.s.marker((cx, y + 1, cz), f"point checkpoint_{n} {yaw_of(self.h)}")
        # A lantern post on the outer corner makes checkpoints visible from afar.
        post_pos = (cx + 1, y + 1, cz + 1)
        self.s.set(post_pos, post)
        self.s.set((cx + 1, y + 2, cz + 1), light, **({"hanging": False} if light == "lantern" else {}))

    def finish(self, forward, up, right, pillar="quartz_pillar", light="sea_lantern"):
        center = self.offset(forward, up, right)
        cx, y, cz = center
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                checker = "gold_block" if (dx + dz) % 2 == 0 else "yellow_glazed_terracotta"
                self.s.set((cx + dx, y, cz + dz), checker)
        self.land(center, "gold_block", {})
        self.s.marker((cx - 2, y + 1, cz - 2), "region finish 4 2 4")
        self.s.marker((cx, y + 1, cz), f"point finish {yaw_of(self.h)}")
        for dx, dz in ((-3, -3), (-3, 3), (3, -3), (3, 3)):
            for level in range(0, 4):
                self.s.set((cx + dx, y + level, cz + dz), pillar)
            self.s.set((cx + dx, y + 4, cz + dz), light)


def start_pad(course, block, rim, width=8, rail="spruce_fence"):
    """Eight spawns side by side on a pad three blocks deep; the first jump leaves from the middle."""
    left = -(width // 2) + 1
    yaw = yaw_of(course.h)
    for forward in (-2, -1, 0):
        for right in range(left - 1, left + width + 1):
            edge = forward == -2 or right in (left - 1, left + width)
            course.s.set(course.offset(forward, 0, right), rim if edge else block)
            course.reserve(course.offset(forward, 0, right), course.offset(forward, 0, right))
    for team in range(1, width + 1):
        course.s.marker(course.offset(0, 1, left + team - 1), f"spawn {team} {yaw}")
    course.s.marker(course.offset(-1, 1, 0), f"lobby {yaw}")
    # A low rail behind and beside the pad keeps waiting players from wandering off.
    for right in range(left - 1, left + width + 1):
        course.s.set(course.offset(-2, 1, right), rail)
    for forward in (-1, 0):
        course.s.set(course.offset(forward, 1, left - 1), rail)
        course.s.set(course.offset(forward, 1, left + width), rail)


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


class Scenery:
    """Places blocks around a finished course without touching it or opening shortcuts."""

    # Scenery a runner can stand inside of; everything else blocks a place above it.
    SOLID_FREE = ("minecraft:fern", "minecraft:hanging_roots")
    FACING = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}

    def __init__(self, course):
        self.c = course
        self.s = course.s
        self.course_blocks = set(self.s.blocks)
        self.placed = set()
        # Every block a runner stands on, with the height they stand at.
        self.route = [(pos, pos[1] + surface(name.split(":")[1], dict(props)))
                      for pos, (name, props, _) in self.s.blocks.items()
                      if name not in ("minecraft:structure_block", "minecraft:ladder")]

    def set(self, pos, block, **props):
        pos = tuple(pos)
        if pos in self.c.reserved or pos in self.course_blocks:
            return False
        self.s.set(pos, block, **props)
        self.placed.add(pos)
        return True

    def standable(self, pos):
        if pos not in self.placed:
            return False
        name = self.s.blocks[pos][0]
        if name in self.SOLID_FREE:
            return False
        for up in (1, 2):
            above = self.s.get((pos[0], pos[1] + up, pos[2]))
            if above is not None and above not in self.SOLID_FREE:
                return False
        return True

    def reachable_from_route(self, pos, top=None):
        top = pos[1] + 1.0 if top is None else top
        for (x, y, z), route_top in self.route:
            distance = math.hypot(pos[0] - x, pos[2] - z)
            if distance <= 5.2 and route_top - 6.0 <= top <= route_top + 1.25:
                return True
        return False

    def supported(self, pos):
        """Whether an attached block still has what it hangs from or stands on."""
        x, y, z = pos
        name, props, _ = self.s.blocks[pos]
        if name == "minecraft:cocoa":
            dx, dz = self.FACING[dict(props)["facing"]]
            return self.s.get((x + dx, y, z + dz)) == "minecraft:jungle_log"
        if name in ("minecraft:fern", "minecraft:moss_carpet"):
            return self.s.get((x, y - 1, z)) is not None
        if name == "minecraft:hanging_roots":
            return self.s.get((x, y + 1, z)) is not None
        return True

    def prune(self):
        """Removes scenery a runner could land on from the course, and then anything that hung
        from or stood on it, until there is none."""
        while True:
            doomed = [p for p in self.placed
                      if (self.standable(p) and self.reachable_from_route(p)) or not self.supported(p)]
            if not doomed:
                return
            for pos in doomed:
                self.s.remove(pos)
                self.placed.discard(pos)


def leaf_blob(scenery, rng, cx, cy, cz, radius, height=2.6):
    for dx in range(-radius - 1, radius + 2):
        for dz in range(-radius - 1, radius + 2):
            for dy in range(-2, 3):
                d = math.hypot(dx, dz) / radius + abs(dy + 0.5) / height
                if d <= 1.0 + rng.uniform(-0.12, 0.08):
                    scenery.set((cx + dx, cy + dy, cz + dz), "jungle_leaves", persistent=True)


def jungle_tree(scenery, rng, x, z, bottom, top, crown):
    """A giant jungle tree growing from a floating islet of moss and roots: a 2x2 trunk with cocoa,
    two side branches with leaf clumps and a broad crown. Every block here survives block updates:
    hanging plants such as glow berries cannot hang from leaves and would drop as items."""
    # The islet: a mossy disc tapering down into hanging roots.
    for dx in range(-3, 5):
        for dz in range(-3, 5):
            d = math.hypot(dx - 0.5, dz - 0.5)
            if d > 3.4 + rng.uniform(-0.3, 0.3):
                continue
            scenery.set((x + dx, bottom, z + dz), "moss_block")
            depth = max(1, round(3.5 - d) + rng.randint(0, 1))
            for dy in range(1, depth + 1):
                scenery.set((x + dx, bottom - dy, z + dz), "rooted_dirt" if dy < depth else "dirt")
            if d > 2.2 and rng.random() < 0.5:
                scenery.set((x + dx, bottom - depth - 1, z + dz), "hanging_roots")
            elif d < 2.8 and rng.random() < 0.25:
                scenery.set((x + dx, bottom + 1, z + dz), "fern" if rng.random() < 0.6 else "moss_carpet")
    for y in range(bottom + 1, top + 1):
        for dx in (0, 1):
            for dz in (0, 1):
                scenery.set((x + dx, y, z + dz), "jungle_log", axis="y")
    for _ in range(4):
        y = rng.randint(bottom + 3, top - 2)
        side = rng.choice([((-1, 0), "east"), ((2, 0), "west"), ((0, -1), "south"), ((0, 2), "north")])
        (ox, oz), facing = side
        shift = rng.randint(0, 1)
        cx, cz = (x + ox, z + shift) if ox else (x + shift, z + oz)
        scenery.set((cx, y, cz), "cocoa", facing=facing, age=2)
    # Two branches reach out below the crown, each ending in a clump of leaves.
    for direction in rng.sample([(1, 0), (-1, 0), (0, 1), (0, -1)], 2):
        y = top - rng.randint(2, 4)
        length = rng.randint(3, 4)
        ox = x + (1 if direction[0] > 0 else 0)
        oz = z + (1 if direction[1] > 0 else 0)
        axis = "x" if direction[0] else "z"
        for i in range(1, length + 1):
            scenery.set((ox + direction[0] * i, y, oz + direction[1] * i), "jungle_log", axis=axis)
        leaf_blob(scenery, rng, ox + direction[0] * (length + 1), y + 1, oz + direction[1] * (length + 1), 2, 1.6)
    leaf_blob(scenery, rng, x, top + 1, z, crown)


def canopy():
    """Six sections through the crowns of a jungle, heading east and north and rising all the way:
    branches, trunk ladders, fence posts, bamboo steps, leaf leaps and a summit climb."""
    s = Structure()
    c = Course(s, (0, 20, 0), (1, 0))
    start_pad(c, "jungle_planks", "stripped_jungle_log", rail="jungle_fence")
    deck = dict(block="moss_block", rim="stripped_jungle_log", post="jungle_fence")

    # 1, Branch walk: warm-up hops along stripped branches and a short beam.
    log = dict(block="stripped_jungle_log", axis="x")
    c.jumps([(3, 0, 0), (3, 0, 0)], **log)
    c.jump(3, 0, 1, **log)
    c.beam(3, **log)
    c.jumps([(3, 1, 0), (3, 0, -1), (4, 0, 0), (3, 1, 0)], **log)
    c.checkpoint(3, 0, 0, **deck)

    # 2, Temple ruins: a ladder up a trunk, then a ladder hanging over the gap.
    ruin = "mossy_stone_bricks"
    c.jump(3, 0, 0, ruin)
    c.ladder(1, 5, "jungle_log", axis="y")
    c.jumps([(3, 0, 0), (3, 0, 2)], "chiseled_stone_bricks")
    c.ladder(2, 4, "jungle_log", gap_below=True, axis="y")
    c.jumps([(3, 0, 0), (3, 0, -2)], ruin)
    c.checkpoint(4, 0, 0, turn="left", **deck)

    # 3, Fence posts: small targets, with a slab in the middle.
    for step in [(4, 0, 0), (3, 0, 1), (3, 0, -1), (3, 0, 0)]:
        c.jump(*step, "jungle_fence")
    c.jump(3, 1, 0, "jungle_slab", type="bottom")
    c.jumps([(3, 0, 1), (3, 0, 0)], "jungle_fence")
    c.jump(3, 0, -1, "jungle_planks")
    c.checkpoint(4, 0, 0, turn="right", **deck)

    # 4, Bamboo steps: short climbing hops left and right, then a long diagonal.
    bamboo = "bamboo_mosaic"
    c.jumps([(4, 1, 0), (2, 1, 1), (2, 1, -1), (3, 0, 0), (4, 0, 1), (3, 1, 0), (2, 1, -1)], bamboo)
    c.checkpoint(4, 0, 0, turn="left", **deck)

    # 5, Leaf leaps: the longest gaps of the course, ending with a gap of three one block up.
    leaves = dict(block="jungle_leaves", persistent=True)
    c.jumps([(4, 0, 0), (3, 1, 1), (4, 0, -1), (3, 1, 0), (4, 0, 0), (3, 0, 2)], **leaves)
    c.jump(4, 1, 0, "moss_block")
    c.jump(3, 0, -1, **leaves)
    c.checkpoint(4, 0, 0, turn="right", **deck)

    # 6, Summit: mossy wall posts, a tall trunk ladder and the run onto the finish deck.
    for step in [(4, 0, 0), (3, 0, 1), (3, 0, -1)]:
        c.jump(*step, "mossy_cobblestone_wall", up="true")
    c.jump(3, 0, 0, ruin)
    c.ladder(1, 6, "jungle_log", axis="y")
    c.jumps([(3, 0, 0), (3, 1, 1)], **log)
    c.finish(4, 0, -1, pillar="stripped_jungle_log", light="ochre_froglight")

    # Trees grow from islets well below the course, so nobody lands on one, and spread their
    # crowns well above it.
    scenery = Scenery(c)
    rng = random.Random(4417)
    xs = [p[0] for p, _ in scenery.route]
    zs = [p[2] for p, _ in scenery.route]
    trees = []
    for _ in range(600):
        if len(trees) >= 18:
            break
        x = rng.randint(min(xs) - 6, max(xs) + 4)
        z = rng.randint(min(zs) - 6, max(zs) + 4)
        near = min((math.hypot(x + 0.5 - p[0], z + 0.5 - p[2]), t) for p, t in scenery.route)
        if not 3.5 <= near[0] <= 9 or any(math.hypot(x - tx, z - tz) < 7 for tx, tz in trees):
            continue
        top = int(near[1]) + rng.randint(7, 10)
        bottom = int(near[1]) - rng.randint(9, 12)
        if any((x + dx, y, z + dz) in c.reserved or (x + dx, y, z + dz) in s.blocks
               for dx in (0, 1) for dz in (0, 1) for y in range(bottom, top + 1)):
            continue
        jungle_tree(scenery, rng, x, z, bottom, top, rng.randint(5, 7))
        trees.append((x, z))
    scenery.prune()
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
    leg([(4, 0, 0), ("ladder", 1, 4, "quartz_bricks"), (3, 0, 0), (3, 1, 0), (4, 0, 0)],
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
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}, {len(structure.blocks)} blocks, "
              f"{checkpoints} checkpoints")


if __name__ == "__main__":
    main()
