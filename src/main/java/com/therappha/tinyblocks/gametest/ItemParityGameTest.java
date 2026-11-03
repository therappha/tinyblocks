package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Dispensers and droppers with each kind of behaviour, and hoppers in and out of what holds items. What they throw out counts too. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class ItemParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private static BlockState hopper(Direction to) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, to);
    }

    private static BlockState east(BlockState block) {
        return block.setValue(DispenserBlock.FACING, Direction.EAST);
    }

    private static BlockState lever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
    }

    /** A dispenser at (3,2,4) on a low wall, facing east over a floor, holding {@code count} of {@code item}, fired once, twice if it has more than one. */
    private static void fired(ParityScene s, BlockState block, Item item, int count) {
        s.fill(0, 0, 2, 12, 0, 6, STONE).put(3, 1, 4, STONE).put(3, 1, 3, STONE);
        s.put(3, 2, 4, east(block)).item(3, 2, 4, 0, item, count);
        s.put(3, 2, 3, lever()).use(5, 3, 2, 3).use(15, 3, 2, 3);
        if (count > 1) s.use(25, 3, 2, 3).use(35, 3, 2, 3);
    }

    private static ParityScene dispensing(String name, Item item, int count) {
        return ParityScene.of(name, s -> {
            fired(s, Blocks.DISPENSER.defaultBlockState(), item, count);
            s.runFor(50).compareEntities();
        });
    }

    /** What it shoots out leaves the space, a real block wide in either: it is counted before it can have gone. */
    private static ParityScene shooting(String name, Item item) {
        return ParityScene.of(name, s -> {
            fired(s, Blocks.DISPENSER.defaultBlockState(), item, 1);
            s.runFor(12).compareEntitiesAt(10);
        });
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("dropper_throws_items_out", s -> {
            fired(s, Blocks.DROPPER.defaultBlockState(), Items.APPLE, 4);
            s.runFor(50).compareEntities();
        }));
        scenes.add(ParityScene.of("dropper_fills_a_chest", s -> {
            fired(s, Blocks.DROPPER.defaultBlockState(), Items.APPLE, 4);
            s.put(4, 1, 4, STONE).put(4, 2, 4, Blocks.CHEST.defaultBlockState());
            s.runFor(50).provenBy(4, 2, 4, "applex2");
        }));
        scenes.add(ParityScene.of("dropper_fills_a_hopper_below_it", s -> {
            s.fill(0, 0, 2, 12, 0, 6, STONE).put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.BARREL.defaultBlockState());
            s.put(4, 2, 4, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.DOWN)).item(4, 2, 4, 0, Items.APPLE, 2);
            s.put(3, 2, 4, STONE).put(3, 3, 4, lever()).use(5, 3, 3, 4).use(15, 3, 3, 4).use(25, 3, 3, 4).runFor(60).provenBy(5, 1, 4, "apple");
        }));

        scenes.add(dispensing("dispenser_throws_a_plain_item", Items.APPLE, 4));
        scenes.add(shooting("dispenser_shoots_an_arrow", Items.ARROW));
        // Shot at full size out of a tiny dispenser, a snowball can hit the pieces next to it and be gone before it is counted (#52).
        scenes.add(shooting("dispenser_throws_a_snowball", Items.SNOWBALL).knownBroken("#52: what comes out of a piece is full size"));
        scenes.add(shooting("dispenser_primes_tnt", Items.TNT));
        scenes.add(dispensing("dispenser_places_water", Items.WATER_BUCKET, 1));
        scenes.add(dispensing("dispenser_lights_a_fire", Items.FLINT_AND_STEEL, 1));
        scenes.add(dispensing("dispenser_places_a_shulker_box", Items.SHULKER_BOX, 1));
        scenes.add(dispensing("dispenser_places_a_pumpkin", Items.CARVED_PUMPKIN, 1));
        scenes.add(dispensing("dispenser_stands_an_armor_stand", Items.ARMOR_STAND, 1));
        scenes.add(dispensing("dispenser_hatches_a_spawn_egg", Items.COW_SPAWN_EGG, 1));

        scenes.add(ParityScene.of("dispenser_takes_water", s -> {
            fired(s, Blocks.DISPENSER.defaultBlockState(), Items.BUCKET, 1);
            s.put(4, 1, 4, STONE).put(4, 1, 3, STONE).put(4, 2, 3, STONE).put(4, 2, 5, STONE).put(5, 2, 4, STONE).put(4, 3, 4, STONE);
            s.raw(4, 2, 4, Blocks.WATER.defaultBlockState()).runFor(50).provenBy(3, 2, 4, "water_bucket");
        }));

        scenes.add(ParityScene.of("dispenser_waxes_copper", s -> {
            fired(s, Blocks.DISPENSER.defaultBlockState(), Items.HONEYCOMB, 1);
            s.put(4, 2, 4, Blocks.COPPER_BLOCK.defaultBlockState()).runFor(50).provenBy(4, 2, 4, "waxed_copper_block");
        }));

        scenes.add(ParityScene.of("dispenser_puts_a_cart_on_a_rail", s -> {
            fired(s, Blocks.DISPENSER.defaultBlockState(), Items.MINECART, 1);
            s.put(4, 1, 4, STONE).put(4, 2, 4, Blocks.RAIL.defaultBlockState()).runFor(50).compareEntities();
        }));

        scenes.add(ParityScene.of("hopper_empties_a_chest_above_it", s -> {
            s.fill(3, 0, 4, 8, 0, 4, STONE);
            s.put(4, 2, 4, Blocks.CHEST.defaultBlockState()).item(4, 2, 4, 0, Items.APPLE, 3);
            s.put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.CHEST.defaultBlockState());
            s.runFor(70).provenBy(5, 1, 4, "applex3");
        }));

        scenes.add(ParityScene.of("hopper_fills_a_barrel_and_a_shulker_box", s -> {
            s.fill(3, 0, 4, 10, 0, 4, STONE);
            s.put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.BARREL.defaultBlockState()).item(4, 1, 4, 0, Items.APPLE, 3);
            s.put(7, 1, 4, hopper(Direction.EAST)).put(8, 1, 4, Blocks.SHULKER_BOX.defaultBlockState()).item(7, 1, 4, 0, Items.BREAD, 3);
            s.runFor(50).provenBy(5, 1, 4, "applex3").provenBy(8, 1, 4, "breadx3");
        }));

        scenes.add(ParityScene.of("hopper_refuses_a_shulker_box_into_a_shulker_box", s -> {
            s.fill(3, 0, 4, 10, 0, 4, STONE);
            s.put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.SHULKER_BOX.defaultBlockState()).item(4, 1, 4, 0, Items.SHULKER_BOX, 1);
            s.runFor(30).provenBy(4, 1, 4, "shulker_boxx1");
        }));

        scenes.add(ParityScene.of("hoppers_feed_a_furnace_and_empty_it", s -> {
            s.fill(3, 0, 3, 9, 0, 5, STONE);
            s.put(5, 2, 4, Blocks.FURNACE.defaultBlockState());
            s.put(5, 3, 4, hopper(Direction.DOWN)).item(5, 3, 4, 0, Items.RAW_IRON, 2);
            s.put(4, 2, 4, hopper(Direction.EAST)).item(4, 2, 4, 0, Items.COAL, 1);
            s.put(5, 1, 4, hopper(Direction.EAST)).put(6, 1, 4, Blocks.CHEST.defaultBlockState());
            s.runFor(460).provenBy(6, 1, 4, "iron_ingotx2");
        }));

        scenes.add(ParityScene.of("hopper_takes_bone_meal_out_of_a_composter", s -> {
            s.fill(3, 0, 4, 8, 0, 4, STONE);
            s.raw(4, 2, 4, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
            s.put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.CHEST.defaultBlockState());
            s.runFor(30).provenBy(5, 1, 4, "bone_mealx1");
        }));

        scenes.add(ParityScene.of("hopper_fills_a_composter", s -> {
            s.fill(3, 0, 4, 8, 0, 4, STONE);
            s.put(4, 2, 4, hopper(Direction.DOWN)).item(4, 2, 4, 0, Items.PUMPKIN_PIE, 8).put(4, 1, 4, Blocks.COMPOSTER.defaultBlockState());
            s.runFor(100);
        }));

        scenes.add(ParityScene.of("hopper_catches_what_a_dropper_throws_at_it", s -> {
            s.fill(0, 0, 2, 12, 0, 6, STONE).put(4, 1, 4, hopper(Direction.EAST)).put(5, 1, 4, Blocks.CHEST.defaultBlockState());
            s.put(4, 3, 4, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.DOWN)).item(4, 3, 4, 0, Items.APPLE, 1);
            s.put(3, 3, 4, STONE).put(3, 4, 4, lever()).use(5, 3, 4, 4).runFor(50).compareEntities().provenBy(5, 1, 4, "apple");
            s.knownBroken("#109, a piece sees no entities");
        }));

        return scenes;
    }

    @GameTestGenerator
    public Collection<TestFunction> items() {
        return RedstoneParityGameTest.everyLayout(scenes());
    }
}
