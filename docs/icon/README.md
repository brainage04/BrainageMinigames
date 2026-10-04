# BrainageMinigames icon

## What this is

`docs/icon/icon.png` — the mod's icon: 512x512 PNG, 8-bit RGBA, non-interlaced, 2 925 bytes,
sha256 `75bdfc58ceceff5d634406c9495b77beb10a9d234a537694ae1edca245cef99a`.
It is the native 32x32 composition described below enlarged **16x with NEAREST** using
Pillow 12.3.0, preserving every source pixel without interpolation. The mod ships
byte-identical copies at `common/src/main/resources/assets/brainage_minigames/icon.png`
and `fabric/src/gametest/resources/assets/brainage_minigames/icon.png`.

## How it was made

**Generated pixel art derived from real vanilla textures and a supplied player skin** —
no renderer, no ML, no antialiasing. `provenance/pixel2/render.py` (Pillow 12.3.0) composes
it deterministically with integer/nearest-neighbour operations only:

1. `diamond_sword` (16x16 vanilla item texture) scaled **2x with NEAREST** onto the 32x32 canvas.
2. `iron_sword` (16x16 vanilla item texture) transposed **90 deg counter-clockwise** (exact
   rotation, no interpolation) and scaled 2x over it.
3. The player head front — the 8x8 base face plus the 8x8 hat overlay, alpha-composited from
   the supplied skin — scaled 2x to 16x16 and pasted centred at (8,8), i.e. **in front of
   everything**.

Source textures:

| texture | origin | sha256 |
|---|---|---|
| `provenance/pixel/sources/supplied-skin.png` | supplied player skin (recorded in the round-3 provenance as `/home/thomas/Downloads/6b252bfa5cbcdd98.png`); not a Mojang asset | `e9ebbeece495d9c96040e235dc865fdb1a530cf6a2243a6c8fcec22e72f03e3f` |
| `provenance/pixel/sources/diamond_sword.png` | Java **1.21.4** client jar, `assets/minecraft/textures/item/diamond_sword.png` | `5634a8cb79a70beb306210de9ec492fc2d4dc8f739372683e9882ef06bf63008` |
| `provenance/pixel/sources/iron_sword.png` | Java **1.21.4** client jar, `assets/minecraft/textures/item/iron_sword.png` | `ed1fa2f83955583e70a19791455d13989e8bd93b1d7240e775a57141022bed6b` |

No shader pack, no Minecraft capture, no camera: the swords are the plain item sprites and
the head is the supplied skin's front face. Re-extract the jar textures with:

```
unzip -p client-1.21.4.jar assets/minecraft/textures/item/diamond_sword.png > diamond_sword.png
unzip -p client-1.21.4.jar assets/minecraft/textures/item/iron_sword.png    > iron_sword.png
```

`provenance/pixel/source-provenance.json` records every source file with its jar member and
sha256 (and which ones this icon does *not* use).

## Provenance files

| file | what it is |
|---|---|
| `provenance/pixel2/render.py` | the author script that generates the native composition and its shipped 512x512 enlargement (and the other round-3 pixel icons) |
| `provenance/pixel2/metadata.json` | this icon's entry extracted from `provenance/from-round3/pixel2/manifest.json`: label, method, source line, notes |
| `provenance/pixel/sources/*.png` | the textures the script reads (the three listed above are the ones this icon uses; the rest are the sibling pixel icons' inputs, kept so the script runs unmodified) |
| `provenance/pixel/source-provenance.json` | jar member / sha256 / origin per source file, and which files this icon uses |
| `provenance/pixel2/sources/*.png` | the three extra client-jar sprites the shared script needs for its AcceleratedDamage section — not used by this icon |

## How to regenerate

From `docs/icon/provenance` (Pillow 12.3.0):

```
python3 pixel2/render.py
```

This rewrites `pixel2/brainage-minigames.png` (the native 32x32 composition, sha256
`94f0b2faa254c9f064b13bc9a2abd2bcc1fab4f6df208d68d2d47b55c7f3aff3`) and
`pixel2/brainage-minigames-512.png` (the shipped 512x512 icon), plus the other round-3
pixel icons (including `get-enchant-info.png`, a retired candidate; ignore it).
Compare `pixel2/brainage-minigames-512.png` with the shipped sha256 above, then copy
it to all three shipped paths listed in “What this is”.

## Notes

- The head is the skin's **front face only**, with the hat layer composited over it; the skin
  file is included so the composition stays reproducible.
- The composition is drawn on its native 32x32 pixel grid, then enlarged 16x with
  NEAREST, so no antialiasing is expected and none is present.
- Not copied: the other candidate variants of this and other mods' icons, the retired
  `get-enchant-info.png`, and the 28 MB `pixel2/jar/client-1.21.4.jar` (over the 5 MB
  single-file limit; the shipped textures are enough to regenerate this icon).

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
