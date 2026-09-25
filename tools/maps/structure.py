"""Builds vanilla structure templates (.nbt) for Brainage Minigames maps.

A map is an ordinary structure template, so it can also be loaded, edited and re-saved in game with
a structure block. Markers are DATA-mode structure blocks whose metadata the mod reads when the map
is pasted (see README "Maps"); they are replaced by air.

Usage:
    from structure import Structure
    s = Structure()
    s.fill((0, 0, 0), (20, 0, 20), "minecraft:stone")
    s.set((3, 1, 3), "minecraft:oak_stairs", facing="east")
    s.set((5, 1, 5), "minecraft:chest", nbt={"LootTable": "brainage_minigames:skywars/mid"})
    s.marker((10, 1, 2), "spawn 1")
    s.save("common/src/main/resources/data/brainage_minigames/structure/maps/bridge/example.nbt")

Positions are (x, y, z) with y up; the template's origin is its minimum corner, and the size is the
bounding box of everything set (negative coordinates are shifted automatically on save).
"""

import gzip
import io
import os
import struct

DATA_VERSION = 4903  # Minecraft 26.2 world_version


class Int(int):
    """Forces TAG_Int for a Python int (default for ints)."""


class Byte(int):
    pass


class Short(int):
    pass


class Long(int):
    pass


class Float(float):
    pass


class Double(float):
    """Default for Python floats."""


def _write_payload(out, value):
    if isinstance(value, bool) or isinstance(value, Byte):
        out.write(struct.pack(">b", int(value)))
    elif isinstance(value, Short):
        out.write(struct.pack(">h", value))
    elif isinstance(value, Long):
        out.write(struct.pack(">q", value))
    elif isinstance(value, int):
        out.write(struct.pack(">i", value))
    elif isinstance(value, Float):
        out.write(struct.pack(">f", value))
    elif isinstance(value, float):
        out.write(struct.pack(">d", value))
    elif isinstance(value, str):
        data = value.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif isinstance(value, dict):
        for key, item in value.items():
            out.write(bytes([_tag(item)]))
            _write_payload(out, key)
            _write_payload(out, item)
        out.write(b"\x00")
    elif isinstance(value, list):
        element = _tag(value[0]) if value else 0
        out.write(bytes([element]))
        out.write(struct.pack(">i", len(value)))
        for item in value:
            _write_payload(out, item)
    else:
        raise TypeError(f"Unsupported NBT value {value!r}")


def _tag(value):
    if isinstance(value, bool) or isinstance(value, Byte):
        return 1
    if isinstance(value, Short):
        return 2
    if isinstance(value, Long):
        return 4
    if isinstance(value, int):
        return 3
    if isinstance(value, Float):
        return 5
    if isinstance(value, float):
        return 6
    if isinstance(value, str):
        return 8
    if isinstance(value, list):
        return 9
    if isinstance(value, dict):
        return 10
    raise TypeError(f"Unsupported NBT value {value!r}")


def write_nbt(path, root):
    buffer = io.BytesIO()
    buffer.write(b"\x0a")
    _write_payload(buffer, "")
    _write_payload(buffer, root)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with gzip.GzipFile(path, "wb", mtime=0) as file:
        file.write(buffer.getvalue())


class Structure:
    def __init__(self):
        self.blocks = {}  # (x, y, z) -> (name, properties tuple, nbt or None)

    def set(self, pos, name, nbt=None, **properties):
        """Sets one block. Property values are written as strings ("true", "east", "3")."""
        if ":" not in name:
            name = "minecraft:" + name
        props = tuple(sorted((key, str(value).lower() if isinstance(value, bool) else str(value))
                             for key, value in properties.items()))
        self.blocks[tuple(pos)] = (name, props, nbt)

    def fill(self, start, end, name, nbt=None, **properties):
        (x1, y1, z1), (x2, y2, z2) = start, end
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set((x, y, z), name, nbt, **properties)

    def remove(self, pos):
        self.blocks.pop(tuple(pos), None)

    def get(self, pos):
        entry = self.blocks.get(tuple(pos))
        return entry[0] if entry else None

    def marker(self, pos, metadata):
        """A DATA structure block the mod turns into a spawn, point, region or other marker."""
        self.set(pos, "minecraft:structure_block", mode="data",
                 nbt={"mode": "DATA", "metadata": metadata})

    def save(self, path, pad_air=False):
        """Writes the template. With pad_air, empty cells inside the bounds become explicit air,
        so pasting the map clears whatever was there; the mod clears the area itself, so this is
        normally unnecessary."""
        if not self.blocks:
            raise ValueError("empty structure")
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        ox, oy, oz = min(xs), min(ys), min(zs)
        size = [max(xs) - ox + 1, max(ys) - oy + 1, max(zs) - oz + 1]
        entries = dict(self.blocks)
        if pad_air:
            for x in range(size[0]):
                for y in range(size[1]):
                    for z in range(size[2]):
                        entries.setdefault((x + ox, y + oy, z + oz), ("minecraft:air", (), None))
        palette, index, blocks = [], {}, []
        for pos in sorted(entries, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = entries[pos]
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                state = {"Name": name}
                if props:
                    state["Properties"] = {k: v for k, v in props}
                palette.append(state)
            block = {"pos": [pos[0] - ox, pos[1] - oy, pos[2] - oz], "state": index[key]}
            if nbt is not None:
                block["nbt"] = dict(nbt)
                block["nbt"].setdefault("id", _block_entity_id(name))
            blocks.append(block)
        write_nbt(path, {
            "DataVersion": DATA_VERSION,
            "size": size,
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        })
        return size


def _block_entity_id(name):
    # Most block entity ids equal the block id; a few differ.
    special = {
        "minecraft:trapped_chest": "minecraft:trapped_chest",
        "minecraft:oak_sign": "minecraft:sign",
        "minecraft:oak_wall_sign": "minecraft:sign",
    }
    return special.get(name, name)
