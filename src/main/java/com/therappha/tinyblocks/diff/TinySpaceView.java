package com.therappha.tinyblocks.diff;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Cells laid over the grids of size {@code gridSize} from the real block at {@code origin}. {@code seam} shifts the
 * cells inside the grids, so the same space can be cut by grid edges in different places.
 *
 * What is "drawn" is what the grid last sent its clients: each grid is watched (SubgridBlockEntity#syncObserver) and a
 * copy of its pieces is taken when it syncs. A piece that changed without a sync still shows its old state there.
 */
public class TinySpaceView implements SpaceView {

    protected final ServerLevel level;
    protected final BlockPos origin;
    protected final int gridSize;
    protected final BlockPos seam;

    private final Map<SubgridBlockEntity, Map<BlockPos, BlockState>> sent = new IdentityHashMap<>();

    public TinySpaceView(ServerLevel level, BlockPos origin, int gridSize, BlockPos seam) {
        this.level = level;
        this.origin = origin;
        this.gridSize = gridSize;
        this.seam = seam;
    }

    public BlockPos gridPos(BlockPos cell) {
        return origin.offset(Math.floorDiv(cell.getX() + seam.getX(), gridSize),
                Math.floorDiv(cell.getY() + seam.getY(), gridSize),
                Math.floorDiv(cell.getZ() + seam.getZ(), gridSize));
    }

    public BlockPos local(BlockPos cell) {
        return new BlockPos(Math.floorMod(cell.getX() + seam.getX(), gridSize),
                Math.floorMod(cell.getY() + seam.getY(), gridSize),
                Math.floorMod(cell.getZ() + seam.getZ(), gridSize));
    }

    @Nullable
    public SubgridBlockEntity grid(BlockPos cell) {
        return level.getBlockEntity(gridPos(cell)) instanceof SubgridBlockEntity be && be.getGridSize() == gridSize ? be : null;
    }

    @Nullable
    public PlacedPiece piece(BlockPos cell) {
        SubgridBlockEntity be = grid(cell);
        if (be == null) return null;
        BlockPos at = local(cell);
        return be.getPieceAt(at.getX(), at.getY(), at.getZ());
    }

    @Override
    public BlockState state(BlockPos cell) {
        PlacedPiece piece = piece(cell);
        return piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
    }

    /** From now on, what {@code be} sends is recorded. Until it syncs, a client has what it holds now. */
    public void watch(SubgridBlockEntity be) {
        if (sent.containsKey(be)) return;
        Map<BlockPos, BlockState> copy = new HashMap<>();
        sent.put(be, copy);
        copyPieces(be, copy);
        be.syncObserver = changed -> {
            if (changed == null) {
                copyPieces(be, copy);
                return;
            }
            PlacedPiece piece = be.getPieceAt(changed.getX(), changed.getY(), changed.getZ());
            if (piece != null) copy.put(changed, piece.definition.renderState(piece));
            else copy.remove(changed);
        };
    }

    private static void copyPieces(SubgridBlockEntity be, Map<BlockPos, BlockState> into) {
        into.clear();
        for (PlacedPiece piece : be.getPieces()) into.put(piece.anchor, piece.definition.renderState(piece));
    }

    @Override
    public BlockState drawn(BlockPos cell) {
        SubgridBlockEntity be = grid(cell);
        if (be == null) return Blocks.AIR.defaultBlockState();
        watch(be);
        return sent.get(be).getOrDefault(local(cell), Blocks.AIR.defaultBlockState());
    }

    @Override
    public BlockEntity blockEntity(BlockPos cell) {
        PlacedPiece piece = piece(cell);
        if (piece == null || !state(cell).hasBlockEntity()) return null;
        return VanillaBlockPiece.boundPhantom(piece, grid(cell), level);
    }

    @Override
    public int analogSignal(BlockPos cell) {
        SubgridBlockEntity be = grid(cell);
        if (be == null) return 0;
        FakeLevel space = VanillaBlockPiece.buildFakeSpace(be, level);
        return state(cell).getAnalogOutputSignal(space, space.world(local(cell)));
    }
}
