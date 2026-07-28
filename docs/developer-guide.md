# TinyBlocks — Developer Guide

TinyBlocks exposes a `PieceDefinition` API so any mod can register custom tiny blocks with their own rendering, hardness, drops, ticking, persistent state, and interactive GUIs — without touching TinyBlocks internals.

---

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

Use `compileOnly` so TinyBlocks is not bundled into your jar. The player installs it separately at runtime.

---

## Grid sizes

TinyBlocks provides four SubgridBlock variants. Piece footprints are expressed in cells of whichever grid hosts them.

| Block ID | Grid | Cell size |
|----------|------|-----------|
| `tinyblocks:subgrid_block_2` | 2×2×2 | 1/2 of a block |
| `tinyblocks:subgrid_block_4` | 4×4×4 | 1/4 of a block |
| `tinyblocks:subgrid_block` | 8×8×8 | 1/8 of a block |
| `tinyblocks:subgrid_block_16` | 16×16×16 | 1/16 of a block |

---

## Creating a PieceDefinition

Subclass `PieceDefinition` with a unique `ResourceLocation` and a footprint size in grid cells:

```java
public static final PieceDefinition MY_PIECE = new PieceDefinition(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "my_piece"),
        new Vec3i(1, 1, 1))  // width × height × depth in grid cells
{
    @Override
    public BlockState renderState(Direction.Axis axis) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }
};
```

The constructor registers the definition automatically — no registry call needed. Make sure the class is loaded at mod startup by referencing it in your mod constructor or a static initialiser.

---

## Hooks reference

All hooks have default no-op implementations. Override only what you need.

### Appearance

| Method | Default | Description |
|--------|---------|-------------|
| `renderState(Axis axis)` | **abstract** | Block whose model and texture are rendered at mini scale. `axis` is the placement axis for directional blocks. |

### Mining

| Method | Default | Description |
|--------|---------|-------------|
| `destroyTime()` | `1.5f` | Hardness, same units as vanilla `BlockBehaviour.Properties.strength()`. Use `-1` for unbreakable. |
| `requiresCorrectTool()` | `false` | When `true`, wrong tool gives 3× reduced progress (same as vanilla). |
| `drops(PlacedPiece piece)` | `List.of()` | Items dropped when this piece is broken. |

### Persistence

Called by `SubgridBlockEntity` on chunk save and load. Use `piece.extraData` (a `CompoundTag`) for all persistent state.

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
    // Return true if state changed so the BE calls setChanged().
    return false;
}
```

### Interaction

```java
@Override
public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos,
                               Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    player.openMenu(new SimpleMenuProvider(...));
    return InteractionResult.SUCCESS;
}
```

Return `InteractionResult.PASS` to fall through to SubgridBlock's default behaviour.

### Neighbor propagation

Mirrors vanilla `neighborChanged`. Fires when a piece adjacent to this one (touching face-to-face, within the same subgrid) is placed, removed, or has its `tick()` return `true`:

```java
@Override
public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos,
                              SubgridBlockEntity be, PlacedPiece changedNeighbor) {
    // react to the neighbor, e.g. re-check a redstone-like signal
}
```

Adjacency is cell-level and does not cross the subgrid's own boundary — pieces on the edge of the grid are not notified about real blocks outside the SubgridBlock.

For state changes made from `onUse()` rather than `tick()`, trigger propagation yourself:

```java
@Override
public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos,
                               Player player, BlockHitResult hit) {
    if (!level.isClientSide() && level.getBlockEntity(subgridPos) instanceof SubgridBlockEntity be) {
        // mutate piece state here
        be.notifyNeighbors(piece);
    }
    return InteractionResult.SUCCESS;
}
```

---

## Runtime state

For in-memory state that does not need to survive a restart, use `piece.runtimeState`:

```java
public static MyState getState(PlacedPiece piece) {
    if (!(piece.runtimeState instanceof MyState)) {
        piece.runtimeState = new MyState();
    }
    return (MyState) piece.runtimeState;
}
```

Populate it from NBT in `onLoaded()` and flush it in `onSaving()`.

---

## PlacedPiece fields

| Field | Type | Description |
|-------|------|-------------|
| `definition` | `PieceDefinition` | The registered definition for this piece. |
| `anchor` | `Vec3i` | Bottom-left-front grid cell of this piece. |
| `footprint` | `Vec3i` | Size in grid cells (from the definition). |
| `axis` | `Direction.Axis` | Placement axis passed to `renderState`. |
| `extraData` | `CompoundTag` | Persistent NBT — read/write in `onLoaded`/`onSaving`. |
| `runtimeState` | `Object` | In-memory only. Rebuild it from `extraData` in `onLoaded` if needed across restarts. |

---

## What you can build

**Decorative pieces** — override only `renderState()`. Any block model works, including blocks from other mods.

**Tool-sensitive pieces** — override `destroyTime()` and `requiresCorrectTool()`.

**Pieces with custom drops** — override `drops()` to return any `ItemStack` list, including NBT-tagged items.

**Interactive pieces with GUI** — override `onUse()` to open any `AbstractContainerMenu`.

**Ticking pieces** — override `requiresTick()` + `tick()` for per-tick server logic: energy accumulation, cooldowns, fuel burning, etc.

**Multi-cell pieces** — set `footprint` to e.g. `new Vec3i(2, 1, 1)` to occupy two cells horizontally. TinyBlocks claims all cells in the footprint automatically.

**Pieces using other mods' blocks** — any `BlockState` works as `renderState()`. The block is never placed in the world; only its model is rendered.

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

## Integrating another mod's blocks

```java
@Override
public BlockState renderState(Direction.Axis axis) {
    // Renders Create's Andesite Casing at mini scale
    Block block = BuiltInRegistries.BLOCK.get(
        ResourceLocation.fromNamespaceAndPath("create", "andesite_casing"));
    return block.defaultBlockState();
}
```

The block is never placed in the world — only its model is rendered. List the other mod as a dependency in your `neoforge.mods.toml` and `compileOnly` in `build.gradle`.
