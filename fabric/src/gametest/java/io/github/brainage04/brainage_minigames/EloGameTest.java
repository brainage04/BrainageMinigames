package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.EloGameTestFunctions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class EloGameTest {
    @GameTest
    public void eloUpdates(GameTestHelper context) {
        EloGameTestFunctions.updates(context);
    }

    @GameTest
    public void eloMatchLifecycle(GameTestHelper context) {
        EloGameTestFunctions.lifecycle(context);
    }
}
