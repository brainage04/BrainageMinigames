package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.MinigamesGameTestFunctions;
import io.github.brainage04.brainage_minigames.UhcResourceGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.EloGameTestFunctions;
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
    }
}
