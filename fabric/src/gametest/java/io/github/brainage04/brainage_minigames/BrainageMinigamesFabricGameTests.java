package io.github.brainage04.brainage_minigames;

import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Registers the shared GameTest functions; their test instances are data in {@code test_instance/}. */
public final class BrainageMinigamesFabricGameTests implements ModInitializer {
    @Override
    public void onInitialize() {
        BrainageMinigamesGameTests.functions(BrainageMinigamesFabricGameTests::ensureDimensions)
                .forEach((id, test) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, id, test));
    }

    private static void ensureDimensions(MinecraftServer server) {
        UhcTestDimensions.ensure(server, (dimension, level) -> {
            @SuppressWarnings("unchecked")
            Map<ResourceKey<Level>, ServerLevel> levels = UhcTestDimensions.field(server, "levels", Map.class);
            levels.put(dimension, level);
        });
    }
}
