"""Generates the Ice Boat Racing tracks: python3 tools/maps/ice_boat_racing.py (from the repo root).

A track is a closed centre line sampled every half block. Every cell within half the track width
of the line is ice, the ring just outside it is a wall (a red and white kerb under a glass rail, so
drivers can see over it), and the rest of the map is snow. Checkpoint gates are square
`region checkpoint_<n>` boxes centred on the line, spanning the whole track and four blocks high,
each with a `point checkpoint_<n> <yaw>` facing along the track where a driver who left their boat
gets a new one. The start grid of eight `point boat_<n>` slots (two columns, staggered) sits just
behind the `region finish` gate, with a `spawn <n>` under each slot.

Course design follows what ice boat racers build: packed ice everywhere (fast, but boats still
steer), blue ice only on long straights where the extra speed is a reward for a clean exit, turns
wide enough to drift through, and walls high enough that a boat cannot climb them.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structure import Structure  # noqa: E402

OUT = "common/src/main/resources/data/brainage_minigames/structure/maps/ice_boat_racing"
SLOTS = 8
GATE_HEIGHT = 4


def catmull_rom(points, spacing=0.5):
    """A closed, smooth curve through `points`, sampled roughly every `spacing` blocks."""
    samples = []
    n = len(points)
    for i in range(n):
        p0, p1, p2, p3 = (points[(i + k - 1) % n] for k in range(4))
        length = math.dist(p1, p2)
        steps = max(1, int(length / spacing))
        for step in range(steps):
            t = step / steps
            t2, t3 = t * t, t * t * t
            samples.append(tuple(
                0.5 * (2 * p1[a] + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2
                       + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3)
                for a in range(2)))
    return samples


def stadium(straight, radius, spacing=0.5):
    """An oval: two straights along x joined by half circles, driven anticlockwise seen from above
    (east along the south straight)."""
    samples = []
    half = straight / 2
    for i in range(int(straight / spacing)):
        samples.append((-half + i * spacing, radius))
    arc = int(math.pi * radius / spacing)
    for i in range(arc):
        a = math.pi / 2 - math.pi * i / arc
        samples.append((half + radius * math.cos(a), radius * math.sin(a)))
    for i in range(int(straight / spacing)):
        samples.append((half - i * spacing, -radius))
    for i in range(arc):
        a = -math.pi / 2 - math.pi * i / arc
        samples.append((-half + radius * math.cos(a), radius * math.sin(a)))
    return samples


def yaw_at(samples, index):
    ax, az = samples[(index - 2) % len(samples)]
    bx, bz = samples[(index + 2) % len(samples)]
    return round(math.degrees(math.atan2(-(bx - ax), bz - az)))


def nearest_index(samples, point):
    return min(range(len(samples)), key=lambda i: math.dist(samples[i], point))


def check_clearance(samples, half_width):
    """Parts of the track that are far apart along the line must be far apart on the ground too,
    so a wall always separates them."""
    n = len(samples)
    needed = 2 * half_width + 3
    window = int(needed * 4)
    for i in range(0, n, 4):
        for j in range(0, n, 4):
            along = min(abs(i - j), n - abs(i - j))
            if along > window * 2 and math.dist(samples[i], samples[j]) < needed:
                raise ValueError(f"track too close to itself near {samples[i]} and {samples[j]}")


def build(samples, half_width, gates, finish_at, blue, name):
    """`gates` are points on the line in driving order; `finish_at` is the finish line point;
    `blue` lists (from, to) points on the line between which the ice is blue."""
    check_clearance(samples, half_width)
    n = len(samples)
    s = Structure()

    # Distance from every nearby cell centre to the line, and the nearest sample.
    reach = half_width + 1.5
    best = {}
    for index, (sx, sz) in enumerate(samples):
        for x in range(math.floor(sx - reach), math.ceil(sx + reach) + 1):
            for z in range(math.floor(sz - reach), math.ceil(sz + reach) + 1):
                d = math.dist((x + 0.5, z + 0.5), (sx, sz))
                if d <= reach and ((x, z) not in best or d < best[(x, z)][0]):
                    best[(x, z)] = (d, index)

    blue_ranges = [(nearest_index(samples, a), nearest_index(samples, b)) for a, b in blue]

    def is_blue(index):
        for a, b in blue_ranges:
            if (a <= index <= b) if a <= b else (index >= a or index <= b):
                return True
        return False

    xs = [c[0] for c in best]
    zs = [c[1] for c in best]
    x1, x2, z1, z2 = min(xs) - 3, max(xs) + 3, min(zs) - 3, max(zs) + 3
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            d, index = best.get((x, z), (math.inf, None))
            if d <= half_width:
                s.set((x, 0, z), "blue_ice" if is_blue(index) else "packed_ice")
            elif d <= half_width + 1.3:
                s.set((x, 0, z), "stone_bricks")
                lamp = (x * 7 + z * 13) % 23 == 0
                kerb = "red_concrete" if (x + z) // 2 % 2 == 0 else "white_concrete"
                s.set((x, 1, z), "sea_lantern" if lamp else kerb)
                s.set((x, 2, z), "light_blue_stained_glass")
            else:
                s.set((x, 0, z), "snow_block")
                # Sparse spruce trees away from the track.
                if d > half_width + 5 and (x * 31 + z * 17) % 97 == 0:
                    spruce(s, x, z)

    # Checkpoint gates, then the finish, each a box across the whole track.
    def gate(point, region_name, point_name):
        index = nearest_index(samples, point)
        cx, cz = samples[index]
        bx, bz = math.floor(cx), math.floor(cz)
        r = math.ceil(half_width)
        # The region marker sits in the air at the top corner, clear of the walls.
        s.marker((bx - r, GATE_HEIGHT, bz - r), f"region {region_name} {2 * r} {1 - GATE_HEIGHT} {2 * r}")
        s.marker((bx, 1, bz), f"point {point_name} {yaw_at(samples, index)}")
        return index

    for number, point in enumerate(gates, start=1):
        gate(point, f"checkpoint_{number}", f"checkpoint_{number}")
    finish_index = gate(finish_at, "finish", "finish")
    finish_arch(s, samples, finish_index, half_width)

    # The grid: two staggered columns behind the finish line, three blocks apart along the track.
    for slot in range(SLOTS):
        index = (finish_index - 10 - slot * 6) % n
        cx, cz = samples[index]
        tx, tz = direction(samples, index)
        side = (-tz, tx)
        offset = (half_width / 2) * (1 if slot % 2 == 0 else -1)
        x = math.floor(cx + side[0] * offset)
        z = math.floor(cz + side[1] * offset)
        yaw = yaw_at(samples, index)
        s.marker((x, 1, z), f"spawn {slot + 1} {yaw}")
        s.marker((x, 2, z), f"point boat_{slot + 1} {yaw}")
    lobby = samples[(finish_index - 10 - SLOTS * 6) % n]
    s.marker((math.floor(lobby[0]), 1, math.floor(lobby[1])),
             f"lobby {yaw_at(samples, (finish_index - 10 - SLOTS * 6) % n)}")

    size = s.save(os.path.join(OUT, name + ".nbt"))
    length = sum(math.dist(samples[i], samples[(i + 1) % n]) for i in range(n))
    print(f"{name}: {size[0]}x{size[1]}x{size[2]}, lap {length:.0f} blocks, {len(gates)} checkpoints")


def direction(samples, index):
    ax, az = samples[(index - 2) % len(samples)]
    bx, bz = samples[(index + 2) % len(samples)]
    length = math.dist((ax, az), (bx, bz))
    return (bx - ax) / length, (bz - az) / length


def finish_arch(s, samples, index, half_width):
    """Two quartz posts outside the walls and a black and white chequered beam over the line."""
    cx, cz = samples[index]
    tx, tz = direction(samples, index)
    side = (-tz, tx)
    reach = half_width + 2.5
    cells = []
    for step in range(-int(reach * 2), int(reach * 2) + 1):
        cell = (math.floor(cx + side[0] * step / 2), math.floor(cz + side[1] * step / 2))
        if cell not in cells:
            cells.append(cell)
    for x, z in (cells[0], cells[-1]):
        for y in range(1, 7):
            s.set((x, y, z), "quartz_pillar")
    for i, (x, z) in enumerate(cells):
        s.set((x, 7, z), "black_concrete" if i % 2 == 0 else "white_concrete")
        s.set((x, 6, z), "white_concrete" if i % 2 == 0 else "black_concrete")


def spruce(s, x, z):
    for y in range(1, 5):
        s.set((x, y, z), "spruce_log", axis="y")
    for y, r in ((3, 2), (4, 1), (5, 1), (6, 0)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if (dx, dz) != (0, 0) or y > 4:
                    if abs(dx) + abs(dz) <= r + 1 and s.get((x + dx, y, z + dz)) is None:
                        s.set((x + dx, y, z + dz), "spruce_leaves", persistent=True)


def frostbite_oval():
    """A 70-block straight each way joined by half circles of radius 20: flat out on blue ice down
    the back straight, then a long drift through each turn. Width 9."""
    samples = stadium(70, 20)
    build(
        samples,
        half_width=4.5,
        gates=[(55, 0), (0, -20), (-55, 0)],
        finish_at=(0, 20),
        blue=[((25, -20), (-25, -20))],
        name="frostbite_oval",
    )


def glacier_circuit():
    """A technical circuit, width 7: a blue-ice main straight into a fast right-hand sweeper, a
    hairpin, a run back west through a series of S bends and a long left-hander home."""
    points = [
        (0, 0), (30, 0), (60, 0),  # main straight, east
        (82, 6), (92, 24),  # sweeper, turning south
        (92, 42), (84, 54), (72, 46),  # hairpin, back north
        (70, 30), (60, 20),  # kink west
        (48, 26), (38, 36), (26, 28), (14, 36),  # S bends
        (0, 30), (-14, 20), (-16, 8),  # long left-hander home
    ]
    samples = catmull_rom(points)
    build(
        samples,
        half_width=3.5,
        gates=[(92, 24), (84, 54), (60, 20), (26, 28), (-14, 20)],
        finish_at=(24, 0),
        blue=[((26, 0), (58, 0))],
        name="glacier_circuit",
    )


def main():
    os.makedirs(OUT, exist_ok=True)
    frostbite_oval()
    glacier_circuit()


if __name__ == "__main__":
    main()
