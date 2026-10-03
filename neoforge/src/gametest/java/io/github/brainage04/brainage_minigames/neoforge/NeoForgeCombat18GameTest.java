package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.Combat18GameTestFunctions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = BrainageMinigames.MOD_ID)
public final class NeoForgeCombat18GameTest {
    private NeoForgeCombat18GameTest() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_cooldown"),
                () -> Combat18GameTestFunctions::cooldownAndScope);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_immunity"),
                () -> Combat18GameTestFunctions::hitImmunity);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_snowballs"),
                () -> Combat18GameTestFunctions::snowballs);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_eggs"),
                () -> Combat18GameTestFunctions::eggs);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_rod"),
                () -> Combat18GameTestFunctions::fishingRod);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sweep_crit"),
                () -> Combat18GameTestFunctions::sweepAndCritical);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_duels"),
                () -> Combat18GameTestFunctions::comboAndBoxing);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_restrictions"),
                () -> Combat18GameTestFunctions::gameRestrictions);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sprint_knockback"),
                () -> Combat18GameTestFunctions::sprintKnockback);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_shield_inventory"),
                () -> Combat18GameTestFunctions::shieldInventoryLocks);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_shield_lifecycle"),
                () -> Combat18GameTestFunctions::shieldLifecycle);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_shield_respawn"),
                () -> Combat18GameTestFunctions::shieldRespawn);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_shield_elimination"),
                () -> Combat18GameTestFunctions::shieldElimination);
    }
}
