package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fluids in a grid as a player meets them: a tank's client copy draws what it was sent.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridFluidAimGameTest {

    private static final String PLATFORM = "empty_platform";

    /**
     * What PhantomSync sends a client for a tank, loaded as the client loads it: the fluid the tank draws moves to what
     * it holds. Loaded as saved data, as it used to be, Create reads it as a save and only a rebuilt tank showed it.
     */
    @GameTest(template = PLATFORM)
    public void aTanksClientCopyDrawsTheFluidItIsSent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = new BlockPos(4, 4, 4);
        SubgridBlockEntity server = SubgridTestSupport.placeSubgrid(helper, new BlockPos(1, 1, 2));
        SubgridBlockEntity client = SubgridTestSupport.placeSubgrid(helper, new BlockPos(4, 1, 2));
        PlacedPiece serverPiece = SubgridTestSupport.placePiece(server, level, cell, AllBlocks.FLUID_TANK.getDefaultState());
        PlacedPiece clientPiece = SubgridTestSupport.placePiece(client, level, cell, AllBlocks.FLUID_TANK.getDefaultState());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    if (!(SubgridTestSupport.blockEntityOf(server, serverPiece, level) instanceof FluidTankBlockEntity sent)
                            || !(SubgridTestSupport.blockEntityOf(client, clientPiece, level) instanceof FluidTankBlockEntity drawn)) {
                        throw new IllegalStateException("a tank piece has no tank block entity");
                    }
                    // The client copy as it was: an empty tank, drawn empty.
                    VanillaBlockPiece.applyPhantomData(client, cell, sent.getUpdateTag(level.registryAccess()), true, level);
                    sent.getTankInventory().fill(new FluidStack(Fluids.WATER, sent.getTankInventory().getCapacity() / 2),
                            IFluidHandler.FluidAction.EXECUTE);
                    VanillaBlockPiece.applyPhantomData(client, cell, sent.getUpdateTag(level.registryAccess()), true, level);
                    float target = drawn.getFluidLevel().getChaseTarget();
                    helper.assertTrue(Math.abs(target - 0.5f) < 0.01f, "the client tank draws its fluid at " + target + ", not at half");
                })
                .thenSucceed();
    }
}
