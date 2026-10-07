package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.mixin.CreeperAccess;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Kit items with an ability, marked in their custom data, and the kits' abilities that need no
 * item of their own (Nether Lord's fireball, Cryomancer's ground freeze, Fishmonger's silverfish).
 */
public final class SkyWarsItems {
    public static final String KEY = BrainageMinigames.MOD_ID + ":skywars_item";
    private static final String ENTITY_KEY = BrainageMinigames.MOD_ID + ":skywars_entity";

    public static final String CORRUPTED_PEARL = "corrupted_pearl";
    public static final String TIME_WARP_PEARL = "time_warp_pearl";
    public static final String ECHO = "echo";
    public static final String ICE_BRIDGE_EGG = "ice_bridge_egg";
    public static final String CAPTURE_EGG = "capture_egg";
    public static final String THROWABLE_SPAWN_EGG = "throwable_spawn_egg";
    public static final String MYSTERY_EGG = "mystery_egg";
    public static final String FISHMONGER_SILVERFISH = "fishmonger_silverfish";
    public static final String CHARGED_CREEPER_EGG = "charged_creeper_egg";
    public static final String THUNDERMEISTER_AXE = "thundermeister_axe";
    public static final String FIRE_NUGGET = "fire_nugget";
    public static final String MY_PRECIOUS = "my_precious";

    /** Corrupted Pearls and Ice Bridge Eggs work only this long after the cages open. */
    static final int START_DELAY_TICKS = 30 * 20;
    static final int TIME_WARP_TICKS = 3 * 20;
    static final int ECHO_SECONDS = 10;
    static final int THUNDER_COOLDOWN_TICKS = 5 * 20;
    static final float THUNDER_DAMAGE = 3.0F;
    static final int FIREBALL_COOLDOWN_TICKS = 20 * 20;
    static final int FREEZE_COOLDOWN_TICKS = 10 * 20;
    static final int FREEZE_RADIUS = 2;
    static final int SILVERFISH_CHANCE = 10;
    static final int ICE_BRIDGE_TICKS = 40;

    private static final List<EntityType<? extends Mob>> MYSTERY_MOBS = List.of(
            EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.CREEPER,
            EntityTypes.ENDERMAN, EntityTypes.SNOW_GOLEM, EntityTypes.WOLF, EntityTypes.BLAZE,
            EntityTypes.SILVERFISH, EntityTypes.WITCH, EntityTypes.CHICKEN, EntityTypes.PIG);
    private static final List<List<Item>> ARMOR_TIERS = List.of(
            List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
            List.of(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS),
            List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
            List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS));
    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private SkyWarsItems() {}

    /** The ability item id of a kit item, or an empty string. */
    public static String ability(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(KEY, "");
    }

    /**
     * Whether using the item on a block runs its ability: true for ability spawn eggs, which would
     * otherwise place a plain mob. Other ability items reach their ability through the use in the
     * air that follows, so it runs only once.
     */
    public static boolean replacesUseOn(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem && !ability(stack).isEmpty();
    }

    /** Runs an ability item or kit ability; {@link InteractionResult#PASS} lets vanilla use it. */
    static InteractionResult use(SkyWarsMatch state, Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        String ability = ability(stack);
        SkyWarsKit kit = state.kits.get(player.getUUID());
        String kitId = kit == null ? "" : kit.id();
        if (ability.isEmpty()) {
            if (stack.is(ItemTags.SWORDS) && kitId.equals("nether_lord")) {
                return fireball(state, player);
            }
            if (stack.is(ItemTags.SWORDS) && kitId.equals("cryomancer")) {
                return freeze(state, match, player);
            }
            return InteractionResult.PASS;
        }
        switch (ability) {
            case CORRUPTED_PEARL -> {
                return delayed(match, player, "The Corrupted Pearl") ? InteractionResult.FAIL : InteractionResult.PASS;
            }
            case TIME_WARP_PEARL -> {
                ThrownEnderpearl pearl = new ThrownEnderpearl(player.level(), player, stack.copyWithCount(1));
                pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                player.level().addFreshEntity(pearl);
                state.thrown.put(pearl, new SkyWarsMatch.Thrown(TIME_WARP_PEARL, player.getUUID(), state.team(player), player.position()));
                player.getCooldowns().addCooldown(stack, 20);
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case ECHO -> {
                return echo(state, match, player, stack);
            }
            case FIRE_NUGGET -> {
                return fireball(state, player);
            }
            case MY_PRECIOUS -> {
                SkyWarsPerks.addMaxHealth(state, player, 4);
                player.heal(4);
                consume(player, stack);
                player.sendSystemMessage(Component.literal("You gained 2 permanent hearts.").withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }
            case ICE_BRIDGE_EGG -> {
                if (delayed(match, player, "The Ice Bridge Egg")) {
                    return InteractionResult.FAIL;
                }
                return throwItem(state, player, stack, ability);
            }
            case CAPTURE_EGG, THROWABLE_SPAWN_EGG, MYSTERY_EGG, FISHMONGER_SILVERFISH, CHARGED_CREEPER_EGG -> {
                return throwItem(state, player, stack, ability);
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
    }

    private static boolean delayed(Match match, ServerPlayer player, String item) {
        int left = START_DELAY_TICKS - match.activeTicks();
        if (left <= 0) {
            return false;
        }
        player.sendSystemMessage(Component.literal(item + " can be used in " + ((left + 19) / 20) + "s.")
                .withStyle(ChatFormatting.RED));
        PlayerUtils.resyncInventory(player);
        return true;
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    private static InteractionResult throwItem(SkyWarsMatch state, ServerPlayer player, ItemStack stack, String ability) {
        Snowball egg = new Snowball(player.level(), player, stack.copyWithCount(1));
        egg.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
        player.level().addFreshEntity(egg);
        String entity = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(ENTITY_KEY, "");
        state.thrown.put(egg, new SkyWarsMatch.Thrown(ability + (entity.isEmpty() ? "" : "|" + entity),
                player.getUUID(), state.team(player), player.position()));
        consume(player, stack);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult echo(SkyWarsMatch state, Match match, ServerPlayer player, ItemStack stack) {
        if (aboveVoid(player)) {
            player.sendSystemMessage(Component.literal("Echo cannot be used above the void.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ArrayDeque<Vec3> history = state.history.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            return InteractionResult.FAIL;
        }
        Vec3 back = history.peekFirst();
        player.teleportTo(back.x, back.y, back.z);
        player.resetFallDistance();
        player.heal(4);
        consume(player, stack);
        return InteractionResult.SUCCESS;
    }

    private static boolean aboveVoid(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int y = pos.getY(); y >= level.getMinY(); y--) {
            pos.setY(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static InteractionResult fireball(SkyWarsMatch state, ServerPlayer player) {
        if (!state.ready(player, "fireball", FIREBALL_COOLDOWN_TICKS)) {
            return InteractionResult.PASS;
        }
        Vec3 look = player.getLookAngle();
        SmallFireball fireball = new SmallFireball(player.level(), player, look);
        fireball.setPos(player.getX() + look.x, player.getEyeY() + look.y * 0.5, player.getZ() + look.z);
        player.level().addFreshEntity(fireball);
        return InteractionResult.SUCCESS;
    }

    /** Turns the ground around Cryomancer into packed ice. */
    private static InteractionResult freeze(SkyWarsMatch state, Match match, ServerPlayer player) {
        if (!state.ready(player, "freeze", FREEZE_COOLDOWN_TICKS)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = player.level();
        BlockPos below = player.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(below.offset(-FREEZE_RADIUS, 0, -FREEZE_RADIUS),
                below.offset(FREEZE_RADIUS, 0, FREEZE_RADIUS))) {
            var state0 = level.getBlockState(pos);
            if (!state0.isAir() && !state0.hasBlockEntity() && state0.isCollisionShapeFullBlock(level, pos)
                    && match.arena().canBuild(pos)) {
                level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** A melee hit by an alive participant: Thundermeister's axe and Fishmonger's silverfish. */
    static void meleeHit(SkyWarsMatch state, ServerPlayer attacker, LivingEntity victim) {
        ItemStack held = attacker.getMainHandItem();
        if (ability(held).equals(THUNDERMEISTER_AXE) && state.ready(attacker, THUNDERMEISTER_AXE, THUNDER_COOLDOWN_TICKS)) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(attacker.level(), EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
                attacker.level().addFreshEntity(bolt);
            }
            // True damage: health lost past armour, protection and invulnerability frames.
            if (victim.getHealth() > THUNDER_DAMAGE) {
                victim.setHealth(victim.getHealth() - THUNDER_DAMAGE);
            } else {
                victim.hurtServer(attacker.level(), attacker.level().damageSources().genericKill(), Float.MAX_VALUE);
            }
        }
        SkyWarsKit kit = state.kits.get(attacker.getUUID());
        if (kit != null && kit.id().equals("fishmonger") && attacker.getRandom().nextInt(100) < SILVERFISH_CHANCE) {
            spawnFriendly(state, attacker.level(), EntityTypes.SILVERFISH, victim.position(), state.team(attacker));
        }
    }

    /** Thrown ability items: Ice Bridge Eggs lay ice as they fly, the others act where they land. */
    static void tick(SkyWarsMatch state, Match match) {
        int now = match.server().getTickCount();
        for (Iterator<Map.Entry<Projectile, SkyWarsMatch.Thrown>> it = state.thrown.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Projectile, SkyWarsMatch.Thrown> entry = it.next();
            Projectile projectile = entry.getKey();
            SkyWarsMatch.Thrown thrown = entry.getValue();
            ServerPlayer owner = match.server().getPlayerList().getPlayer(thrown.owner());
            if (thrown.ability().equals(ICE_BRIDGE_EGG) && !projectile.isRemoved() && projectile.tickCount <= ICE_BRIDGE_TICKS) {
                bridge(match, projectile);
            }
            if (!projectile.isRemoved()) {
                continue;
            }
            it.remove();
            if (owner == null || !match.isAlive(owner.getUUID())) {
                continue;
            }
            land(state, match, owner, thrown, projectile.position());
        }
        for (Iterator<SkyWarsMatch.Warp> it = state.warps.iterator(); it.hasNext(); ) {
            SkyWarsMatch.Warp warp = it.next();
            if (warp.returnTick() > now) {
                continue;
            }
            it.remove();
            if (match.isAlive(warp.player().getUUID()) && !warp.player().hasDisconnected()) {
                warp.player().teleportTo(warp.origin().x, warp.origin().y, warp.origin().z);
                warp.player().resetFallDistance();
            }
        }
        if (match.activeTicks() % 20 == 0) {
            for (ServerPlayer player : match.alivePlayers()) {
                SkyWarsKit kit = state.kits.get(player.getUUID());
                if (kit != null && kit.id().equals("chronobreaker")) {
                    ArrayDeque<Vec3> history = state.history.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
                    history.addLast(player.position());
                    while (history.size() > ECHO_SECONDS) {
                        history.removeFirst();
                    }
                }
            }
        }
    }

    private static void bridge(Match match, Projectile projectile) {
        ServerLevel level = (ServerLevel) projectile.level();
        BlockPos center = projectile.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            if (level.getBlockState(pos).isAir() && match.arena().canBuild(pos)) {
                level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
            }
        }
    }

    private static void land(SkyWarsMatch state, Match match, ServerPlayer owner, SkyWarsMatch.Thrown thrown, Vec3 at) {
        ServerLevel level = owner.level();
        String[] parts = thrown.ability().split("\\|", 2);
        switch (parts[0]) {
            case TIME_WARP_PEARL -> {
                owner.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TIME_WARP_TICKS, 1));
                state.warps.add(new SkyWarsMatch.Warp(owner, thrown.origin(), match.server().getTickCount() + TIME_WARP_TICKS));
            }
            case CAPTURE_EGG -> {
                Mob captured = null;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(2.5))) {
                    if (captured == null || mob.distanceToSqr(at) < captured.distanceToSqr(at)) {
                        captured = mob;
                    }
                }
                if (captured != null) {
                    owner.getInventory().add(captureEgg(captured.getType()));
                    state.friendlyMobs.remove(captured);
                    captured.discard();
                }
            }
            case THROWABLE_SPAWN_EGG -> {
                EntityType<?> type = parts.length > 1 ? BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(parts[1])) : null;
                Mob mob = type == null
                        ? spawnFriendly(state, level, owner.getRandom().nextBoolean() ? EntityTypes.ZOMBIE : EntityTypes.SKELETON, at, thrown.team())
                        : spawnFriendly(state, level, type, at, thrown.team());
                if (mob != null && type == null) {
                    List<Item> armor = ARMOR_TIERS.get(owner.getRandom().nextInt(ARMOR_TIERS.size()));
                    for (int index = 0; index < ARMOR_SLOTS.size(); index++) {
                        mob.setItemSlot(ARMOR_SLOTS.get(index), new ItemStack(armor.get(index)));
                    }
                }
            }
            case MYSTERY_EGG -> {
                // Hypixel's Zookeeper notes a 0.01% chance of a Giant.
                EntityType<? extends Mob> type = owner.getRandom().nextInt(10_000) == 0
                        ? EntityTypes.GIANT : MYSTERY_MOBS.get(owner.getRandom().nextInt(MYSTERY_MOBS.size()));
                spawnFriendly(state, level, type, at, thrown.team());
            }
            case FISHMONGER_SILVERFISH -> spawnFriendly(state, level, EntityTypes.SILVERFISH, at, thrown.team());
            case CHARGED_CREEPER_EGG -> {
                if (spawnFriendly(state, level, EntityTypes.CREEPER, at, thrown.team()) instanceof Creeper creeper) {
                    creeper.getEntityData().set(CreeperAccess.brainage_minigames$powered(), true);
                }
            }
            default -> {}
        }
    }

    /** A Throwable Spawn Egg of a captured mob. */
    static ItemStack captureEgg(EntityType<?> type) {
        Item egg = SpawnEggItem.byId(type).map(holder -> holder.value()).orElse(Items.ZOMBIE_SPAWN_EGG);
        ItemStack stack = new ItemStack(egg);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Throwable Spawn Egg").withStyle(style -> style.withItalic(false)));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, THROWABLE_SPAWN_EGG);
        tag.putString(ENTITY_KEY, EntityType.getKey(type).toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Spawns a mob that fights for {@code team}; see {@link SkyWarsPerks#tickFriendlyMobs}. */
    static @Nullable Mob spawnFriendly(SkyWarsMatch state, ServerLevel level, EntityType<?> type, Vec3 at, int team) {
        if (!(type.create(level, EntitySpawnReason.TRIGGERED) instanceof Mob mob)) {
            return null;
        }
        mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        state.friendlyMobs.put(mob, team);
        return mob;
    }
}
