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
        registerMode(event, "hypixel", UhcModeGameTestFunctions::hypixelBorder);
        registerMode(event, "badlion", UhcModeGameTestFunctions::badlionBorder);
        registerMode(event, "deathmatch", UhcModeGameTestFunctions::deathmatch);
        registerMode(event, "disabled", UhcModeGameTestFunctions::disabledDeathmatch);
        registerMode(event, "clocks", UhcModeGameTestFunctions::clocks);
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
    }

    private static void registerMode(RegisterEvent event, String name,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("uhc_mode_" + name),
                () -> context -> {
                    UhcTestDimensions.ensure(context.getLevel().getServer());
                    test.accept(context);
                });
    }
}
