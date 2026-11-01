package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Minimizer, Grid Save and shaft connector recipes (Create is required, so its items are in them). */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecipesGameTest {

    private static boolean loaded(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, name)).isPresent();
    }

    /** The fixed-size Minimizers of older versions, in a saved world, turn into the one there is now. */
    @GameTest(template = "empty_platform")
    public void anOldMinimizerIsTheMinimizer(GameTestHelper helper) {
        for (String old : new String[] {"minimizer_2", "minimizer_4", "minimizer_9", "minimizer_16"}) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, old);
            helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) == com.therappha.tinyblocks.setup.Registration.MINIMIZER.get(),
                    old + " doesn't lead to the Minimizer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void theMinimizerAndTheConnectorAreCraftable(GameTestHelper helper) {
        helper.assertTrue(loaded(helper, "minimizer"), "no Minimizer recipe with an electron tube");
        helper.assertTrue(loaded(helper, "grid_save"), "no Grid Save recipe");
        helper.assertTrue(loaded(helper, "shaft_connector"), "no shaft connector recipe");
        helper.succeed();
    }
}
