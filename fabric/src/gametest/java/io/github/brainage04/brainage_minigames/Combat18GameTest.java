package io.github.brainage04.brainage_minigames;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class Combat18GameTest {
    @GameTest(maxTicks = 100)
    public void cooldownAndScope(GameTestHelper context) {
        Combat18GameTestFunctions.cooldownAndScope(context);
    }

    @GameTest(maxTicks = 100)
    public void hitImmunity(GameTestHelper context) {
        Combat18GameTestFunctions.hitImmunity(context);
    }

    @GameTest(maxTicks = 100)
    public void snowballs(GameTestHelper context) {
        Combat18GameTestFunctions.snowballs(context);
    }

    @GameTest(maxTicks = 100)
    public void eggs(GameTestHelper context) {
        Combat18GameTestFunctions.eggs(context);
    }

    @GameTest(maxTicks = 100)
    public void fishingRod(GameTestHelper context) {
        Combat18GameTestFunctions.fishingRod(context);
    }

    @GameTest(maxTicks = 100)
    public void sweepAndCritical(GameTestHelper context) {
        Combat18GameTestFunctions.sweepAndCritical(context);
    }

    @GameTest(maxTicks = 100)
    public void comboAndBoxing(GameTestHelper context) {
        Combat18GameTestFunctions.comboAndBoxing(context);
    }

    @GameTest(maxTicks = 100)
    public void gameRestrictions(GameTestHelper context) {
        Combat18GameTestFunctions.gameRestrictions(context);
    }

    @GameTest(maxTicks = 100)
    public void sprintKnockback(GameTestHelper context) {
        Combat18GameTestFunctions.sprintKnockback(context);
    }

    @GameTest(maxTicks = 100)
    public void swordBlocking(GameTestHelper context) {
        Combat18GameTestFunctions.swordBlocking(context);
    }

    @GameTest(maxTicks = 100)
    public void swordLifecycle(GameTestHelper context) {
        Combat18GameTestFunctions.swordLifecycle(context);
    }

    @GameTest(maxTicks = 100)
    public void swordRespawn(GameTestHelper context) {
        Combat18GameTestFunctions.swordRespawn(context);
    }

    @GameTest(maxTicks = 100)
    public void swordElimination(GameTestHelper context) {
        Combat18GameTestFunctions.swordElimination(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceWeapons(GameTestHelper context) {
        CombatBalanceGameTestFunctions.weapons(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceEnchantments(GameTestHelper context) {
        CombatBalanceGameTestFunctions.enchantments(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceStrengthWeaknessCritical(GameTestHelper context) {
        CombatBalanceGameTestFunctions.strengthWeaknessCritical(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceArmorAndDurability(GameTestHelper context) {
        CombatBalanceGameTestFunctions.armorAndDurability(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceProtection(GameTestHelper context) {
        CombatBalanceGameTestFunctions.protection(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceFireProtectionAndAspect(GameTestHelper context) {
        CombatBalanceGameTestFunctions.fireProtectionAndAspect(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceApplesAndHead(GameTestHelper context) {
        CombatBalanceGameTestFunctions.applesAndHead(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceRecipeAndCooldown(GameTestHelper context) {
        CombatBalanceGameTestFunctions.recipeAndCooldown(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceBowAndPunch(GameTestHelper context) {
        CombatBalanceGameTestFunctions.bowAndPunch(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceExhaustion(GameTestHelper context) {
        CombatBalanceGameTestFunctions.exhaustion(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceNaturalRegeneration(GameTestHelper context) {
        CombatBalanceGameTestFunctions.naturalRegeneration(context);
    }

    @GameTest(maxTicks = 100)
    public void balancePotionDurationsAndHealing(GameTestHelper context) {
        CombatBalanceGameTestFunctions.potionDurationsAndHealing(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceSplash(GameTestHelper context) {
        CombatBalanceGameTestFunctions.splash(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceBlastAndKnockback(GameTestHelper context) {
        CombatBalanceGameTestFunctions.blastAndKnockback(context);
    }

    @GameTest(maxTicks = 100)
    public void balanceFireAndLava(GameTestHelper context) {
        CombatBalanceGameTestFunctions.fireAndLava(context);
    }
}
