package io.github.brainage04.brainage_minigames.game.uhc;

import static net.minecraft.world.item.Items.*;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Published effects plus explicitly labelled local policies for unpublished shop/effect details. */
public final class UhcExtraRecipes {
    private static final Map<Match, State> STATES = new HashMap<>();
    private static final UUID NO_TARGET = new UUID(0, 0);
    private static final String[] DICE = {"artemis_bow", "anduril", "exodus", "axe_of_perun", "hermes_boots", "chest_of_fate"};
    private static final class State {
        final Map<UUID, int[]> timers = new HashMap<>();
        final Map<UUID, UUID> arrows = new HashMap<>();
        final Map<UUID, Integer> dice = new HashMap<>();
    }
    private UhcExtraRecipes() {}
    private static State state(Match match) { return STATES.computeIfAbsent(match, key -> new State()); }
    public static void close(Match match) { STATES.remove(match); }
    public static void start(Match match) { STATES.put(match, new State()); }
    public static boolean brewingLevel(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        for (Match match : STATES.keySet()) if (match.phase() == io.github.brainage04.brainage_minigames.game.MatchPhase.ACTIVE && match.server() == serverLevel.getServer()
                && match.arena() instanceof UhcArena arena && arena.border(serverLevel) != null) return true;
        return false;
    }

    public static List<UhcProgression.Node> nodes() {
        List<UhcProgression.Node> result = new ArrayList<>();
        node(result, "artemis_bow", 100_000, "weaponsmith", "hunter");
        node(result, "flask_of_ichor", 120_000, "alchemy", "bloodcraft");
        node(result, "exodus", 350_000, "cooking", "armorsmith", "bloodcraft", "engineering");
        node(result, "hide_of_leviathan", 150_000, "survivalism", "armorsmith");
        node(result, "tablets_of_destiny", 250_000, "engineering", "enchanting");
        node(result, "axe_of_perun", 150_000, "weaponsmith", "enchanting");
        node(result, "excalibur", 150_000, "weaponsmith", "alchemy");
        node(result, "anduril", 150_000, "weaponsmith", "engineering");
        node(result, "deaths_scythe", 200_000, "bloodcraft", "weaponsmith");
        node(result, "chest_of_fate", 75_000, "bloodcraft", "survivalism", "alchemy");
        node(result, "cornucopia", 50_000, "cooking", "engineering");
        node(result, "essence_of_yggdrasil", 75_000, "enchanting", "alchemy");
        node(result, "voidbox", 50_000, "bloodcraft", "hunter");
        node(result, "deus_ex_machina", 100_000, "bloodcraft", "alchemy", "cooking");
        node(result, "dice_of_god", 100_000, "bloodcraft", "engineering");
        node(result, "kings_rod", 50_000, "engineering", "survivalism");
        node(result, "daredevil", 150_000, "hunter", "bloodcraft", "survivalism");
        // Later prices/prerequisites were not published in the announcements: local policy.
        node(result, "shoes_of_vidar", 100_000, "survivalism", "enchanting");
        node(result, "ambrosia", 100_000, "alchemy", "enchanting");
        node(result, "potion_of_vitality", 100_000, "alchemy", "cooking");
        node(result, "miners_blessing", 100_000, "engineering", "toolsmith");
        node(result, "bloodlust", 100_000, "bloodcraft", "weaponsmith");
        node(result, "modular_bow", 100_000, "hunter", "enchanting");
        node(result, "expert_seal", 100_000, "enchanting", "armorsmith");
        node(result, "hermes_boots", 100_000, "survivalism", "hunter");
        node(result, "barbarian_chestplate", 100_000, "armorsmith", "weaponsmith");
        node(result, "fates_call", 100_000, "strategist", "engineering");
        node(result, "the_mark", 100_000, "strategist", "hunter");
        node(result, "warlock_pants", 100_000, "strategist", "armorsmith");
        node(result, "flask_of_cleansing", 100_000, "alchemy", "bloodcraft");
        return List.copyOf(result);
    }
    private static void node(List<UhcProgression.Node> list, String id, int cost, String... trees) {
        list.add(new UhcProgression.Node(id, cost, java.util.Arrays.stream(trees).map(tree -> tree + "/recipe4").toList(), false));
    }

