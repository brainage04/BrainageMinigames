package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AntiJanitorGameTest {
    @GameTest(environment = "brainage_minigames:anti_janitor_combat", maxTicks = 200)
    public void combat(GameTestHelper context) {
        AntiJanitorGameTestFunctions.combat(context);
    }

    @GameTest(environment = "brainage_minigames:anti_janitor_loot", maxTicks = 200)
    public void loot(GameTestHelper context) {
        AntiJanitorGameTestFunctions.loot(context);
    }

    @GameTest(environment = "brainage_minigames:anti_janitor_shield_loot", maxTicks = 200)
    public void shieldLoot(GameTestHelper context) {
        AntiJanitorGameTestFunctions.shieldLoot(context);
    }

    @GameTest(environment = "brainage_minigames:anti_janitor_locations", maxTicks = 200)
    public void locations(GameTestHelper context) {
        AntiJanitorGameTestFunctions.locations(context);
    }

    @GameTest(environment = "brainage_minigames:anti_janitor_scope", maxTicks = 200)
    public void scope(GameTestHelper context) {
        AntiJanitorGameTestFunctions.scope(context);
    }
}
