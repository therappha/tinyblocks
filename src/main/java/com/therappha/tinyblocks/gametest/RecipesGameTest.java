package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Minimizer and shaft connector recipes (Create is required, so its items are in them). */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecipesGameTest {

    private static boolean loaded(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, name)).isPresent();
    }

    @GameTest(template = "empty_platform")
    public void theMinimizerAndTheConnectorAreCraftable(GameTestHelper helper) {
        helper.assertTrue(loaded(helper, "minimizer"), "no Minimizer recipe with an electron tube");
        helper.assertTrue(loaded(helper, "shaft_connector"), "no shaft connector recipe");
        helper.succeed();
    }
}
