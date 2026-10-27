package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The recipes that load depend on whether Create is installed; it is here. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecipesGameTest {

    private static boolean loaded(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, name)).isPresent();
    }

    @GameTest(template = "empty_platform")
    public void withCreateTheMinimizerTakesAnElectronTubeAndTheConnectorIsCraftable(GameTestHelper helper) {
        helper.assertTrue(loaded(helper, "minimizer_create"), "no Minimizer recipe with an electron tube");
        helper.assertTrue(!loaded(helper, "minimizer"), "the recipe without Create loaded alongside it");
        helper.assertTrue(loaded(helper, "shaft_connector"), "no shaft connector recipe");
        helper.succeed();
    }
}
