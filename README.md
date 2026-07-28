# TinyBlocks

[![CI](https://github.com/therappha/tinyblocks/actions/workflows/ci.yml/badge.svg)](https://github.com/therappha/tinyblocks/actions/workflows/ci.yml)
[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE)

A **subgrid engine** for NeoForge 1.21.1. Adds an invisible grid inside any single block space, letting mods place and interact with scaled-down blocks at 1/2, 1/4, 1/8, 1/16 — or any even subdivision.

TinyBlocks ships no playable content of its own. It is a foundation other mods build on.

---

## How it works

Right-clicking a solid block face with a placement item places a piece inside a **SubgridBlock** in the adjacent air space. Each piece has:

- Real collision and individual hitboxes
- Per-piece hardness, tool requirements, and drops
- Crack animation and break particles scoped to the targeted piece only
- Right-click interaction (GUI, state changes — defined by the piece)
- Neighbor propagation between adjacent pieces in the same subgrid

SubgridBlocks come in four grid sizes:

| Size | Scale | Block |
|------|-------|-------|
| 2×2×2 | 1/2 | `tinyblocks:subgrid_block_2` |
| 4×4×4 | 1/4 | `tinyblocks:subgrid_block_4` |
| 8×8×8 | 1/8 | `tinyblocks:subgrid_block` |
| 16×16×16 | 1/16 | `tinyblocks:subgrid_block_16` |

---

## For mod developers

Implement `PieceDefinition` to register a custom piece:

```java
public static final PieceDefinition MY_PIECE = new PieceDefinition(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "my_piece"),
        new Vec3i(1, 1, 1)) {

    @Override
    public BlockState renderState(Direction.Axis axis) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }

    @Override public float destroyTime() { return 5f; }
    @Override public boolean requiresCorrectTool() { return true; }

    @Override
    public List<ItemStack> drops(PlacedPiece piece) {
        return List.of(new ItemStack(Items.IRON_INGOT));
    }
};
```

The constructor auto-registers the definition — no registry call needed. For interactive pieces with GUI, ticking, and NBT persistence, see the full API reference:

→ **[docs/developer-guide.md](docs/developer-guide.md)**

---

## Debug commands & items

TinyBlocks has no creative tab. All items are debug-only, obtainable via `/give`:

```
/give @s tinyblocks:subgrid_block_2
/give @s tinyblocks:subgrid_block_4
/give @s tinyblocks:subgrid_block
/give @s tinyblocks:subgrid_block_16

/give @s tinyblocks:tiny_stone_block_2
/give @s tinyblocks:tiny_stone_block_4
/give @s tinyblocks:tiny_stone_block
/give @s tinyblocks:tiny_stone_block_16
```

Commands:

- `/tinyblocks status` — inspect the SubgridBlock you're looking at
- `/tinyblocks highlight [radius]` — toggle cyan outlines on all SubgridBlocks in range (default radius 16)

---

## Building

```
./gradlew build       # build jar
./gradlew runClient   # launch dev client
```

Requires JDK 21 and NeoForge 21.1.x.

---

## License

[CC BY-NC-SA 4.0](LICENSE) — free to use and modify, no commercial use, derivatives must use the same license.
