package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.MinigamesGameTestFunctions;
import io.github.brainage04.brainage_minigames.UhcResourceGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcModeGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.EloGameTestFunctions;
import io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the NeoForge GameTest functions; their instances live in {@code
 * data/brainage_minigames/test_instance}.
 */
@EventBusSubscriber(modid = BrainageMinigames.MOD_ID)
public final class NeoForgeCommandGameTest {
    private NeoForgeCommandGameTest() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("container_protection"),
                () -> io.github.brainage04.brainage_minigames.ContainerProtectionGameTestFunctions::protection);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("container_automation"),
                () -> io.github.brainage04.brainage_minigames.ContainerProtectionGameTestFunctions::automation);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("container_lifetime"),
                () -> io.github.brainage04.brainage_minigames.ContainerProtectionGameTestFunctions::offAndLifetime);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("elo_updates"),
                () -> EloGameTestFunctions::updates);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("elo_match_lifecycle"),
                () -> EloGameTestFunctions::lifecycle);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("commands"),
                () -> MinigamesGameTestFunctions::commands);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("snapshot"),
                () -> MinigamesGameTestFunctions::snapshot);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("kit"),
                () -> MinigamesGameTestFunctions::kit);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("duel"),
                () -> MinigamesGameTestFunctions::duel);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("regeneration"),
                () -> MinigamesGameTestFunctions::regeneration);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("map_arena"),
                () -> MinigamesGameTestFunctions::mapArena);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_nether_portals"),
                () ->
                        context -> {
                            UhcTestDimensions.ensure(context.getLevel().getServer());
                            MinigamesGameTestFunctions.uhcNetherPortals(context);
                        });
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_resource_drops"),
                () -> UhcResourceGameTestFunctions::drops);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_resource_generation"),
                () -> UhcResourceGameTestFunctions::generation);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_progression"),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    UhcProgressionGameTestFunctions.progression(context);
                });
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_advanced_crafts"),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    io.github.brainage04.brainage_minigames.UhcAdvancedGameTestFunctions.advanced(context);
                });
        registerSettings(event, "kits_coins", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::kitsAndCoins);
        registerSettings(event, "craft_prompts", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::craftPrompts);
        registerSettings(event, "presets_timing", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::presetsAndTiming);
        registerSettings(event, "borders", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::borders);
        registerSettings(event, "logger_uhc", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::loggerUhc);
        registerSettings(event, "logger_meetup", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::loggerMeetup);
        registerSettings(event, "logger_final_uhc", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::loggerFinalUhc);
        registerSettings(event, "end_portals", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::endPortals);
        registerSettings(event, "nether_border", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::netherBorder);
        registerSettings(event, "coin_combat", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinCombat);
        registerSettings(event, "coin_placement", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinPlacement);
        registerSettings(event, "coin_gathering", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinGathering);
        registerSettings(event, "coin_hypixel_border", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinHypixelBorder);
        registerSettings(event, "coin_badlion_border", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinBadlionBorder);
        registerSettings(event, "coin_logger_kill", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinLoggerKill);
        registerSettings(event, "coin_scope_meetup", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinScopeMeetup);
        registerSettings(event, "coin_scope_final_uhc", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinScopeFinalUhc);
        registerSettings(event, "coin_scope_duel", io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions::coinScopeDuel);
        registerMode(event, "hypixel", UhcModeGameTestFunctions::hypixelBorder);
        registerMode(event, "badlion", UhcModeGameTestFunctions::badlionBorder);
        registerMode(event, "deathmatch", UhcModeGameTestFunctions::deathmatch);
        registerMode(event, "deathmatch_lifecycle", UhcModeGameTestFunctions::deathmatchLifecycle);
        registerMode(event, "disabled", UhcModeGameTestFunctions::disabledDeathmatch);
        registerMode(event, "disabled_setting", UhcModeGameTestFunctions::disabledDeathmatchSetting);
        registerMode(event, "clocks", UhcModeGameTestFunctions::clocks);
        registerMode(event, "preparation_clock", UhcModeGameTestFunctions::preparationClock);
        registerMode(event, "chat", UhcModeGameTestFunctions::readableChat);
        registerMode(event, "health", UhcModeGameTestFunctions::doubleHealth);
        registerMode(event, "sunrise", UhcModeGameTestFunctions::sunriseGrace);
        registerMode(event, "sidebar", UhcModeGameTestFunctions::sidebarText);
        registerMode(event, "following", UhcModeGameTestFunctions::followingRule);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_spawn_tickets"),
                () -> io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions::ticketedSpread);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_spawn_fifty"),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    UhcModeGameTestFunctions.fiftyPlayerSpread(context);
                });
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_region_seed"),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    io.github.brainage04.brainage_minigames.game.uhc.UhcRegionGameTestFunctions.regionSeed(context);
                });
        registerConcurrent(event, "borders", io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions::borders);
        registerConcurrent(event, "mixed", io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions::mixed);
        registerConcurrent(event, "nether", io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions::nether);
        registerConcurrent(event, "deathmatch", io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions::deathmatch);
        registerConcurrent(event, "cleanup", io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions::cleanup);
    }

    private static void registerMode(RegisterEvent event, String name,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_mode_" + name),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    test.accept(context);
                });
    }
    private static void registerSettings(RegisterEvent event, String name,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_settings_" + name),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    test.accept(context);
                });
    }

    private static void registerConcurrent(RegisterEvent event, String name,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_concurrent_" + name),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    test.accept(context);
                });
    }
}
