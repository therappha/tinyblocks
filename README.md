# TinyBlocks

[![CI](https://github.com/therappha/tinyblocks/actions/workflows/ci.yml/badge.svg)](https://github.com/therappha/tinyblocks/actions/workflows/ci.yml)
[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE)

A mod for NeoForge 1.21.1. Craft a Minimizer with a stick and an iron nugget, hold it in your off-hand, then place any block to shrink it down — 1/2, 1/4, 1/8, or 1/16 the size of a normal block, all inside a single block space.

Shrunk-down blocks keep their own hitbox, mining time, crack animation, and drops, and behave like real blocks — redstone, pistons, and other interactions propagate between neighboring pieces just like in the full-size world.

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
    public BlockState renderState(Direction facing) {
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

The TinyBlocks creative tab holds the Minimizer (all four grid sizes) — craftable from a stick and an iron nugget. This is the only item you need: hold it in your off-hand and place *any* real vanilla block (piston, water bucket, crops, doors, hoppers, chests, redstone, ...) to shrink it down. Pieces run the block's own real vanilla logic against a fake position space, so behavior — animation, sound, neighbor propagation, container menus, growth, ticking — matches the full-size block, not a reimplementation of it.

```
/give @s tinyblocks:subgrid_block_2
/give @s tinyblocks:subgrid_block_4
/give @s tinyblocks:subgrid_block
/give @s tinyblocks:subgrid_block_16

/give @s tinyblocks:piece_remover_stick   # right-click a piece to remove it without vanilla mining (no flicker)
```

Commands:

- `/tinyblocks status` — inspect the SubgridBlock you're looking at
- `/tinyblocks highlight [radius]` — toggle cyan outlines on all SubgridBlocks in range (default radius 16)

---

## Testing

```
./gradlew test               # unit tests — pure logic (coordinate math, grid bookkeeping), seconds to run
./gradlew runGameTestServer   # GameTest — real headless server, actual pieces inside a real SubgridBlock
```

Unit tests cover the parts of the engine that don't need a running world. Everything else — a piece's real interaction, ticking, and neighbor-propagation behavior — only means anything against a live `ServerLevel`, which is what `runGameTestServer` boots headlessly and runs against.

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
