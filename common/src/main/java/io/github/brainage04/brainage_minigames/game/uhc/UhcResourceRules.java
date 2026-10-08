package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/** World-persisted percentages: vanilla 26.2 has no portable floating-point gamerule type. */
public final class UhcResourceRules {
    public static final GameRule<Integer> APPLE_DROP_PERCENT = percent(GameRuleCategory.DROPS);
    public static final GameRule<Integer> SUGAR_CANE_GENERATION_PERCENT = percent(GameRuleCategory.UPDATES);
    public static final GameRule<Integer> SUGAR_CANE_DROP_PERCENT = percent(GameRuleCategory.DROPS);

    /** The configured feature every vanilla sugar cane patch places. */
    private static final ResourceKey<ConfiguredFeature<?, ?>> SUGAR_CANE_FEATURE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.withDefaultNamespace("sugar_cane"));

    public enum Ore {
        COAL("coal"),
        COPPER("copper"),
        IRON("iron"),
        GOLD("gold"),
        REDSTONE("redstone"),
        LAPIS("lapis"),
        DIAMOND("diamond"),
        EMERALD("emerald"),
        NETHER_QUARTZ("nether_quartz"),
        ANCIENT_DEBRIS("ancient_debris");

        private final String name;
        public final GameRule<Integer> generationPercent = percent(GameRuleCategory.UPDATES);
        public final GameRule<Integer> dropPercent = percent(GameRuleCategory.DROPS);

        Ore(String name) {
            this.name = name;
        }
    }

    private UhcResourceRules() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("uhc_apple_drop_percent"), APPLE_DROP_PERCENT);
        registry.accept(BrainageMinigames.id("uhc_sugar_cane_generation_percent"), SUGAR_CANE_GENERATION_PERCENT);
        registry.accept(BrainageMinigames.id("uhc_sugar_cane_drop_percent"), SUGAR_CANE_DROP_PERCENT);
        for (Ore ore : Ore.values()) {
            registry.accept(BrainageMinigames.id("uhc_" + ore.name + "_generation_percent"), ore.generationPercent);
            registry.accept(BrainageMinigames.id("uhc_" + ore.name + "_drop_percent"), ore.dropPercent);
        }
    }

    private static GameRule<Integer> percent(GameRuleCategory category) {
        return percent(category, 200);
    }

    /** A nonnegative integer percentage gamerule. */
    static GameRule<Integer> percent(GameRuleCategory category, int defaultValue) {
        return new GameRule<>(
                category,
                GameRuleType.INT,
                IntegerArgumentType.integer(0),
                GameRuleTypeVisitor::visitInteger,
                Codec.intRange(0, Integer.MAX_VALUE),
                Integer::intValue,
                defaultValue,
                FeatureFlagSet.of());
    }

    public static boolean applies(ServerLevel level) {
        return ModDimensions.natural(level.dimension()) || ModDimensions.nether(level.dimension());
    }

    public static Ore ore(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Ore.COAL;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Ore.COPPER;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Ore.IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return Ore.GOLD;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Ore.REDSTONE;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Ore.LAPIS;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Ore.DIAMOND;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Ore.EMERALD;
        if (block == Blocks.NETHER_QUARTZ_ORE) return Ore.NETHER_QUARTZ;
        if (block == Blocks.ANCIENT_DEBRIS) return Ore.ANCIENT_DEBRIS;
        return null;
    }

    /**
     * The generation rule of a placed feature's configured feature: its ore's, or sugar cane's;
     * null for every other feature.
     */
    public static @Nullable GameRule<Integer> generationRule(Holder<ConfiguredFeature<?, ?>> feature) {
        if (feature.is(SUGAR_CANE_FEATURE)) return SUGAR_CANE_GENERATION_PERCENT;
        if (feature.value().config() instanceof OreConfiguration config && !config.targetStates.isEmpty()) {
            Ore ore = ore(config.targetStates.getFirst().state);
            return ore == null ? null : ore.generationPercent;
        }
        return null;
    }

    /** One fractional roll per feature per chunk, before count/rarity and position modifiers. */
    public static int attempts(int percent, RandomSource random) {
        int remainder = percent % 100;
        return percent / 100 + (remainder != 0 && random.nextInt(100) < remainder ? 1 : 0);
    }

    /**
     * Records actual placements independently of match membership, in every UHC-style dimension:
     * these resources keep vanilla drops, and placed logs never fell with a tree.
     */
    public static void blockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (applies(level) && (ore(state) != null || state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.LOGS))) {
            UhcPlacedResources.get(level).add(pos);
        }
    }

    /** Whether a player placed the resource at {@code pos}, as {@link #blockPlaced} records it. */
    public static boolean placed(ServerLevel level, BlockPos pos) {
        return UhcPlacedResources.get(level).contains(pos);
    }

    static boolean silkTouch(ServerLevel level, @Nullable ItemInstance tool) {
        return tool != null && EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
    }

    /** Sugar cane that grows during play, from natural or replanted stalks, keeps vanilla drops. */
    public static void sugarCaneGrown(ServerLevel level, BlockPos pos) {
        if (applies(level)) UhcPlacedResources.get(level).add(pos);
    }

    /** Scales natural resource loot, never replanted or regrown resources or Silk Touch block items. */
    public static List<ItemStack> multiplyDrops(
            List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        ServerLevel level = params.getLevel();
        if (drops.isEmpty() || !applies(level)) return drops;
        Ore ore = ore(state);
        boolean apples = state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES);
        boolean cane = state.is(Blocks.SUGAR_CANE);
        if (ore == null && !apples && !cane) return drops;
        int percent = level.getGameRules().get(
                ore != null ? ore.dropPercent : apples ? APPLE_DROP_PERCENT : SUGAR_CANE_DROP_PERCENT);
        if (percent == 100) return drops;
        BlockPos pos = BlockPos.containing(params.getParameter(LootContextParams.ORIGIN));
        if (placed(level, pos)) return drops;
        // Debris normally drops itself; only its natural, non-Silk-Touch loot is scalable.
        boolean normalDebris = ore == Ore.ANCIENT_DEBRIS
                && !silkTouch(level, params.getOptionalParameter(LootContextParams.TOOL));
        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemStack stack = drops.get(i);
            if (apples && !stack.is(Items.APPLE)) continue;
            // Sugar cane's only loot is the cane itself.
            if (stack.is(state.getBlock().asItem()) && !normalDebris && !cane) continue;
            long count = scaled(stack.getCount(), percent, level.getRandom());
            if (count == 0) {
                drops.remove(i);
                continue;
            }
            int limit = stack.getMaxStackSize();
            stack.setCount((int) Math.min(count, limit));
            count -= stack.getCount();
            while (count > 0) {
                int size = (int) Math.min(count, limit);
                drops.add(stack.copyWithCount(size));
                count -= size;
            }
        }
        return drops;
    }

    /** {@code count} items at {@code percent}: the whole multiple, plus one more per item at the fractional chance. */
    public static long scaled(int count, int percent, RandomSource random) {
        long scaled = (long) count * (percent / 100);
        int remainder = percent % 100;
        if (remainder != 0) {
            for (int item = 0; item < count; item++) {
                if (random.nextInt(100) < remainder) scaled++;
            }
        }
        return scaled;
    }
}
