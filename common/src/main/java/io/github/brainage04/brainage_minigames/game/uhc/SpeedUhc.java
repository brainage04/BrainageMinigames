package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/**
 * Speed UHC's own rules and what its kits, perks and Masteries do. Every participant's resources
 * come quickly: gravel and chickens also drop arrows, sugar cane the player breaks drops a book and
 * a sugar instead of itself, and brewing stands inside the match's border brew at once. Smelted ore
 * and cooked meat (CutClean) and whole-tree felling (Timber) are on for every Speed UHC match.
 */
public final class SpeedUhc {
    private static final Identifier INVIGORATE_ID = BrainageMinigames.id("speed_uhc_invigorate");
    /** Arrows a broken gravel block adds to its usual drop. */
    public static final int GRAVEL_ARROWS = 2;
    /** Arrows a killed chicken adds to its usual drop. */
    public static final int CHICKEN_ARROWS = 4;
    /** Fire damage this long after the last starts a new Cold Blood episode. */
    private static final int COLD_BLOOD_GAP_TICKS = 5 * 20;
    /** Kits a bot without a saved choice picks from: the ones whose armour it wears at once. */
    private static final List<String> BOT_KITS = List.of(SpeedUhcKit.DEFAULT, "archaeologist", "knight", "miner", "tamer");
    /** Masteries a bot without a saved choice picks from: the ones that help without aiming. */
    private static final List<SpeedUhcMastery> BOT_MASTERIES = List.of(
            SpeedUhcMastery.FORTUNE, SpeedUhcMastery.MASTER_BAKER, SpeedUhcMastery.HUNTSMAN, SpeedUhcMastery.GUARDIAN);
    private static final Map<Match, State> STATES = new HashMap<>();

    /** What each participant took into the match and what their perks track. */
    static final class State {
        final Match match;
        final boolean maxed;
        final Map<UUID, Set<SpeedUhcPerk>> perks = new HashMap<>();
        final Map<UUID, SpeedUhcKit> kits = new HashMap<>();
        final Map<UUID, SpeedUhcMastery> masteries = new HashMap<>();
        final Map<UUID, Integer> bowKills = new HashMap<>();
        final Map<UUID, Integer> invigorated = new HashMap<>();
        final Map<UUID, long[]> coldBlood = new HashMap<>();
        final Set<UUID> inNether = new HashSet<>();

        State(Match match) {
            this.match = match;
            this.maxed = SpeedUhcProgression.maxPerks(match.server());
        }

        boolean has(ServerPlayer player, SpeedUhcPerk perk) {
            return perks.getOrDefault(player.getUUID(), Set.of()).contains(perk);
        }

        /** The perk's number for the player, 0 when they do not have it. */
        int value(ServerPlayer player, SpeedUhcPerk perk) {
            return has(player, perk) ? perk.value(maxed) : 0;
        }

        boolean mastery(ServerPlayer player, SpeedUhcMastery mastery) {
            return masteries.get(player.getUUID()) == mastery;
        }
    }

    private SpeedUhc() {}

    /** The Speed UHC state of an alive participant, or null. */
    static @Nullable State state(ServerPlayer player) {
        Match match = MatchManager.activeMatch(player.getUUID());
        return match == null || !match.isAlive(player.getUUID()) ? null : STATES.get(match);
    }

    /**
     * Whether {@code actor} (the player breaking a block or killing a mob) plays an active Speed UHC,
     * which turns CutClean and Timber on for them.
     */
    public static boolean forces(@Nullable Entity actor) {
        return actor instanceof ServerPlayer player && state(player) != null;
    }

    public static Optional<SpeedUhcKit> kit(Match match, UUID player) {
        State state = STATES.get(match);
        return state == null ? Optional.empty() : Optional.ofNullable(state.kits.get(player));
    }

    public static Optional<SpeedUhcMastery> mastery(Match match, UUID player) {
        State state = STATES.get(match);
        return state == null ? Optional.empty() : Optional.ofNullable(state.masteries.get(player));
    }

    private static boolean roll(ServerPlayer player, int percent) {
        return percent > 0 && player.getRandom().nextInt(100) < percent;
    }

    // Match lifecycle

