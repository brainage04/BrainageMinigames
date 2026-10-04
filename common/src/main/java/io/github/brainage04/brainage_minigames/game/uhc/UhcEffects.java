package io.github.brainage04.brainage_minigames.game.uhc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public final class UhcEffects {
    public static final ThreadLocal<ServerPlayer> MINER = new ThreadLocal<>();

    private UhcEffects() {}

    public static float damage(ServerPlayer player, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) return amount;
        int level = UhcProgression.level(player, UhcProgression.Tree.SURVIVALISM);
        return level == 0 ? amount : amount * (1 - level * 0.04f);
    }

    public static void mined(ServerPlayer player, BlockState state) {
        UhcResourceRules.Ore ore = UhcResourceRules.ore(state);
        if (ore == null) return;
        UhcProgression.mined(player, ore);
        int level = UhcProgression.level(player, UhcProgression.Tree.ENGINEERING);
        if (level > 0) player.addEffect(new MobEffectInstance(MobEffects.HASTE, level * 5 * 20, 0));
    }

    public static int experience(ServerPlayer player, int vanilla) {
        int level = UhcProgression.level(player, UhcProgression.Tree.ENCHANTING);
        int numerator = vanilla * level * 5;
        return vanilla + numerator / 100 + (numerator % 100 != 0 && player.getRandom().nextInt(100) < numerator % 100 ? 1 : 0);
    }

    public static MobEffectInstance potion(ServerPlayer player, MobEffectInstance effect) {
        if (effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL || effect.isInfiniteDuration()) return effect;
        int level = UhcProgression.level(player, UhcProgression.Tree.ALCHEMY);
        return level == 0 ? effect : effect.withScaledDuration(1 + level * 0.02f);
    }

    public static boolean convenienceBlock(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.OBSIDIAN);
    }

    public static List<ItemStack> drops(List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        if (!(params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) || UhcProgression.match(player) == null) return drops;
        var tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (state.is(BlockTags.LEAVES) && tool != null && UhcCrafting.kind(tool).equals("lucky_shears") && player.getRandom().nextInt(1000) < 25) {
            // Five times the vanilla 0.5% chance; the local policy permits all leaf species.
            List<ItemStack> result = new ArrayList<>(drops);
            result.addAll(UhcResourceRules.multiplyDrops(new ArrayList<>(List.of(new ItemStack(Items.APPLE))), state, params));
            return result;
        }
        int level = convenienceBlock(state) ? UhcProgression.level(player, UhcProgression.Tree.INVENTION) : 0;
        if (level == 0 || player.getRandom().nextInt(100) >= level * 5) return drops;
        List<ItemStack> result = new ArrayList<>(drops);
        for (ItemStack drop : drops) if (drop.is(Items.SAND) || drop.is(Items.RED_SAND) || drop.is(Items.FLINT) || drop.is(Items.OBSIDIAN)) result.add(drop.copy());
        return result;
    }
}
