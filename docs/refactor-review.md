# Review of the refactor plan (#115)

A review of `docs/refactor-plan.md` at c7dbbe0, against the code on `refactor/115-split-big-classes`. I read the
plan and the code. No code was changed and no Gradle task was run: every finding comes from reading the source.

Severities: **blocker** (unsafe or wrong), **major** (should change), **minor**.

**Verdict:** no blockers. Five majors to fix before implementation. With those fixed, I would approve the plan.

## Decided already

- **Step 23** (the `GameTestServer`-only mixin): **deferred**. It needs the user's yes, and the user is away. Nothing
  else depends on it.
- #116 and #117 stay out of the refactor, as the plan already says.

## Steps checked and found sound

These steps hold as written. Small notes on some of them are under Minor.

- **1, 2:** comments and dead code. `snapshot` returns null (`VanillaBlockPiece.java:1151`) and `applyChanges`
  overwrites `before` on its first line (`:1169`). `populateCells` is empty (`:805`). `realPos` in `tick` is unused
  (`:161`). `pieceKey()` only returns `pieceVersion` (`SubgridBlockEntity.java:1279`).
- **3:** debug logging is off in the test runs (`run/logs/debug.log` has no DEBUG lines), so the six-signal loop
  never runs there. The other reads it removes are of in-grid cells only. See minor m5.
- **5, 7, 9, 10:** these move code only. The order inside `serverTick` stays the same (`SubgridBlockEntity.java:717-721`).
- **11, 12:** the accessors they open up are listed, apart from m2.
- **15:** the five resync copies are at `SubgridEventHandler.java:74,94,443,619` and `MinimizerShapes.java:115`. Passing
  the block in keeps which state each copy reads. GameTests post `RightClickBlock` (`SubgridTestSupport.java:105`,
  `MinimizerShapesGameTest.java:136`), so the moved code is exercised.
- **16:** the crosshair math is the same as `GridRay.cellAt`: compare `ClientSetup.java:137-140` with
  `GridRay.java:24-29`.
- **18:** the refusal order is the same in both places (`GridSaveItem.java:309-315` and `:381-387`).
- **19 to 21:** a `javap -c -p` comparison catches any name that resolves to a different class. That makes it a real
  check.

## Major

### M1. Step 6: drop it, or spell out what the merge must keep

The two loops differ in more ways than the plan's "what must hold" lists:

- **Which level they use.** `resolve` reads `this.level`, and returns `OutOfScope` before anything else when that
  level is not a `ServerLevel` (`SubgridBlockEntity.java:857`). `resolveOrCreate` never checks: it uses the
  `realLevel` argument throughout, `directlyInGrid` included (`:960`).
- **What the creating path does at each hop.** It calls `getBlockState(targetPos)` once for `wasAir` (`:974`). Then
  `createAt` calls it again and does `setBlock(..., UPDATE_ALL)` (`:385-386`), all in the middle of the loop. That is
  a real neighbour update in the real world, with its own order.

The gain is about 30 lines. The only test that tells `Created` from `InAdjacentGrid` is one assertion
(`SubgridCrossBoundaryGameTest.java:184`). If the merge used `this.level` on the creating path, every test would still
pass, because tests always pass the grid's own level.

**What to do:** drop step 6, which the plan already offers to do. If it stays, add both points above to "what must
hold", and name `SubgridCrossBoundaryGameTest` in the check.

### M2. Step 8: PendingDrops is churn

Every member stays on `SubgridBlockEntity` as a delegate: `deferDrop`, `cancelPendingDrop`, `drainReadyDrops`,
`pendingDropCount`, `hasActiveCaptures`, `captureStarted` and `captureEnded`. `PendingDrop` stays nested there too.
The state is still read from other places in `SubgridBlockEntity`:

- `removeIfLeftEmpty` (`:733`)
- `transform` and `transformMoved` (`:428`, `:439`)
- `turn` (`:524`)

