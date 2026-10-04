package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcSettingsGameTest {
    @GameTest(environment = "brainage_minigames:uhc_settings_kits_coins", maxTicks = 500_000)
    public void kitsAndCoins(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.kitsAndCoins(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_craft_prompts", maxTicks = 500_000)
    public void craftPrompts(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.craftPrompts(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_presets_timing", maxTicks = 500_000)
    public void presetsAndTiming(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.presetsAndTiming(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_borders", maxTicks = 500_000)
    public void borders(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.borders(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_logger_uhc", maxTicks = 500_000)
    public void loggerUhc(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.loggerUhc(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_logger_meetup", maxTicks = 500_000)
    public void loggerMeetup(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.loggerMeetup(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_logger_final_uhc", maxTicks = 500_000)
    public void loggerFinalUhc(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.loggerFinalUhc(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_end_portals", maxTicks = 500_000)
    public void endPortals(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.endPortals(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_nether_border", maxTicks = 500_000)
    public void netherBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.netherBorder(context);
    }
}
