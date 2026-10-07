package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies {@link UhcSpawnRules} to natural spawning, both while chunks generate and while they tick:
 * a spawn of a mob makes {@code percent / 100} of it on average, and each one counts {@code 100 /
 * percent} towards its category's mob caps, so the caps hold that many times as many.
 */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
    private static final String SPAWN_AT_POSITION =
            "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V";

    /** Counts each mob towards the caps as often as its weight says, which may be never. */
    @WrapOperation(
            method = "createState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;query(JLjava/util/function/Consumer;)V"))
    private static void brainage_minigames$weightedCount(NaturalSpawner.ChunkGetter chunks, long chunk,
            Consumer<LevelChunk> count, Operation<Void> original, @Local Entity entity) {
        for (int times = UhcSpawnRules.capWeight(entity); times > 0; times--) {
            original.call(chunks, chunk, count);
        }
    }

    /** Below 100%, only that share of spawn attempts go ahead; none at 0. */
    @WrapOperation(
            method = SPAWN_AT_POSITION,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;test(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
    private static boolean brainage_minigames$admitSpawn(NaturalSpawner.SpawnPredicate test, EntityType<?> type,
            BlockPos pos, ChunkAccess chunk, Operation<Boolean> original, @Local(argsOnly = true) ServerLevel level) {
        return UhcSpawnRules.admits(level, type) && original.call(test, type, pos, chunk);
    }

    /** Above 100%, each spawned mob brings that many more of it on average. */
    @WrapOperation(
            method = SPAWN_AT_POSITION,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void brainage_minigames$extraSpawns(ServerLevel level, Entity entity, Operation<Void> original,
            @Local LocalRef<SpawnGroupData> group) {
        original.call(level, entity);
        if (entity instanceof Mob mob && UhcSpawnRules.percent(level, mob.getType()) > 100) {
            UhcSpawnRules.spawnCopies(level, mob, UhcSpawnRules.count(level, mob.getType()) - 1,
                    EntitySpawnReason.NATURAL, group, copy -> original.call(level, copy));
        }
    }

    /** Animals placed with a new chunk: none, one or several per spawn, by the same percentage. */
    @WrapOperation(
            method = "spawnMobsForChunkGeneration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void brainage_minigames$generatedSpawns(ServerLevelAccessor level, Entity entity,
            Operation<Void> original, @Local LocalRef<SpawnGroupData> group) {
        int count = UhcSpawnRules.count(level.getLevel(), entity.getType());
        if (count == 0) return;
        original.call(level, entity);
        if (count > 1 && entity instanceof Mob mob) {
            UhcSpawnRules.spawnCopies(level, mob, count - 1, EntitySpawnReason.CHUNK_GENERATION, group,
                    copy -> original.call(level, copy));
        }
    }
}
