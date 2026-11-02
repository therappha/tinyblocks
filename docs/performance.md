# Performance: POC with many tinyblocks (issue #64)

Branch `perf/poc-many-tinyblocks`. POC: changes what weighed the most, without reorganizing anything.

## How it was measured

- **Cost GameTest** `SubgridManyPiecesCostGameTest` (in the style of `SubgridGearFieldCostGameTest`), with 4 scenarios:
  - stone: a 16-grid with 2048 stone pieces and a full 8-grid (512);
  - cogs+stone: a 16-grid with a layer of 256 cogwheels + motor, under 6 layers of stone (1793 pieces, 257 ticking);
  - redstone: an 8-grid with a stone floor and ceiling, lever → line of dust → lamp, and a pair of links (394 pieces);
  - 9 neighbouring 8-grids, each half full of stone (2304 pieces).

  Each one logs a `[many-cost]` line: time to place, one `serverTick` (average of 50–100), a lever flip, an outline
  and a collision query (through `BlockState`, average of 1000), and how many faces a renderer draws
  (`PieceFaceCulling.countDrawnFaces` against 6 per piece).
- **JFR** on `runGameTestServer` (`settings=profile`), `jfr print --events jdk.ExecutionSample` and a Python script
  that adds up samples per frame under `SubgridBlockEntity.serverTick`.
- **Client**: the game can't be run here. Culling is counted on the server (same logic the renderer uses); the rest
  of the rendering is reasoning from the code.

All numbers are from a single run, with the Gradle lock held only by this work; they vary about ±30% between runs
(lever flips vary more, from JIT/GC).

## Before → after

| Scenario | Before | 1. lazy fake space | 2. fake space per grid | 3. version + 4. mesh |
|---|---|---|---|---|
| cogs+stone, tick | 22.6 ms | 1.8 ms | 38 µs | 29 µs |
| gear field 16 (256 cogs), tick | 4.7 ms | 1.5 ms | 18 µs | 17–39 µs |
| gear field 8 (64 cogs), tick | 436 µs | 237 µs | 4 µs | 3 µs |
| stone 16-grid, tick | 59 µs | 46 µs | 55 µs | 26 µs |
| redstone, lever on / off | 7.1 / 11.3 ms | 2.6 / 7.9 ms | 2.1 / 2.7 ms | 1.8 / 3.7 ms |
| placing 2048 stone | 595 ms | 101 ms | 107 ms | 82 ms |
| outline query, stone 16-grid | 25 µs | 21 µs | 18 µs | 1.35 µs |
| outline query, stone 8-grid | 6.4 µs | 5.6 µs | 5.8 µs | 0.33 µs |
| outline rebuild, 256 cogs | ~220 ms | – | – | 167 ms |
| collision (query) | ~0.1 µs | = | = | = (was already cached) |
| faces drawn, stone 16-grid | 12288 | | | 1024 |
| faces drawn, cogs+stone | 10758 | | | 2182 |
| faces drawn, 9 grids | 13824 | | | 2304 |

## What changed

