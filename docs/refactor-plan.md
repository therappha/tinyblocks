# Refactor plan (#115)

A plan, not a change: nothing in the code moves until it has been reviewed. Every step below is one commit that
compiles and passes `./gradlew compileJava -q`, `./gradlew test -q` and `./gradlew runGameTestServer -q` on its own.
The last run also has `-PrandomSeeds=60`.

## What doesn't change

- Behaviour. Where something looks like a bug, it goes into an issue, not into a step.
- Block, item, block entity and piece ids (`tinyblocks:subgrid_block*`, `tinyblocks:vanilla_block`,
  `tinyblocks:stone_block` and the other debug pieces, ...), tags and recipes.
- The save format: the NBT keys of `SubgridBlockEntity` (`pieces`, `scheduledTicks`), `PlacedPiece`
  (`type`, `ax`/`ay`/`az`, `facing`, `speed`, `extra`), `VanillaBlockPiece` (`state`, `be`) and the Grid Save item.
- Network payloads: their types, ids, codecs and the protocol version `"1"`.
- Mixins and their json: no mixin class, target or injected member is renamed (step 23, if accepted, adds one). Two
  members the mixins read stay where they are: `SubgridRenderer.drawingPhantom` and `VanillaBlockPiece.boundPhantom`.
- Test code. `src/test` and `gametest/` keep their logic. A step that moves something a test calls keeps that member
  where it is, as a delegate, so no test needs to change. The list of what tests reach is at the end.

## What's wrong now

The mod is 203 Java files and about 27k lines; 58 files and about 10.5k lines of that are GameTests (they live in
`src/main`, under `gametest/`).

**Classes too big to follow.**

| Class | Lines | What it mixes |
|---|---|---|
| `subgrid/SubgridBlockEntity` | 1618 | Cell storage, placement and removal, piece lifecycle, scheduled ticks, deferred drops and piston captures, rotation, cross-grid resolution (about 250 lines), redstone through the faces (about 140), collision and outline shapes (about 90), phantom data sync (about 70), NBT and client sync. |
| `v2/VanillaBlockPiece` | 1504 | The piece definition itself, the per-grid tick space (`TickSpace`), the cell getter of a grid's fake space (`SubgridFakeCellGetter`, about 230 lines, nested), the write-back of a fake space's writes into pieces (`applyChanges` and `syncBlockEntity`, about 300), the client copy of a piston's animation (about 120, with its own reflection), phantom block entity binding, and four builders of fake spaces. |
| `setup/SubgridEventHandler` | 622 | Event subscriptions, and placement of a block item in a cell, an item used on a cell (`runItemInteraction`) and breaking with drops, which aren't events. |
| `client/SubgridRenderer` | 371 | The block entity renderer and the cached mesh (`StaticMesh`, about 150 lines, nested), which `SubgridChunkMesh` uses too, and connector helpers that `SubgridVisual` and `GridSavePreview` reach into the renderer for. |
| `client/flywheel/SubgridVisual` | 336 | Cohesive. Too many inline names (30 lines), a leftover diagnostic, and a header still calling it an experiment. |
| `client/ClientSetup` | 350 | Two top-level classes in one file (`ClientSetup` and `SubgridClientHandler`). |

**Duplicated code.**

- The footprint triple loop (`for x … for y … for z` over `anchor`/`footprint`) is written out 7 times in
  `SubgridBlockEntity` (`takeOut`, `neighborsOf`, `canFit`, `addToGridNoShape`, `rebuildAfterRemove`,
  `refreshSignals`, `notifyFace`), plus a relative variant in `buildOutlineShape`.
- `SubgridBlockEntity.resolve(int,int,int)` and `resolveOrCreate` are the same hop loop, about 35 lines each; one reads,
  the other creates grids on the way.
- `SubgridBlockEntity.placePieceCrossBoundary` has two identical switch arms (`InAdjacentGrid`, `Created`).
- `VanillaBlockPiece.buildFakeSpace` and `buildClientFakeSpace` are the same three lines; `operatingServerSpace` repeats
  `buildFakeServerSpace` with an operated cell.
- "Resync two ticks later" (`level.getBlockTicks().schedule(new ScheduledTick<>(…, gameTime + 2, 0L))`) appears five
  times: four in `SubgridEventHandler`, one in `setup/MinimizerShapes`.
