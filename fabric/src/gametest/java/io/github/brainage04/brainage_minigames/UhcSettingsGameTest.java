package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UhcSettingsGameTest {
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_combat", maxTicks = 500_000)
    public void coinCombat(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinCombat(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_placement", maxTicks = 500_000)
    public void coinPlacement(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinPlacement(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_gathering", maxTicks = 500_000)
    public void coinGathering(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinGathering(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_hypixel_border", maxTicks = 500_000)
    public void coinHypixelBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinHypixelBorder(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_badlion_border", maxTicks = 500_000)
    public void coinBadlionBorder(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinBadlionBorder(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_logger_kill", maxTicks = 500_000)
    public void coinLoggerKill(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinLoggerKill(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_pvp_announced_without_grace_uhc", maxTicks = 500_000)
    public void pvpAnnouncedWithoutGraceUhc(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.pvpAnnouncedWithoutGraceUhc(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_pvp_announced_without_grace_meetup", maxTicks = 500_000)
    public void pvpAnnouncedWithoutGraceMeetup(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.pvpAnnouncedWithoutGraceMeetup(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_pvp_announced_without_grace_final_uhc", maxTicks = 500_000)
    public void pvpAnnouncedWithoutGraceFinalUhc(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.pvpAnnouncedWithoutGraceFinalUhc(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_scope_meetup", maxTicks = 500_000)
    public void coinScopeMeetup(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinScopeMeetup(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_scope_final_uhc", maxTicks = 500_000)
    public void coinScopeFinalUhc(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinScopeFinalUhc(context);
    }
    @GameTest(environment = "brainage_minigames:uhc_settings_coin_scope_duel", maxTicks = 500_000)
    public void coinScopeDuel(GameTestHelper context) {
        UhcTestDimensions.ensure(context.getLevel().getServer());
        UhcSettingsGameTestFunctions.coinScopeDuel(context);
    }
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