    public static void registerRecipes() {
        add("artemis_bow", BOW, 1, "FEF/FBF/FIF", 'F', FEATHER, 'E', EMERALD, 'B', BOW, 'I', ENDER_EYE);
        add("flask_of_ichor", SPLASH_POTION, 1, " H /MBM/ I ", 'H', PLAYER_HEAD, 'M', BROWN_MUSHROOM, 'B', GLASS_BOTTLE, 'I', INK_SAC);
        add("exodus", DIAMOND_HELMET, 1, "DDD/DHD/ECE", 'D', DIAMOND, 'H', PLAYER_HEAD, 'E', EMERALD, 'C', GOLDEN_CARROT);
        add("hide_of_leviathan", DIAMOND_LEGGINGS, 1, "LWL/DPD/G G", 'L', LAPIS_BLOCK, 'W', WATER_BUCKET, 'D', DIAMOND, 'P', DIAMOND_LEGGINGS, 'G', LILY_PAD);
        add("tablets_of_destiny", ENCHANTED_BOOK, 1, " M /SBW/EEE", 'M', MAGMA_CREAM, 'S', GOLDEN_SWORD, 'B', WRITABLE_BOOK, 'W', BOW, 'E', EXPERIENCE_BOTTLE);
        add("axe_of_perun", DIAMOND_AXE, 1, "DTM/DS / S ", 'D', DIAMOND, 'T', TNT, 'M', MAGMA_CREAM, 'S', STICK);
        add("excalibur", DIAMOND_SWORD, 1, "SMS/STS/SDS", 'S', SOUL_SAND, 'M', MAGMA_CREAM, 'T', TNT, 'D', DIAMOND_SWORD);
        add("anduril", IRON_SWORD, 1, "FIF/FIF/FBF", 'F', FEATHER, 'I', IRON_BLOCK, 'B', BLAZE_ROD);
        add("deaths_scythe", IRON_HOE, 1, " HH/ BC/B  ", 'H', PLAYER_HEAD, 'B', BONE, 'C', CLOCK);
        add("chest_of_fate", POTION, 1, "WWW/WHW/WWW", 'W', OAK_PLANKS, 'H', PLAYER_HEAD);
        add("cornucopia", GOLDEN_CARROT, 5, "CCC/CAC/CCC", 'C', CARROT, 'A', GOLDEN_APPLE);
        add("essence_of_yggdrasil", EXPERIENCE_BOTTLE, 1, "LEL/GBG/LRL", 'L', OAK_LEAVES, 'E', ENCHANTING_TABLE, 'G', GLOWSTONE, 'B', GLASS_BOTTLE, 'R', REDSTONE_BLOCK);
        add("voidbox", ENDER_CHEST, 2, "GGG/GHG/GGG", 'G', GLASS, 'H', PLAYER_HEAD);
        add("deus_ex_machina", POTION, 1, " E / H / B ", 'E', EMERALD, 'H', PLAYER_HEAD, 'B', GLASS_BOTTLE);
        add("dice_of_god", END_PORTAL_FRAME, 1, "MHM/MJM/MMM", 'M', MOSSY_COBBLESTONE, 'H', PLAYER_HEAD, 'J', JUKEBOX);
        add("kings_rod", FISHING_ROD, 1, " R /LCL/ W ", 'R', FISHING_ROD, 'L', LILY_PAD, 'C', COMPASS, 'W', WATER_BUCKET);
        add("daredevil", HORSE_SPAWN_EGG, 1, "HS /BBB/BBB", 'H', PLAYER_HEAD, 'S', SADDLE, 'B', BONE);
        add("shoes_of_vidar", DIAMOND_BOOTS, 1, " P /WBW/ R ", 'P', PUFFERFISH, 'W', POTION, 'B', DIAMOND_BOOTS, 'R', FISHING_ROD);
        add("ambrosia", GLOWSTONE_DUST, 1, "BHB/GFG/GGG", 'B', BLAZE_POWDER, 'H', WITHER_SKELETON_SKULL, 'G', GLOWSTONE, 'F', GHAST_TEAR);
        add("potion_of_vitality", SPLASH_POTION, 1, " H / W / B ", 'H', WITHER_SKELETON_SKULL, 'W', NETHER_WART, 'B', GLASS_BOTTLE);
        add("miners_blessing", DIAMOND_PICKAXE, 1, "ESE/EPE/BBB", 'E', EXPERIENCE_BOTTLE, 'S', IRON_SWORD, 'P', DIAMOND_PICKAXE, 'B', BOOKSHELF);
        add("bloodlust", DIAMOND_SWORD, 1, "RDR/RSR/RER", 'R', REDSTONE_BLOCK, 'D', DIAMOND, 'S', DIAMOND_SWORD, 'E', EXPERIENCE_BOTTLE);
        // Unpublished effects/2019 image links: these three grids and Modular Bow's modes are local policy.
        add("modular_bow", BOW, 1, " C /BWB/FSF", 'C', CLOCK, 'B', BLAZE_POWDER, 'W', BOW, 'F', FERMENTED_SPIDER_EYE, 'S', SLIME_BALL);
        add("expert_seal", NETHER_STAR, 1, "EIE/GDG/EIE", 'E', EXPERIENCE_BOTTLE, 'I', IRON_BLOCK, 'G', GOLD_BLOCK, 'D', DIAMOND_BLOCK);
        add("hermes_boots", DIAMOND_BOOTS, 1, "DHD/PBP/F F", 'D', DIAMOND, 'H', PLAYER_HEAD, 'P', BLAZE_POWDER, 'B', DIAMOND_BOOTS, 'F', FEATHER);
        add("barbarian_chestplate", DIAMOND_CHESTPLATE, 1, "BCB/IPI/   ", 'B', BLAZE_ROD, 'C', DIAMOND_CHESTPLATE, 'I', IRON_BLOCK, 'P', POTION);
        add("fates_call", CHEST, 1, "GGG/GCG/GGG", 'G', GOLD_INGOT, 'C', CHEST);
        add("the_mark", COMPASS, 1, " E /RCR/ R ", 'E', ENDER_EYE, 'R', REDSTONE_BLOCK, 'C', COMPASS);
        add("warlock_pants", DIAMOND_LEGGINGS, 1, " H / P / B ", 'H', PLAYER_HEAD, 'P', DIAMOND_LEGGINGS, 'B', BLAZE_ROD);
        add("flask_of_cleansing", SPLASH_POTION, 1, " G / M / B ", 'G', GRAVEL, 'M', MILK_BUCKET, 'B', GLASS_BOTTLE);
    }
    private static void add(String id, net.minecraft.world.item.Item output, int count, String pattern, Object... keys) {
        UhcCrafting.add(id, UhcProgression.Tree.EXTRAS, 4, output, count, pattern, keys);
    }
    private static MobEffectInstance effect(Holder<MobEffect> effect, int seconds, int amplifier) { return new MobEffectInstance(effect, seconds * 20, amplifier); }
    private static void potion(ItemStack stack, MobEffectInstance... effects) { stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(effects), Optional.empty())); }
    public static void output(ServerPlayer player, ItemStack stack, String id) {
        switch (id) {
            case "artemis_bow" -> UhcCrafting.enchant(player, stack, Enchantments.POWER, 3);
            case "flask_of_ichor" -> potion(stack, effect(MobEffects.POISON, 12, 1));
            case "exodus" -> { UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 3); UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 3); }
            case "hide_of_leviathan" -> { UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 4); UhcCrafting.enchant(player, stack, Enchantments.RESPIRATION, 3); }
            case "tablets_of_destiny" -> { UhcCrafting.enchant(player, stack, Enchantments.SHARPNESS, 3); UhcCrafting.enchant(player, stack, Enchantments.POWER, 3); UhcCrafting.enchant(player, stack, Enchantments.FIRE_ASPECT, 1); UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 4); }
            case "axe_of_perun" -> { UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 1); stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BrainageMinigames.id("perun"), 3.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build()); }
            case "anduril" -> UhcCrafting.enchant(player, stack, Enchantments.SHARPNESS, 2);
            case "cornucopia" -> { stack.set(DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable.builder().onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(List.of(effect(MobEffects.REGENERATION, 10, 1)))).build()); }
            case "deus_ex_machina" -> potion(stack, effect(MobEffects.RESISTANCE, 10, 4));
            case "kings_rod" -> { UhcCrafting.enchant(player, stack, Enchantments.LURE, 5); UhcCrafting.enchant(player, stack, Enchantments.LUCK_OF_THE_SEA, 10); }
            case "shoes_of_vidar" -> { UhcCrafting.enchant(player, stack, Enchantments.DEPTH_STRIDER, 2); UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 3); UhcCrafting.enchant(player, stack, Enchantments.PROJECTILE_PROTECTION, 2); UhcCrafting.enchant(player, stack, Enchantments.THORNS, 1); }
            case "potion_of_vitality" -> potion(stack, effect(MobEffects.SPEED, 12, 1));
            case "flask_of_cleansing" -> potion(stack, effect(MobEffects.WEAKNESS, 5, 0));
            case "bloodlust" -> { UhcCrafting.enchant(player, stack, Enchantments.SHARPNESS, 1); stack.remove(DataComponents.ENCHANTABLE); }
            case "modular_bow" -> UhcCrafting.enchant(player, stack, Enchantments.POWER, 3);
            case "hermes_boots" -> { UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 2); UhcCrafting.enchant(player, stack, Enchantments.FEATHER_FALLING, 1); UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 2); stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(BrainageMinigames.id("hermes"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.FEET)); }
            case "barbarian_chestplate", "warlock_pants" -> UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 1);
            default -> { }
        }
    }

    public static void tick(Match match) {
        int tick = match.activeTicks(); if (tick % 20 != 0) return;
        State state = state(match);
        for (ServerPlayer player : match.alivePlayers()) {
            if (UhcCrafting.kind(player.getMainHandItem()).equals("anduril")) { player.addEffect(effect(MobEffects.SPEED, 2, 0)); player.addEffect(effect(MobEffects.RESISTANCE, 2, 0)); }
            if (UhcCrafting.kind(player.getMainHandItem()).equals("miners_blessing")) player.addEffect(effect(MobEffects.SATURATION, 2, 2));
            if (UhcCrafting.kind(player.getItemBySlot(EquipmentSlot.CHEST)).equals("barbarian_chestplate")) { player.addEffect(effect(MobEffects.STRENGTH, 2, 0)); player.addEffect(effect(MobEffects.RESISTANCE, 2, 0)); }
            int[] timers = state.timers.get(player.getUUID());
            if (timers != null && timers[2] != 0) {
                int elapsed = tick - timers[2];
                if (elapsed >= 1500 && elapsed < 1800) player.addEffect(effect(MobEffects.SPEED, (1800 - elapsed) / 20, 2));
                else if (elapsed >= 900 && elapsed < 1500) player.addEffect(effect(MobEffects.SPEED, (1500 - elapsed) / 20, 1));
                if (elapsed >= 1800) timers[2] = 0;
            }
        }
    }
    private static boolean enemies(Match match, ServerPlayer attacker, ServerPlayer other) { return other != attacker && match.isActiveParticipant(other.getUUID()) && match.teamOf(attacker.getUUID()).orElse(null) != match.teamOf(other.getUUID()).orElse(null); }
    public static void hit(LivingEntity victim, DamageSource source) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        Match match = UhcProgression.match(attacker); if (match == null) return;
        if (victim instanceof ServerPlayer player && !enemies(match, attacker, player)) return;
        if (UhcCrafting.kind(attacker.getItemBySlot(EquipmentSlot.HEAD)).equals("exodus")) attacker.addEffect(effect(MobEffects.REGENERATION, 2, 0));
        if (source.getDirectEntity() != attacker) return;
        String kind = UhcCrafting.kind(attacker.getMainHandItem());
        if (kind.equals("deaths_scythe")) { float removed = victim.getHealth() * 0.2f; victim.hurtServer(attacker.level(), new DamageSource(victim.damageSources().genericKill().typeHolder(), attacker), removed); attacker.heal(removed * 0.25f); return; }
        int index = kind.equals("axe_of_perun") ? 0 : kind.equals("excalibur") ? 1 : -1; if (index < 0) return;
        int[] timers = state(match).timers.computeIfAbsent(attacker.getUUID(), key -> new int[] {-1000, -1000, 0});
        int tick = match.activeTicks(); if (tick - timers[index] < (index == 0 ? 80 : 100)) return; timers[index] = tick;
        attacker.level().sendParticles(index == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1, victim.getZ(), 8, 0.3, 0.3, 0.3, 0);
        if (index == 0) victim.hurtServer(attacker.level(), new DamageSource(victim.damageSources().genericKill().typeHolder(), attacker), 4);
        else for (ServerPlayer other : match.alivePlayers()) if (enemies(match, attacker, other) && other.level() == victim.level() && other.distanceToSqr(victim) <= 9) other.hurtServer(attacker.level(), new DamageSource(other.damageSources().genericKill().typeHolder(), attacker), 4);
    }
    public static void killed(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (UhcCrafting.kind(stack).equals("bloodlust")) {
            int kills = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("bloodlust_kills", 0) + 1;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("bloodlust_kills", kills));
            int level = kills >= 10 ? 5 : kills >= 6 ? 4 : kills >= 3 ? 3 : 2; UhcCrafting.enchant(player, stack, Enchantments.SHARPNESS, level);
        }
        if (UhcCrafting.kind(player.getItemBySlot(EquipmentSlot.LEGS)).equals("warlock_pants")) player.heal(2);
    }
    public static void mined(ServerPlayer player, ItemStack tool, int previousDamage) {
        if (UhcProgression.match(player) == null || !UhcCrafting.kind(tool).equals("miners_blessing")) return;
        int used = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("miner_used", 0);
        int total = used + Math.max(0, tool.getDamageValue() - previousDamage);
        if (total == used) return;
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putInt("miner_used", total));
        if (total / 100 > used / 100) player.addEffect(effect(MobEffects.REGENERATION, 5, 0));
        if (total / 250 > used / 250) { UhcCrafting.enchant(player, tool, Enchantments.SHARPNESS, total / 250); UhcCrafting.enchant(player, tool, Enchantments.EFFICIENCY, total / 250); }
    }
    public static void arrowTick(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof ServerPlayer owner)) return;
        Match match = UhcProgression.match(owner);
        if (match == null) return;
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !UhcCrafting.kind(weapon).equals("artemis_bow")
                || arrow.getDeltaMovement().lengthSqr() < 0.01) return;
        State state = state(match); UUID target = state.arrows.get(arrow.getUUID());
        if (target == null) {
            target = NO_TARGET;
            // Homing is confirmed, but its probability/range/turn rate are not published: local policy.
            if (owner.getRandom().nextInt(4) == 0) { double best = 40 * 40; for (ServerPlayer other : match.alivePlayers()) if (enemies(match, owner, other) && other.level() == arrow.level()) { double distance = arrow.distanceToSqr(other); if (distance < best && other.position().subtract(arrow.position()).normalize().dot(arrow.getDeltaMovement().normalize()) > 0.5) { best = distance; target = other.getUUID(); } } }
            state.arrows.put(arrow.getUUID(), target);
        }
        if (target.equals(NO_TARGET)) return;
        ServerPlayer player = match.server().getPlayerList().getPlayer(target); if (player == null || !enemies(match, owner, player)) return;
        Vec3 velocity = arrow.getDeltaMovement(); Vec3 toward = player.position().add(0, player.getBbHeight() / 2, 0).subtract(arrow.position()).normalize();
        arrow.setDeltaMovement(velocity.normalize().scale(0.8).add(toward.scale(0.2)).normalize().scale(velocity.length()));
    }
    public static void arrowRemoved(AbstractArrow arrow) { for (State state : STATES.values()) state.arrows.remove(arrow.getUUID()); }

    public static boolean splash(ServerLevel level, LivingEntity owner, ItemStack potion, Vec3 center) {
        String kind = UhcCrafting.kind(potion); if (!kind.equals("potion_of_vitality") && !kind.equals("flask_of_cleansing")) return false;
        if (!(owner instanceof ServerPlayer thrower) || UhcProgression.match(thrower) == null) return true;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center.add(-4, -2, -4), center.add(4, 2, 4)))) {
            if (target.position().distanceToSqr(center) >= 16) continue;
            if (kind.equals("flask_of_cleansing")) { target.removeAllEffects(); target.addEffect(effect(MobEffects.WEAKNESS, 5, 0)); }
            else if (target == owner) { target.addEffect(UhcEffects.potion(thrower, effect(MobEffects.SPEED, 12, 1))); target.addEffect(UhcEffects.potion(thrower, effect(MobEffects.REGENERATION, 8, 1))); }
            else { target.addEffect(UhcEffects.potion(thrower, effect(MobEffects.WEAKNESS, 12, 1))); target.addEffect(UhcEffects.potion(thrower, effect(MobEffects.WITHER, 6, 1))); }
        }
        return true;
    }
    public static boolean ambrosiaEligible(ItemStack potion) { return potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects(); }
    public static ItemStack ambrosia(ItemStack potion) {
        if (!potion.has(DataComponents.POTION_CONTENTS)) return ItemStack.EMPTY;
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) effects.add(new MobEffectInstance(effect.getEffect(), effect.isInfiniteDuration() ? 1200 : Math.min(1200, effect.getDuration()), 2));
        if (effects.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = potion.copy(); result.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), effects, Optional.empty())); return result;
    }

    public static InteractionResult use(ServerPlayer player, ItemStack stack) {
        String kind = UhcCrafting.kind(stack); Match match = UhcProgression.match(player);
        if (match == null) return InteractionResult.PASS;
        if (kind.equals("modular_bow")) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            int mode = (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("modular_mode", 0) + 1) % 3;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("modular_mode", mode));
            EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(holder -> holder.is(Enchantments.POWER) || holder.is(Enchantments.PUNCH) || holder.is(Enchantments.FLAME)));
            UhcCrafting.enchant(player, stack, mode == 0 ? Enchantments.POWER : mode == 1 ? Enchantments.PUNCH : Enchantments.FLAME, mode == 0 ? 3 : mode == 1 ? 2 : 1);
            player.sendSystemMessage(Component.literal("Modular bow: " + (mode == 0 ? "Power III" : mode == 1 ? "Punch II" : "Flame I"))); return InteractionResult.SUCCESS;
        }
        if (kind.equals("expert_seal")) {
            for (ItemStack item : player.getInventory().getNonEquipmentItems()) seal(item);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) seal(player.getItemBySlot(slot));
        } else if (kind.equals("essence_of_yggdrasil")) {
            var team = match.teamOf(player.getUUID()).orElseThrow();
            for (ServerPlayer teammate : match.alivePlayers()) if (team.members().contains(teammate.getUUID())) teammate.giveExperienceLevels(teammate == player ? team.members().size() == 1 ? 30 : 15 : 8);
        } else if (kind.equals("chest_of_fate")) {
            // Original risk amount/chance is unpublished: local 50% chance of 20 true damage.
            if (player.getRandom().nextBoolean()) { player.addEffect(effect(MobEffects.ABSORPTION, 105, 4)); player.addEffect(effect(MobEffects.SPEED, 45, 0)); state(match).timers.computeIfAbsent(player.getUUID(), key -> new int[] {-1000, -1000, 0})[2] = Math.max(1, match.activeTicks()); }
            else player.hurtServer(player.level(), player.damageSources().genericKill(), 20);
        } else if (kind.equals("dice_of_god")) {
            int used = player.level().getGameRules().get(UhcProgression.NO_DUPLICATE_CRAFTS) ? state(match).dice.getOrDefault(player.getUUID(), 0) : 0;
            if (used == 63) used = 0; int roll = player.getRandom().nextInt(6 - Integer.bitCount(used)), index = 0;
            while ((used & 1 << index) != 0 || roll-- > 0) index++;
            state(match).dice.put(player.getUUID(), used | 1 << index);
            String id = DICE[index]; UhcCrafting.Recipe recipe = UhcCrafting.recipes().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
            ItemStack reward = UhcCrafting.output(player, recipe); if (!player.getInventory().add(reward)) player.drop(reward, false);
        } else if (kind.equals("the_mark")) {
            ServerPlayer nearest = null; double best = Double.MAX_VALUE;
            for (ServerPlayer other : match.alivePlayers()) if (enemies(match, player, other) && other.level() == player.level() && player.distanceToSqr(other) < best) { best = player.distanceToSqr(other); nearest = other; }
            if (nearest == null) return InteractionResult.FAIL; nearest.addEffect(effect(MobEffects.GLOWING, 30, 0)); player.sendSystemMessage(Component.literal("Marked " + nearest.getScoreboardName() + "."));
        } else if (kind.equals("fates_call")) {
            BlockPos pos = player.blockPosition().relative(player.getDirection());
            if (!player.level().getBlockState(pos).canBeReplaced() || !MatchManager.allowPlace(player, pos, Blocks.CHEST.defaultBlockState())) return InteractionResult.FAIL;
            player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            if (player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) { chest.setItem(0, new ItemStack(GOLDEN_APPLE, 2)); chest.setItem(1, new ItemStack(GOLD_INGOT, 8)); chest.setItem(2, new ItemStack(DIAMOND)); chest.setItem(3, new ItemStack(ARROW, 32)); chest.setChanged(); }
        } else if (kind.equals("kit_wolf")) {
            var wolf = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.SPAWN_ITEM_USE); if (wolf == null) return InteractionResult.FAIL;
            wolf.tame(player); wolf.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0); player.level().addFreshEntity(wolf);
        } else if (kind.equals("daredevil") || kind.equals("kit_horse")) {
            var horse = EntityTypes.HORSE.create(player.level(), EntitySpawnReason.SPAWN_ITEM_USE); if (horse == null) return InteractionResult.FAIL;
            horse.setTamed(true); horse.setOwner(player); horse.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
            if (kind.equals("daredevil")) { horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3375 * 0.8); horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50); horse.setHealth(50); horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(SADDLE)); }
            else { horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(15); horse.setHealth(15); }
            horse.addTag("brainage_" + kind); player.level().addFreshEntity(horse);
        } else return InteractionResult.PASS;
        stack.shrink(1); return InteractionResult.SUCCESS;
    }
    private static void seal(ItemStack stack) {
        if (stack.isEmpty()) return;
        EnchantmentHelper.updateEnchantments(stack, mutable -> { for (var enchantment : mutable.keySet()) mutable.set(enchantment, mutable.getLevel(enchantment) + 1); });
    }
}
