package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcMobDrops;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules.SpawningMob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;

/** Mob loot rules on both loaders: mobs killed in disposable UHC and overworld levels. */
public final class UhcMobDropGameTestFunctions {
    /** Where the mobs die; their loot lands within {@link #LOOT}. */
    private static final BlockPos AT = new BlockPos(8, 100, 8);
    private static final AABB LOOT = new AABB(AT).inflate(6);
    private static final List<Item> OTHER_MEAT = List.of(Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT,
            Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.COOKED_RABBIT);

    private UhcMobDropGameTestFunctions() {}

    /**
     * A mob's drop rule scales the loot of naturally spawned ones in the UHC dimension: three times
     * as much at 300%, none at 0%. Mobs that did not spawn naturally, the equipment a mob carries and
     * mobs elsewhere keep their usual loot.
     */
    public static void drops(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int cow = server.getGameRules().get(SpawningMob.COW.dropPercent);
        int zombie = server.getGameRules().get(SpawningMob.ZOMBIE.dropPercent);
        try {
            inLevel(context, ModDimensions.UHC, level -> {
                server.getGameRules().set(SpawningMob.COW.dropPercent, 300, server);
                Map<Item, Integer> tripled = kill(level, EntityTypes.COW, 8, true, false);
                check(tripled.getOrDefault(Items.BEEF, 0) >= 24 && tripled.values().stream().allMatch(count -> count % 3 == 0),
                        "8 natural cows at 300% dropped " + tripled);
                server.getGameRules().set(SpawningMob.COW.dropPercent, 0, server);
                Map<Item, Integer> none = kill(level, EntityTypes.COW, 8, true, false);
                check(none.isEmpty(), "8 natural cows at 0% dropped " + none);
                Map<Item, Integer> bred = kill(level, EntityTypes.COW, 8, false, false);
                check(bred.getOrDefault(Items.BEEF, 0) >= 8, "8 cows that did not spawn naturally, at 0%, dropped " + bred);
                server.getGameRules().set(SpawningMob.ZOMBIE.dropPercent, 0, server);
                Map<Item, Integer> armed = kill(level, EntityTypes.ZOMBIE, 4, true, false, mob -> {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                });
                check(armed.getOrDefault(Items.IRON_SWORD, 0) == 4 && armed.size() == 1,
                        "4 natural zombies with swords at 0% dropped " + armed);
                return null;
            });
            inLevel(context, Level.OVERWORLD, level -> {
                Map<Item, Integer> elsewhere = kill(level, EntityTypes.COW, 8, true, false);
                check(elsewhere.getOrDefault(Items.BEEF, 0) >= 8, "8 overworld cows at 0% dropped " + elsewhere);
                return null;
            });
        } finally {
            server.getGameRules().set(SpawningMob.COW.dropPercent, cow, server);
            server.getGameRules().set(SpawningMob.ZOMBIE.dropPercent, zombie, server);
        }
        context.succeed();
    }

