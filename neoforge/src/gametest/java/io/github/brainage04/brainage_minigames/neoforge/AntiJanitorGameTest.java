package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.AntiJanitorGameTestFunctions;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = BrainageMinigames.MOD_ID)
public final class AntiJanitorGameTest {
    private AntiJanitorGameTest() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("anti_janitor_combat"),
                () -> AntiJanitorGameTestFunctions::combat);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("anti_janitor_loot"),
                () -> AntiJanitorGameTestFunctions::loot);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("anti_janitor_locations"),
                () -> AntiJanitorGameTestFunctions::locations);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("anti_janitor_scope"),
                () -> AntiJanitorGameTestFunctions::scope);
    }
}
