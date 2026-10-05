package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** World-persisted percentages: vanilla 26.2 has no portable floating-point gamerule type. */
public final class UhcResourceRules {
    public static final GameRule<Integer> APPLE_DROP_PERCENT = percent(GameRuleCategory.DROPS);

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
        for (Ore ore : Ore.values()) {
            registry.accept(BrainageMinigames.id("uhc_" + ore.name + "_generation_percent"), ore.generationPercent);
            registry.accept(BrainageMinigames.id("uhc_" + ore.name + "_drop_percent"), ore.dropPercent);
        }
    }

    private static GameRule<Integer> percent(GameRuleCategory category) {
        return new GameRule<>(
                category,
                GameRuleType.INT,
                IntegerArgumentType.integer(0),
                GameRuleTypeVisitor::visitInteger,
                Codec.intRange(0, Integer.MAX_VALUE),
                Integer::intValue,
                200,
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

    /** One fractional roll per feature per chunk, before count/rarity and position modifiers. */
    public static int attempts(int percent, RandomSource random) {
        int remainder = percent % 100;
        return percent / 100 + (remainder != 0 && random.nextInt(100) < remainder ? 1 : 0);
    }

    /** Records actual placements independently of match membership, in both UHC dimensions. */
    public static void blockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (applies(level) && (ore(state) != null || state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES))) {
            UhcPlacedResources.get(level).add(pos);
        }
    }

    /** Scales natural resource loot, never replanted resources or Silk Touch block items. */
    public static List<ItemStack> multiplyDrops(
            List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        ServerLevel level = params.getLevel();
        if (drops.isEmpty() || !applies(level)) return drops;
        Ore ore = ore(state);
        boolean apples = state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES);
        if (ore == null && !apples) return drops;
        int percent = level.getGameRules().get(ore == null ? APPLE_DROP_PERCENT : ore.dropPercent);
        if (percent == 100) return drops;
        BlockPos pos = BlockPos.containing(params.getParameter(LootContextParams.ORIGIN));
        if (UhcPlacedResources.get(level).contains(pos)) return drops;
        var tool = params.getOptionalParameter(LootContextParams.TOOL);
        // Debris normally drops itself; only its natural, non-Silk-Touch loot is scalable.
        boolean normalDebris = ore == Ore.ANCIENT_DEBRIS
                && (tool == null || EnchantmentHelper.getItemEnchantmentLevel(
                        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) == 0);
        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemStack stack = drops.get(i);
            if (apples && !stack.is(Items.APPLE)) continue;
            if (stack.is(state.getBlock().asItem()) && !normalDebris) continue;
            int original = stack.getCount();
            long count = (long) original * (percent / 100);
            int remainder = percent % 100;
            if (remainder != 0) {
                for (int item = 0; item < original; item++) {
                    if (level.getRandom().nextInt(100) < remainder) count++;
                }
            }
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
}