    static void start(Match match) {
        State state = new State(match);
        STATES.put(match, state);
        MinecraftServer server = match.server();
        boolean ownKits = match.kit().equals(SpeedUhcKit.DEFAULT_KIT);
        for (ServerPlayer player : match.alivePlayers()) {
            UUID id = player.getUUID();
            Set<SpeedUhcPerk> perks = EnumSet.noneOf(SpeedUhcPerk.class);
            for (SpeedUhcPerk perk : SpeedUhcPerk.values()) {
                if (SpeedUhcProgression.active(server, id, perk)) perks.add(perk);
            }
            state.perks.put(id, perks);
            SpeedUhcKit kit = kitFor(match, player);
            state.kits.put(id, kit);
            if (ownKits && !kit.isDefault()) {
                // The match gave Default's items; another kit replaces them.
                player.getInventory().clearContent();
                for (ItemStack item : kit.stacks(player.registryAccess())) KitStorage.equipOrGive(player, item);
            }
            SpeedUhcMastery mastery = masteryFor(match, player);
            state.masteries.put(id, mastery);
            int tenacity = state.value(player, SpeedUhcPerk.TENACITY);
            if (tenacity > 0) player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, tenacity * 20, 0));
            player.sendSystemMessage(Component.literal("Kit: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(kit.name()).withStyle(kit.color()))
                    .append(Component.literal("  Mastery: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(mastery.displayName).withStyle(ChatFormatting.GOLD)));
        }
    }

    /** The player's saved kit; a bot without one picks one of {@link #BOT_KITS} it owns. */
    static SpeedUhcKit kitFor(Match match, ServerPlayer player) {
        MinecraftServer server = match.server();
        if (match.isBot(player.getUUID()) && !SpeedUhcProgression.hasSelection(server, player.getUUID())) {
            List<SpeedUhcKit> owned = new ArrayList<>();
            for (String id : BOT_KITS) {
                SpeedUhcKit kit = SpeedUhcKit.find(id);
                if (kit != null && SpeedUhcProgression.owns(server, player.getUUID(), kit)) owned.add(kit);
            }
            return owned.get(player.getRandom().nextInt(owned.size()));
        }
        return SpeedUhcProgression.selectedKit(server, player.getUUID());
    }

    /** The player's active Mastery; a bot without a choice picks one of {@link #BOT_MASTERIES} it owns. */
    static SpeedUhcMastery masteryFor(Match match, ServerPlayer player) {
        MinecraftServer server = match.server();
        if (match.isBot(player.getUUID()) && !SpeedUhcProgression.hasMastery(server, player.getUUID())) {
            List<SpeedUhcMastery> owned = new ArrayList<>();
            for (SpeedUhcMastery mastery : BOT_MASTERIES) {
                if (SpeedUhcProgression.owns(server, player.getUUID(), mastery)) owned.add(mastery);
            }
            if (!owned.isEmpty()) return owned.get(player.getRandom().nextInt(owned.size()));
        }
        return SpeedUhcProgression.mastery(server, player.getUUID());
    }

    static void tick(Match match) {
        State state = STATES.get(match);
        if (state == null || match.activeTicks() % 20 != 0) return;
        for (ServerPlayer player : match.alivePlayers()) {
            if (state.has(player, SpeedUhcPerk.SWIMMING_CHAMPION) && player.isInWater()) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false, true));
            }
            if (state.mastery(player, SpeedUhcMastery.BERSERK) && player.getHealth() < 6.0F) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 0, false, false, true));
            }
            boolean nether = io.github.brainage04.brainage_minigames.dimension.ModDimensions.nether(player.level().dimension());
            if (nether && state.inNether.add(player.getUUID())) {
                int seconds = state.value(player, SpeedUhcPerk.PORTAL_PROTECTION);
                if (seconds > 0) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, seconds * 20, 0));
            } else if (!nether) {
                state.inNether.remove(player.getUUID());
            }
        }
    }

    static void killed(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = STATES.get(match);
        if (state == null || killer == null || killer == victim || !match.isAlive(killer.getUUID())) return;
        Optional<MatchTeam> team = match.teamOf(killer.getUUID());
        if (team.isPresent() && team.equals(match.teamOf(victim.getUUID()))) return;
        DamageSource last = victim.getLastDamageSource();
        if (last != null && last.getDirectEntity() instanceof AbstractArrow arrow) {
            int kills = state.bowKills.merge(killer.getUUID(), 1, Integer::sum);
            int every = state.value(killer, SpeedUhcPerk.BOW_FLEX);
            ItemStack weapon = arrow.getWeaponItem();
            if (every > 0 && kills % every == 0 && weapon != null && weapon.is(Items.BOW)) {
                ItemStack bow = findInInventory(killer, weapon);
                if (bow != null) {
                    var power = killer.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER);
                    EnchantmentHelper.updateEnchantments(bow, enchantments ->
                            enchantments.set(power, enchantments.getLevel(power) + 1));
                }
            }
        }
        if (state.has(killer, SpeedUhcPerk.NOURISHMENT)) {
            killer.getFoodData().setFoodLevel(20);
            killer.getFoodData().setSaturation(20.0F);
        }
        if (roll(killer, state.value(killer, SpeedUhcPerk.NO_MERCY))) {
            UhcProgression.award(match, killer.getUUID(), UhcProgression.CoinAction.KILL);
        }
        switch (state.masteries.getOrDefault(killer.getUUID(), SpeedUhcMastery.WILD_SPECIALIST)) {
            case HUNTSMAN -> killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 30 * 20, 1));
            case VAMPIRISM -> killer.heal(1.0F);
            case INVIGORATE -> {
                int extra = state.invigorated.merge(killer.getUUID(), 1, (old, one) -> Math.min(4, old + one));
                AttributeInstance health = killer.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.addOrUpdateTransientModifier(
                            new AttributeModifier(INVIGORATE_ID, extra, AttributeModifier.Operation.ADD_VALUE));
                }
            }
            default -> {}
        }
    }

    private static @Nullable ItemStack findInInventory(ServerPlayer player, ItemStack copy) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, copy)) return stack;
        }
        return null;
    }

    static void release(Match match, ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(INVIGORATE_ID) != null) {
            health.removeModifier(INVIGORATE_ID);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }

    static void close(Match match) {
        STATES.remove(match);
    }

    // Damage

    /** Damage {@code victim} is about to take, scaled by the Speed UHC perks and Masteries. */
    public static float hurt(ServerPlayer victim, DamageSource source, float amount) {
        State state = state(victim);
        if (state == null || amount <= 0) return amount;
        Entity cause = source.getEntity();
        if (cause == null && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)
                && state.mastery(victim, SpeedUhcMastery.WILD_SPECIALIST)) {
            amount *= 0.5F;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            int seconds = state.value(victim, SpeedUhcPerk.COLD_BLOOD);
            long now = victim.level().getGameTime();
            long[] episode = state.coldBlood.computeIfAbsent(victim.getUUID(), id -> new long[] {Long.MIN_VALUE, Long.MIN_VALUE});
            if (now - episode[1] > COLD_BLOOD_GAP_TICKS) episode[0] = now;
            episode[1] = now;
            if (seconds > 0 && now - episode[0] < seconds * 20L) amount *= 0.5F;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            amount *= 1 - state.value(victim, SpeedUhcPerk.LOW_GRAVITY) / 100F;
        }
        if (cause instanceof Enemy && source.getDirectEntity() == cause
                && victim.getHealth() < state.value(victim, SpeedUhcPerk.MONSTER_TAMER) * 2) {
            return 0;
        }
        if (!(cause instanceof ServerPlayer attacker) || attacker == victim || !state.match.isAlive(attacker.getUUID())) {
            return amount;
        }
        Optional<MatchTeam> team = state.match.teamOf(attacker.getUUID());
        if (team.isPresent() && team.equals(state.match.teamOf(victim.getUUID()))) return amount;
        if (state.mastery(victim, SpeedUhcMastery.GUARDIAN)) amount *= 0.95F;
        if (source.getDirectEntity() instanceof AbstractArrow) {
            if (roll(attacker, state.value(attacker, SpeedUhcPerk.ARROW_RECOVERY))) {
                PlayerUtils.giveOrDrop(attacker, new ItemStack(Items.ARROW));
            }
            double distance = attacker.distanceTo(victim);
            if (state.mastery(attacker, SpeedUhcMastery.SNIPER) && attacker.level() == victim.level() && distance > 20) {
                amount *= (float) (1 + 0.02 * distance);
            }
        }
        return amount;
    }

    // Resources

    /**
     * Gravel adds arrows, sugar cane the player breaks drops a book and a sugar instead, Fortune
     * may add one more of an ore's drop and Telekinesis puts ore drops in the inventory.
     */
    public static List<ItemStack> drops(List<ItemStack> drops, BlockState block, LootParams.Builder params) {
        if (!(params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) return drops;
        State state = state(player);
        if (state == null) return drops;
        List<ItemStack> result = new ArrayList<>(drops.size() + 2);
        if (block.is(Blocks.SUGAR_CANE)) {
            for (ItemStack drop : drops) {
                if (!drop.is(Items.SUGAR_CANE)) result.add(drop);
            }
            result.add(new ItemStack(Items.BOOK));
            result.add(new ItemStack(Items.SUGAR));
            return result;
        }
        result.addAll(drops);
        if (block.is(Blocks.GRAVEL)) result.add(new ItemStack(Items.ARROW, GRAVEL_ARROWS));
        if (UhcResourceRules.ore(block) == null || drops.isEmpty()) return result;
        if (state.mastery(player, SpeedUhcMastery.FORTUNE) && player.getRandom().nextInt(100) < 25) {
            result.add(drops.getFirst().copyWithCount(1));
        }
        if (!state.has(player, SpeedUhcPerk.TELEKINESIS)) return result;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack drop : result) {
            if (!player.getInventory().add(drop) && !drop.isEmpty()) left.add(drop);
        }
        return left;
    }

    /** Chickens add arrows; Ender Generosity and Marksmob roll their extra drops. */
    public static void mobDrops(LivingEntity victim, ServerLevel level, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        State state = state(player);
        if (state == null) return;
        if (victim instanceof Chicken) victim.spawnAtLocation(level, new ItemStack(Items.ARROW, CHICKEN_ARROWS));
        if (victim instanceof EnderMan && roll(player, state.value(player, SpeedUhcPerk.ENDER_GENEROSITY))) {
            victim.spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL));
        }
        if ((victim instanceof AbstractSkeleton || victim instanceof Spider)
                && roll(player, state.value(player, SpeedUhcPerk.MARKSMOB))) {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 1);
            victim.spawnAtLocation(level, bow);
        }
    }

    /** Expert Miner: more experience from ores and mobs the player kills. */
    public static int experience(ServerPlayer player, int experience) {
        State state = state(player);
        int percent = state == null ? 0 : state.value(player, SpeedUhcPerk.EXPERT_MINER);
        if (percent == 0 || experience <= 0) return experience;
        int numerator = experience * percent;
        return experience + numerator / 100 + (player.getRandom().nextInt(100) < numerator % 100 ? 1 : 0);
    }

    /** Master Brewer lengthens beneficial potion effects; Medicine shortens Poison. */
    public static MobEffectInstance potion(ServerPlayer player, MobEffectInstance effect) {
        State state = state(player);
        if (state == null || effect.isInfiniteDuration()) return effect;
        Holder<MobEffect> type = effect.getEffect();
        if (effect.is(MobEffects.POISON)) {
            int percent = state.value(player, SpeedUhcPerk.MEDICINE);
            return percent == 0 ? effect : effect.withScaledDuration(1 - percent / 100F);
        }
        int percent = state.value(player, SpeedUhcPerk.MASTER_BREWER);
        return percent == 0 || !type.value().isBeneficial() ? effect : effect.withScaledDuration(1 + percent / 100F);
    }

    /** Vitamins and Master Baker after the player finished eating a golden apple. */
    public static void ateGoldenApple(ServerPlayer player) {
        State state = state(player);
        if (state == null) return;
        int seconds = state.value(player, SpeedUhcPerk.VITAMINS);
        if (seconds > 0) player.addEffect(new MobEffectInstance(MobEffects.SPEED, seconds * 20, 1));
        // A golden apple's Regeneration II heals 4 health; half as much again.
        if (state.mastery(player, SpeedUhcMastery.MASTER_BAKER)) player.heal(2.0F);
    }

    /** Whether a brewing stand at {@code pos} stands inside an active Speed UHC's border, where brewing is instant. */
    public static boolean instantBrewing(Level level, BlockPos pos) {
        if (STATES.isEmpty() || !(level instanceof ServerLevel serverLevel)) return false;
        for (State state : STATES.values()) {
            if (state.match.phase() != MatchPhase.ACTIVE || !(state.match.arena() instanceof UhcArena arena)) continue;
            WorldBorder border = arena.border(serverLevel);
            if (border != null && border.isWithinBounds(pos)) return true;
        }
        return false;
    }
}
