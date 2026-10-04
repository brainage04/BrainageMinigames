package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ContainerProtectionGameTest {
    @GameTest(environment = "brainage_minigames:container_protection", maxTicks = 100)
    public void protection(GameTestHelper context) { ContainerProtectionGameTestFunctions.protection(context); }

    @GameTest(environment = "brainage_minigames:container_automation", maxTicks = 100)
    public void automation(GameTestHelper context) { ContainerProtectionGameTestFunctions.automation(context); }

    @GameTest(environment = "brainage_minigames:container_lifetime", maxTicks = 100)
    public void offAndLifetime(GameTestHelper context) { ContainerProtectionGameTestFunctions.offAndLifetime(context); }
}
