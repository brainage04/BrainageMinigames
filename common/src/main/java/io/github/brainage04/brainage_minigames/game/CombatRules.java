package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.duel.DuelGame;
import io.github.brainage04.brainage_minigames.mixin.AttributeModifiersAccess;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import org.jspecify.annotations.Nullable;

/** Match-scoped combat, never a dimension-wide change to ordinary survival players. */
public final class CombatRules {
    public static final GameRule<Boolean> COMBAT_1_8 = new GameRule<>(
            GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
            false, FeatureFlagSet.of());
    public static final int HIT_IMMUNITY = 20;
    public static final int IMMUNITY_THRESHOLD = 10;

    private CombatRules() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("combat_1_8"), COMBAT_1_8);
    }

    /** Reflection API: read the world's rule; callers must separately check match participation. */
    public static boolean legacyBalance(ServerLevel level) { return level.getGameRules().get(COMBAT_1_8); }
    /** Total weapon damage including the player's base point; custom deltas remain intact. */
    public static double weaponDamage(ServerLevel level, ItemStack weapon, double vanillaDamage) {
        return vanillaDamage + (legacyBalance(level) ? CombatBalance.weaponDelta(weapon) : 0);
    }
    /** Legacy flat reduction as a fraction, independent of toughness and hit size. */
    public static float armorReduction(float armor) { return CombatBalance.armorReduction(armor); }
    /** Per-piece EPF, before the single randomized aggregate roll (modifier: .75/1.25/1.5/2.5). */
    public static int protectionPoints(int level, double modifier) { return CombatBalance.protectionPoints(level, modifier); }
    public static double strengthMultiplier(int level) { return CombatBalance.strengthMultiplier(level); }
    public static int instantHealing(int level) { return CombatBalance.instantHealing(level); }
    public static int instantHarming(int level) { return CombatBalance.instantHarming(level); }
    public static int regenerationInterval(int amplifier) { return CombatBalance.regenerationInterval(amplifier); }

    public static boolean classic(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player && !player.isSpectator()
                && legacyBalance(player.level()) && MatchManager.activeMatch(player.getUUID()) != null;
    }

    public static boolean legacyDamage(LivingEntity victim, DamageSource source) {
        return classic(victim) || classic(source.getEntity());
    }

    /** Ignore only vanilla protection attribute modifiers, retaining unrelated/custom modifiers. */
    public static double attributeWithoutEnchantment(LivingEntity entity, Holder<Attribute> attribute, String path) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) return attribute.value().getDefaultValue();
        double base = instance.getBaseValue();
        var modifiers = ((AttributeModifiersAccess) instance).brainage_minigames$modifiers().values();
        for (var modifier : modifiers) {
            if (!excluded(modifier, path) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
        }
        double value = base;
        for (var modifier : modifiers) {
            if (!excluded(modifier, path) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += base * modifier.amount();
        }
        for (var modifier : modifiers) {
            if (!excluded(modifier, path) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1 + modifier.amount();
        }
        return attribute.value().sanitizeValue(value);
    }

    private static boolean excluded(AttributeModifier modifier, String path) {
        String id = modifier.id().getPath();
        return modifier.id().getNamespace().equals("minecraft") && id.startsWith(path)
                && (id.length() == path.length() || id.charAt(path.length()) == '/');
    }

    public static boolean noAttackCooldown(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return false;
        Match match = MatchManager.activeMatch(player.getUUID());
        return match != null && (legacyBalance(serverPlayer.level())
                || match.game() instanceof DuelGame duel && duel.mechanic() == DuelGame.Mechanic.COMBO);
    }

    public static boolean zeroDamageHit(Player victim, DamageSource source) {
        if (!classic(victim) || !(source.getEntity() instanceof ServerPlayer owner) || owner.isSpectator()) return false;
        Entity projectile = source.getDirectEntity();
        return (projectile instanceof Snowball || projectile instanceof ThrownEgg || projectile instanceof FishingHook)
                && MatchManager.activeMatch(owner.getUUID()) == MatchManager.activeMatch(victim.getUUID());
    }

    /** Combo's explicit shorter hit delay overrides the normal ten-tick legacy/modern delay. */
    public static void prepareComboHit(ServerPlayer victim, int delay) {
        if (victim.invulnerableTime > IMMUNITY_THRESHOLD && HIT_IMMUNITY - victim.invulnerableTime >= delay) victim.invulnerableTime = 0;
    }
}
