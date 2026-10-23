package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issue #58: pick block on a piece returns the piece's own block, never the host SubgridBlock. */
class PieceCloneStackTest {

    private static ItemStack cloneOf(BlockState state) {
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, BlockPos.ZERO, Direction.NORTH);
        piece.runtimeState = state;
        return VanillaBlockPiece.INSTANCE.cloneStack(piece, null, null, BlockPos.ZERO, null);
    }

    @Test
    void pickBlockGivesThePiecesOwnBlock() {
        assertEquals(Items.STONE, cloneOf(Blocks.STONE.defaultBlockState()).getItem());
        assertEquals(Items.OAK_LOG, cloneOf(Blocks.OAK_LOG.defaultBlockState()).getItem());
    }

    @Test
    void pickBlockNeverReturnsTheSubgridHost() {
        ItemStack stack = cloneOf(Blocks.STONE.defaultBlockState());
        assertNotEquals(Registration.SUBGRID_BLOCK.get().asItem(), stack.getItem());
    }

    @Test
    void nothingToPickOnAnAirPiece() {
        assertTrue(cloneOf(Blocks.AIR.defaultBlockState()).isEmpty());
    }
}