    /**
     * With {@code uhc_all_meat_is_beef}, every meat mob in the UHC dimension drops beef, cooked beef
     * when it burns; fish stay fish. With the rule off, or elsewhere, meat stays as it is.
     */
    public static void allMeatIsBeef(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        boolean previous = server.getGameRules().get(UhcMobDrops.ALL_MEAT_IS_BEEF);
        List<EntityType<? extends Mob>> meaty = List.of(EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.CHICKEN,
                EntityTypes.RABBIT, EntityTypes.COW, EntityTypes.MOOSHROOM, EntityTypes.HOGLIN);
        try {
            server.getGameRules().set(UhcMobDrops.ALL_MEAT_IS_BEEF, true, server);
            inLevel(context, ModDimensions.UHC, level -> {
                for (EntityType<? extends Mob> type : meaty) {
                    Map<Item, Integer> raw = kill(level, type, 16, false, false);
                    check(raw.getOrDefault(Items.BEEF, 0) > 0 && OTHER_MEAT.stream().noneMatch(raw::containsKey)
                                    && !raw.containsKey(Items.COOKED_BEEF),
                            "16 " + type + " dropped " + raw);
                }
                for (EntityType<? extends Mob> type : List.of(EntityTypes.PIG, EntityTypes.CHICKEN)) {
                    Map<Item, Integer> cooked = kill(level, type, 8, false, true);
                    check(cooked.getOrDefault(Items.COOKED_BEEF, 0) > 0 && OTHER_MEAT.stream().noneMatch(cooked::containsKey)
                                    && !cooked.containsKey(Items.BEEF),
                            "8 burning " + type + " dropped " + cooked);
                }
                Map<Item, Integer> fish = kill(level, EntityTypes.COD, 4, false, false);
                check(fish.getOrDefault(Items.COD, 0) >= 4 && !fish.containsKey(Items.BEEF), "4 cod dropped " + fish);
                server.getGameRules().set(UhcMobDrops.ALL_MEAT_IS_BEEF, false, server);
                Map<Item, Integer> off = kill(level, EntityTypes.PIG, 8, false, false);
                check(off.getOrDefault(Items.PORKCHOP, 0) > 0 && !off.containsKey(Items.BEEF), "8 pigs with the rule off dropped " + off);
                return null;
            });
            server.getGameRules().set(UhcMobDrops.ALL_MEAT_IS_BEEF, true, server);
            inLevel(context, Level.OVERWORLD, level -> {
                Map<Item, Integer> elsewhere = kill(level, EntityTypes.PIG, 8, false, false);
                check(elsewhere.getOrDefault(Items.PORKCHOP, 0) > 0 && !elsewhere.containsKey(Items.BEEF),
                        "8 overworld pigs dropped " + elsewhere);
                return null;
            });
        } finally {
            server.getGameRules().set(UhcMobDrops.ALL_MEAT_IS_BEEF, previous, server);
        }
        context.succeed();
    }

    /** Runs {@code action} in a disposable level of {@code dimension} whose entities at {@link #AT} are tracked. */
    private static void inLevel(GameTestHelper context, ResourceKey<Level> dimension, Function<ServerLevel, Void> action) {
        MinecraftServer server = context.getLevel().getServer();
        UhcResourceGameTestFunctions.isolated(server, dimension, context.getLevel().getChunkSource().getGenerator(), level -> {
            level.getChunkAt(AT);
            UhcResourceGameTestFunctions.field(level, "entityManager", PersistentEntitySectionManager.class)
                    .updateChunkStatus(new ChunkPos(AT.getX() >> 4, AT.getZ() >> 4), Visibility.TRACKED);
            return action.apply(level);
        });
    }

    private static Map<Item, Integer> kill(ServerLevel level, EntityType<? extends Mob> type, int count, boolean natural,
            boolean burning) {
        return kill(level, type, count, natural, burning, mob -> {});
    }

    /**
     * Kills {@code count} new adult mobs of {@code type} at {@link #AT} and totals their loot by item.
     * {@code natural} marks them as naturally spawned, as the natural spawner does in a UHC dimension.
     */
    private static Map<Item, Integer> kill(ServerLevel level, EntityType<? extends Mob> type, int count, boolean natural,
            boolean burning, java.util.function.Consumer<Mob> prepare) {
        level.getEntitiesOfClass(ItemEntity.class, LOOT).forEach(ItemEntity::discard);
        for (int i = 0; i < count; i++) {
            Mob mob = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            check(mob != null, "could not create " + type);
            mob.snapTo(AT.getX() + 0.5, AT.getY(), AT.getZ() + 0.5);
            if (natural) mob.addTag(UhcMobDrops.NATURAL_TAG);
            prepare.accept(mob);
            level.addFreshEntity(mob);
            if (burning) mob.igniteForSeconds(10);
            ((LivingEntity) mob).kill(level);
            check(mob.isDeadOrDying(), type + " did not die");
        }
        Map<Item, Integer> totals = new HashMap<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, LOOT)) {
            totals.merge(item.getItem().getItem(), item.getItem().getCount(), Integer::sum);
            item.discard();
        }
        return totals;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
