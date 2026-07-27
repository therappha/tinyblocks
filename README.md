# TinyGears

A NeoForge 1.21.1 mod that adds a **subgrid engine** — an invisible 8×8×8 grid that lives inside a single block space, allowing tiny pieces to be placed, connected, and interacted with at 1/8 scale.

## Concept

Clicking any solid block face with a Tiny item places a piece inside an invisible **SubgridBlock** in the adjacent air space. Pieces have real collision, real hitboxes, and can be broken individually. Multiple SubgridBlocks can connect seamlessly at their boundaries.

## Current pieces

| Item | Description |
|------|-------------|
| Tiny Stone Block | 1×1×1 decorative piece |

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

Kinetic pieces override `ports(Direction.Axis axis)` to expose `KineticPort` connections.

## Commands

- `/tinygears status` — inspect the SubgridBlock you're looking at
- `/tinygears help` — list commands

## Dependencies

- NeoForge 21.1.244
- Create 6.0.10-280
- Flywheel 1.0.6 (runtime)

## Building

```
./gradlew build
```

Run client: `./gradlew runClient` (do NOT use IntelliJ Run with Coverage — causes a LinkageError).
