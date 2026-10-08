package io.github.brainage04.brainage_minigames.game.uhc;

import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jspecify.annotations.Nullable;

/**
 * Natural mob spawning percentages in the UHC-style dimensions, as {@link UhcResourceRules} scales
 * resources: a rule per group of mobs and per mob, multiplied together. A mob's percentage scales
 * both how many of it each spawn produces and how much of its category's mob cap each one takes, so
 * at 200% twice as many spawn and the cap holds twice as many; see {@code NaturalSpawnerMixin}.
 * Each mob also has a drop rule for the loot of naturally spawned ones; see {@link UhcMobDrops}.
 */
public final class UhcSpawnRules {
    /** Behaviour groups, each with its own rule. */
    public enum Group {
        PASSIVE,
        NEUTRAL,
        HOSTILE;

        public final GameRule<Integer> percent = UhcResourceRules.percent(GameRuleCategory.SPAWNING, 100);

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Every mob the overworld and nether biomes and structures spawn naturally. */
    public enum SpawningMob {
        ARMADILLO(EntityTypes.ARMADILLO, Group.PASSIVE),
        AXOLOTL(EntityTypes.AXOLOTL, Group.PASSIVE),
        BAT(EntityTypes.BAT, Group.PASSIVE),
        CAMEL(EntityTypes.CAMEL, Group.PASSIVE),
        CAT(EntityTypes.CAT, Group.PASSIVE),
        CHICKEN(EntityTypes.CHICKEN, Group.PASSIVE, 200),
        COD(EntityTypes.COD, Group.PASSIVE),
        COW(EntityTypes.COW, Group.PASSIVE, 200),
        DONKEY(EntityTypes.DONKEY, Group.PASSIVE),
        FOX(EntityTypes.FOX, Group.PASSIVE),
        FROG(EntityTypes.FROG, Group.PASSIVE),
        GLOW_SQUID(EntityTypes.GLOW_SQUID, Group.PASSIVE),
        HORSE(EntityTypes.HORSE, Group.PASSIVE, 200),
        MOOSHROOM(EntityTypes.MOOSHROOM, Group.PASSIVE),
        OCELOT(EntityTypes.OCELOT, Group.PASSIVE),
        PARROT(EntityTypes.PARROT, Group.PASSIVE),
        PIG(EntityTypes.PIG, Group.PASSIVE),
        RABBIT(EntityTypes.RABBIT, Group.PASSIVE),
        SALMON(EntityTypes.SALMON, Group.PASSIVE),
        SHEEP(EntityTypes.SHEEP, Group.PASSIVE),
        SQUID(EntityTypes.SQUID, Group.PASSIVE),
        STRIDER(EntityTypes.STRIDER, Group.PASSIVE),
        TROPICAL_FISH(EntityTypes.TROPICAL_FISH, Group.PASSIVE),
        TURTLE(EntityTypes.TURTLE, Group.PASSIVE),
        DOLPHIN(EntityTypes.DOLPHIN, Group.NEUTRAL),
        ENDERMAN(EntityTypes.ENDERMAN, Group.NEUTRAL),
        GOAT(EntityTypes.GOAT, Group.NEUTRAL),
        LLAMA(EntityTypes.LLAMA, Group.NEUTRAL),
        NAUTILUS(EntityTypes.NAUTILUS, Group.NEUTRAL),
        PANDA(EntityTypes.PANDA, Group.NEUTRAL),
        PIGLIN(EntityTypes.PIGLIN, Group.NEUTRAL),
        POLAR_BEAR(EntityTypes.POLAR_BEAR, Group.NEUTRAL),
        PUFFERFISH(EntityTypes.PUFFERFISH, Group.NEUTRAL),
        WOLF(EntityTypes.WOLF, Group.NEUTRAL),
        ZOMBIFIED_PIGLIN(EntityTypes.ZOMBIFIED_PIGLIN, Group.NEUTRAL),
        BLAZE(EntityTypes.BLAZE, Group.HOSTILE),
        BOGGED(EntityTypes.BOGGED, Group.HOSTILE),
        CAVE_SPIDER(EntityTypes.CAVE_SPIDER, Group.HOSTILE),
        CREEPER(EntityTypes.CREEPER, Group.HOSTILE),
        DROWNED(EntityTypes.DROWNED, Group.HOSTILE),
        GHAST(EntityTypes.GHAST, Group.HOSTILE),
        GUARDIAN(EntityTypes.GUARDIAN, Group.HOSTILE),
        HOGLIN(EntityTypes.HOGLIN, Group.HOSTILE),
        HUSK(EntityTypes.HUSK, Group.HOSTILE),
        MAGMA_CUBE(EntityTypes.MAGMA_CUBE, Group.HOSTILE),
        PARCHED(EntityTypes.PARCHED, Group.HOSTILE),
        PILLAGER(EntityTypes.PILLAGER, Group.HOSTILE),
        SKELETON(EntityTypes.SKELETON, Group.HOSTILE),
        SLIME(EntityTypes.SLIME, Group.HOSTILE),
        SPIDER(EntityTypes.SPIDER, Group.HOSTILE),
        STRAY(EntityTypes.STRAY, Group.HOSTILE),
        SULFUR_CUBE(EntityTypes.SULFUR_CUBE, Group.HOSTILE),
        WITCH(EntityTypes.WITCH, Group.HOSTILE),
        WITHER_SKELETON(EntityTypes.WITHER_SKELETON, Group.HOSTILE),
        ZOMBIE(EntityTypes.ZOMBIE, Group.HOSTILE),
        ZOMBIE_HORSE(EntityTypes.ZOMBIE_HORSE, Group.HOSTILE),
        ZOMBIE_VILLAGER(EntityTypes.ZOMBIE_VILLAGER, Group.HOSTILE);

