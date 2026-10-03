package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.uhc.UhcModeGameTestFunctions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcModesGameTest {
    @GameTest(environment = "brainage_minigames:uhc_mode_hypixel", maxTicks = 200)
    public void hypixelBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.hypixelBorder(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_badlion", maxTicks = 200)
    public void badlionBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.badlionBorder(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_deathmatch", maxTicks = 300)
    public void deathmatch(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.deathmatch(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_disabled", maxTicks = 200)
    public void disabledDeathmatch(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.disabledDeathmatch(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_clocks", maxTicks = 100)
    public void clocks(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.clocks(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_chat", maxTicks = 200)
    public void readableChat(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.readableChat(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_health", maxTicks = 200)
    public void doubleHealth(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.doubleHealth(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_sunrise", maxTicks = 200)
    public void sunriseGrace(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.sunriseGrace(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_sidebar", maxTicks = 200)
    public void sidebarText(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.sidebarText(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_following", maxTicks = 100)
    public void followingRule(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.followingRule(context);
    }
}
