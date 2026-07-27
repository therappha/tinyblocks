# TinyBlocks — Developer Guide

TinyBlocks exposes a `PieceDefinition` API so any mod can register its own tiny blocks with custom rendering, hardness, drops, and interactive state — without touching TinyBlocks' internals.

## Adding TinyBlocks as a dependency

Once published on Modrinth:

```groovy
// build.gradle
repositories {
    maven { url "https://api.modrinth.com/maven" }
}

dependencies {
    compileOnly "maven.modrinth:tinyblocks:1.0.0"
}
```

At runtime the player installs TinyBlocks normally. Use `compileOnly` so it's not bundled into your jar.

---

## Creating a PieceDefinition

Subclass `PieceDefinition` and give it a unique `ResourceLocation` and a footprint size (in grid cells):

```java
public static final PieceDefinition MY_PIECE = new PieceDefinition(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "my_piece"),
        new Vec3i(1, 1, 1))   // width × height × depth in grid cells
{
    @Override
    public BlockState renderState(Direction.Axis axis) {
        // The block whose model and texture will be rendered at mini scale.
        return Blocks.IRON_BLOCK.defaultBlockState();
    }
};
```

The constructor registers the definition automatically — no registry call needed.
Make sure the class is loaded at mod startup (e.g. assign it to a `public static final` field in your mod class or a dedicated `Definitions` class, and reference it in your mod constructor).

---

## Hooks reference

All hooks have default no-op implementations. Override only what you need.

### Appearance

| Method | Default | Description |
|--------|---------|-------------|
| `renderState(Axis axis)` | **abstract** | Block whose model is rendered at mini scale. `axis` is the placement axis if your block is directional. |

### Mining

| Method | Default | Description |
|--------|---------|-------------|
| `destroyTime()` | `1.5f` | Hardness in the same units as vanilla (`BlockBehaviour.Properties.strength()`). `-1` = unbreakable. |
| `requiresCorrectTool()` | `false` | If `true`, wrong tool deals 3× reduced progress (same as vanilla). |
| `drops(PlacedPiece piece)` | `List.of()` | Items dropped when this piece is broken. |

### Persistence

These are called by `SubgridBlockEntity` when the chunk saves/loads. Use `piece.extraData` (a `CompoundTag`) to store per-piece NBT.

```java
@Override
public void onSaving(PlacedPiece piece, HolderLookup.Provider registries) {
    piece.extraData.putInt("charge", getCharge(piece));
}

@Override
public void onLoaded(PlacedPiece piece, HolderLookup.Provider registries) {
    setCharge(piece, piece.extraData.getInt("charge"));
}
```

### Ticking

```java
@Override
public boolean requiresTick() { return true; }

@Override
public boolean tick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
    // Called every server tick for each piece of this type.
    // Return true if state changed and the BE should call setChanged().
    boolean dirty = false;
    // ... your logic ...
    return dirty;
}
```

### Interaction

```java
@Override
public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos,
                               Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    // open a menu, toggle state, etc.
    player.openMenu(new SimpleMenuProvider(...));
    return InteractionResult.SUCCESS;
}
```

Return `InteractionResult.PASS` to let the interaction fall through to the SubgridBlock's default behaviour.

---

## Runtime state

For in-memory state that does not need to survive a restart (e.g. cached objects, open menu references), use `piece.runtimeState`:

```java
public static MyState getState(PlacedPiece piece) {
    if (!(piece.runtimeState instanceof MyState)) {
        piece.runtimeState = new MyState();
    }
    return (MyState) piece.runtimeState;
}
```

Populate it from NBT in `onLoaded()` and flush it back in `onSaving()`.

---

## Full example — interactive energy cell

```java
public class EnergyCellDefinition extends PieceDefinition {

    public static final EnergyCellDefinition INSTANCE = new EnergyCellDefinition();

    private EnergyCellDefinition() {
        super(ResourceLocation.fromNamespaceAndPath(MOD_ID, "energy_cell"), new Vec3i(1, 1, 1));
    }

    @Override
    public BlockState renderState(Direction.Axis axis) {
        return Blocks.AMETHYST_BLOCK.defaultBlockState();
    }

    @Override public float destroyTime() { return 2f; }
    @Override public boolean requiresCorrectTool() { return true; }

    @Override
    public List<ItemStack> drops(PlacedPiece piece) {
        return List.of(new ItemStack(MyItems.ENERGY_CELL.get()));
    }

    @Override
    public void onSaving(PlacedPiece piece, HolderLookup.Provider registries) {
        piece.extraData.putInt("energy", getState(piece).energy);
    }

    @Override
    public void onLoaded(PlacedPiece piece, HolderLookup.Provider registries) {
        getState(piece).energy = piece.extraData.getInt("energy");
    }

    @Override public boolean requiresTick() { return true; }

    @Override
    public boolean tick(PlacedPiece piece, ServerLevel level, BlockPos pos, SubgridBlockEntity be) {
        State s = getState(piece);
        if (s.energy < 1000) { s.energy++; return true; }
        return false;
    }

    @Override
    public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos pos,
                                   Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal("Energy: " + getState(piece).energy));
        }
        return InteractionResult.SUCCESS;
    }

    private static State getState(PlacedPiece piece) {
        if (!(piece.runtimeState instanceof State)) piece.runtimeState = new State();
        return (State) piece.runtimeState;
    }

    public static class State {
        public int energy;
    }
}
```

---

## PlacedPiece fields

| Field | Type | Description |
|-------|------|-------------|
| `definition` | `PieceDefinition` | The registered definition for this piece. |
| `anchor` | `Vec3i` | Bottom-left-front grid cell of this piece. |
| `footprint` | `Vec3i` | Size in grid cells (from the definition). |
| `axis` | `Direction.Axis` | Placement axis (passed to `renderState`). |
| `extraData` | `CompoundTag` | Persistent NBT — read/write in `onLoaded`/`onSaving`. |
| `runtimeState` | `Object` | In-memory only, survives chunk reload only if rebuilt in `onLoaded`. |

---

## Grid sizes

TinyBlocks ships two SubgridBlock variants:

| Block | Grid | Cell size |
|-------|------|-----------|
| `subgrid_block` | 8×8×8 | 1/8 of a block |
| `subgrid_block_16` | 16×16×16 | 1/16 of a block |

Piece footprints are expressed in cells of whichever grid hosts them.
