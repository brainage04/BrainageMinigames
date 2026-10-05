package io.github.brainage04.brainage_minigames.game;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import io.github.brainage04.brainage_minigames.mixin.AttributeModifiersAccess;

/** Event-time 1.8 values; registry definitions and custom kit modifiers are not mutated. */
public final class CombatBalance {
    private static final Identifier STRENGTH = Identifier.withDefaultNamespace("effect.strength");
    private static final Identifier WEAKNESS = Identifier.withDefaultNamespace("effect.weakness");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    // The component supplies vanilla client's use input/pose, not modern shield damage processing.
    private static final BlocksAttacks SWORD_BLOCK = new BlocksAttacks(0, 0, List.of(),
            new BlocksAttacks.ItemDamageFunction(0, 0, 0), Optional.empty(), Optional.empty(), Optional.empty());

    private CombatBalance() {}

    public static double weaponDelta(ItemStack stack) {
        if (stack.is(Items.WOODEN_SWORD) || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.STONE_SWORD)
                || stack.is(Items.IRON_SWORD) || stack.is(Items.DIAMOND_SWORD)) return 1;
        if (stack.is(Items.WOODEN_AXE) || stack.is(Items.GOLDEN_AXE) || stack.is(Items.IRON_AXE)) return -3;
        if (stack.is(Items.STONE_AXE)) return -4;
        if (stack.is(Items.DIAMOND_AXE)) return -2;
        if (stack.is(Items.WOODEN_SHOVEL) || stack.is(Items.GOLDEN_SHOVEL) || stack.is(Items.STONE_SHOVEL)
                || stack.is(Items.IRON_SHOVEL) || stack.is(Items.DIAMOND_SHOVEL)) return -0.5;
        return 0;
    }

    /** Rebuild only affected contributions before clamping, preserving all other modifiers. */
    public static double attackDamage(LivingEntity entity, AttributeInstance attribute) {
        double base = attribute.getBaseValue();
        var modifiers = ((AttributeModifiersAccess) attribute).brainage_minigames$modifiers().values();
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    && !modifier.id().equals(STRENGTH) && !modifier.id().equals(WEAKNESS)) base += modifier.amount();
        }
        base += weaponDelta(entity.getMainHandItem());
        MobEffectInstance weakness = entity.getEffect(MobEffects.WEAKNESS);
        if (weakness != null) base -= 0.5 * (weakness.getAmplifier() + 1);
        double damage = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) damage += base * modifier.amount();
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) damage *= 1 + modifier.amount();
        }
        MobEffectInstance strength = entity.getEffect(MobEffects.STRENGTH);
        if (strength != null) damage *= strengthMultiplier(strength.getAmplifier() + 1);
        return attribute.getAttribute().value().sanitizeValue(damage);
    }

    public static double strengthMultiplier(int level) { return 1 + 1.3 * level; }
    public static float armorReduction(float armor) { return Mth.clamp(armor, 0, 20) * 0.04F; }
    public static float blockedDamage(float damage) { return (damage + 1) * 0.5F; }
    public static int instantHealing(int level) { return 4 << Math.max(0, level - 1); }
    public static int instantHarming(int level) { return 6 << Math.max(0, level - 1); }
    public static int regenerationInterval(int amplifier) { return Math.max(1, 50 >> amplifier); }
    public static int protectionPoints(int level, double typeModifier) {
        return Mth.floor((6 + level * level) * typeModifier / 3);
    }

    public static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(key)) return enchantments.getLevel(enchantment);
        }
        return 0;
    }

    public static int highestEnchantment(LivingEntity entity, ResourceKey<Enchantment> key) {
        int highest = 0;
        for (EquipmentSlot slot : ARMOR) highest = Math.max(highest, enchantmentLevel(entity.getItemBySlot(slot), key));
        return highest;
    }

    /** One shared random roll for every applicable vanilla protection enchantment. */
    public static float protection(LivingEntity victim, DamageSource source, float vanilla) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return vanilla;
        int sum = 0, replaced = 0;
        for (EquipmentSlot slot : ARMOR) {
            var enchantments = victim.getItemBySlot(slot).getOrDefault(DataComponents.ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                int level = enchantments.getLevel(enchantment);
                double modifier;
                int modern;
                if (enchantment.is(Enchantments.PROTECTION)) { modifier = 0.75; modern = level; }
                else if (enchantment.is(Enchantments.FIRE_PROTECTION) && source.is(DamageTypeTags.IS_FIRE)) { modifier = 1.25; modern = 2 * level; }
                else if (enchantment.is(Enchantments.BLAST_PROTECTION) && source.is(DamageTypeTags.IS_EXPLOSION)) { modifier = 1.5; modern = 2 * level; }
                else if (enchantment.is(Enchantments.PROJECTILE_PROTECTION) && source.is(DamageTypeTags.IS_PROJECTILE)) { modifier = 1.5; modern = 2 * level; }
                else if (enchantment.is(Enchantments.FEATHER_FALLING) && source.is(DamageTypeTags.IS_FALL)) { modifier = 2.5; modern = 3 * level; }
                else continue;
                sum += protectionPoints(level, modifier);
                replaced += modern;
            }
        }
        sum = Math.min(25, sum);
        int rolled = sum == 0 ? 0 : Math.min(20, (sum + 1) / 2 + victim.getRandom().nextInt(sum / 2 + 1));
        return Math.max(0, vanilla - replaced) + rolled;
    }

    public static int fireDuration(int ticks, int level) { return Math.max(0, ticks - Mth.floor(ticks * level * 0.15)); }
    public static double blastImpulse(double impulse, int level) { return Math.max(0, impulse - Mth.floor(impulse * level * 0.15)); }

    public static boolean swordBlocking(LivingEntity entity) {
        return CombatRules.classic(entity) && entity.isUsingItem()
                && entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && entity.getUseItem().is(ItemTags.SWORDS);
    }

    public static void updateSword(ItemStack stack, boolean enabled) {
        BlocksAttacks blocking = stack.get(DataComponents.BLOCKS_ATTACKS);
        if (enabled && stack.is(ItemTags.SWORDS) && blocking == null) stack.set(DataComponents.BLOCKS_ATTACKS, SWORD_BLOCK);
        else if (!enabled && SWORD_BLOCK.equals(blocking)) stack.remove(DataComponents.BLOCKS_ATTACKS);
    }

    public static void updateInventory(ServerPlayer player, boolean enabled) {
        if (!enabled && player.isUsingItem() && SWORD_BLOCK.equals(player.getUseItem().get(DataComponents.BLOCKS_ATTACKS))) player.stopUsingItem();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) updateSword(player.getInventory().getItem(slot), enabled);
        updateSword(player.inventoryMenu.getCarried(), enabled);
        if (player.containerMenu != player.inventoryMenu) {
            updateSword(player.containerMenu.getCarried(), enabled);
            for (var slot : player.containerMenu.slots) updateSword(slot.getItem(), enabled);
        }
    }

    public static MobEffectInstance potionEffect(PotionContents contents, MobEffectInstance effect, float scale) {
        boolean extended = contents.potion().filter(Potions.LONG_REGENERATION::equals).isPresent()
                && effect.is(MobEffects.REGENERATION) && effect.getAmplifier() == 0 && effect.getDuration() == 1800;
        return new MobEffectInstance(effect.getEffect(), effect.mapDuration(d -> Mth.floor((extended ? 2400 : d) * scale)),
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }
}
