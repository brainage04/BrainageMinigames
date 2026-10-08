package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules.SpawningMob;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * Death loot rules in the UHC-style dimensions: {@code uhc_all_meat_is_beef} turns every meat a mob
 * drops into beef, and each naturally spawning mob's {@code uhc_<mob>_drop_percent} scales the loot
 * of the ones that spawned naturally, as the ore rules scale natural ores. Only a mob's loot table
 * counts: never its experience or the equipment it picked up or spawned with.
 */
public final class UhcMobDrops {
    public static final GameRule<Boolean> ALL_MEAT_IS_BEEF = new GameRule<>(
            GameRuleCategory.DROPS, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
            false, FeatureFlagSet.of());

    /**
     * Marks a mob that spawned naturally, with a new chunk or while chunks tick; it persists with the
     * mob, and a mob that converts (a zombie drowning) keeps it. Spawn eggs, spawners, breeding,
     * commands, raids and patrols never mark one.
     */
    public static final String NATURAL_TAG = BrainageMinigames.MOD_ID + ".natural_spawn";

    private UhcMobDrops() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("uhc_all_meat_is_beef"), ALL_MEAT_IS_BEEF);
    }

    /** Marks {@code entity} and its passengers as naturally spawned, in the UHC-style dimensions. */
    public static void spawnedNaturally(ServerLevel level, Entity entity) {
        if (!UhcResourceRules.applies(level)) return;
        entity.getSelfAndPassengers().forEach(spawned -> spawned.addTag(NATURAL_TAG));
    }

    /** Where {@code mob}'s death loot goes: through these rules first, in the UHC-style dimensions. */
    public static Consumer<ItemStack> deathLoot(LivingEntity mob, Consumer<ItemStack> drop) {
        if (!(mob.level() instanceof ServerLevel level) || !UhcResourceRules.applies(level)) return drop;
        boolean beef = level.getGameRules().get(ALL_MEAT_IS_BEEF);
        SpawningMob spawning = UhcSpawnRules.mob(mob.getType());
        int percent = spawning != null && mob.entityTags().contains(NATURAL_TAG)
                ? level.getGameRules().get(spawning.dropPercent)
                : 100;
        if (!beef && percent == 100) return drop;
        return stack -> {
            ItemStack loot = beef ? beef(stack) : stack;
            if (percent == 100) {
                drop.accept(loot);
                return;
            }
            long count = UhcResourceRules.scaled(loot.getCount(), percent, level.getRandom());
            int limit = loot.getMaxStackSize();
            while (count > 0) {
                int size = (int) Math.min(count, limit);
                drop.accept(loot.copyWithCount(size));
                count -= size;
            }
        };
    }

    /**
     * Raw beef for any raw meat, cooked beef for any cooked meat (as from a mob that died burning);
     * fish are not meat and stay as they are.
     */
    private static ItemStack beef(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.PORKCHOP || item == Items.MUTTON || item == Items.CHICKEN || item == Items.RABBIT) {
            return new ItemStack(Items.BEEF, stack.getCount());
        }
        if (item == Items.COOKED_PORKCHOP || item == Items.COOKED_MUTTON || item == Items.COOKED_CHICKEN
                || item == Items.COOKED_RABBIT) {
            return new ItemStack(Items.COOKED_BEEF, stack.getCount());
        }
        return stack;
    }
}
