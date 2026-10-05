package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.Combat18GameTestFunctions;
import io.github.brainage04.brainage_minigames.CombatBalanceGameTestFunctions;
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
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sword_blocking"),
                () -> Combat18GameTestFunctions::swordBlocking);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sword_lifecycle"),
                () -> Combat18GameTestFunctions::swordLifecycle);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sword_respawn"),
                () -> Combat18GameTestFunctions::swordRespawn);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("combat18_sword_elimination"),
                () -> Combat18GameTestFunctions::swordElimination);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_weapons"),
                () -> CombatBalanceGameTestFunctions::weapons);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_enchantments"),
                () -> CombatBalanceGameTestFunctions::enchantments);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_strength_weakness_critical"),
                () -> CombatBalanceGameTestFunctions::strengthWeaknessCritical);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_armor_and_durability"),
                () -> CombatBalanceGameTestFunctions::armorAndDurability);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_protection"),
                () -> CombatBalanceGameTestFunctions::protection);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_fire_protection_and_aspect"),
                () -> CombatBalanceGameTestFunctions::fireProtectionAndAspect);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_apples_and_head"),
                () -> CombatBalanceGameTestFunctions::applesAndHead);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_recipe_and_cooldown"),
                () -> CombatBalanceGameTestFunctions::recipeAndCooldown);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_bow_and_punch"),
                () -> CombatBalanceGameTestFunctions::bowAndPunch);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_exhaustion"),
                () -> CombatBalanceGameTestFunctions::exhaustion);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_natural_regeneration"),
                () -> CombatBalanceGameTestFunctions::naturalRegeneration);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_potion_durations_and_healing"),
                () -> CombatBalanceGameTestFunctions::potionDurationsAndHealing);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_splash"),
                () -> CombatBalanceGameTestFunctions::splash);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_blast_and_knockback"),
                () -> CombatBalanceGameTestFunctions::blastAndKnockback);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), BrainageMinigames.id("balance18_fire_and_lava"),
                () -> CombatBalanceGameTestFunctions::fireAndLava);
    }
}