1. **Lazy fake space** (`09b8078`). Every fake space copied the state of all pieces into the cell map
   (`populateCells`), every call took a `snapshot` of all of them, and `applyChanges` compared all of them: O(n) per
   ticking piece, O(n²) per grid tick. JFR before: 632 of 701 samples of `VanillaBlockPiece.tick` in
   `populateCells`/`snapshot`/`applyChanges` (HashMap.put/resize). Now an unwritten cell falls through to
   `realBlockStateAt` (which already read the piece's state, live), and the diff only looks at written cells, in grid
   order. The two places that used the seeded map as a snapshot (`runItemInteraction`, the test's `useItemOn`) take an
   explicit snapshot. The piston's "moved elsewhere" still looks at the other pieces, as before.
   As a bonus: `logResolve` formatted the `CellRef` description even without debug.
2. **One fake server space per grid** (`a177d4a`). With (1) done, what was left was `FakeServerLevel.create` (the
   `Level` constructor): 55 of 72 samples. Now each grid keeps one (`VanillaBlockPiece.TickSpace`), cleaned and pointed
   at the piece before each use; reentrant use gets a new one.
3. **Version counter** (`4d8b3af`). Outline (every query) and redstone output (every tick) hashed all pieces.
   `pieceVersion` goes up on add/remove, `setChanged`, `notifyNeighbors` and on client load. The outline of partial
   pieces now merges as a balanced tree.
4. **Cached mesh with culling** (`d6ed3bb`). `SubgridRenderer` ran `renderSingleBlock` (model lookup, getQuads,
   RandomSource) + `blockEntityFor` per piece per frame. Now `StaticMesh` keeps the quads of MODEL pieces per render
   type, rebuilt only when `pieceVersion` changes (or models reload), without faces hidden by a neighbour in the same
   grid (`PieceFaceCulling`, same rule as `Block#shouldRenderFace`). A piece with a block entity, a fluid, another
   render shape or a funnel stays on the per-frame path.

All GameTests pass (`All 96 required tests passed`), and so do the unit tests.

### After the review of PR #80

- `TickSpace` is now cleaned (written cells and captured ticks, including those of the `blockEvent` `FakeLevel`
  delegate) when the piece gives it back, not only when the next one takes it: phantoms stay bound to it between
  ticks, and whoever read from outside the grid through them (`boundPhantom`) saw the last tick's writes (or a piece
  already removed).
- `applyChanges` compares each cell with what it had at the call's first write (`FakeCellGetter.originalState`), not
  with what it has when the diff runs: a write from outside in between (another grid placing a piece here) isn't undone.
- The crack is no longer drawn twice on a piece with a block entity and a baked model (furnace, hopper).
- New GameTests (`SubgridTickSpaceGameTest`): reading through a phantom bound to the space after a tick (failed before
  the fix), and outline after a piece only changes state (a trapdoor opened by a lever).
- Measured again: all within noise (cogs+stone 34 µs, stone 16-grid 26–46 µs, lever 2.3 / 2.2 ms,
  outline 16-grid 1.3–1.8 µs).

**After the rebase on `master` (with #74):** rotating, placing and picking up grids already change `pieceVersion`, because they go through `addToGridNoShape`, `rebuildAfterRemove`, `setChanged` or `notifyNeighbors`. New code that changes `runtimeState` without going through one of these leaves the outline, redstone output and mesh stale. All 121 GameTests pass.

## What still weighs

- **Rendering is still immediate**: the cached mesh still sends all vertices to the buffer every frame (far fewer, and
  with no lookup). Not tested in game: it needs a visual check (grass/redstone tint, leaf/glass cutout, crack, piston).
- **Outline of a grid with many partial pieces**: 256 cogs = ~170 ms to rebuild the `VoxelShape` (the fine coordinates
  blow up the merged shape). It only happens when something changes, but in such a grid each placement hitches.
- **Lever flip / redstone cascade**: ~2–4 ms in a grid of 394 pieces. Each `onNeighborChanged` still creates a new
  `FakeLevel` (the `Level` constructor), and a cascade creates dozens.
- **Placing pieces**: each `placePiece` rebuilds the whole collision shape (gridSize³) and creates fake levels for
  onPlace and for the neighbours.
- The ticks themselves are now Create's tick (`KineticBlockEntity.tick`), which is as it should be.

## Next steps

- Chunk baker: `IDynamicBakedModel` on `SubgridBlock` reading the pieces through `ModelData`, so the static mesh goes
  into the chunk section mesh (vanilla's cache and frustum culling). Culling between neighbouring grids of the same
  size along with it.
- Reuse the non-server `FakeLevel` the same way as `TickSpace` for `onNeighborChanged`/`scheduledTick`/`randomTick`.
- Outline: don't merge partial pieces; `clip` per cell (GridRay) and draw only the outline of the piece aimed at.
- Collision: update the `BitSetDiscreteVoxelShape` incrementally instead of rebuilding it on each add/remove.
- Far LOD (item 6 of #64), if still needed.
- Measure for real on the client with Spark in a full world.
