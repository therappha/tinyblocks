# TinyBlocks

A NeoForge 1.21.1 mod that adds a **subgrid engine** — place and interact with blocks at 1/8 or 1/16 scale inside a single block space.

## How it works

Clicking any solid block face with a Tiny item places a piece inside an invisible **SubgridBlock** in the adjacent air space. Pieces have real collision, individual hitboxes, and can be broken one by one. The crack animation and particles target only the piece being mined.

## Items

| Item | Description |
|------|-------------|
| Tiny Furnace | 1×1×1 functional furnace with smelting, fuel, and GUI |

## For mod developers

TinyBlocks exposes a `PieceDefinition` API so any mod can add custom tiny blocks with their own rendering, hardness, drops, NBT persistence, ticking, and GUI — without touching TinyBlocks internals.

See [docs/developer-guide.md](docs/developer-guide.md) for the full API reference and examples.

Quick example:

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