        public final EntityType<?> type;
        public final Group group;
        public final GameRule<Integer> percent;
        /** How much of its loot a naturally spawned one drops; see {@link UhcMobDrops}. */
        public final GameRule<Integer> dropPercent = UhcResourceRules.percent(GameRuleCategory.DROPS, 100);

        SpawningMob(EntityType<?> type, Group group) {
            this(type, group, 100);
        }

        /** {@code spawnPercent}: the default of its own spawn rule. */
        SpawningMob(EntityType<?> type, Group group, int spawnPercent) {
            this.type = type;
            this.group = group;
            this.percent = UhcResourceRules.percent(GameRuleCategory.SPAWNING, spawnPercent);
        }

        /** The entity id's path, which names the rule. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<EntityType<?>, SpawningMob> BY_TYPE = new IdentityHashMap<>();

    static {
        for (SpawningMob mob : SpawningMob.values()) BY_TYPE.put(mob.type, mob);
    }

    private UhcSpawnRules() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        for (Group group : Group.values()) {
            registry.accept(BrainageMinigames.id("uhc_" + group.id() + "_spawn_percent"), group.percent);
        }
        for (SpawningMob mob : SpawningMob.values()) {
            registry.accept(BrainageMinigames.id("uhc_" + mob.id() + "_spawn_percent"), mob.percent);
            registry.accept(BrainageMinigames.id("uhc_" + mob.id() + "_drop_percent"), mob.dropPercent);
        }
    }

    public static @Nullable SpawningMob mob(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    /**
     * The percentage of natural spawns of {@code type} in {@code level}: its group's rule times its
     * own, or 100 for other mobs and outside the UHC-style dimensions.
     */
    public static int percent(ServerLevel level, EntityType<?> type) {
        SpawningMob mob;
        if (!UhcResourceRules.applies(level) || (mob = BY_TYPE.get(type)) == null) return 100;
        var rules = level.getGameRules();
        return (int) Math.min(Integer.MAX_VALUE, (long) rules.get(mob.group.percent) * rules.get(mob.percent) / 100);
    }

    /**
     * How many times {@code entity} counts towards its category's mob caps: 100 divided by its
     * percentage, rounded up or down per entity (by its UUID, so the same mob always counts the
     * same) to average out exactly. At 200% half the mobs count once and half not at all. A mob that
     * should not spawn at all, or any other mob, counts once.
     */
    public static int capWeight(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return 1;
        int percent = percent(level, entity.getType());
        if (percent == 100 || percent == 0) return 1;
        int tenThousandths = 1_000_000 / percent;
        int roll = Math.floorMod(entity.getUUID().hashCode(), 10_000);
        return tenThousandths / 10_000 + (roll < tenThousandths % 10_000 ? 1 : 0);
    }

    /** Whether a natural spawn of {@code type} goes ahead, before the mob is made: below 100%, at that chance. */
    public static boolean admits(ServerLevel level, EntityType<?> type) {
        int percent = percent(level, type);
        return percent >= 100 || percent > 0 && level.getRandom().nextInt(100) < percent;
    }

    /**
     * How many mobs one spawn of {@code type} makes: {@code floor(percent / 100)} plus one more at
     * the fractional chance, as {@link UhcResourceRules#attempts}.
     */
    public static int count(ServerLevel level, EntityType<?> type) {
        return UhcResourceRules.attempts(percent(level, type), level.getRandom());
    }

    /**
     * Adds {@code copies} more of {@code mob} where it spawned, each prepared for its spawn reason as
     * one more member of the same group, through {@code add}.
     */
    public static void spawnCopies(ServerLevelAccessor level, Mob mob, int copies, EntitySpawnReason reason,
            LocalRef<@Nullable SpawnGroupData> group, Consumer<Mob> add) {
        for (int copy = 0; copy < copies; copy++) {
            if (!(mob.getType().create(level.getLevel(), EntitySpawnReason.NATURAL) instanceof Mob extra)) return;
            extra.snapTo(mob.getX(), mob.getY(), mob.getZ(), level.getRandom().nextFloat() * 360.0F, 0.0F);
            group.set(extra.finalizeSpawn(level, level.getCurrentDifficultyAt(extra.blockPosition()), reason, group.get()));
            add.accept(extra);
        }
    }
}
