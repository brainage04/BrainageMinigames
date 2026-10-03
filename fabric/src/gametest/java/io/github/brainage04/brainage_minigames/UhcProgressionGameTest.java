package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcProgressionGameTest {
    @GameTest(environment = "brainage_minigames:uhc_nether_match", maxTicks = 1500)
    public void coinsUnlockingRecipesAndMaxAll(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcProgressionGameTestFunctions.progression(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_nether_match", maxTicks = 1500)
    public void advancedCraftsAndOptionalRngPolicy(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcAdvancedGameTestFunctions.advanced(context);
    }
}
