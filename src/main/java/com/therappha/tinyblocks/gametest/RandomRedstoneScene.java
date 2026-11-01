package com.therappha.tinyblocks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A space of connected redstone made from a seed, to run in the world and in grids (ParityRun) and catch what nobody
 * thought of testing. Circuits are walked, not sprinkled: each starts at a source and goes on through dust, climbs and
 * drops, repeaters, comparators and torches to something that reacts; later ones cross the earlier ones. Hoppers bring
 * items into it. Then a random script flips, breaks, places and fills things. The same seed makes the same scene.
 *
 * Left out, since the world itself wouldn't repeat them: random ticks, daylight, entities. BUD and quasi-connectivity
 * contraptions aren't built on purpose.
 */
final class RandomRedstoneScene {

    private static final int SIZE = 16, MAX_Y = 7;
    private static final BlockState WIRE = Blocks.REDSTONE_WIRE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final List<Block> SOLID = List.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS);
    private static final List<Block> CLEAR = List.of(Blocks.GLOWSTONE, Blocks.GLASS, Blocks.SEA_LANTERN);
    private static final List<Item> ITEMS = List.of(Items.APPLE, Items.BREAD, Items.COBBLESTONE, Items.STICK);

    private final Random random;
    private final ParityScene scene;
    private final Map<BlockPos, BlockState> taken = new HashMap<>();
    private final List<BlockPos> sources = new ArrayList<>(), containers = new ArrayList<>(), breakable = new ArrayList<>();

    private RandomRedstoneScene(long seed, ParityScene scene) {
        this.random = new Random(seed);
        this.scene = scene;
    }

    static ParityScene generate(long seed) {
        ParityScene scene = new ParityScene("random_" + seed);
        RandomRedstoneScene generator = new RandomRedstoneScene(seed, scene);
        generator.build();
        scene.watchOnlyAround = true;
        return scene;
    }

    private void build() {
        int circuits = 2 + random.nextInt(4);
        for (int i = 0; i < circuits; i++) circuit();
        int hoppers = random.nextInt(3);
        for (int i = 0; i < hoppers; i++) hopperLine();
        script();
    }

    private static boolean inside(BlockPos cell) {
        return cell.getX() >= 0 && cell.getX() < SIZE && cell.getZ() >= 0 && cell.getZ() < SIZE && cell.getY() >= 0 && cell.getY() <= MAX_Y;
    }

    private boolean free(BlockPos cell) {
        return inside(cell) && !taken.containsKey(cell);
    }

    private void set(BlockPos cell, BlockState state) {
        taken.put(cell, state);
        // What dust and the like stand on goes first; anything built on nothing would pop off as it is placed.
        scene.put(cell.getX(), cell.getY(), cell.getZ(), state);
        if (!state.is(Blocks.SMOOTH_STONE)) breakable.add(cell);
    }

    /** Something solid under {@code cell}, down to the floor if it is not there yet. */
    private void support(BlockPos cell) {
        for (int y = 0; y < cell.getY(); y++) {
            BlockPos below = new BlockPos(cell.getX(), y, cell.getZ());
            if (!taken.containsKey(below)) set(below, y == 0 ? FLOOR : block(SOLID));
        }
    }

    private BlockState block(List<Block> blocks) {
        return blocks.get(random.nextInt(blocks.size())).defaultBlockState();
    }

    private Direction horizontal() {
        return Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong()));
    }

    private void circuit() {
        BlockPos at = new BlockPos(random.nextInt(SIZE), 1, random.nextInt(SIZE));
        if (!free(at)) return;
        support(at);
        BlockState source = switch (random.nextInt(4)) {
            case 0 -> Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, Direction.NORTH);
            case 1 -> Blocks.REDSTONE_TORCH.defaultBlockState();
            default -> Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
        };
        set(at, source);
        if (!source.is(Blocks.REDSTONE_TORCH)) sources.add(at);
        Direction heading = horizontal();
        int length = 4 + random.nextInt(14);
        for (int i = 0; i < length; i++) {
            if (random.nextInt(4) == 0) heading = horizontal();
            BlockPos next = step(at, heading);
            if (next == null) break;
            at = next;
        }
        finish(at, heading);
    }

    /** One more element past {@code at}; where the circuit continues from, or null if it ran into something. */
    private BlockPos step(BlockPos at, Direction heading) {
        BlockPos ahead = at.relative(heading);
        if (!free(ahead)) return null;
        switch (random.nextInt(12)) {
            case 0 -> { // up a step
                BlockPos top = ahead.above();
                if (!free(top) || !inside(top.above())) break;
                support(ahead);
                set(ahead, block(random.nextBoolean() ? SOLID : CLEAR));
                set(top, WIRE);
                return top;
            }
            case 1 -> { // down a step
                BlockPos low = ahead.below();
                if (low.getY() < 1 || !free(ahead) || !free(low)) break;
                support(low);
                set(low, WIRE);
                return low;
            }
            case 2 -> {
                support(ahead);
                set(ahead, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, heading.getOpposite())
                        .setValue(RepeaterBlock.DELAY, 1 + random.nextInt(4)));
                return ahead;
            }
            case 3 -> {
                support(ahead);
                set(ahead, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, heading.getOpposite())
                        .setValue(ComparatorBlock.MODE, random.nextBoolean() ? ComparatorMode.COMPARE : ComparatorMode.SUBTRACT));
                return ahead;
            }
            case 4 -> { // through a block and out of a torch on its far side
                BlockPos far = ahead.relative(heading);
                if (!free(far)) break;
                support(ahead);
                set(ahead, block(SOLID));
                set(far, Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, heading));
                return far;
            }
            case 5 -> {
                support(ahead);
                set(ahead, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, heading.getOpposite()));
                return ahead;
            }
            default -> {
            }
        }
        support(ahead);
        set(ahead, WIRE);
        return ahead;
    }

    private void finish(BlockPos at, Direction heading) {
        BlockPos end = at.relative(heading);
        if (!free(end)) return;
        support(end);
        switch (random.nextInt(6)) {
            case 0 -> set(end, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, heading));
            case 1 -> set(end, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, heading));
            case 2 -> set(end, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, heading));
            case 3 -> {
                BlockPos top = end.above();
                if (!free(top)) {
                    set(end, Blocks.REDSTONE_LAMP.defaultBlockState());
                    return;
                }
                BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, heading);
                taken.put(end, door);
                taken.put(top, door);
                scene.raw(end.getX(), end.getY(), end.getZ(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                scene.raw(top.getX(), top.getY(), top.getZ(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            case 4 -> set(end, Blocks.DROPPER.defaultBlockState().setValue(net.minecraft.world.level.block.DropperBlock.FACING, heading));
            default -> set(end, Blocks.REDSTONE_LAMP.defaultBlockState());
        }
    }

    /** Hoppers running into a chest, maybe with a comparator reading the chest. */
    private void hopperLine() {
        Direction heading = horizontal();
        BlockPos at = new BlockPos(random.nextInt(SIZE), 1 + random.nextInt(2), random.nextInt(SIZE));
        int length = 2 + random.nextInt(5);
        BlockPos first = at;
        for (int i = 0; i < length; i++) {
            if (!free(at)) return;
            support(at);
            set(at, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, heading));
            containers.add(at);
            at = at.relative(heading);
        }
        if (!free(at)) return;
        support(at);
        set(at, Blocks.CHEST.defaultBlockState());
        containers.add(at);
        BlockPos reader = at.relative(heading);
        if (free(reader)) {
            support(reader);
            set(reader, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, heading.getOpposite()));
        }
        scene.item(first.getX(), first.getY(), first.getZ(), 0, ITEMS.get(random.nextInt(ITEMS.size())), 1 + random.nextInt(16));
    }

    private void script() {
        int actions = 3 + random.nextInt(10);
        int tick = 3;
        for (int i = 0; i < actions; i++) {
            tick += 1 + random.nextInt(14);
            switch (random.nextInt(6)) {
                case 0, 1, 2 -> {
                    if (sources.isEmpty()) continue;
                    BlockPos cell = sources.get(random.nextInt(sources.size()));
                    scene.use(tick, cell.getX(), cell.getY(), cell.getZ());
                }
                case 3 -> {
                    if (breakable.isEmpty()) continue;
                    BlockPos cell = breakable.get(random.nextInt(breakable.size()));
                    scene.breakAt(tick, cell.getX(), cell.getY(), cell.getZ());
                }
                case 4 -> {
                    if (containers.isEmpty()) continue;
                    BlockPos cell = containers.get(random.nextInt(containers.size()));
                    scene.insert(tick, cell.getX(), cell.getY(), cell.getZ(), ITEMS.get(random.nextInt(ITEMS.size())), 1 + random.nextInt(32));
                }
                default -> {
                    if (containers.isEmpty()) continue;
                    BlockPos cell = containers.get(random.nextInt(containers.size()));
                    scene.take(tick, cell.getX(), cell.getY(), cell.getZ(), 1 + random.nextInt(16));
                }
            }
        }
        scene.runFor(tick + 60);
    }
}
