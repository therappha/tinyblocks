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
