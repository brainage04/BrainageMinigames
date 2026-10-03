package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Repeats the whole ore placement pipeline, including rare veins without a count modifier. */
@Mixin(PlacedFeature.class)
abstract class PlacedFeatureMixin {
    @Shadow @Final private Holder<ConfiguredFeature<?, ?>> feature;

    @WrapOperation(
            method = "placeWithContext",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;of(Ljava/lang/Object;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> brainage_minigames$oreAttempts(
            Object origin,
            Operation<Stream<BlockPos>> original,
            PlacementContext context,
            RandomSource random,
            BlockPos pos) {
        var level = context.getLevel().getLevel();
        if (!UhcResourceRules.applies(level)
                || !(feature.value().config() instanceof OreConfiguration config)
                || config.targetStates.isEmpty()) {
            return original.call(origin);
        }
        var ore = UhcResourceRules.ore(config.targetStates.getFirst().state);
        if (ore == null) return original.call(origin);
        int count = UhcResourceRules.attempts(level.getGameRules().get(ore.generationPercent), random);
        if (count == 1) return original.call(origin);
        return count == 0 ? Stream.empty() : IntStream.range(0, count).mapToObj(i -> pos);
    }
}
