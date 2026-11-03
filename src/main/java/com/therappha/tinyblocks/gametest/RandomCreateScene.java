package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A Create network made from a seed, to run in the world and in grids (ParityRun). It is a tree grown from one source:
 * each part is put where its parent hands rotation on, and only where nothing else kinetic is close enough to join it,
 * so no cycle closes (clashing speeds in one would make Create break a part, which ends a scene rather than tests it)
 * and cogwheels mesh only with the one they were put against, on a side where they really do. Shafts, encased shafts,
 * clutches and gearshifts on levers, gearboxes, cogwheels, large cogwheels and belts make the tree; presses over depots,
 * deployers and drills end branches. Hoppers and chutes put items on belts, whose ends hand them to depots, chutes and
 * funnels or drop them. Then a script flips levers, breaks parts and puts them back, and fills hoppers. The same seed
 * makes the same scene.
 *
 * {@link Kind#OVERSTRESS} grows from a hand crank instead, a source a few machines can overstress, and adds machines
 * until they do. {@link Kind#CONNECTORS} grows only the network, and stands grids in for some of its shafts
 * (ParitySpace.Bridged): connectors through grids, grids of different sizes side by side.
 *
 * When an item moves never repeats to the tick, even between real copies, so what items do is compared at the end;
 * speed and stress tick by tick. Nothing is built that needs a hopper or funnel to pick up an item lying in the world
 * (#109): what drops, drops where nothing catches it.
 */
final class RandomCreateScene {

    enum Kind {
        MACHINES, OVERSTRESS, CONNECTORS
    }

    private static final int SIZE = 16, MAX_Y = 7;
    /** Hand crank: 8 su per rpm at 32 rpm (Create's defaults); more impact per rpm of the source than this overstresses it. */
    private static final float CRANK_CAPACITY = 8;
    private static final List<Item> ITEMS = List.of(Items.APPLE, Items.COBBLESTONE, Items.STICK, Items.IRON_INGOT);

    /** Where rotation is handed on: a cell to put a part in, coming in along {@code heading}, or a cogwheel to mesh another with. */
    private record Port(BlockPos cell, Direction heading, BlockPos parent, float ratio, boolean mesh) {}

    private final Random random;
    private final ParityScene scene;
    private final Kind kind;
    private final Map<BlockPos, BlockState> taken = new HashMap<>();
    private final Map<BlockPos, BlockState> kinetic = new HashMap<>();
    private final Set<BlockPos> catchers = new HashSet<>(), dropZone = new HashSet<>();
    private final List<Port> ports = new ArrayList<>();
    private final List<BlockPos> levers = new ArrayList<>(), breakable = new ArrayList<>(), hoppers = new ArrayList<>();
    /** Shafts in a straight run, by the way rotation goes through them: where CONNECTORS stands grids in. */
    private final Map<BlockPos, Direction> runs = new LinkedHashMap<>();
    private BlockPos source;
    private boolean items, drops;
    /** Stress impact per rpm of the source, summed over the machines, for OVERSTRESS. */
    private float impact;

    private RandomCreateScene(long seed, Kind kind, ParityScene scene) {
        this.random = new Random(seed);
        this.kind = kind;
        this.scene = scene;
    }

    static ParityScene generate(long seed, Kind kind) {
        for (int attempt = 0; ; attempt++) {
            ParityScene scene = new ParityScene("create_" + kind.name().toLowerCase() + "_" + seed);
            RandomCreateScene generator = new RandomCreateScene(seed * 1009 + kind.ordinal() * 7919L + attempt * 104729L, kind, scene);
            generator.build();
            scene.watchOnlyAround = true;
            // One that doesn't do what its kind is for (overstress, stand grids in) is grown again from the next seed down the line.
            boolean does = switch (kind) {
                case MACHINES -> true;
                case OVERSTRESS -> generator.impact > CRANK_CAPACITY;
                case CONNECTORS -> !scene.standIns.isEmpty();
            };
            if (does || attempt == 50) return scene;
        }
    }

    // --- placing -------------------------------------------------------------------------------------------------

    private static boolean inside(BlockPos cell) {
        return cell.getX() >= 0 && cell.getX() < SIZE && cell.getZ() >= 0 && cell.getZ() < SIZE && cell.getY() >= 0 && cell.getY() <= MAX_Y;
    }

    private boolean free(BlockPos cell) {
        return inside(cell) && !taken.containsKey(cell);
    }

    private static boolean isCog(BlockState state) {
        return ICogWheel.isSmallCog(state) || ICogWheel.isLargeCog(state);
    }

    /**
     * Whether {@code state} can go at {@code cell} touching no kinetic part but those in {@code joins}: nothing next to
     * it face to face, and no cogwheel on either side of a diagonal, which is as far as Create looks for a neighbour.
     */
    private boolean fits(BlockPos cell, BlockState state, Set<BlockPos> joins) {
        if (!free(cell)) return false;
        boolean cog = isCog(state);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos next = cell.offset(dx, dy, dz);
                    BlockState other = kinetic.get(next);
                    if (other == null || next.equals(cell) || joins.contains(next)) continue;
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1 || cog || isCog(other)) return false;
                }
            }
        }
        return true;
    }

    private void set(BlockPos cell, BlockState state) {
        taken.put(cell, state);
        scene.raw(cell.getX(), cell.getY(), cell.getZ(), state);
    }

    private void setKinetic(BlockPos cell, BlockState state) {
        set(cell, state);
        kinetic.put(cell, state);
    }

    /** Kept free: air a part needs in front of it. */
    private void reserve(BlockPos cell) {
        taken.put(cell, Blocks.AIR.defaultBlockState());
    }

    /** Where something may drop: down from {@code cell}, a block around, where nothing that picks items up may stand. */
    private boolean canDropAt(BlockPos cell) {
        for (BlockPos at : column(cell)) if (catchers.contains(at)) return false;
        return true;
    }

    private void dropsAt(BlockPos cell) {
        dropZone.addAll(column(cell));
        drops = true;
    }

    private static List<BlockPos> column(BlockPos cell) {
        List<BlockPos> cells = new ArrayList<>();
        for (int y = cell.getY(); y >= 0; y--)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) cells.add(new BlockPos(cell.getX() + dx, y, cell.getZ() + dz));
        return cells;
    }

    private boolean canCatchAt(BlockPos cell) {
        return free(cell) && !dropZone.contains(cell);
    }

    private void setCatcher(BlockPos cell, BlockState state) {
        set(cell, state);
        catchers.add(cell);
    }

    private Direction any(List<Direction> directions) {
        return directions.get(random.nextInt(directions.size()));
    }

    private static List<Direction> across(Axis axis) {
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.values()) if (direction.getAxis() != axis) directions.add(direction);
        return directions;
    }

    private static BlockState along(BlockState block, Axis axis) {
        return block.setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    private void port(BlockPos cell, Direction heading, BlockPos parent, float ratio) {
        ports.add(new Port(cell, heading, parent, ratio, false));
    }

    private void meshPort(BlockPos cog, Direction axisWay, float ratio) {
        ports.add(new Port(cog, axisWay, cog, ratio, true));
    }

    // --- growing -------------------------------------------------------------------------------------------------

    private void build() {
        source();
        int budget = kind == Kind.CONNECTORS ? 8 + random.nextInt(14) : 10 + random.nextInt(22);
        int tries = 0;
        while (!ports.isEmpty() && tries++ < 400) {
            boolean overstressed = impact > CRANK_CAPACITY;
            if (budget <= 0 && (kind != Kind.OVERSTRESS || overstressed)) break;
            Port port = ports.remove(random.nextInt(ports.size()));
            // Each try is another part: a port is given up only when none of a few fits there.
            for (int attempt = 0; attempt < 6; attempt++) {
                if (grow(port)) {
                    budget--;
                    break;
                }
            }
        }
        if (kind == Kind.CONNECTORS) standIns();
        script();
    }

    private void source() {
        for (int attempt = 0; attempt < 50; attempt++) {
            BlockPos at = new BlockPos(2 + random.nextInt(SIZE - 4), 1 + random.nextInt(MAX_Y - 2), 2 + random.nextInt(SIZE - 4));
            Direction out = Direction.values()[random.nextInt(6)];
            if (!free(at) || !free(at.relative(out))) continue;
            source = at;
            if (kind == Kind.OVERSTRESS) {
                // A crank holds on by its shaft: the part it turns, which the script never breaks.
                setKinetic(at, AllBlocks.HAND_CRANK.getDefaultState().setValue(DirectionalKineticBlock.FACING, out.getOpposite()));
            } else {
                setKinetic(at, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, out));
                breakable.add(at);
            }
            port(at.relative(out), out, at, 1);
            return;
        }
    }

    private boolean grow(Port port) {
        boolean grew = port.mesh() ? mesh(port) : place(port);
        // What a crank holds on by stays: broken, the crank would drop.
        if (grew && kind == Kind.OVERSTRESS && port.parent().equals(source)) breakable.remove(port.cell());
        return grew;
    }

    private boolean place(Port port) {
        Axis axis = port.heading().getAxis();
        int roll = random.nextInt(kind == Kind.CONNECTORS ? 80 : 100);
        if (kind == Kind.OVERSTRESS && random.nextInt(3) == 0) roll = 80 + random.nextInt(20);
        if (roll < 28) return shaft(port, AllBlocks.SHAFT.getDefaultState());
        if (roll < 33) return shaft(port, (random.nextBoolean() ? AllBlocks.ANDESITE_ENCASED_SHAFT : AllBlocks.BRASS_ENCASED_SHAFT).getDefaultState());
        if (roll < 42) return switched(port, (random.nextBoolean() ? AllBlocks.CLUTCH : AllBlocks.GEARSHIFT).getDefaultState());
        if (roll < 52) return gearbox(port);
        if (roll < 64) return cog(port, AllBlocks.COGWHEEL.getDefaultState());
        if (roll < 70) return cog(port, AllBlocks.LARGE_COGWHEEL.getDefaultState());
        if (roll < 80) return axis != Axis.Y && belt(port);
        if (roll < 87) return axis != Axis.Y && press(port);
        if (roll < 94) return deployer(port);
        return drill(port);
    }

    private boolean shaft(Port port, BlockState block) {
        BlockState state = along(block, port.heading().getAxis());
        if (!fits(port.cell(), state, Set.of(port.parent()))) return false;
        setKinetic(port.cell(), state);
        breakable.add(port.cell());
        if (block.is(AllBlocks.SHAFT.get())) runs.put(port.cell(), port.heading());
        port(port.cell().relative(port.heading()), port.heading(), port.cell(), port.ratio());
        return true;
    }

    /** A clutch or gearshift, with a lever on one of its sides to switch it. */
    private boolean switched(Port port, BlockState block) {
        BlockState state = along(block, port.heading().getAxis());
        if (!fits(port.cell(), state, Set.of(port.parent()))) return false;
        Direction side = any(across(port.heading().getAxis()));
        BlockPos at = port.cell().relative(side);
        if (!free(at)) return false;
        setKinetic(port.cell(), state);
        BlockState lever = Blocks.LEVER.defaultBlockState();
        lever = switch (side) {
            case UP -> lever.setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
            case DOWN -> lever.setValue(LeverBlock.FACE, AttachFace.CEILING).setValue(LeverBlock.FACING, Direction.NORTH);
            default -> lever.setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, side);
        };
        set(at, lever);
        levers.add(at);
        port(port.cell().relative(port.heading()), port.heading(), port.cell(), port.ratio());
        return true;
    }

    private boolean gearbox(Port port) {
        Axis axis = any(across(port.heading().getAxis())).getAxis();
        BlockState state = along(AllBlocks.GEARBOX.getDefaultState(), axis);
        if (!fits(port.cell(), state, Set.of(port.parent()))) return false;
        setKinetic(port.cell(), state);
        breakable.add(port.cell());
        for (Direction out : across(axis)) {
            if (out == port.heading().getOpposite() || random.nextInt(3) == 0) continue;
            port(port.cell().relative(out), out, port.cell(), port.ratio());
        }
        return true;
    }

    /** A cogwheel on the shaft's line; later another can mesh with it, and the line can go on through it. */
    private boolean cog(Port port, BlockState block) {
        BlockState state = along(block, port.heading().getAxis());
        if (!fits(port.cell(), state, Set.of(port.parent()))) return false;
        setKinetic(port.cell(), state);
        breakable.add(port.cell());
        if (random.nextBoolean()) port(port.cell().relative(port.heading()), port.heading(), port.cell(), port.ratio());
        meshPort(port.cell(), port.heading(), port.ratio());
        if (random.nextBoolean()) meshPort(port.cell(), port.heading(), port.ratio());
        return true;
    }

    /**
     * Another cogwheel meshing with the one at the port, on the same axis: a small one beside a small one, turning the
     * other way; a small one on a large one's diagonal or a large one on a small one's, at twice or half the speed.
     */
    private boolean mesh(Port port) {
        BlockPos cog = port.cell();
        BlockState there = kinetic.get(cog);
        if (there == null) return false;
        Axis axis = port.heading().getAxis();
        boolean large = ICogWheel.isLargeCog(there);
        boolean toLarge = !large && random.nextInt(3) == 0;
        List<Direction> sides = across(axis);
        Direction a = any(sides);
        BlockPos at = cog.relative(a);
        if (large || toLarge) {
            List<Direction> others = new ArrayList<>(sides);
            others.removeIf(d -> d.getAxis() == a.getAxis());
            at = at.relative(any(others));
        }
        BlockState state = along((toLarge ? AllBlocks.LARGE_COGWHEEL : AllBlocks.COGWHEEL).getDefaultState(), axis);
        if (!fits(at, state, Set.of(cog))) return false;
        setKinetic(at, state);
        breakable.add(at);
        float ratio = port.ratio() * (large ? 2 : toLarge ? 0.5f : 1);
        for (Direction way : List.of(port.heading(), port.heading().getOpposite())) {
            if (random.nextInt(3) == 0) port(at.relative(way), way, at, ratio);
        }
        if (random.nextBoolean()) meshPort(at, port.heading(), ratio);
        return true;
    }

    /**
     * A belt whose first pulley takes the port's shaft, running across it; rotation goes on out of its last pulley.
     * Which way items go is whichever way it turns, so both ends get something to hand them to, or drop them.
     */
    private boolean belt(Port port) {
        Direction heading = port.heading();
        Direction run = random.nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
        int length = 2 + random.nextInt(5);
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < length; i++) cells.add(port.cell().relative(run, i));
        Set<BlockPos> joins = new HashSet<>(cells);
        joins.add(port.parent());
        for (BlockPos cell : cells) {
            BlockState probe = AllBlocks.BELT.getDefaultState();
            if (!fits(cell, probe, joins) || !canCatchAt(cell) || !free(cell.above())) return false;
        }
        BlockPos first = cells.get(0).relative(run.getOpposite()), last = cells.get(length - 1).relative(run);
        if (!free(first) || !free(last)) return false;
        for (int i = 0; i < length; i++) {
            BeltPart part = i == 0 ? BeltPart.START : i == length - 1 ? BeltPart.END : BeltPart.MIDDLE;
            setKinetic(cells.get(i), AllBlocks.BELT.getDefaultState().setValue(BeltBlock.HORIZONTAL_FACING, run)
                    .setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL).setValue(BeltBlock.PART, part));
            catchers.add(cells.get(i));
        }
        beltEnd(first, run.getOpposite());
        beltEnd(last, run);
        feed(cells.get(random.nextInt(length)));
        scene.settleTicks = Math.max(scene.settleTicks, 80); // a belt only takes items once it knows its length
        BlockPos out = cells.get(length - 1);
        port(out.relative(heading), heading, out, port.ratio());
        return true;
    }

    /** What is past an end of a belt that runs out along {@code out}. */
    private void beltEnd(BlockPos at, Direction out) {
        switch (random.nextInt(4)) {
            case 0 -> {
                if (canCatchAt(at)) setCatcher(at, AllBlocks.DEPOT.getDefaultState());
            }
            case 1 -> {
                BlockPos chest = at.below();
                if (canCatchAt(at) && canCatchAt(chest)) {
                    setCatcher(chest, Blocks.CHEST.defaultBlockState());
                    setCatcher(at, AllBlocks.CHUTE.getDefaultState());
                }
            }
            case 2 -> {
                BlockPos chest = at.relative(out);
                if (canCatchAt(at) && canCatchAt(chest)) {
                    setCatcher(chest, Blocks.CHEST.defaultBlockState());
                    setCatcher(at, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, out.getOpposite()));
                }
            }
            default -> {
                if (free(at) && canDropAt(at)) {
                    reserve(at);
                    dropsAt(at);
                }
            }
        }
    }

    /** A hopper above a belt segment, maybe through a chute, with something in it. */
    private void feed(BlockPos segment) {
        BlockPos above = segment.above();
        boolean chute = random.nextBoolean() && free(above.above()) && canCatchAt(above.above());
        if (!canCatchAt(above)) return;
        BlockPos hopper = chute ? above.above() : above;
        if (chute) setCatcher(above, AllBlocks.CHUTE.getDefaultState());
        setCatcher(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        hoppers.add(hopper);
        scene.item(hopper.getX(), hopper.getY(), hopper.getZ(), 0, ITEMS.get(random.nextInt(ITEMS.size())), 1 + random.nextInt(8));
        items = true;
    }

    /** A press over a depot, and a hopper feeding the depot iron. */
    private boolean press(Port port) {
        BlockPos cell = port.cell();
        BlockState state = AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, port.heading());
        BlockPos depot = cell.below(2);
        Direction side = any(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        BlockPos hopper = depot.relative(side);
        if (!fits(cell, state, Set.of(port.parent())) || !free(cell.below()) || !canCatchAt(depot) || !canCatchAt(hopper)) return false;
        setKinetic(cell, state);
        reserve(cell.below());
        setCatcher(depot, AllBlocks.DEPOT.getDefaultState());
        setCatcher(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, side.getOpposite()));
        hoppers.add(hopper);
        scene.item(hopper.getX(), hopper.getY(), hopper.getZ(), 0, Items.IRON_INGOT, 1 + random.nextInt(3));
        machine(cell, 8, port.ratio());
        items = true;
        return true;
    }

    /** A deployer, fed planks by a hopper behind it, putting them on a block two cells in front. */
    private boolean deployer(Port port) {
        Axis axis = port.heading().getAxis();
        List<Direction> level = new ArrayList<>(across(axis));
        level.removeIf(d -> d.getAxis() == Axis.Y);
        Direction facing = any(level);
        Axis pointing = facing.getAxis();
        boolean first = switch (pointing) {
            case X -> axis == Axis.Y;
            case Y, Z -> axis == Axis.X;
        };
        BlockState state = AllBlocks.DEPLOYER.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, first);
        BlockPos cell = port.cell(), hand = cell.relative(facing), target = hand.relative(facing), floor = target.below();
        BlockPos hopper = cell.relative(facing.getOpposite());
        if (!fits(cell, state, Set.of(port.parent())) || !free(hand) || !free(target) || !free(floor) || !canCatchAt(hopper)) return false;
        setKinetic(cell, state);
        reserve(hand);
        reserve(target);
        set(floor, Blocks.STONE.defaultBlockState());
        setCatcher(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
        hoppers.add(hopper);
        scene.item(hopper.getX(), hopper.getY(), hopper.getZ(), 0, Items.OAK_PLANKS, 1 + random.nextInt(3));
        scene.endOnlyCell(target);
        machine(cell, 4, port.ratio());
        items = true;
        return true;
    }

    /** A drill against a block, which it breaks: what drops is counted where nothing catches it. */
    private boolean drill(Port port) {
        BlockPos cell = port.cell(), front = cell.relative(port.heading());
        BlockState state = AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DirectionalKineticBlock.FACING, port.heading());
        if (!fits(cell, state, Set.of(port.parent())) || !free(front) || !canDropAt(front)) return false;
        setKinetic(cell, state);
        List<BlockState> blocks = List.of(Blocks.DIRT.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        set(front, blocks.get(random.nextInt(blocks.size())));
        dropsAt(front);
        scene.endOnlyCell(front);
        machine(cell, 4, port.ratio());
        return true;
    }

    private void machine(BlockPos cell, float impactPerRpm, float ratio) {
        impact += impactPerRpm * Math.abs(ratio);
        if (kind == Kind.OVERSTRESS && impactPerRpm >= 4 && !AllBlocks.DEPLOYER.has(taken.get(cell))) breakable.add(cell);
    }

    /**
     * Grids in for some of the plain shafts: alone, or two or three in a row, grids of different sizes side by side,
     * joined through connectors facing each other.
     */
    private void standIns() {
        List<BlockPos> candidates = new ArrayList<>(runs.keySet());
        int wanted = 1 + random.nextInt(3);
        for (int i = 0; i < wanted && !candidates.isEmpty(); i++) {
            BlockPos cell = candidates.remove(random.nextInt(candidates.size()));
            Direction way = runs.get(cell);
            for (BlockPos at = cell; runs.get(at) == way && standIn(at, way); at = at.relative(way)) {
                candidates.remove(at);
                if (random.nextInt(3) != 0) break;
            }
        }
    }

    private boolean standIn(BlockPos cell, Direction way) {
        for (ParityScene.StandIn standIn : scene.standIns) if (standIn.cell().equals(cell)) return false;
        int size = 4 + random.nextInt(13);
        scene.standIns.add(new ParityScene.StandIn(cell, size, way.getAxis(), way, random.nextBoolean()));
        return true;
    }

    // --- the script ----------------------------------------------------------------------------------------------

    private void script() {
        int actions = 2 + random.nextInt(8);
        int tick = 2;
        Map<BlockPos, Integer> busyUntil = new HashMap<>();
        for (int i = 0; i < actions; i++) {
            tick += 4 + random.nextInt(18);
            int roll = random.nextInt(10);
            if (roll < 4 && !levers.isEmpty()) {
                BlockPos lever = levers.get(random.nextInt(levers.size()));
                scene.use(tick, lever.getX(), lever.getY(), lever.getZ());
            } else if (roll < 8 && !breakable.isEmpty()) {
                BlockPos cell = breakable.get(random.nextInt(breakable.size()));
                if (busyUntil.getOrDefault(cell, -1) >= tick) continue;
                int back = tick + 5 + random.nextInt(25);
                busyUntil.put(cell, back);
                scene.breakAt(tick, cell.getX(), cell.getY(), cell.getZ());
                scene.placeAt(back, cell.getX(), cell.getY(), cell.getZ(), taken.get(cell));
            } else if (!hoppers.isEmpty()) {
                BlockPos hopper = hoppers.get(random.nextInt(hoppers.size()));
                scene.insert(tick, hopper.getX(), hopper.getY(), hopper.getZ(), ITEMS.get(random.nextInt(ITEMS.size())), 1 + random.nextInt(6));
                items = true;
            }
        }
        int end = tick + 40;
        if (kind == Kind.OVERSTRESS) {
            // Turned from before the comparison starts, with a rest in the middle where it runs down and stops.
            int rest = end / 2;
            for (int t = -scene.settleTicks + 2; t < end; t += 4) {
                if (t < rest || t > rest + 30) scene.crank(t, source.getX(), source.getY(), source.getZ());
            }
            end += 30;
        }
        if (items) {
            end = Math.max(end, 300);
            scene.endOnlyField("working.held").endOnlyField("working.on_belt").endOnlyField("working.items").endOnlyField("working.analog_signal");
        }
        if (drops) scene.compareEntities();
        scene.runFor(end);
    }
}
