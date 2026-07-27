package com.therappha.tinyblocks.subgrid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class SubgridBlock extends BaseEntityBlock {

    /** Valid grid sizes: powers of 2 from 2 to 16. Each cell maps exactly to whole 1/16 pixels. */
    public static final List<Integer> VALID_SIZES = List.of(2, 4, 8, 16);

    private static final Codec<Integer> GRID_SIZE_CODEC = Codec.INT.validate(v ->
            VALID_SIZES.contains(v)
                    ? com.mojang.serialization.DataResult.success(v)
                    : com.mojang.serialization.DataResult.error(() -> "grid_size must be one of " + VALID_SIZES + ", got " + v));

    public static final MapCodec<SubgridBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Properties.CODEC.fieldOf("properties").forGetter(SubgridBlock::properties),
            GRID_SIZE_CODEC.fieldOf("grid_size").forGetter(b -> b.gridSize)
    ).apply(i, SubgridBlock::new));

    /** Grid resolution: 2, 4, 8, or 16. Determines how many cells fit per block axis. */
    public final int gridSize;

    public SubgridBlock(Properties properties, int gridSize) {
        super(properties);
        this.gridSize = gridSize;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return Shapes.empty();
        return be.getCachedShape();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return Shapes.empty();
        return be.getCachedShape();
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return 0f;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return 0f;

        Vec3 hitLoc = bhr.getLocation();
        Direction face = bhr.getDirection();
        int gs = be.gridSize;
        int max = gs - 1;
        double nudge = 0.5 / gs;
        int gx = Mth.clamp((int)(((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gs), 0, max);
        int gy = Mth.clamp((int)(((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gs), 0, max);
        int gz = Mth.clamp((int)(((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gs), 0, max);

        PlacedPiece piece = be.getPieceAt(gx, gy, gz);
        if (piece == null) return 0f;

        float hardness = piece.definition.destroyTime();
        if (hardness < 0) return 0f;

        BlockState renderState = piece.definition.renderState(piece.axis);
        float digSpeed = player.getDigSpeed(renderState, pos);
        boolean correct = !piece.definition.requiresCorrectTool() || player.hasCorrectToolForDrops(renderState);
        return digSpeed / hardness / (correct ? 30f : 100f);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, Registration.SUBGRID_BLOCK_ENTITY.get(),
            (lvl, pos, blockState, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return InteractionResult.PASS;

        Vec3 hitLoc = hit.getLocation();
        Direction face = hit.getDirection();
        int gs = be.gridSize;
        int max = gs - 1;
        double nudge = 0.5 / gs;
        int gx = Mth.clamp((int)(((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gs), 0, max);
        int gy = Mth.clamp((int)(((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gs), 0, max);
        int gz = Mth.clamp((int)(((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gs), 0, max);

        PlacedPiece piece = be.getPieceAt(gx, gy, gz);
        if (piece == null) return InteractionResult.PASS;

        return piece.definition.onUse(piece, level, pos, player, hit);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SubgridBlockEntity sg) {
            sg.notifyUpdate();
        }
    }
}
