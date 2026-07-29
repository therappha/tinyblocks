package com.therappha.tinyblocks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeCellGetter;
import com.therappha.tinyblocks.v2.FakeLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class TinyBlocksCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tinyblocks")
                .then(Commands.literal("status").executes(TinyBlocksCommand::executeStatus))
                .then(Commands.literal("v2test").executes(TinyBlocksCommand::executeV2Test))
                .then(Commands.literal("v2test2").executes(TinyBlocksCommand::executeV2Test2))
                .then(Commands.literal("v2test3").executes(TinyBlocksCommand::executeV2Test3))
                .then(Commands.literal("help").executes(TinyBlocksCommand::executeHelp))
        );
    }

    /**
     * v2 prototype checkpoint: proves real vanilla BlockState methods (getSignal,
     * getDestroySpeed) run correctly against a fake, in-memory position space instead of a
     * real chunk — no reimplementation of redstone/hardness logic on our side.
     */
    private static int executeV2Test(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        FakeCellGetter fake = new FakeCellGetter();
        BlockPos redstoneBlockPos = new BlockPos(0, 0, 0);
        BlockPos stonePos = new BlockPos(1, 0, 0);
        fake.set(redstoneBlockPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        fake.set(stonePos, Blocks.STONE.defaultBlockState());

        int signal = fake.getBlockState(redstoneBlockPos).getSignal(fake, redstoneBlockPos, Direction.EAST);
        float destroySpeed = fake.getBlockState(stonePos).getDestroySpeed(fake, stonePos);

        source.sendSuccess(() -> Component.literal(
            "[v2test] redstone_block.getSignal(fake, EAST) = " + signal + " (expected 15)"
        ), false);
        source.sendSuccess(() -> Component.literal(
            "[v2test] stone.getDestroySpeed(fake) = " + destroySpeed + " (expected 1.5)"
        ), false);
        source.sendSuccess(() -> Component.literal(
            signal == 15 && destroySpeed == 1.5f
                ? "[v2test] PASS — real vanilla block methods work against the fake position space"
                : "[v2test] FAIL — values don't match vanilla defaults"
        ), false);

        return signal;
    }

    /**
     * v2 prototype checkpoint 2: proves a real vanilla block's useWithoutItem() — which needs
     * a genuine Level, not just BlockGetter — runs correctly through FakeLevel against a fake
     * position. Flips a real lever and reads its POWERED state back afterward.
     */
    private static int executeV2Test2(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        FakeCellGetter fake = new FakeCellGetter();
        BlockPos leverPos = new BlockPos(0, 0, 0);
        fake.set(leverPos, Blocks.LEVER.defaultBlockState());

        FakeLevel fakeLevel = new FakeLevel(source.getLevel(), fake);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leverPos), Direction.UP, leverPos, false);

        boolean before = fake.getBlockState(leverPos).getValue(BlockStateProperties.POWERED);
        InteractionResult result = fake.getBlockState(leverPos).useWithoutItem(fakeLevel, player, hit);
        boolean after = fake.getBlockState(leverPos).getValue(BlockStateProperties.POWERED);

        source.sendSuccess(() -> Component.literal(
            "[v2test2] lever.useWithoutItem() -> " + result
        ), false);
        source.sendSuccess(() -> Component.literal(
            "[v2test2] POWERED before=" + before + " after=" + after
        ), false);
        source.sendSuccess(() -> Component.literal(
            !before && after
                ? "[v2test2] PASS — real vanilla interaction logic flipped state through FakeLevel"
                : "[v2test2] FAIL — state didn't flip as expected"
        ), false);

        return after ? 1 : 0;
    }

    /**
     * v2 prototype checkpoint 3: proves a chain of real vanilla blocks (lever -> redstone wire
     * -> redstone lamp) propagates correctly through repeated calls to the block's own
     * handleNeighborChanged() — no scheduled tick involved, we drive the cascade ourselves.
     * Real wire power calculation and lamp lighting logic, zero reimplementation.
     */
    private static int executeV2Test3(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        FakeCellGetter fake = new FakeCellGetter();
        BlockPos leverPos = new BlockPos(0, 0, 0);
        BlockPos wirePos = new BlockPos(1, 0, 0);
        BlockPos lampPos = new BlockPos(2, 0, 0);
        // Lever and redstone wire both require a solid supporting block beneath them to
        // survive canSurvive() checks — without this they get destroyed (replaced with air)
        // the moment handleNeighborChanged runs, which is why getValue(POWER) crashed before.
        fake.set(leverPos.below(), Blocks.STONE.defaultBlockState());
        fake.set(wirePos.below(), Blocks.STONE.defaultBlockState());
        fake.set(lampPos.below(), Blocks.STONE.defaultBlockState());
        fake.set(leverPos, Blocks.LEVER.defaultBlockState());
        fake.set(wirePos, Blocks.REDSTONE_WIRE.defaultBlockState());
        fake.set(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());

        FakeLevel fakeLevel = new FakeLevel(source.getLevel(), fake);

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leverPos), Direction.UP, leverPos, false);
        fake.getBlockState(leverPos).useWithoutItem(fakeLevel, player, hit);

        fake.getBlockState(wirePos).handleNeighborChanged(fakeLevel, wirePos, Blocks.LEVER, leverPos, false);
        int wirePower = fake.getBlockState(wirePos).getValue(BlockStateProperties.POWER);

        fake.getBlockState(lampPos).handleNeighborChanged(fakeLevel, lampPos, Blocks.REDSTONE_WIRE, wirePos, false);
        boolean lampLit = fake.getBlockState(lampPos).getValue(BlockStateProperties.LIT);

        source.sendSuccess(() -> Component.literal(
            "[v2test3] lever ON -> wire.POWER = " + wirePower + " (expected 15)"
        ), false);
        source.sendSuccess(() -> Component.literal(
            "[v2test3] wire -> lamp.LIT = " + lampLit + " (expected true, or false if it needs a scheduled tick)"
        ), false);
        source.sendSuccess(() -> Component.literal(
            wirePower == 15
                ? "[v2test3] PASS (wire) — real redstone power calculation ran through FakeLevel"
                : "[v2test3] FAIL (wire) — power didn't propagate as expected"
        ), false);

        return wirePower;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[TinyGears] Commands:\n" +
            "  /tinyblocks status — info about the SubgridBlock you're looking at\n" +
            "  /tinyblocks help   — this message"
        ), false);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr)) {
            source.sendFailure(Component.literal("Not looking at a block"));
            return 0;
        }

        BlockPos pos = bhr.getBlockPos();
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof SubgridBlock)) {
            source.sendFailure(Component.literal("Not looking at a SubgridBlock (looking at: " + pos.toShortString() + ")"));
            return 0;
        }

        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) {
            source.sendFailure(Component.literal("No BlockEntity at " + pos.toShortString()));
            return 0;
        }

        List<PlacedPiece> pieces = be.getPieces();
        source.sendSuccess(() -> Component.literal(
            "SubgridBlock at " + pos.toShortString() + " — " + pieces.size() + " piece(s):"
        ), false);

        for (int i = 0; i < pieces.size(); i++) {
            PlacedPiece p = pieces.get(i);
            int fi = i;
            source.sendSuccess(() -> Component.literal(String.format(
                "  [%d] %s  pos=(%d,%d,%d)  facing=%s",
                fi,
                p.definition.id().getPath(),
                p.anchor.getX(), p.anchor.getY(), p.anchor.getZ(),
                p.facing.getName()
            )), false);
        }

        return pieces.size();
    }
}
