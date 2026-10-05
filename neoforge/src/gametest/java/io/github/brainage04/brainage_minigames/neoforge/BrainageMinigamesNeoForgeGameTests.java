package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.BrainageMinigamesGameTests;
import io.github.brainage04.brainage_minigames.UhcTestDimensions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registers the shared GameTest functions; their test instances are data in {@code test_instance/}. */
@EventBusSubscriber(modid = BrainageMinigames.MOD_ID)
public final class BrainageMinigamesNeoForgeGameTests {
    private BrainageMinigamesNeoForgeGameTests() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), helper ->
                BrainageMinigamesGameTests.functions(BrainageMinigamesNeoForgeGameTests::ensureDimensions)
                        .forEach(helper::register));
    }

    private static void ensureDimensions(MinecraftServer server) {
        UhcTestDimensions.ensure(server, (dimension, level) -> {
            server.forgeGetWorldMap().put(dimension, level);
            server.markWorldsDirty();
        });
    }
}