- Spawning a piece's drops at its cell is written twice in `SubgridEventHandler` (`removeAndDrop`,
  `onLeftClickBlock`).
- The crosshair cell in `SubgridClientHandler.onRenderBlockHighlight` is `GridRay.cellAt`, written out again.
- In `SubgridRenderer`, push/translate/scale of a slice is written three times and the crumbling buffer twice.
- "Is this piece a gear connector" (`faceOf(piece) != null && isGear(size)`) is in `SubgridRenderer.isGear` and again
  inline in `SubgridVisual.createChildren`.
- `GridSaveItem.pickUp` and `pickUpBox` repeat the same per-grid refusals (adventure mode, a piston moving, protections).

**Dead code and leftovers.**

- `VanillaBlockPiece.populateCells` is an empty method, still called three times. `VanillaBlockPiece.snapshot` returns
  null, and the `before` map it fills is passed to `applyChanges`, which overwrites the parameter on its first line.
  `tick` computes a `realPos` it never uses. `SubgridBlockEntity.pieceKey()` only returns `pieceVersion`.
- Eight `#TODO remove before release` markers: `client/SubgridFluidView` (2), `client/ScaledVertexConsumer` (1),
  `client/SubgridRenderer` (2), `setup/SubgridEventHandler` (3), plus a reference to "#TODO removal" in
  `ScaledVertexConsumer`. All of them say "or fold into a permanent comment once verified live". The code they mark
  is in use (fluid rendering, buckets on cells); the bucket path is covered by fluid GameTests, the rendering isn't. What
  has to go is the marker.
