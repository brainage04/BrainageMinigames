package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class Combat18GameTest {
    @GameTest(maxTicks = 100)
    public void cooldownAndScope(GameTestHelper context) {
        Combat18GameTestFunctions.cooldownAndScope(context);
    }

    @GameTest(maxTicks = 100)
    public void hitImmunity(GameTestHelper context) {
        Combat18GameTestFunctions.hitImmunity(context);
    }

    @GameTest(maxTicks = 100)
    public void snowballs(GameTestHelper context) {
        Combat18GameTestFunctions.snowballs(context);
    }

    @GameTest(maxTicks = 100)
    public void eggs(GameTestHelper context) {
        Combat18GameTestFunctions.eggs(context);
    }

    @GameTest(maxTicks = 100)
    public void fishingRod(GameTestHelper context) {
        Combat18GameTestFunctions.fishingRod(context);
    }

    @GameTest(maxTicks = 100)
    public void sweepAndCritical(GameTestHelper context) {
        Combat18GameTestFunctions.sweepAndCritical(context);
    }

    @GameTest(maxTicks = 100)
    public void comboAndBoxing(GameTestHelper context) {
        Combat18GameTestFunctions.comboAndBoxing(context);
    }

    @GameTest(maxTicks = 100)
    public void gameRestrictions(GameTestHelper context) {
        Combat18GameTestFunctions.gameRestrictions(context);
    }

    @GameTest(maxTicks = 100)
    public void sprintKnockback(GameTestHelper context) {
        Combat18GameTestFunctions.sprintKnockback(context);
    }
}
