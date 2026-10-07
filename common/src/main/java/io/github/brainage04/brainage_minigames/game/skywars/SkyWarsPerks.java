package io.github.brainage04.brainage_minigames.game.skywars;

import static io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk.*;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * What SkyWars perks do, plus the mode-wide rules that ride on the same hooks: no hunger, no
 * ender pearl damage, ores smelt as they drop and mined blocks go straight into the inventory.
 */
public final class SkyWarsPerks {
    private static final Identifier MAX_HEALTH_ID = BrainageMinigames.id("skywars_max_health");
    static final String COMPASS = "tracking_compass";
    /** Friendly mobs look for enemies this far away. */
    private static final double MOB_RANGE = 16.0;

    private SkyWarsPerks() {}

    private static @Nullable SkyWarsMatch state(ServerPlayer player) {
        Match match = MatchManager.activeMatch(player.getUUID());
        return match == null || !match.isAlive(player.getUUID()) ? null : SkyWarsGame.state(match);
    }

    private static boolean roll(ServerPlayer player, int percent) {
        return percent > 0 && player.getRandom().nextInt(100) < percent;
    }

    // Start of the game

    static void start(SkyWarsMatch state, ServerPlayer player) {
        int resistance = state.perkValue(player, RESISTANCE_BOOST);
        if (resistance > 0) player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, resistance * 20, 1));
        int fat = state.perkValue(player, FAT);
        if (fat > 0) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, fat * 20, 0));
        int haste = state.perkValue(player, SPEED_BOOST);
        if (haste > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, haste * 20, 1));
            if (state.upgradedPerks) player.addEffect(new MobEffectInstance(MobEffects.SPEED, 7 * 20, 0));
        }
        int pledge = state.perkValue(player, DRAGONS_PLEDGE);
        if (pledge > 0) {
            addMaxHealth(state, player, pledge * 2 - player.getMaxHealth());
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Dragon's Pledge: the island chest nearest each such player holds just an ender pearl. */
    static void pledgeChests(SkyWarsMatch state, Match match) {
        Set<BlockPos> used = new HashSet<>();
        for (ServerPlayer player : match.alivePlayers()) {
            if (!state.hasPerk(player, DRAGONS_PLEDGE)) continue;
            BlockPos nearest = null;
            for (Map.Entry<BlockPos, SkyWarsMatch.ChestKind> chest : state.chests.entrySet()) {
                if (chest.getValue() == SkyWarsMatch.ChestKind.ISLAND && !used.contains(chest.getKey())
                        && (nearest == null || chest.getKey().distSqr(player.blockPosition()) < nearest.distSqr(player.blockPosition()))) {
                    nearest = chest.getKey();
                }
            }
            if (nearest != null && used.add(nearest)
                    && match.arena().level().getBlockEntity(nearest) instanceof Container container) {
                container.clearContent();
                container.setItem(container.getContainerSize() / 2, new ItemStack(Items.ENDER_PEARL));
            }
        }
    }

    /** Changes the player's max health by {@code delta} points until they are released. */
    static void addMaxHealth(SkyWarsMatch state, ServerPlayer player, double delta) {
        double total = state.maxHealth.merge(player.getUUID(), delta, Double::sum);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrUpdateTransientModifier(new AttributeModifier(MAX_HEALTH_ID, total, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }

    static void release(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(MAX_HEALTH_ID) != null) {
            health.removeModifier(MAX_HEALTH_ID);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }

    // Kills

    static void killed(SkyWarsMatch state, Match match, ServerPlayer killer, ServerPlayer victim, boolean inVoid) {
        DamageSource last = victim.getLastDamageSource();
        boolean bow = last != null && last.getDirectEntity() instanceof AbstractArrow;
        ItemStack held = killer.getMainHandItem();
        int[] kills = state.killsOf(killer.getUUID());
        kills[0]++;
        boolean solo = match.layout().isFreeForAll() || match.layout().commonTeamSize() == 1;
        int strength = state.hasPerk(killer, BULLDOZER) ? (solo ? 5 : 2) : 0;
        if (strength > 0) killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, strength * 20, 0));
        int regeneration = state.perkValue(killer, JUGGERNAUT);
        if (regeneration > 0) killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regeneration * 20, 0));
        int absorption = state.perkValue(killer, SAVIOR);
        if (absorption > 0) killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorption * 20, 0));
        int levels = state.perkValue(killer, KNOWLEDGE);
        if (levels > 0) killer.giveExperienceLevels(levels);
        if (roll(killer, state.perkValue(killer, LUCKY_CHARM))) PlayerUtils.giveOrDrop(killer, new ItemStack(Items.GOLDEN_APPLE));
        if (roll(killer, state.perkValue(killer, DIAMOND_IN_THE_ROUGH))) PlayerUtils.giveOrDrop(killer, new ItemStack(Items.DIAMOND));
        if (inVoid && roll(killer, state.perkValue(killer, BLACK_MAGIC))) PlayerUtils.giveOrDrop(killer, new ItemStack(Items.ENDER_PEARL));
        int heal = state.perkValue(killer, TENACITY);
        if (heal > 0) killer.heal(heal);
        if (roll(killer, state.perkValue(killer, NECROMANCER))) {
            SkyWarsItems.spawnFriendly(state, killer.level(), EntityTypes.ZOMBIE, victim.position(), state.team(killer));
        }
        int every = state.perkValue(killer, LIBRARIAN);
        if (every > 0 && kills[0] % every == 0) {
            var books = List.of(Enchantments.SHARPNESS, Enchantments.PROTECTION, Enchantments.POWER);
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.updateEnchantments(book, enchantments -> enchantments.set(
                    holder(killer.registryAccess(), books.get(killer.getRandom().nextInt(books.size()))), 1));
            PlayerUtils.giveOrDrop(killer, book);
        }
        if (bow) {
            kills[2]++;
            int per = state.perkValue(killer, MARKSMANSHIP);
            ItemStack weapon = last.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : null;
            ItemStack bowStack = weapon != null && weapon.is(Items.BOW) ? findInInventory(killer, weapon) : null;
            if (per > 0 && kills[2] % per == 0 && bowStack != null) upgrade(killer, bowStack, Enchantments.POWER);
        } else if (held.is(ItemTags.AXES)) {
            kills[1]++;
            int per = state.perkValue(killer, BARBARIAN);
            if (per > 0 && kills[1] % per == 0) upgrade(killer, held, Enchantments.SHARPNESS);
        } else if (held.is(ItemTags.SWORDS)) {
            kills[3]++;
            if (kills[3] <= state.perkValue(killer, DOUBLE_EDGED_SWORD)) {
                upgrade(killer, held, Enchantments.SHARPNESS);
                addMaxHealth(state, killer, -4);
            }
        }
    }

    private static @Nullable ItemStack findInInventory(ServerPlayer player, ItemStack copy) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, copy)) return stack;
        }
        return null;
    }

    private static Holder<Enchantment> holder(RegistryAccess registries, ResourceKey<Enchantment> key) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static void upgrade(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> key) {
        Holder<Enchantment> enchantment = holder(player.registryAccess(), key);
        EnchantmentHelper.updateEnchantments(stack, enchantments ->
                enchantments.set(enchantment, enchantments.getLevel(enchantment) + 1));
    }

    // Damage

    /** Damage {@code victim} is about to take, scaled by the SkyWars rules and perks. */
    public static float hurt(ServerPlayer victim, DamageSource source, float amount) {
        SkyWarsMatch state = state(victim);
        if (state == null || state.match == null) return amount;
        if (source.is(DamageTypes.ENDER_PEARL)) return 0;
        if (source.getEntity() instanceof Mob mob) {
            Integer team = state.friendlyMobs.get(mob);
            if (team != null && team == state.team(victim)) return 0;
        }
        if (source.getEntity() == null && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
            amount *= 1 - state.perkValue(victim, ENVIRONMENTAL_EXPERT) / 100F;
        }
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim
                || !state.match.isAlive(attacker.getUUID()) || state.team(attacker) == state.team(victim)) {
            return amount;
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            if (roll(attacker, state.perkValue(attacker, ARROW_RECOVERY))) PlayerUtils.giveOrDrop(attacker, new ItemStack(Items.ARROW));
            if (roll(attacker, state.perkValue(attacker, ANNOY_O_MITE))) {
                SkyWarsItems.spawnFriendly(state, victim.level(), EntityTypes.SILVERFISH, victim.position(), state.team(attacker));
            }
            if (arrow.isCritArrow() && roll(attacker, state.perkValue(attacker, FROST))) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 3 * 20, 0), attacker);
            }
            return amount;
        }
        if (source.getDirectEntity() != attacker) return amount;
        if (attacker.getMainHandItem().isEmpty() && !victim.getMainHandItem().isEmpty()
                && roll(attacker, state.perkValue(attacker, ROBBERY))) {
            ItemStack stolen = victim.getMainHandItem().copy();
            victim.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            PlayerUtils.giveOrDrop(attacker, stolen);
        }
        if (wearsDiamond(victim) && roll(attacker, state.perkValue(attacker, DIAMONDPIERCER))) {
            amount *= 1.2F;
        }
        SkyWarsItems.meleeHit(state, attacker, victim);
        return amount;
    }

    private static boolean wearsDiamond(LivingEntity entity) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (armor.is(Items.DIAMOND_HELMET) || armor.is(Items.DIAMOND_CHESTPLATE)
                    || armor.is(Items.DIAMOND_LEGGINGS) || armor.is(Items.DIAMOND_BOOTS)) return true;
        }
        return false;
    }

    // Items

    /** Blazing Arrows: an arrow the player just shot may be set on fire. */
    public static void shot(ServerPlayer player, Projectile projectile) {
        SkyWarsMatch state = state(player);
        if (state != null && projectile instanceof AbstractArrow arrow
                && roll(player, state.perkValue(player, BLAZING_ARROWS))) {
            arrow.igniteForSeconds(100);
        }
    }

    /** Bridger: whether a block the player just placed is refunded. */
    public static boolean refundPlacedBlock(ServerPlayer player) {
        SkyWarsMatch state = state(player);
        return state != null && !player.isCreative() && roll(player, state.perkValue(player, BRIDGER));
    }

    /** Apothecary: positive potion effects last longer. */
    public static MobEffectInstance potion(ServerPlayer player, MobEffectInstance effect) {
        SkyWarsMatch state = state(player);
        if (state == null || effect.isInfiniteDuration()
                || effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) return effect;
        int percent = state.perkValue(player, APOTHECARY);
        return percent == 0 ? effect : effect.withScaledDuration(1 + percent / 100F);
    }

    /** Mining Expertise doubles ore drops; every ore smelts and mined drops go to the inventory. */
    public static List<ItemStack> drops(List<ItemStack> drops, BlockState state, LootParams.Builder params) {
        if (drops.isEmpty() || !(params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) return drops;
        SkyWarsMatch match = state(player);
        if (match == null) return drops;
        List<ItemStack> result = new ArrayList<>();
        boolean doubled = UhcResourceRules.ore(state) != null && roll(player, match.perkValue(player, MINING_EXPERTISE));
        for (ItemStack drop : drops) {
            ItemStack smelted = smelt(drop);
            if (doubled) {
                smelted.grow(smelted.getCount());
            }
            if (!player.getInventory().add(smelted) && !smelted.isEmpty()) result.add(smelted);
        }
        return result;
    }

    private static ItemStack smelt(ItemStack drop) {
        ItemStack result = drop.is(Items.RAW_IRON) ? new ItemStack(Items.IRON_INGOT, drop.getCount())
                : drop.is(Items.RAW_GOLD) ? new ItemStack(Items.GOLD_INGOT, drop.getCount())
                : drop.is(Items.RAW_COPPER) ? new ItemStack(Items.COPPER_INGOT, drop.getCount())
                : drop.copy();
        return result;
    }

    /** Fortune Teller: the second enchanting offer for a sword or armour includes Sharpness I or Protection I. */
    public static List<EnchantmentInstance> enchantments(ServerPlayer player, RegistryAccess registries, ItemStack stack,
            int option, List<EnchantmentInstance> offers) {
        SkyWarsMatch state = state(player);
        if (state == null || option != 1 || offers.isEmpty() || !state.hasPerk(player, FORTUNE_TELLER)) return offers;
        ResourceKey<Enchantment> key = stack.is(ItemTags.SWORDS) ? Enchantments.SHARPNESS
                : stack.is(ItemTags.ARMOR_ENCHANTABLE) ? Enchantments.PROTECTION : null;
        if (key == null) return offers;
        Holder<Enchantment> guaranteed = holder(registries, key);
        for (EnchantmentInstance offer : offers) {
            if (offer.enchantment().equals(guaranteed)) return offers;
        }
        List<EnchantmentInstance> result = new ArrayList<>();
        for (EnchantmentInstance offer : offers) {
            if (Enchantment.areCompatible(offer.enchantment(), guaranteed)) result.add(offer);
        }
        result.addFirst(new EnchantmentInstance(guaranteed, 1));
        return result;
    }

    // Every tick

    static void tick(SkyWarsMatch state, Match match) {
        boolean second = match.activeTicks() % 20 == 0;
        for (ServerPlayer player : match.alivePlayers()) {
            if (second) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0F);
                updateCompass(match, player);
            }
            openedChest(state, player);
        }
        if (match.activeTicks() % 10 == 0) tickFriendlyMobs(state, match);
    }

    /** Hide and Seek: a Tracking Compass some seconds before each refill. */
    static void beforeRefill(SkyWarsMatch state, Match match, int ticksLeft) {
        for (ServerPlayer player : match.alivePlayers()) {
            int seconds = state.perkValue(player, HIDE_AND_SEEK);
            if (seconds > 0 && ticksLeft == seconds * 20 && state.compasses.add(player.getUUID())) {
                ItemStack compass = new ItemStack(Items.COMPASS);
                compass.set(DataComponents.CUSTOM_NAME, Component.literal("Tracking Compass").withStyle(style -> style.withItalic(false)));
                compass.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(
                        tagWith(SkyWarsItems.KEY, COMPASS)));
                PlayerUtils.giveOrDrop(player, compass);
                updateCompass(match, player);
                player.sendSystemMessage(Component.literal("Your Tracking Compass points to the nearest enemy.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static net.minecraft.nbt.CompoundTag tagWith(String key, String value) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString(key, value);
        return tag;
    }

    static void refilled(SkyWarsMatch state) {
        state.refills++;
        state.openedSinceRefill.clear();
        state.compasses.clear();
    }

    private static void updateCompass(Match match, ServerPlayer player) {
        ServerPlayer nearest = nearestEnemy(match, player, Double.MAX_VALUE);
        if (nearest == null) return;
        LodestoneTracker tracker = new LodestoneTracker(
                java.util.Optional.of(GlobalPos.of(nearest.level().dimension(), nearest.blockPosition())), false);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (SkyWarsItems.ability(stack).equals(COMPASS)) stack.set(DataComponents.LODESTONE_TRACKER, tracker);
        }
    }

    private static @Nullable ServerPlayer nearestEnemy(Match match, LivingEntity from, double range) {
        int team = match.teamOf(from.getUUID()).map(t -> t.number()).orElse(0);
        return nearestEnemy(match, from, team, range);
    }

    private static @Nullable ServerPlayer nearestEnemy(Match match, LivingEntity from, int team, double range) {
        ServerPlayer nearest = null;
        double best = range * range;
        for (ServerPlayer other : match.alivePlayers()) {
            if (other == from || other.level() != from.level()
                    || match.teamOf(other.getUUID()).map(t -> t.number()).orElse(-1) == team) continue;
            double distance = other.distanceToSqr(from);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    /** Friendly mobs leave their team alone and go for the nearest enemy participant. */
    static void tickFriendlyMobs(SkyWarsMatch state, Match match) {
        for (Iterator<Map.Entry<Mob, Integer>> it = state.friendlyMobs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Mob, Integer> entry = it.next();
            Mob mob = entry.getKey();
            if (mob.isRemoved() || !mob.isAlive()) {
                it.remove();
                continue;
            }
            LivingEntity target = mob.getTarget();
            boolean ally = target instanceof ServerPlayer player
                    && (!match.isAlive(player.getUUID()) || state.team(player) == entry.getValue());
            if (target == null || ally) {
                mob.setTarget(nearestEnemy(match, mob, entry.getValue(), MOB_RANGE));
            }
        }
    }

    /** Fruit Finder and Ender End Game act on the chest a player has open. */
    private static void openedChest(SkyWarsMatch state, ServerPlayer player) {
        if (!(player.containerMenu instanceof ChestMenu menu)) return;
        BlockPos pos = chestPos(menu.getContainer());
        SkyWarsMatch.ChestKind kind = pos == null ? null : state.chests.get(pos);
        if (kind == null) return;
        Container container = menu.getContainer();
        if (kind == SkyWarsMatch.ChestKind.MID && state.hasPerk(player, FRUIT_FINDER) && state.openedMid.add(player.getUUID())
                && !container.hasAnyMatching(stack -> stack.is(Items.GOLDEN_APPLE))) {
            addToChest(container, new ItemStack(Items.GOLDEN_APPLE));
        }
        if (state.refills > 0 && state.openedSinceRefill.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(pos)
                && roll(player, state.perkValue(player, ENDER_END_GAME))) {
            addToChest(container, new ItemStack(Items.ENDER_PEARL));
        }
    }

    /** The position of a single chest; SkyWars maps have no double chests. */
    private static @Nullable BlockPos chestPos(Container container) {
        return container instanceof BlockEntity entity ? entity.getBlockPos() : null;
    }

    private static void addToChest(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack);
                return;
            }
        }
    }
}
