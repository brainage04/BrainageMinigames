package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.duel.DuelGame;
import java.util.function.BiConsumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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

    /** Both 1.8 and 26.2 count down from 20; full hits resume at 10. */
    public static final int HIT_IMMUNITY = 20;
    public static final int IMMUNITY_THRESHOLD = 10;

    private static final CustomData SHIELD_DATA = shieldData();

    private static CustomData shieldData() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("brainage_minigames:combat_1_8_shield", true);
        return CustomData.of(tag);
    }

    static ItemStack blockingShield() {
        ItemStack shield = new ItemStack(Items.SHIELD);
        shield.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        shield.set(DataComponents.CUSTOM_DATA, SHIELD_DATA);
        shield.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        return shield;
    }

    public static boolean isBlockingShield(ItemStack stack) {
        return stack.is(Items.SHIELD) && SHIELD_DATA.equals(stack.get(DataComponents.CUSTOM_DATA));
    }

    /** The provided shield stays locked until its match removes it, including between ticks. */
    public static boolean shieldLocked(Player player) {
        return player instanceof ServerPlayer && isBlockingShield(player.getOffhandItem());
    }

    public static boolean shieldSlotLocked(Slot slot) {
        return slot.container instanceof Inventory inventory
                && slot.getContainerSlot() == Inventory.SLOT_OFFHAND
                && shieldLocked(inventory.player);
    }

    private CombatRules() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("combat_1_8"), COMBAT_1_8);
    }

    public static boolean classic(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player
                && !player.isSpectator()
                && player.level().getGameRules().get(COMBAT_1_8)
                && MatchManager.activeMatch(player.getUUID()) != null;
    }

    public static boolean noAttackCooldown(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return false;
        Match match = MatchManager.activeMatch(player.getUUID());
        return match != null && (serverPlayer.level().getGameRules().get(COMBAT_1_8)
                || match.game() instanceof DuelGame duel && duel.mechanic() == DuelGame.Mechanic.COMBO);
    }

    /** Let vanilla process an actual zero-damage hit, including immunity, PvP and game rules. */
    public static boolean zeroDamageHit(Player victim, DamageSource source) {
        if (!classic(victim) || !(source.getEntity() instanceof ServerPlayer owner) || owner.isSpectator()) return false;
        Entity projectile = source.getDirectEntity();
        return (projectile instanceof Snowball || projectile instanceof ThrownEgg || projectile instanceof FishingHook)
                && MatchManager.activeMatch(owner.getUUID()) == MatchManager.activeMatch(victim.getUUID());
    }

    /** Combo's explicit shorter hit delay overrides the normal ten-tick legacy/modern delay. */
    public static void prepareComboHit(ServerPlayer victim, int delay) {
        int sinceLastHit = HIT_IMMUNITY - victim.invulnerableTime;
        if (victim.invulnerableTime > IMMUNITY_THRESHOLD && sinceLastHit >= delay) {
            victim.invulnerableTime = 0;
        }
    }
}
