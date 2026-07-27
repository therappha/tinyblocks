# TinyBlocks

A NeoForge 1.21.1 subgrid engine that adds an invisible grid inside a single block space, letting other mods place and interact with scaled-down blocks at any even subdivision — 1/2, 1/4, 1/8, 1/16, or beyond.

## How it works

Clicking any solid block face with a placement item places a piece inside a SubgridBlock in the adjacent air space. Pieces have real collision, individual hitboxes, per-piece hardness, tool requirements, and drops. The crack animation and break particles target only the piece being mined.

## Debug items

TinyBlocks ships no creative tab. All items are for development and testing only, obtainable via `/give`:

| Item | Command |
|------|---------|
| Subgrid block (8×8×8) | `/give @s tinyblocks:subgrid_block` |
| Subgrid block (16×16×16) | `/give @s tinyblocks:subgrid_block_16` |
| Tiny Stone (places in 8³ grid) | `/give @s tinyblocks:tiny_stone_block` |
| Tiny Stone (places in 16³ grid) | `/give @s tinyblocks:tiny_stone_block_16` |

## For mod developers

`PieceDefinition` is the extension point. Register a new piece by subclassing it:

```java
public static final PieceDefinition MY_PIECE = new PieceDefinition(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "my_piece"),
        new Vec3i(1, 1, 1)) {

    @Override
    public BlockState renderState(Direction.Axis axis) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }
};
```

See [docs/developer-guide.md](docs/developer-guide.md) for the full API reference and examples.

## Commands

- `/tinyblocks status` — inspect the SubgridBlock you're looking at

## Dependencies

- NeoForge 21.1.x

## Building

```
./gradlew build
```

Run client: `./gradlew runClient`

## License

[CC BY-NC-SA 4.0](LICENSE) — free to use and modify, no commercial use, derivatives must use the same license.
