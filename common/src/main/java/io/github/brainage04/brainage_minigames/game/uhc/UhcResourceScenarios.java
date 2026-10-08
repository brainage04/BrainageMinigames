package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules.Ore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Optional resource scenarios for every UHC-style game, all off by default. Drop rules apply in the
 * UHC-style dimensions ({@link UhcResourceRules#applies}); tool and kill rules follow UHC-style match
 * participants, including the deathmatch arena.
 */
public final class UhcResourceScenarios {
    public static final GameRule<Boolean> CUT_CLEAN = UhcModeRules.bool(false);
    public static final GameRule<Boolean> TIMBER = UhcModeRules.bool(false);
    public static final GameRule<Boolean> VEIN_MINER = UhcModeRules.bool(false);
    public static final GameRule<Boolean> HASTEY_BOYS = UhcModeRules.bool(false);
    public static final GameRule<Boolean> BLOOD_DIAMONDS = UhcModeRules.bool(false);
    public static final GameRule<Boolean> DIAMONDLESS = UhcModeRules.bool(false);
    public static final GameRule<Boolean> GOLDLESS = UhcModeRules.bool(false);

    /** Every rule with its id path and a one-line description, in documentation order. */
    public record Rule(String name, GameRule<Boolean> rule, String description) {}

    public static final List<Rule> RULES = List.of(
            new Rule("uhc_cutclean", CUT_CLEAN, "Ores drop ingots and animals cooked food; gravel always gives flint"),
            new Rule("uhc_timber", TIMBER, "Breaking a natural log fells the whole tree"),
            new Rule("uhc_vein_miner", VEIN_MINER, "Mining an ore mines its whole vein"),
            new Rule("uhc_hastey_boys", HASTEY_BOYS, "Tools get Efficiency III and Unbreaking III"),
            new Rule("uhc_blood_diamonds", BLOOD_DIAMONDS, "Mining a diamond ore costs half a heart"),
            new Rule("uhc_diamondless", DIAMONDLESS, "Diamond ore drops nothing; a dead player drops a diamond"),
            new Rule("uhc_goldless", GOLDLESS, "Gold ore drops nothing; a dead player drops 8 gold and a golden head"));

    /** The cooked form of each raw food a mob drops, as a furnace smelts it. */
    private static final Map<Item, Item> COOKED = Map.of(
            Items.BEEF, Items.COOKED_BEEF,
            Items.PORKCHOP, Items.COOKED_PORKCHOP,
            Items.CHICKEN, Items.COOKED_CHICKEN,
            Items.MUTTON, Items.COOKED_MUTTON,
            Items.RABBIT, Items.COOKED_RABBIT,
            Items.COD, Items.COOKED_COD,
            Items.SALMON, Items.COOKED_SALMON);

    /** The smelted form of each ore drop that a furnace changes. */
    private static final Map<Item, Item> SMELTED = Map.of(
            Items.RAW_IRON, Items.IRON_INGOT,
            Items.RAW_GOLD, Items.GOLD_INGOT,
            Items.RAW_COPPER, Items.COPPER_INGOT,
            Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);

    /** CutClean's least food from a killed cow, mooshroom, pig or chicken. */
    static final int GUARANTEED_MEAT = 3;

    private UhcResourceScenarios() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        for (Rule rule : RULES) registry.accept(BrainageMinigames.id(rule.name()), rule.rule());
    }

    /** CutClean for a drop in {@code level} caused by {@code actor} (the breaker or killer, if any); always on in Speed UHC. */
    public static boolean cutClean(ServerLevel level, @Nullable Entity actor) {
        return on(level, CUT_CLEAN) || SpeedUhc.forces(actor);
    }

    /** Timber for a log {@code actor} breaks in {@code level}; always on in Speed UHC. */
    public static boolean timber(ServerLevel level, @Nullable Entity actor) {
        return on(level, TIMBER) || SpeedUhc.forces(actor);
    }

    static boolean on(ServerLevel level, GameRule<Boolean> rule) {
        return UhcResourceRules.applies(level) && level.getGameRules().get(rule);
    }

    /** Whether the match is a UHC-style game: UHC and its variants, Meetup or FinalUHC. */
    public static boolean family(Match match) {
        return family(match.game());
    }

    public static boolean family(Minigame game) {
        return game instanceof UhcGame || game instanceof MeetupGame || game instanceof FinalUhcGame;
    }

    /**
     * Diamondless/Goldless empty those ores' loot, Silk Touch and explosions included; CutClean smelts
     * the rest of an ore's loot (never a Silk Touch ore block) and turns gravel into flint. Runs
     * before the drop percentages, which then multiply the smelted items.
     */
    public static List<ItemStack> blockDrops(List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        ServerLevel level = params.getLevel();
        if (drops.isEmpty() || !UhcResourceRules.applies(level)) return drops;
        Ore ore = UhcResourceRules.ore(state);
        var rules = level.getGameRules();
        if (ore == Ore.DIAMOND && rules.get(DIAMONDLESS) || ore == Ore.GOLD && rules.get(GOLDLESS)) return new ArrayList<>();
        boolean gravel = state.is(Blocks.GRAVEL);
        if (ore == null && !gravel || !cutClean(level, params.getOptionalParameter(LootContextParams.THIS_ENTITY))) return drops;
        boolean silk = UhcResourceRules.silkTouch(level, params.getOptionalParameter(LootContextParams.TOOL));
        if (silk) return drops;
        List<ItemStack> result = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            Item smelted = gravel ? (drop.is(Items.GRAVEL) ? Items.FLINT : null) : SMELTED.get(drop.getItem());
            result.add(smelted == null ? drop : drop.transmuteCopy(smelted));
        }
        return result;
    }

    /**
     * CutClean's mob loot: raw food comes cooked, a cow, mooshroom, pig or chicken always gives at
     * least three pieces of meat, a chicken a feather and a cow or mooshroom a piece of leather.
     */
    public static List<ItemStack> mobDrops(List<ItemStack> drops, Entity mob, ServerLevel level, @Nullable Entity killer) {
        if (!cutClean(level, killer)) return drops;
        List<ItemStack> result = new ArrayList<>(drops.size() + 3);
        for (ItemStack drop : drops) {
            Item cooked = COOKED.get(drop.getItem());
            result.add(cooked == null ? drop : drop.transmuteCopy(cooked));
        }
        EntityType<?> type = mob.getType();
        boolean cow = type == EntityTypes.COW || type == EntityTypes.MOOSHROOM;
        if (cow) guarantee(result, Items.COOKED_BEEF, GUARANTEED_MEAT);
        if (type == EntityTypes.PIG) guarantee(result, Items.COOKED_PORKCHOP, GUARANTEED_MEAT);
        if (type == EntityTypes.CHICKEN) {
            guarantee(result, Items.COOKED_CHICKEN, GUARANTEED_MEAT);
            guarantee(result, Items.FEATHER, 1);
        }
        if (cow) guarantee(result, Items.LEATHER, 1);
        return result;
    }

    private static void guarantee(List<ItemStack> drops, Item item, int least) {
        int count = 0;
        for (ItemStack drop : drops) if (drop.is(item)) count += drop.getCount();
        if (count < least) drops.add(new ItemStack(item, least - count));
    }

    /**
     * After a player broke {@code state} at {@code pos}: Blood Diamonds' cost, then Timber or Vein
     * Miner. {@code harvested} is whether the tool could harvest the block before the break.
     */
    public static void broken(ServerPlayer player, BlockPos pos, BlockState state, ItemStack tool, boolean held,
            boolean harvested) {
        ServerLevel level = player.level();
        if (harvested && UhcResourceRules.ore(state) == Ore.DIAMOND && on(level, BLOOD_DIAMONDS)) bleed(player);
        UhcChainBreaks.broken(player, pos, state, tool, held, harvested);
    }

    /**
     * Half a heart of true damage: armour, enchantments, effects, perks and damage immunity frames
     * do not reduce it. Absorption is spent first, like any damage; it can kill.
     */
    static void bleed(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;
        ServerLevel level = player.level();
        float cost = 1.0F;
        float absorbed = Math.min(player.getAbsorptionAmount(), cost);
        player.setAbsorptionAmount(player.getAbsorptionAmount() - absorbed);
        cost -= absorbed;
        var source = level.damageSources().genericKill();
        if (cost > 0 && player.getHealth() <= cost) {
            player.hurtServer(level, source, Float.MAX_VALUE);
            return;
        }
        if (cost > 0) player.setHealth(player.getHealth() - cost);
        level.broadcastDamageEvent(player, source);
    }

    /** Hastey Boys: every tool UHC-style participants hold gets Efficiency III and Unbreaking III. */
    public static void tick(MinecraftServer server) {
        if (!server.getGameRules().get(HASTEY_BOYS)) return;
        for (Match match : MatchManager.matches()) {
            if (match.phase() != MatchPhase.ACTIVE || !family(match)) continue;
            for (ServerPlayer player : match.alivePlayers()) hasten(player);
        }
    }

    private static void hasten(ServerPlayer player) {
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> efficiency = enchantments.getOrThrow(Enchantments.EFFICIENCY);
        Holder<Enchantment> unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) hasten(inventory.getItem(slot), efficiency, unbreaking);
        hasten(player.containerMenu.getCarried(), efficiency, unbreaking);
    }

    /**
     * Raises Efficiency and Unbreaking to at least III on an enchantable mining tool. Tools with a
     * fixed number of uses (the Philosopher's Pickaxe, the Lumberjack's Axe) keep theirs.
     */
    static void hasten(ItemStack stack, Holder<Enchantment> efficiency, Holder<Enchantment> unbreaking) {
        if (stack.isEmpty() || !stack.is(ItemTags.MINING_ENCHANTABLE) || !stack.has(DataComponents.ENCHANTABLE)
                || !Objects.equals(stack.get(DataComponents.MAX_DAMAGE), stack.getPrototype().get(DataComponents.MAX_DAMAGE))) return;
        var current = stack.getEnchantments();
        if (current.getLevel(efficiency) >= 3 && current.getLevel(unbreaking) >= 3) return;
        EnchantmentHelper.updateEnchantments(stack, enchantments -> {
            enchantments.upgrade(efficiency, 3);
            enchantments.upgrade(unbreaking, 3);
        });
    }

    /**
     * Diamondless and Goldless kill rewards, as an eliminated participant's own items: they drop,
     * or go into the anti-janitor or Time Bomb chest, with the rest of the inventory. Games that
     * keep the inventory drop them on the ground instead.
     */
    public static void eliminated(Match match, ServerPlayer victim) {
        if (!family(match)) return;
        var rules = victim.level().getGameRules();
        List<ItemStack> rewards = new ArrayList<>(3);
        if (rules.get(DIAMONDLESS)) rewards.add(new ItemStack(Items.DIAMOND));
        if (rules.get(GOLDLESS)) {
            rewards.add(new ItemStack(Items.GOLD_INGOT, 8));
            rewards.add(UhcCrafting.goldenHead(victim));
        }
        boolean drops = match.game().dropsInventoryOnElimination();
        for (ItemStack reward : rewards) {
            if (drops) victim.getInventory().add(reward);
            if (!reward.isEmpty()) victim.drop(reward, true, false);
        }
    }
}
