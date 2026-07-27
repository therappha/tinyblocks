# TinyBlocks

A NeoForge 1.21.1 mod that adds a **subgrid engine** — place and interact with blocks at 1/8 or 1/16 scale inside a single block space.

## How it works

Clicking any solid block face with a Tiny item places a piece inside an invisible **SubgridBlock** in the adjacent air space. Pieces have real collision, individual hitboxes, and can be broken one by one. The crack animation and break particles target only the piece being mined.

## Current pieces

| Item | Description |
|------|-------------|
| Tiny Furnace | 1×1×1 functional furnace with smelting, fuel, and GUI |

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

    @Override
    public float destroyTime() { return 5f; }

    @Override
    public boolean requiresCorrectTool() { return true; }

    @Override
    public List<ItemStack> drops(PlacedPiece piece) {
        return List.of(new ItemStack(Items.IRON_INGOT));
    }
};
```

For interactive pieces, override `onUse()`, `requiresTick()` + `tick()`, `onLoaded()`, and `onSaving()` to manage GUI, state, and NBT persistence.

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
