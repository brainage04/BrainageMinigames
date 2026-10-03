package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcResourceGameTest {
    @GameTest(environment = "brainage_minigames:uhc_resource_drops", maxTicks = 200)
    public void resourceDrops(GameTestHelper context) {
        UhcResourceGameTestFunctions.drops(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_resource_generation", maxTicks = 200)
    public void resourceGeneration(GameTestHelper context) {
        UhcResourceGameTestFunctions.generation(context);
    }
}
