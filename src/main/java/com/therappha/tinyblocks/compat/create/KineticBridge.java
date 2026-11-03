package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.mixin.create.RotationPropagatorAccessor;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeSpace;
import com.therappha.tinyblocks.v2.PhantomOwners;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Joins Create's kinetic network through a ShaftConnectorPiece: the plate on one face of a SubgridBlock
 * connects the one piece in its socket (inside the grid, ShaftConnectorPiece#socket) with the real kinetic block
 * touching that face.
 *
 * Create assumes every connection is between two single blocks next to each other in one level and one
 * coordinate frame: neighbours are found at pos.relative(face), the speed ratio comes from the difference of
 * the two positions, and a block entity's rotation source is stored as a BlockPos and re-read with
 * level.getBlockEntity(source). The plate breaks all three — it is wider than one cell, its phantom sits at
 * the anchor's virtual position in a fake space, and the real block on its face sits at a real position in the
 * real level — so the mixins in com.therappha.tinyblocks.mixin.create route exactly those lookups through here.
 */
public final class KineticBridge {

    private KineticBridge() {}

    /** A connector plate and the grid it sits in. */
    private record Connector(SubgridBlockEntity grid, PlacedPiece piece) {
        Direction face() { return piece.facing; }
    }

    @Nullable
    private static PlacedPiece pieceOf(BlockEntity be) {
        PhantomOwners.Owner owner = PhantomOwners.of(be);
        SubgridBlockEntity grid = owner == null ? null : owner.grid();
        return grid == null ? null : grid.getPieceAt(owner.cell().getX(), owner.cell().getY(), owner.cell().getZ());
    }

    /** The plate being removed right now, see whileRemoving. */
    private static final ThreadLocal<Connector> REMOVING = new ThreadLocal<>();

    /** Runs {@code removal} with {@code piece} still known as a plate, though it is no longer in its grid. */
    static void whileRemoving(SubgridBlockEntity grid, PlacedPiece piece, Runnable removal) {
        REMOVING.set(new Connector(grid, piece));
        try {
            removal.run();
        } finally {
            REMOVING.remove();
        }
    }

    @Nullable
    private static Connector connectorOf(KineticBlockEntity phantom) {
        Connector removing = REMOVING.get();
        if (removing != null && removing.piece().runtimeBlockEntity == phantom) return removing;
        PhantomOwners.Owner owner = PhantomOwners.of(phantom);
        SubgridBlockEntity grid = owner == null ? null : owner.grid();
        PlacedPiece piece = pieceOf(phantom);
        return grid != null && piece != null && ShaftConnectorPiece.faceOf(piece) != null ? new Connector(grid, piece) : null;
    }

    /** The connector phantom on {@code face} of {@code grid}, bound to a fake space of that grid, or null. */
    @Nullable
    static KineticBlockEntity connectorOn(SubgridBlockEntity grid, Direction face, ServerLevel level) {
        for (PlacedPiece piece : grid.getPieces()) {
            if (ShaftConnectorPiece.faceOf(piece) == face
                    && VanillaBlockPiece.boundPhantom(piece, grid, level) instanceof KineticBlockEntity kinetic) {
                return kinetic;
            }
        }
        return null;
    }

    /**
     * What a plate meets across its face: the real kinetic block there, or, when that is another SubgridBlock, the
     * piece in the cell its socket line meets (ShaftConnectorPiece#partnerCell) — of the same size the cell right
     * across; of another size only that grid's own connector facing back. Null when nothing kinetic is there.
     */
    @Nullable
    private static KineticBlockEntity partnerOf(Connector plate) {
        if (!(plate.grid().getLevel() instanceof ServerLevel level)) return null;
        BlockPos across = plate.grid().getBlockPos().relative(plate.face());
        BlockEntity there = level.getBlockEntity(across);
        if (there instanceof SubgridBlockEntity other) {
            BlockPos cell = ShaftConnectorPiece.partnerCell(plate.face(), plate.grid().getGridSize(), other.getGridSize());
            PlacedPiece piece = other.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
            if (piece == null) return null;
            // Across sizes only a connector facing back joins: two frames meet only where both sides say so.
            if (other.getGridSize() != plate.grid().getGridSize()
                    && ShaftConnectorPiece.faceOf(piece) != plate.face().getOpposite()) {
                return null;
            }
            return VanillaBlockPiece.boundPhantom(piece, other, level) instanceof KineticBlockEntity k ? k : null;
        }
        return there instanceof KineticBlockEntity k ? k : null;
    }

    /** Whether {@code be} is a plate's partner across its face (see partnerOf). */
    private static boolean isPartner(Connector plate, KineticBlockEntity be) {
        return partnerOf(plate) == be;
    }

    /** The grid across a plate's face, when it is one of another size. */
    @Nullable
    private static SubgridBlockEntity otherSizeAcross(Connector plate) {
        Level level = plate.grid().getLevel();
        return level != null && level.getBlockEntity(plate.grid().getBlockPos().relative(plate.face())) instanceof SubgridBlockEntity other
                && other.getGridSize() != plate.grid().getGridSize() ? other : null;
    }

    /**
     * findConnectedNeighbour for a plate looking across into a grid of another size, at that grid's connector: Create
     * looks the position up in the plate's own fake space, whose frame the other grid's virtual positions are not in.
     */
    @Nullable
    public static KineticBlockEntity acrossGrids(KineticBlockEntity current, BlockPos neighbourPos) {
        Connector plate = isPhantom(current) ? connectorOf(current) : null;
        KineticBlockEntity other = plate != null && otherSizeAcross(plate) != null ? partnerOf(plate) : null;
        return other != null && other.getBlockPos().equals(neighbourPos) ? other : null;
    }

    @Nullable
    private static Direction directionBetween(BlockPos from, BlockPos to) {
        BlockPos diff = to.subtract(from);
        if (diff.distManhattan(BlockPos.ZERO) != 1) return null;
        return Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
    }

    private static boolean isPhantom(KineticBlockEntity be) {
        return PhantomOwners.of(be) != null || be.getLevel() instanceof FakeSpace;
    }

    private static boolean hasShaftTowards(KineticBlockEntity be, Direction face) {
        BlockState state = be.getBlockState();
        return state.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(be.getLevel(), be.getBlockPos(), state, face);
    }

    /** Create's axis-to-axis speed ratio for two ends joined through {@code towardsTo} (from's side). */
    private static float axisRatio(KineticBlockEntity from, KineticBlockEntity to, Direction towardsTo) {
        if (!hasShaftTowards(from, towardsTo) || !hasShaftTowards(to, towardsTo.getOpposite())) return 0;
        float toModifier = RotationPropagatorAccessor.tinyblocks$getAxisModifier(to, towardsTo.getOpposite());
        if (toModifier != 0) toModifier = 1 / toModifier;
        return RotationPropagatorAccessor.tinyblocks$getAxisModifier(from, towardsTo) * toModifier;
    }

    /**
     * Create walks a network through each block entity's own level. A phantom can be left bound to the real
     * level (anything outside a tick may build or look it up there), where its virtual neighbours don't exist,
     * so the walk would stop at it. Rebinds it to a fake space of its grid before Create looks around it.
     */
    public static void bindPhantom(KineticBlockEntity be) {
        if (be.getLevel() instanceof FakeSpace) return;
        PhantomOwners.Owner owner = PhantomOwners.of(be);
        SubgridBlockEntity grid = owner == null ? null : owner.grid();
        PlacedPiece piece = pieceOf(be);
        if (grid != null && piece != null && grid.getLevel() instanceof ServerLevel level) {
            VanillaBlockPiece.boundPhantom(piece, grid, level);
        }
    }

    // --- RotationPropagator.findConnectedNeighbour ---------------------------------------------------------

    /**
     * A real kinetic block asking about the SubgridBlock it touches: the connector on that face. Phantoms need
     * nothing here — their fake space already answers a lookup past the edge with the real block entity, and a
     * lookup of any cell of the plate with the plate's phantom.
     */
    @Nullable
    public static KineticBlockEntity connectorBehind(KineticBlockEntity current, BlockPos neighbourPos) {
        if (isPhantom(current) || !(current.getLevel() instanceof ServerLevel level)) return null;
        if (!(level.getBlockEntity(neighbourPos) instanceof SubgridBlockEntity grid)) return null;
        Direction face = directionBetween(neighbourPos, current.getBlockPos());
        return face == null ? null : connectorOn(grid, face, level);
    }

    // --- RotationPropagator.handleRemoved -----------------------------------------------------------------

    /**
     * handleRemoved stops whatever a removed block was driving by looking at its neighbours, and skips any that
     * is not a kinetic block — a SubgridBlock among them. The plates on those SubgridBlocks that took their
     * rotation from the removed real block.
     */
    public static List<KineticBlockEntity> platesDrivenBy(Level level, BlockPos pos, @Nullable KineticBlockEntity removed) {
        if (removed == null || level.isClientSide || !(level instanceof ServerLevel server) || isPhantom(removed)
                || removed.getTheoreticalSpeed() == 0) {
            return List.of();
        }
        List<KineticBlockEntity> plates = new java.util.ArrayList<>();
        for (Direction towardsGrid : Direction.values()) {
            if (server.getBlockEntity(pos.relative(towardsGrid)) instanceof SubgridBlockEntity grid) {
                KineticBlockEntity plate = connectorOn(grid, towardsGrid.getOpposite(), server);
                if (plate != null && plate.hasSource() && plate.source.equals(pos)) plates.add(plate);
            }
        }
        return plates;
    }

    // --- RotationPropagator.getPotentialNeighbourLocations -------------------------------------------------

    /**
     * Create only looks next to the funnel's anchor, which is one corner of a box several cells big. A funnel
     * reaches its socket (ShaftConnectorPiece#socket) on one end and, on the other, the real block past its face —
     * asked for as the virtual cell just outside the face, which the fake space answers with that real block.
     */
    public static List<BlockPos> withFunnelEnds(KineticBlockEntity be, List<BlockPos> locations) {
        Connector connector = connectorOf(be);
        if (connector == null) return locations;
        SubgridBlockEntity grid = connector.grid();
        BlockPos socket = ShaftConnectorPiece.socket(connector.face(), grid.getGridSize());
        // A gear has no socket: it reaches out from its own cells, and in to the cogwheels it meshes with.
        BlockPos outside = socket != null ? socket : connector.piece().anchor;
        while (grid.inBounds(outside.getX(), outside.getY(), outside.getZ())) outside = outside.relative(connector.face());
        BlockPos far = grid.virtualPosOf(outside);
        if (otherSizeAcross(connector) != null) {
            // Another size across: its piece sits in its own frame, not next to this one's.
            KineticBlockEntity partner = partnerOf(connector);
            far = partner == null ? null : partner.getBlockPos();
        }
        locations = new java.util.ArrayList<>(locations);
        List<BlockPos> ends = new java.util.ArrayList<>();
        ends.add(far);
        if (socket != null) {
            ends.add(grid.virtualPosOf(socket));
        } else {
            ShaftConnectorPiece.meshCells(connector.face(), grid.getGridSize()).forEach(cell -> ends.add(grid.virtualPosOf(cell)));
        }
        for (BlockPos end : ends) {
            if (end != null && !locations.contains(end)) locations.add(end);
        }
        return locations;
    }

    // --- RotationPropagator.getRotationSpeedModifier -------------------------------------------------------

    /**
     * Speed ratio across a plate: between the plate and the real block on its face, and between the plate and the
     * one piece in its socket. Create's own axis-to-axis rule, with the direction taken from the plate's face
     * instead of from the difference of the positions. 0 for any other pair that involves a plate or crosses
     * between the real and the virtual frame, so Create never reads that difference as an adjacency. Null when
     * Create's own rules apply.
     */
    @Nullable
    public static Float bridgedModifier(KineticBlockEntity from, KineticBlockEntity to) {
        boolean fromPhantom = isPhantom(from);
        boolean toPhantom = isPhantom(to);
        Connector fromPlate = fromPhantom ? connectorOf(from) : null;
        Connector toPlate = toPhantom ? connectorOf(to) : null;

        if (fromPhantom != toPhantom) {
            KineticBlockEntity real = fromPhantom ? to : from;
            Connector plate = fromPhantom ? fromPlate : toPlate;
            if (plate == null || !plate.face().equals(directionBetween(plate.grid().getBlockPos(), real.getBlockPos()))) {
                return 0f;
            }
            return axisRatio(from, to, fromPhantom ? plate.face() : plate.face().getOpposite());
        }

        if (fromPhantom) {
            // A plate and what it meets across its face: a piece (or the plate) of the grid next to it.
            if (fromPlate != null && isPartner(fromPlate, to)) return axisRatio(from, to, fromPlate.face());
            if (toPlate != null && isPartner(toPlate, from)) return axisRatio(from, to, toPlate.face().getOpposite());
        }

        if (!fromPhantom || (fromPlate == null) == (toPlate == null)) {
            // Both real, or two phantoms that are both ordinary: Create's own rules. Two plates never connect.
            return fromPlate == null ? null : 0f;
        }
        Connector plate = fromPlate != null ? fromPlate : toPlate;
        KineticBlockEntity inner = fromPlate != null ? to : from;
        PhantomOwners.Owner innerOwner = PhantomOwners.of(inner);
        if (innerOwner == null || innerOwner.grid() != plate.grid()) return 0f;
        if (ShaftConnectorPiece.isGear(plate.grid().getGridSize())) {
            return meshRatio(plate, innerOwner.cell(), inner, fromPlate != null);
        }
        if (!ShaftConnectorPiece.isSocket(plate.piece(), innerOwner.cell(), plate.grid().getGridSize())) return 0f;
        return axisRatio(from, to, fromPlate != null ? plate.face().getOpposite() : plate.face());
    }

    /**
     * Speed ratio between a gear connector and a tiny cogwheel in {@code cell}: a small one on its sides, turning on
     * its axis, turns twice as fast the other way — Create's large-to-small ratio. 0 for anything else, a tiny shaft
     * against it included.
     */
    private static float meshRatio(Connector gear, BlockPos cell, KineticBlockEntity cog, boolean fromGear) {
        BlockState state = cog.getBlockState();
        if (!com.simibubi.create.content.kinetics.simpleRelays.ICogWheel.isSmallCog(state)
                || !(state.getBlock() instanceof IRotate rotate)
                || rotate.getRotationAxis(state) != gear.face().getAxis()
                || !ShaftConnectorPiece.meshCells(gear.face(), gear.grid().getGridSize()).contains(cell)) {
            return 0f;
        }
        return fromGear ? -2f : -0.5f;
    }

    // --- KineticBlockEntity source lookups -----------------------------------------------------------------

    /**
     * What {@code self}'s stored rotation source refers to. The source is the other end's own position, in the
     * other end's frame: a plate driven from outside stores a real position, a real block driven from inside
     * stores a virtual one. {@code found} is what self's own level answered for that position.
     */
    @Nullable
    public static BlockEntity resolveSource(KineticBlockEntity self, BlockPos source, @Nullable BlockEntity found) {
        if (isPhantom(self)) {
            // Checked before trusting `found`: the fake space reads a position of another frame (a real one, or
            // a grid of another size) as one of its own.
            Connector plate = connectorOf(self);
            KineticBlockEntity across = plate != null ? partnerOf(plate) : null;
            if (across != null && across.getBlockPos().equals(source)) return across;
            return found;
        }
        if (found instanceof KineticBlockEntity || !(self.getLevel() instanceof ServerLevel level)) return found;
        for (Direction face : Direction.values()) {
            if (level.getBlockEntity(self.getBlockPos().relative(face)) instanceof SubgridBlockEntity grid) {
                KineticBlockEntity connector = connectorOn(grid, face.getOpposite(), level);
                if (connector != null && connector.getBlockPos().equals(source)) return connector;
            }
        }
        return found;
    }

    // --- DirectionalShaftHalvesBlockEntity.getSourceFacing ------------------------------------------------

    /**
     * The face of a real clutch or gearshift its rotation comes in by, when it comes from a plate: Create reads that side
     * off the difference between its position and its source's, and a plate's is in the grid's frame. Taken for some
     * other side, a powered gearshift reversed its input, clashed with it and broke; a clutch cut the wrong side.
     * Null when Create's own reading holds.
     */
    @Nullable
    public static Direction sourceFaceAcross(KineticBlockEntity self) {
        BlockPos source = self.source;
        if (source == null || isPhantom(self) || !(self.getLevel() instanceof ServerLevel level)) return null;
        for (Direction face : Direction.values()) {
            if (level.getBlockEntity(self.getBlockPos().relative(face)) instanceof SubgridBlockEntity grid) {
                KineticBlockEntity connector = connectorOn(grid, face.getOpposite(), level);
                if (connector != null && connector.getBlockPos().equals(source)) return face;
            }
        }
        return null;
    }

    // --- RotationPropagator.propagateMissingSource ---------------------------------------------------------

    /**
     * propagateMissingSource walks the network breadth-first by position, re-reading every position with the
     * level of the block entity it started from. Past a plate that position belongs to the other frame, so
     * while the walk runs every neighbour it discovers is remembered and handed back for its own position.
     */
    private static final ThreadLocal<Map<BlockPos, KineticBlockEntity>> WALK = new ThreadLocal<>();

    public static void beginWalk() { WALK.set(new HashMap<>()); }

    public static void endWalk() { WALK.remove(); }

    public static void rememberNeighbours(List<KineticBlockEntity> neighbours) {
        Map<BlockPos, KineticBlockEntity> seen = WALK.get();
        if (seen == null) return;
        for (KineticBlockEntity neighbour : neighbours) seen.put(neighbour.getBlockPos(), neighbour);
    }

    @Nullable
    public static BlockEntity walkLookup(BlockPos pos, @Nullable BlockEntity found) {
        Map<BlockPos, KineticBlockEntity> seen = WALK.get();
        KineticBlockEntity remembered = seen == null ? null : seen.get(pos);
        return remembered != null ? remembered : found;
    }

    // --- TorquePropagator ----------------------------------------------------------------------------------

    /**
     * Create keys its kinetic networks by level instance. A phantom's fake space is rebuilt on every tick, so
     * without this each tick got a fresh, empty network map (the stress of a network inside a grid never
     * added up, and every fake space leaked one map); and a network running through a plate was split in
     * two. Every fake space files its networks under the real level it belongs to.
     */
    public static Level networkLevel(Level level) {
        return level instanceof FakeSpace fake ? fake.realLevel() : level;
    }
}
