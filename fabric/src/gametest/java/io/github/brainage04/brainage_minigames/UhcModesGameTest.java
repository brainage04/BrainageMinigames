package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.uhc.UhcModeGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcModesGameTest {
    @GameTest(environment = "brainage_minigames:uhc_mode_hypixel", maxTicks = 500_000)
    public void hypixelBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.hypixelBorder(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_badlion", maxTicks = 500_000)
    public void badlionBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.badlionBorder(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_deathmatch", maxTicks = 500_000)
    public void deathmatch(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.deathmatch(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_deathmatch_lifecycle", maxTicks = 500_000)
    public void deathmatchLifecycle(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.deathmatchLifecycle(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_disabled", maxTicks = 500_000)
    public void disabledDeathmatch(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.disabledDeathmatch(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_disabled_setting", maxTicks = 500_000)
    public void disabledDeathmatchSetting(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.disabledDeathmatchSetting(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_clocks", maxTicks = 100)
    public void clocks(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.clocks(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_preparation_clock", maxTicks = 50)
    public void preparationClock(GameTestHelper context) {
        UhcModeGameTestFunctions.preparationClock(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_chat", maxTicks = 500_000)
    public void readableChat(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.readableChat(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_health", maxTicks = 500_000)
    public void doubleHealth(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.doubleHealth(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_sunrise", maxTicks = 500_000)
    public void sunriseGrace(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.sunriseGrace(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_sidebar", maxTicks = 500_000)
    public void sidebarText(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.sidebarText(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_mode_following", maxTicks = 100)
    public void followingRule(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.followingRule(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_spawn_tickets", maxTicks = 500_000)
    public void spawnTerrainLoadsThroughTicketsAcrossTicks(GameTestHelper context) {
        UhcSpawnGameTestFunctions.ticketedSpread(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_spawn_fifty", maxTicks = 500_000)
    public void fiftyPlayerMatchWaitsForDrySpread(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcModeGameTestFunctions.fiftyPlayerSpread(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_region_seed", maxTicks = 500_000)
    public void regionSeedReplaysCentresAndStarts(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcRegionGameTestFunctions.regionSeed(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_concurrent_borders", maxTicks = 500_000)
    public void concurrentBorders(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions.borders(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_concurrent_mixed", maxTicks = 500_000)
    public void concurrentMixed(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions.mixed(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_concurrent_nether", maxTicks = 500_000)
    public void concurrentNether(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions.nether(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_concurrent_deathmatch", maxTicks = 500_000)
    public void concurrentDeathmatch(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions.deathmatch(context);
    }

    @GameTest(environment = "brainage_minigames:uhc_concurrent_cleanup", maxTicks = 500_000)
    public void concurrentCleanup(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions.cleanup(context);
    }
}