`GridSaveItem` reads it as well (`:311`, `:383`). Moving about 80 lines leaves about 25 lines of delegates behind, and
the state ends up split across two classes. The plan turns down moving scheduled ticks for this same reason ("a class
of delegates and no clearer code", in "Left alone").

**What to do:** drop step 8. Or, if it stays, have the callers go to `PendingDrops` directly.

### M3. Step 14: `operatingServerSpace` must keep `cells.live = false`

`operatingServerSpace` differs from `buildFakeServerSpace` in two ways: the operated cell, and
`cells.live = false` (`VanillaBlockPiece.java:1133`). The plan names only the first.

`live` decides whether `afterItsCall` moves later work into a fresh `TickSpace` and applies it
(`VanillaBlockPiece.java:898-908`). This space is never applied: it only binds phantoms, through `boundPhantom`, the
tick candidate at `:179`, and `PieceClickDispatch` at `:464`. If `live` stays true, writes made later through a
phantom bound to it, such as a hopper filling a chest that never ticks or a listener's click, go into a space nobody
reads. That is the bug `afterItsCall` was added to fix.

**What to do:** say that the shared builder only covers `buildFakeSpace` and `buildClientFakeSpace`. Either leave
`operatingServerSpace` as it is, or have it set `live = false` after the shared builder. Name
`SubgridTickSpaceGameTest` and `SubgridHopperGameTest` in the check.

### M4. Step 4: prove the check catches a change of order

The plan says the parity scenes would catch a change in neighbour order. No one has seen that happen. `goal.md` asks
for every check to be seen to fail first, and the order of `neighborsOf` (`SubgridBlockEntity.java:1074-1081`) is the
one thing in this step that matters.

The step also can't be done the way it is written for `refreshSignals`. Its loop sets a local
(`space`, `:1321`) that a lambda can't capture, because it isn't effectively final.

**What to do:**

1. As a one-off, before the step, change the loop order in `neighborsOf` (z outer, or `Direction.values()` reversed)
   and run the redstone parity tests.
2. If one fails, the check is real. If none fails, say so in the plan, and add a unit test of
   `PlacedPiece.forEachCell`'s order. New tests are allowed; changing existing ones is not.
3. Leave the `refreshSignals` loop for step 7, or keep the fake space in a holder.

### M5. Step 22: take `relightChildren` out of the imports commit

Step 22 adds a logic change (merging `updateLight` and the periodic pass in `beginFrame`,
`SubgridVisual.java:269-276` and `:296-302`) to a commit that is otherwise only imports. That takes `SubgridVisual`
out of the `javap` check, which is the strongest check in the plan. It is also client-only code that no test covers.

**What to do:** make `relightChildren` a step of its own, or put it in step 17 next to the `isGearConnector` change
to `SubgridVisual`. Step 22 then stays `javap`-identical for every file.

## Minor

- **m1. Step 13:** the plan says that if the reflection failed, "only the piston animation would go". It would not. An
  `ExceptionInInitializerError` or `NoClassDefFoundError` is an `Error`, and `SubgridBlockEntity.clientTick` only
  catches `Exception` (`SubgridBlockEntity.java:786`). The network handler doesn't catch it either. The client tick
  would crash on the first moving piston. This can't happen on 1.21.1, so only the wording needs fixing.
- **m2. Step 11:** `applyChanges` reads `SubgridFakeCellGetter`'s private `be` field (`VanillaBlockPiece.java:1362`).
  Add it to the list of members that become package-private.
- **m3. Steps 5, 7, 8 and 10:** "moved unchanged" can't be done literally. `level` and `worldPosition` are protected
  fields of `BlockEntity`, which lives in another package, so a helper class that isn't a subclass can't read them
  (for example `resolve`, `:822-831`, and `flushPhantomData`, `:647-667`). Say that they become `getLevel()` and
  `getBlockPos()`, which return the same fields. In step 10, keep `markNeighbouringPhantomsDirty` last in
  `notifyUpdate` (`:1160`).
- **m4. Step 4:** Java 21 has no `case A a, B b ->` with bindings. Merging `placePieceCrossBoundary`'s two arms
  (`:326-335`) needs a small helper, `placeIn(grid, x, y, z)`. Say so, or drop the merge: it saves 3 lines.
- **m5. Step 3:** the reads removed from `FakeLevel.runBlockEvent` (`FakeLevel.java:294-296`) and the "AFTER"
  argument in `onNeighborChanged` (`VanillaBlockPiece.java:567-569`) run on every call, not only with debug on. They
  only read in-grid cells (`getPieceAt`), so taking them out is safe. The plan should say that this is the reason.
  Its stated reason holds only for the signal loop.
- **m6. Step 1:** the check `grep "TODO remove before release"` misses the "#TODO removal" reference
  (`ScaledVertexConsumer.java:29`) that the step also removes. Use `grep -rn "TODO" src/main`.
- **m7. "What doesn't change", Mixins:** mixins also call `markPhantomDataDirty` (`SyncedBlockEntityMixin`),
  `turnMovedQuietly` and `lookAroundMoved` (`SubLevelAssemblyHelperMixin:36,44`). List them next to
  `drawingPhantom` and `boundPhantom`, so step 10's delegate is known to be needed for a mixin too, not only for
  tests.
- **m8. Step 17:** make the slice helper `pushSlice(poseStack, slice)`, with `popPose` at the call site. A helper
  that takes a lambda would allocate one per slice per frame in `render` (`SubgridRenderer.java:59-65,107-115,119-162`).
- **m9. Missed, possible bug (for an issue, not this refactor):** scheduled piece ticks lose `priority` and `type` in
  three places:
  - on save and load (`SubgridBlockEntity.java:1569-1574`, `:1601`)
  - on a turn (`:531`)
  - through `scheduleCaptured` (`VanillaBlockPiece.java:1373`)

  A fluid tick reloaded with a null type runs both the block and the fluid tick (`VanillaBlockPiece.java:615-616`).
  Open an issue.
- **m10. Missed:** after step 15, `onRightClickBlock` is still about 200 lines (`SubgridEventHandler.java:161-396`),
  most of it the branching on what was clicked (`:261-366`). The step moves only the last 50 lines. Consider moving
  "which cell does this click land in" (`:261-374`) into `PiecePlacement` as well.
- **m11. Docs:** `docs/performance.md:49,53` names `runItemInteraction` in `SubgridEventHandler` and
  `VanillaBlockPiece.TickSpace`. Both move. Update those names or leave them knowingly. It is a history of the
  changes, so leaving them is fine if the plan says so.

## Counts

0 blockers, 5 majors, 11 minors. Step 23 is deferred.

## Round 2

A review of the revised plan at 012c1ab. As in round 1, I only read: no Gradle task was run and no code was changed.
Each round 1 finding was checked against the code, not only against what the plan now says.

**Verdict:** 0 blockers, 1 major, 4 minors. I approve the plan once R2-M1 is fixed. It is a small change to step 4's
test. The minors can be done during implementation.

### Round 1 findings, checked

- **Step 23:** deferred, and nothing depends on it. Resolved.
- **M1 (hop-loop merge):** dropped. Step 6 moves the two loops apart. It names both differences found in round 1:
  which level each loop reads (`SubgridBlockEntity.java:857` against `:960`), and the `setBlock` in the middle of the
  creating loop (`:385-386`). Resolved.
- **M2 (PendingDrops):** dropped. It is gone from the target structure and is listed under "Left alone". Resolved.
- **M3 (`operatingServerSpace`):** step 14 now covers only `buildFakeSpace` and `buildClientFakeSpace`, which are the
  same three lines over a `Level` (`VanillaBlockPiece.java:766-778`). It leaves `operatingServerSpace` and its
  `cells.live = false` (`:1133`) alone. Resolved. Step 13 is checked below.
- **M4 (neighbour order):** step 5 leaves the `refreshSignals` loop alone, and `rebuildAfterRemove` copies its index
  into a final local, which is right. Step 4 is the new check, and it doesn't fully cover the order: see R2-M1.
- **M5 (`relightChildren`):** moved into step 17. Steps 19 to 22 are now imports only and `javap`-identical. Resolved.
- **m1:** step 12 and the risk list now give the right failure (an `Error`, which `SubgridBlockEntity.java:786` doesn't
  catch). Resolved.
- **m2:** `SubgridFakeCellGetter.be` is now in step 10's list (it is read at `VanillaBlockPiece.java:1362`). Resolved.
- **m3:** steps 6, 7 and 9 now read the grid through `getLevel()` and `getBlockPos()`, and step 9 keeps
  `markNeighbouringPhantomsDirty` last in `notifyUpdate` (`:1160`). Resolved. One more access to widen: see R2-m3.
- **m4:** step 5 now uses a `placeIn` helper. Resolved.
- **m5:** step 3 now gives the right reason for each read it removes: `FakeLevel.java:294-296`, and
  `VanillaBlockPiece.java:555-569`. Resolved.
- **m6:** step 1's check is now `grep -rn "TODO" src/main`. Today the only matches are the eight markers and
  `ScaledVertexConsumer.java:29`, so an empty result does prove the step was done. Resolved.
- **m7:** "What doesn't change" now lists every member a mixin calls, and step 9 names `SyncedBlockEntityMixin`.
  Resolved.
- **m8:** `pushSlice`, with no lambda. Resolved.
- **m9:** opened as #118 and listed under "Left alone". Resolved.
- **m10 (declined):** I accept the decline. In `SubgridEventHandler.java:265-349`, every branch (outside placement,
  connector, plate target, wrench, the piece's own use, the item's use) ends the click with its own side effect and
  its own `return`. Pulling them out would need a result type, and the handler would still have to switch on it. The
  only part that could move cleanly is the choice of target cell (`:353-374`), which ends in assignments. It is about
  20 lines, too small to be worth a step.
- **m11:** `docs/performance.md` is kept as a record of its time, and the plan says so. Resolved.

### The two new test steps

- **Step 4 (neighbour order).** Reversing `Direction.values()` would be caught: a single-cell piece with neighbours on
  several sides is enough. A wrong cell order would only be caught in part. See R2-M1.
- **Step 13 (the `live` flag).** This step works. The order is right: it finds or adds the test before step 14. It
  requires the test to be seen to fail with the one-off applied. And it chose the right effect to look at: with `live`
  wrongly true, the only thing lost is the chest telling its comparator (`FakeServerLevel.java:241` →
  `afterItsCall`). The items still arrive, because the hopper writes them into the chest's block entity directly. So
  the existing hopper tests probably pass with the one-off, and the new comparator-and-lamp scene is what will catch
  it. See R2-m1 for how to make sure it fails.

### Major

#### R2-M1. Step 4: a 2 by 1 by 2 test piece can't see the y order

A footprint one cell tall gives the same cell order for x-y-z as for x-z-y, and for y-x-z as for x-y-z. So the new
unit test can't tell a `forEachCell` that walks y in the wrong place.

The real multi-cell pieces are shaft connectors. Their footprint is `w x w x d` (`ShaftConnectorPiece.java:48`), and
on the X axis it turns into `d x w x w` (`PlacedPiece.java:66-69`). There, y and z both span more than one cell, so
the y order does change which neighbour hears first. The one-off in step 4 also only moves z outermost; it never
moves y.

**What to do:**

- Give the test piece a footprint of 2 by 2 by 2, with neighbours placed so that every loop order gives a different
  `neighborsOf` sequence.
- Add "y and z swapped" to the one-off changes, and require the test to fail under each of them.

### Minor

- **R2-m1. Step 13:** for the new scene to fail with the one-off, the chest's phantom must first be built by the
  chest's own `tick`. That call binds it to an operating space (`VanillaBlockPiece.java:178-179`). If a reader built
  it first, it would be bound to a `TickSpace`, which `release` sets to not live, and the scene would pass either
  way. The scene must also judge by the comparator or the lamp, not by the items. If it doesn't fail at first, place
  the chest before the hopper. The step's "seen to fail" rule already catches this, so this is only a hint for the
  implementer.
- **R2-m2. Step 4:** a test-only `PieceDefinition` registers itself in the global `REGISTRY` from its constructor
  (`PieceDefinition.java:36`), so give it an id no real piece uses. A piece's footprint also turns with its facing's
  axis (`PlacedPiece.java:33`). Pick the facing on purpose, or a 2 by 2 by 2 footprint makes it not matter.
- **R2-m3. Step 6:** `GridResolver` calls `grid.outOfBounds`, which is private (`SubgridBlockEntity.java:1546`). Add it
  to what becomes package-private, as step 10 does for its classes, or use `!grid.inBounds(...)`. The compiler would
  catch this anyway.
- **R2-m4. Step 11:** the file list includes `v2/TickSpace`, but `TickSpace` calls none of the methods that move
  (`VanillaBlockPiece.java:216-251`); only `SubgridFakeCellGetter.afterItsCall` calls `applyChanges`. Drop it from the
  list, or say why it is there.

### Counts, round 2

0 blockers, 1 major (R2-M1), 4 minors. Step 23 is still deferred.

## Round 3

A check of the round 2 fixes in 7d1c72d, against the code. As before, I only read: no Gradle task was run and no
code was changed.

**Verdict: approved.** 0 blockers, 0 majors, 1 minor. Step 23 is still deferred.

- **R2-M1:** resolved. Step 4's test piece is now 2 by 2 by 2, with neighbours placed so that every loop order gives a
  different `neighborsOf` sequence. "y and z swapped" is now a third one-off change, and the test must fail under each
  of the three. A cube's footprint is the same after `PlacedPiece#rotate` on any axis (`PlacedPiece.java:66-69`), so
  the facing doesn't matter, as the plan says.
- **R2-m1:** resolved. Step 13's scene places the chest before the hopper, and judges by the comparator and the lamp.
- **R2-m2:** resolved. No piece uses the id `tinyblocks:test_cube_2` today (`grep -rn test_cube src` finds nothing).
- **R2-m3:** resolved. Step 6 makes `outOfBounds` package-private.
- **R2-m4:** resolved. `v2/TickSpace` is out of step 11's file list, and the plan says why `SubgridFakeCellGetter`
  stays in it: its `afterItsCall` calls `applyChanges`.

### Minor

- **R3-m1. Step 13:** placing the chest first sets the tick order only within one grid. Pieces tick in the order of
  the grid's `pieces` list, which is the order they were placed in (`SubgridBlockEntity.java:704`). If the chest and
  the hopper were in different grids, the order would depend on which grid ticks first. Build the scene with the
  chest and the hopper in the same grid. The step's "seen to fail" rule still guards against getting this wrong.
