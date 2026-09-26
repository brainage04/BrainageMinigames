package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
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
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("commands"),
                () -> NeoForgeGameTestFunctions::commands);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("snapshot"),
                () -> NeoForgeGameTestFunctions::snapshot);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("kit"),
                () -> NeoForgeGameTestFunctions::kit);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("duel"),
                () -> NeoForgeGameTestFunctions::duel);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("regeneration"),
                () -> NeoForgeGameTestFunctions::regeneration);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("map_arena"),
                () -> NeoForgeGameTestFunctions::mapArena);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                BrainageMinigames.id("uhc_nether_portals"),
                () -> NeoForgeGameTestFunctions::uhcNetherPortals);
    }
}