- Diagnostics for closed investigations: the `[xgrid-fluid]` trace and `SubgridBlockEntity.setTracingFluid` (#43,
  closed); the `[piston-anim]` traces in `VanillaBlockPiece`, `FakeLevel.runBlockEvent` and the piston client code
  (#34, closed), including a debug-only loop that asks the fake level for six signals; `SubgridVisual.logLight`, an INFO
  `[fw-light]` line logged three times per game.
- Stale labels: "v2 prototype checkpoint 2", "Tier 3", "Phase C debug convenience", "EXPERIMENT
  (feat/flywheel-create-experiment)" in class comments of `FakeLevel`, `FakeServerLevel`, `FakeCellGetter`,
  `FakeSpace`, `VanillaBlockPiece`, `SubgridEventHandler`, `SubgridVisual`, `PieceDefinition`, and in
  `neoforge.mods.toml` and `build.gradle` comments.
- Javadoc on the wrong member: in `SubgridBlockEntity` two comments are stacked over `tracingFluid` (the first belongs
  to `logResolve`) and the one about building the shape in one pass sits over `shapeRebuildCount`; in
  `SubgridEventHandler` the comment about the debug placement sits over `MAX_GRIDS_PASSED`.
- `subgrid/SubgridBlock` imports `java.util.List` twice.

**Fully qualified names inline.** About 490 lines of non-test code (and 270 more in GameTests, which stay) name a class
by its full package instead of importing it. The worst: `VanillaBlockPiece` (60 lines), `FakeServerLevel` (58), `SubgridRenderer` (47), `FakeLevel`
(37), `SubgridBlockEntity` (36), `SubgridEventHandler` (36), `SubgridVisual` (30), `ConnectorPlacement` (27),
`NetworkSetup` (26), `TinyBlocksCommand` (18). Some are needed: a simple name that clashes
(`GridTickScheduler.BlockEvent` and NeoForge's `BlockEvent`, a mod's `Block` next to vanilla's), and names in javadoc.

**In the wrong place.**

- Item placement and item use on a cell live in an event handler (`SubgridEventHandler`).
- `setup/MinimizerShapes` is the Minimizer's shape building, not setup; it stays in `setup` (it calls package-private
  helpers there) but gets them from their new home.
- `StaticMesh` is a type of its own that two classes use, nested in one of them.
- The client copy of a piston's animation sits in the piece definition every server loads.

## Target structure

Same packages, same public entry points. What moves goes into package-private classes next to where it was; only
`GridWriteBack` and `PistonAnimation` are public, because callers in other packages need one or two of their methods.

```
subgrid/
  SubgridBlockEntity   ~900   cells, pieces, placement and removal, lifecycle, ticks, rotation, NBT, sync;
                              one-line delegates for what moved, so callers and tests keep calling it here
  GridResolver         ~230   where a local cell lands: this grid, a neighbour grid, the real world (CellRef stays
                              nested in SubgridBlockEntity)
  GridSignals          ~130   what the six faces give in redstone, cell by cell and per face
  GridShapes            ~90   collision (occupancy) and outline shapes
  PendingDrops          ~80   drops held while a piston's capture is still moving (PendingDrop stays nested)
  PhantomSync           ~70   pushing phantom block entity data to clients
  PlacedPiece                 + forEachCell / anyCell
v2/
  VanillaBlockPiece    ~650   the piece definition, phantom block entities, fake space builders
  SubgridFakeCellGetter ~230  moved out unchanged
  TickSpace             ~45   moved out unchanged
  GridWriteBack        ~320   applyChanges, syncBlockEntity, followStateChange, scheduleCaptured
  PistonAnimation      ~120   the client's copy of a moving piston
setup/
  SubgridEventHandler  ~370   events only, plus handleMinePiece (tests call it)
  PiecePlacement       ~260   a block item into a cell, an item on a cell, breaking with drops, resync later
client/
  SubgridRenderer      ~200
  StaticMesh           ~150
  SubgridClientHandler ~310   moved out of ClientSetup.java
  PieceTransforms             + acrossOf, isGearConnector (from SubgridRenderer)
```

## Steps

Each step names its files and its check. "Tests" means the three Gradle commands above. A step marked **client**
changes code that only runs on the client; GameTests have no client, no GPU and no Flywheel, so it goes on the list for
the user to check in game.

For a step that only moves text (comments, imports), a stronger check exists: build before and after and compare
`javap -c -p` of every class under `build/classes/java/main` (without `-l`, so line numbers don't count). It must be
identical (if javac numbered the constant pool differently, compare with the `#n` indices stripped). Steps 1 and 19 to
22 use it.

### 1. Leftover markers, stale labels, misplaced comments

Comments only. Each `#TODO remove before release` becomes a short comment that says why the code is there (fluids
have no baked model and render through `renderLiquid`, which takes no pose; buckets only override `use`, so the
dispenser's position-based hooks are called directly). Stale "v2 prototype", "Tier 3", "EXPERIMENT" labels go or are
reworded to what the class is now. The three misplaced javadocs go back over their members. The duplicate import goes.

Files: `client/SubgridFluidView`, `client/ScaledVertexConsumer`, `client/SubgridRenderer`, `client/flywheel/SubgridVisual`,
`setup/SubgridEventHandler`, `subgrid/SubgridBlockEntity`, `subgrid/SubgridBlock`, `subgrid/PieceDefinition`,
`v2/FakeLevel`, `v2/FakeServerLevel`, `v2/FakeCellGetter`, `v2/FakeSpace`, `v2/VanillaBlockPiece`,
`META-INF/neoforge.mods.toml` (comments), `build.gradle` (comment).

Check: `grep -rn "TODO remove before release" src` is empty; `javap` identical; tests. **Client** in name only: no
client code changes.

The "Phase C debug convenience" comment on `onLeftClickBlock` stays true to what it does: a block item in hand
removes a piece at once, in survival too. That looks like a debug shortcut that shipped: #117, left as it is here.

### 2. Dead code

Remove `VanillaBlockPiece.populateCells` and its three calls, `VanillaBlockPiece.snapshot` with the `before` locals it
fills, the `before` parameter of `applyChanges` (it is overwritten on entry), and the unused `realPos` in
`VanillaBlockPiece.tick`. Inline `SubgridBlockEntity.pieceKey()` as `pieceVersion`.

Files: `v2/VanillaBlockPiece`, `subgrid/SubgridBlockEntity`.

Check: tests. Nothing removed is read: `snapshot` returns null and every caller passes it straight to `applyChanges`.

### 3. Diagnostics of closed issues

Remove the `[xgrid-fluid]` trace with `SubgridBlockEntity.tracingFluid`/`setTracingFluid` and its try/finally in
`VanillaBlockPiece.runScheduledTick` (#43); every `[piston-anim]` log line and `isPistonRelated` (#34), including the
debug-only six-signal loop in `onNeighborChanged` and the before/after reads in `FakeLevel.runBlockEvent`;
`SubgridVisual.logLight` and `lightLogBudget`. The general `[xgrid]` debug line in `logResolve` stays: it isn't tied to
an investigation and costs nothing while debug is off.

Files: `subgrid/SubgridBlockEntity`, `v2/VanillaBlockPiece`, `v2/FakeLevel`, `client/flywheel/SubgridVisual`.

Check: `grep -rn "piston-anim\|xgrid-fluid\|fw-light" src/main` is empty; tests. Only log output changes, and only at
DEBUG, except the three `[fw-light]` INFO lines. The removed reads in `runBlockEvent` and the signal loop are reads of
the fake space with no effect on it; the signal loop only ran with debug logging on. **Client** (`SubgridVisual`): a
log line only.

### 4. Cell iteration on PlacedPiece

Add `PlacedPiece.forEachCell(CellVisitor)` and `anyCell(CellTest)` (small functional interfaces on ints, no
allocation per cell) and use them for the seven footprint loops in `SubgridBlockEntity`. The order stays x outer, then
y, then z: `neighborsOf` fills a `LinkedHashSet`, and that order is the order neighbours hear of a change. `canFit` and
`notifyFace` stop at the first hit, as they do now. Merge the two identical arms of `placePieceCrossBoundary`.

Files: `subgrid/PlacedPiece`, `subgrid/SubgridBlockEntity`.

Check: tests, the redstone and Create parity scenes above all (they hear neighbours in order).

### 5. GridResolver out of SubgridBlockEntity

Move, unchanged, `resolve(Direction,…)`, `resolve(int,int,int)`, `resolveOrCreate`, `directlyInGrid`,
`peelOneGridWidth`, `MAX_CHAIN_HOPS`, `logResolve` and `describeCellRef` into `subgrid/GridResolver` (package-private,
static methods taking the grid). `SubgridBlockEntity` keeps `resolve`, `resolveOrCreate`, `singleStepOverflowAxis` and
`foldRemainingIntoRealWorld` as delegates, and `wrap` as it is (tests call all of them on it); `CellRef` stays nested in
it.
The logger name in `[xgrid]` lines changes to `GridResolver`.

Files: `subgrid/SubgridBlockEntity`, new `subgrid/GridResolver`.

Check: tests; `SubgridBlockEntityTest` and `SubgridCrossBoundaryGameTest` cover the hops.

### 6. One hop loop for resolve and resolveOrCreate

The two loops become one, with a `create` flag. What must hold: the read-only path never calls `createAt` and never
asks `getBlockState(target).isAir()`; it tests `getBlockEntity(target) instanceof SubgridBlockEntity` of the same size,
as now. The creating path asks `isAir` before `createAt`, reports `Created` only when a hop made a grid, and
`InAdjacentGrid` otherwise. Both fold into `RealWorld` the same way and log the same first hop.

Files: `subgrid/GridResolver`.

Check: tests. This is the one merge of logic in the plan; if the reviewer finds it not worth the risk, it is dropped and
the rest doesn't depend on it.

### 7. GridSignals out of SubgridBlockEntity

Move the face redstone state (`weakCells`, `strongCells`, `weakFace`, `strongFace`, `signalKey`, `signalsBuilt`) and
`refreshSignals`, `faceSignal`, `overlapSignal`, `updateRedstoneOutputs`, `faceIndex`, `onFace` into
`subgrid/GridSignals`, one per grid. `SubgridBlockEntity` keeps `faceSignal`, `overlapSignal` and the static
`acrossFace` as delegates; `notifyFace` stays in it (it walks pieces) and asks `GridSignals.onFace`. The tick order in
`serverTick` (`updateRedstoneOutputs` after the phantom flush) doesn't change.

Files: `subgrid/SubgridBlockEntity`, new `subgrid/GridSignals`.

Check: tests, `SubgridRedstoneFacesGameTest` and the redstone parity scenes above all.

### 8. PendingDrops out of SubgridBlockEntity

Move `pendingDrops`, `activeCaptures`, `deferDrop`, `cancelPendingDrop`, `drainReadyDrops`, `tickPendingDrops`,
`hasActiveCaptures`, `captureStarted`/`captureEnded` and the drop half of `turn` (moving drop points) into
`subgrid/PendingDrops`. The `PendingDrop` record stays nested in `SubgridBlockEntity` (`SubgridBlockEntityTest` names
it); every method stays on `SubgridBlockEntity` as a delegate.

Files: `subgrid/SubgridBlockEntity`, new `subgrid/PendingDrops`.

Check: tests, `SubgridBlockEntityTest`, the piston GameTests.

### 9. GridShapes out of SubgridBlockEntity

Move the body of `rebuildShape` (occupancy from `cellOwner`), `buildOutlineShape`, `joinAll` and `pieceShape` into
`subgrid/GridShapes`. `SubgridBlockEntity` keeps `rebuildShape` with the `shapeRebuildCount++` in it (the unit test
reads that field), `getCachedShape`, `getOutlineShape` with its cache, and `pieceShape` as a delegate (`ClientSetup`
calls it).

Files: `subgrid/SubgridBlockEntity`, new `subgrid/GridShapes`.

Check: tests, `SubgridCollisionGameTest`, `SubgridTickSpaceGameTest` (outline after a state change).

### 10. PhantomSync out of SubgridBlockEntity

Move `phantomDataDirty`, `phantomDataFlushes`, `PHANTOM_CHANGE_SCAN_INTERVAL`, `flushPhantomData` and
`markNeighbouringPhantomsDirty` into `subgrid/PhantomSync`. `markPhantomDataDirty` and `phantomDataFlushes` stay as
delegates (GameTests and `FakeLevel` call them).

Files: `subgrid/SubgridBlockEntity`, new `subgrid/PhantomSync`.

Check: tests, the Create GameTests that count flushes.

### 11. SubgridFakeCellGetter and TickSpace out of VanillaBlockPiece

Move both nested classes, unchanged, to their own package-private files. What they call in `VanillaBlockPiece`
(`stateOf`, `applyChanges`, `blockEntityFor`, `peekBlockEntity`) becomes package-private where it was private.
`SubgridBlockEntity.tickSpace` stays an `Object` holding a `TickSpace`.

Files: `v2/VanillaBlockPiece`, new `v2/SubgridFakeCellGetter`, new `v2/TickSpace`.

Check: tests.

### 12. GridWriteBack out of VanillaBlockPiece

Move `applyChanges`, `syncBlockEntity`, both `followStateChange` and `scheduleCaptured` into `v2/GridWriteBack`, a
public class whose only public methods are the two that were public. They are called from `SubgridBlockEntity.placeOrUpdatePiece` and `SubgridEventHandler`; those calls
change to `GridWriteBack`. No test calls them.

Files: `v2/VanillaBlockPiece`, new `v2/GridWriteBack`, `v2/SubgridFakeCellGetter`, `subgrid/SubgridBlockEntity`,
`setup/SubgridEventHandler`.

Check: tests, every parity scene (each one goes through `applyChanges`).

### 13. PistonAnimation out of VanillaBlockPiece

Move the reflected piston fields, `PISTON_LINGER_TICKS`, `PISTON_PROGRESS_PER_TICK`, `clientTickPiston` and
`applyClientAnimation` into `v2/PistonAnimation` (public, `applyClientAnimation` its only public method).
`VanillaBlockPiece.clientTick` calls it; `NetworkSetup`'s handler calls it directly. One difference: the reflection now runs when the class is first used rather than when
`VanillaBlockPiece` loads. It can't fail on 1.21.1; if it did, only the piston animation would go, not every piece.

Files: `v2/VanillaBlockPiece`, new `v2/PistonAnimation`, `network/NetworkSetup`.

Check: tests. **Client**: a piston in a grid still animates out and back, a sticky piston pulls its block smoothly.

### 14. One builder of fake spaces

`buildFakeSpace` and `buildClientFakeSpace` go through one private builder; `operatingServerSpace` goes through the
same path as `buildFakeServerSpace`, with an operated cell. Public signatures stay (tests call `buildFakeSpace`,
`buildFakeServerSpace`).

Files: `v2/VanillaBlockPiece`.

Check: tests. **Client**: `buildClientFakeSpace` hosts `SubgridVisual`'s phantoms and the placement preview.

### 15. PiecePlacement out of SubgridEventHandler

Move the placement of a block item into a cell (the end of `onRightClickBlock`, from `fakeAnchor` on),
`runItemInteraction`, `removeAndDrop` and `clickOnCell` into `setup/PiecePlacement` (package-private), with two new
helpers: `resyncLater(level, pos, block)` for the five copies of the two-tick resync and `dropAt(level, at, stacks)` for
the two drop loops. `resyncLater` takes the block: two of the copies schedule `event.getState().getBlock()`, the others
the block read from the level, and that stays as it is. `MinimizerShapes` calls `PiecePlacement.removeAndDrop` and
`resyncLater`. The event methods keep their early returns in the same order. `handleMinePiece` stays public on
`SubgridEventHandler` (GameTests and `NetworkSetup` call it).

Files: `setup/SubgridEventHandler`, new `setup/PiecePlacement`, `setup/MinimizerShapes`.

Check: tests, `SubgridMiningGameTest`, `SubgridPieceDropsGameTest`, `SubgridFluidGameTest` (buckets),
`SubgridFacingPlacementGameTest`, `MinimizerShapesGameTest`. A real right click goes through
`onRightClickBlock`, which GameTests only reach in part: placing by hand in game is on the user's list.

### 16. SubgridClientHandler into its own file

Move the second top-level class of `ClientSetup.java` into `client/SubgridClientHandler.java`, unchanged and still
package-private. The crosshair cell in `onRenderBlockHighlight` becomes `GridRay.cellAt` (the same clamp and nudge). The
ghost preview below it is not the same math (no clamp) and stays.

Files: `client/ClientSetup`, new `client/SubgridClientHandler`.

Check: compiles; tests. **Client**: the outline of the aimed piece and the Minimizer's face grid.

### 17. StaticMesh out of SubgridRenderer

Move `StaticMesh` into `client/StaticMesh.java` (package-private; `SubgridChunkMesh` is in the same package). Move
`acrossOf` and `isGear` into `PieceTransforms` as `acrossOf` and `isGearConnector`, used by `SubgridRenderer`,
`SubgridVisual` and `GridSavePreview`. One helper for push/translate/scale of a slice, one for the crumbling buffer.
`drawingPhantom` stays on `SubgridRenderer` (a mixin reads it).

Files: `client/SubgridRenderer`, new `client/StaticMesh`, `client/PieceTransforms`, `client/SubgridChunkMesh`,
`client/GridSavePreview`, `client/flywheel/SubgridVisual`.

Check: compiles; tests (`PieceTransformsTest`). **Client**: everything a grid draws: static pieces in the chunk and
per frame, the crack while mining, fluids, chests and other block entities, gear connectors and funnels, the Grid Save
ghost.

### 18. GridSaveItem's shared refusals

One `mayTake(level, grid, player)` for what `pickUp` and `pickUpBox` refuse per grid, in the order both use now:
adventure mode, a piston moving (with its message), then the protections.

Files: `items/GridSaveItem`.

Check: tests, `GridSaveGameTest`, `GridBoxGameTest`.

### 19 to 22. Imports instead of inline names

One commit per group, after the moves, so each file is cleaned once:

19. `v2/`
20. `subgrid/`
21. `setup/`, `items/`, `network/`, `command/`, `diff/`, `compat/create/` (not `compat/create/client/`), `TinyBlocks`
22. `client/`, `client/flywheel/`, `compat/create/client/`, and the light-update loops of `SubgridVisual` that are the
    same (`updateLight`, the periodic pass in `beginFrame`) as one `relightChildren` **(client)**

Not `gametest/` and not `src/test`. A name stays qualified where its simple name clashes or is already taken in that
file, and in javadoc.

Check: `javap` identical for 19 to 21, and for 22 apart from `SubgridVisual`; tests. **Client** for 22.

### 23. The test server writes its chunks without waiting for the disk (needs a decision)

See "Why a GameTest run is slow". `MinecraftServer#forceSynchronousWrites` answers true unless a dedicated server's
`sync-chunk-writes` says otherwise, and `GameTestServer` doesn't override it, so every region file of the test world is
opened with `DSYNC`. A mixin on `MinecraftServer#forceSynchronousWrites` answers false when the server is a
`GameTestServer`, and nothing else. A real server, dedicated or integrated, is not a `GameTestServer`, so the mod
behaves as before there; the test world loses only durability across a power cut, and it is deleted before the next run
anyway (`build.gradle`).

Files: new `mixin/MinecraftServerMixin` (or a name the reviewer prefers), `tinyblocks.mixins.json`.

Check: tests (`LifecycleParityGameTest` and the reload moves still read back what they wrote); the time from "Saving
chunks for level … overworld" to "… the_nether" in `run/logs/latest.log` drops from about 4 minutes to seconds.

It is the only step that adds a mixin, so it needs the reviewer's and the user's yes; without it, it is dropped.
The alternative, ending the JVM with `Runtime.halt` before the save, skips the shutdown hooks and leaves no world to
look at, so this plan doesn't propose it.

## Why a GameTest run is slow

Measured from the logs in `run/logs` (one per run), the world a run leaves, thread dumps of one default run of this
branch's base (`73a5487`), and a probe of the disk.

| Run (03 Nov) | Tests | Shutdown save | Note |
|---|---|---|---|
| 03:26 | 1.7 min | 11 s | stage 1 |
| 05:26 | 2.0 min | 72 s | stage 2, the world kept between runs |
| 05:35 to 06:08, four runs | 2.9 to 3.1 min | 3.7 to 4.1 min | after `73a5487`, a new world every run |
| 06:17, run for this plan | 4.1 min | 3.8 min | slower with thread dumps taken during it; 690 required tests passed |

**The save is more than half of the time, and it waits for the disk.** Between "Saving chunks for level … overworld"
and the nether's line, the server thread sits in `ChunkStorage.flushWorker` and the `IOWorker-chunk` thread in
`pwrite`, from `RegionFile.write` and `RegionFile.writeHeader`, using almost no CPU (about 6 s in 4 minutes). Region
files are opened with `DSYNC` because `MinecraftServer#forceSynchronousWrites` answers true and `GameTestServer` doesn't
override it; each chunk is two synchronous writes, its data and the 8 KB header. On this machine one 8 KB `DSYNC` write
took about 15 ms during a save (`dd … oflag=dsync` in `build/`: 547 kB/s, against 2.5 GB/s without), and the world holds
37,777 chunks: some 75,000 such writes are the 4 minutes. Step 23 takes `DSYNC` away for the test server only.

**Why it got worse with `73a5487`.** With the world kept between runs, the places that don't move from run to run
(`NEAR_ORIGIN`, `FRAME_SEAM`, and what they load) were generated once and then only read back; with a new world every
run, all of it is generated and written again every run.

**Why there are 37,777 chunks.** Only about 6,400 are full; the rest are half generated (`structure_starts` to
`initialize_light`). The test area holds 13,600. Most of the others come in squares of 529 (23 by 23): one full chunk
in a ring of half-generated ones, which is what a single chunk loaded at full status leaves around it. Some of those
squares sit exactly at the virtual positions of grids in the test area: for the column at block (8,256,052, 6,485,532)
the 1/8 frame lands near chunk (-66,000, 97,000) and the 1/16 frame at chunk (-132,556, 194,076), and both squares are
there. Something asks the real level for a block or a chunk at a phantom's virtual position, and the server generates
it there, synchronously, during the test. `CreateBeltParityGameTest.parityBeltMovedNearTheWorldOrigin` already notes
that a belt behaves differently depending on whether that virtual chunk happens to be loaded. That is a behaviour fix,
not a refactor: #116, and an entry in `goal.md`'s "Extra branches". Fixed, it would shorten both the tests and the
save. Other squares (x 8.25M to 10.4M, z 6.3M to 7.9M) aren't explained here; Sable's plots for the sub-level tests are
one candidate.

**The test phase** grew by about a minute with stage 2's tests. Thread dumps during it land in `SpaceDiff.compare`
(`ParityRun.compare`: every tick, five spaces per parity test) and in placing test structures, which is the tests doing
their work. Batch `parity:8` takes 50 to 90 s on its own; the log doesn't say which tests are in it. Nothing there
changes without changing test logic, so the plan leaves it.

## Risks

- **Order of neighbour updates** (step 4). Changing the loop order would change which neighbour hears first. The step
  keeps x, y, z and `Direction.values()`; the parity scenes compare with real blocks every tick, so a change shows.
- **Resolution** (steps 5 and 6). Step 6 is the only real merge of logic. `SubgridBlockEntityTest` pins the hop math,
  the cross-boundary and fluid-leak GameTests pin where writes land.
- **Write-back** (step 12). The heart of every fake-space call. Moved unchanged; the risk is a private helper resolved
  to a different overload after the move, which the compiler catches only partly: run every parity scene.
- **Static initialisation**. `VanillaBlockPiece.INSTANCE` registers the piece on class load and `TinyBlocks` forces
  that load (`VanillaBlockPiece.init()`); nothing in this plan moves it. The piston reflection becomes lazy (step 13).
- **Access widening**. Private members become package-private for the moved classes; nothing becomes public that
  wasn't.
- **Merge conflicts**. The branches listed in `goal.md`'s "Extra branches" stack on this one: #112's frame seam
  touches `SubgridBlockEntity.virtualOrigin`, which stays put; #116 will touch phantom binding in `VanillaBlockPiece`,
  which steps 11 to 14 move around, so it goes after them.
- **The mixin of step 23** runs in every server; what it changes is gated on `GameTestServer`.
- **Client rendering, not checked by any GameTest**: steps 13, 14, 16, 17 and 22, plus the log line in step 3. For the
  user to check in game, with Flywheel and without it: a grid of mixed blocks near and far (chunk mesh, per frame
  drawing, culling of hidden faces); the crack while mining a piece; water and lava in a grid; a chest opening; a piston
  and a sticky piston in a grid; tiny Create shafts and cogs turning, a gear connector and a funnel connector; the
  aimed piece's outline and the Minimizer's face grid; the Grid Save ghost, turned with R; placing blocks, buckets and
  items by hand.

## Left alone, on purpose

- **The `v2` package and its class names.** A rename touches every caller, the mixins, and tests in that package that
  use package-private members (`VanillaBlockPieceTest`, `FakeCellGetterTest`); the Extra branches stacked on this one
  would all conflict. The name is odd, not wrong.
- **`FakeLevel` and `FakeServerLevel` repeating each other's overrides.** They are siblings (`Level`, `ServerLevel`),
  so nothing can be inherited; each one lists what it overrides, which is what matters when a crash says a field is
  null. Shared helpers would hide that list.
- **`PieceDefinitions`' debug pieces and `TinyPieceItem`.** Their ids are in saved worlds, and `TinyPieceItem` is part of
  the API in `docs/developer-guide.md`. No item uses `TinyPieceItem` now, so the ghost preview in
  `SubgridClientHandler` never draws; it is client code nobody can see, so it isn't touched either.
- **`SubgridBlockEntity.tickSpace` and `renderCache` as `Object`.** The common class can't name the client's
  `StaticMesh`, and `TickSpace` is package-private in `v2`.
- **`FakeCellGetter.setRaw` and `aliasOffset`.** Only tests use them now; the tests are kept, so they stay.
- **`compat/create/`.** Cohesive already; only its imports are cleaned.
- **Mixins**, **GameTests and unit tests** (the rules), **resources** other than two comments.
- **`SubgridBlockEntity`'s scheduled ticks, rotation and NBT.** Interwoven with the cell storage; moving them would
  give a class of delegates and no clearer code.

## What tests reach, which stays in place

From `src/test` and `gametest/`: on `SubgridBlockEntity`, `placePiece`, `removePieceAt`, `getPieceAt`,
`placePieceCrossBoundary`, `resolve`, `resolveOrCreate`, `singleStepOverflowAxis`, `foldRemainingIntoRealWorld`,
`wrap`, `createAt`, `realPositionOf`, `virtualPosOf`, `transform`, `transformCell`, `getOutlineShape`, `notifyUpdate`,
`notifyNeighbors`, `scheduleTick`, `serverTick`, `deferDrop`, `cancelPendingDrop`, `drainReadyDrops`,
`pendingDropCount`, `hasActiveCaptures`, `captureStarted`, `captureEnded`, `phantomDataFlushes`, `shapeRebuildCount`,
`HORIZONTAL_WINDOW`, and the nested `CellRef`, `Neighbor`, `PendingDrop`; on `VanillaBlockPiece`, `INSTANCE`,
`blockEntityFor`, `boundPhantom`, `buildFakeSpace`, `buildFakeServerSpace`, `snapshotStates`, `directionTo`; on
`SubgridEventHandler`, `handleMinePiece`; `PieceTransforms.slices`; `FakeCellGetter.setRaw`, `getStateRaw`,
`aliasOffset`.
