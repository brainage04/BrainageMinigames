package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Native application paths with literal 1.8 and vanilla controls, not production formula self-tests. */
public final class CombatBalanceGameTestFunctions {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private CombatBalanceGameTestFunctions() {}

    public static void weapons(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            Item[] weapons = {Items.WOODEN_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD,
                    Items.WOODEN_AXE, Items.GOLDEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE,
                    Items.WOODEN_SHOVEL, Items.GOLDEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL};
            double[] old = {5,5,6,7,8, 4,4,5,6,7, 2,2,3,4,5};
            double[] modern = {4,4,5,6,7, 7,7,9,9,9, 2.5,2.5,3.5,4.5,5.5};
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);
            for (boolean enabled : new boolean[] {false, true}) {
                rule(p, enabled);
                for (int i = 0; i < weapons.length; i++) {
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapons[i]));
                    for (int t = 0; t < 25; t++) p.doTick();
                    near((enabled ? old : modern)[i], p.getAttributeValue(Attributes.ATTACK_DAMAGE), "weapon " + weapons[i] + " rule=" + enabled);
                    reset(f.victim());
                    p.attack(f.victim());
                    near(20 - (enabled ? old : modern)[i], f.victim().getHealth(), "applied weapon " + weapons[i]);
                }
            }
        });
    }

    public static void enchantments(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var zombie = context.spawn(EntityTypes.ZOMBIE, new Vec3(1, 2, 1));
            var spider = context.spawn(EntityTypes.SPIDER, new Vec3(2, 2, 1));
            try {
                var source = p.damageSources().playerAttack(p);
                for (boolean enabled : new boolean[] {false, true}) {
                    rule(p, enabled);
                    for (int rank = 1; rank <= 5; rank++) {
                        ItemStack sword = enchanted(p, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, rank);
                        near(enabled ? rank * 1.25 : rank * 0.5 + 0.5,
                                EnchantmentHelper.modifyDamage(p.level(), sword, f.victim(), source, 0), "Sharpness " + rank);
                        sword = enchanted(p, Items.DIAMOND_SWORD, Enchantments.SMITE, rank);
                        near(rank * 2.5, EnchantmentHelper.modifyDamage(p.level(), sword, zombie, source, 0), "Smite " + rank);
                        near(0, EnchantmentHelper.modifyDamage(p.level(), sword, spider, source, 0), "Smite predicate");
                        sword = enchanted(p, Items.DIAMOND_SWORD, Enchantments.BANE_OF_ARTHROPODS, rank);
                        near(rank * 2.5, EnchantmentHelper.modifyDamage(p.level(), sword, spider, source, 0), "Bane damage");
                        spider.removeAllEffects();
                        long seed = 110 + rank;
                        spider.getRandom().setSeed(seed);
                        EnchantmentHelper.doPostAttackEffectsWithItemSource(p.level(), spider, source, sword);
                        var slow = spider.getEffect(MobEffects.SLOWNESS);
                        check(slow != null && slow.getAmplifier() == 3, "Bane Slowness IV missing");
                        if (enabled) near(20 + RandomSource.create(seed).nextInt(10 * rank), slow.getDuration(), "discrete Bane duration");
                        else check(slow.getDuration() >= 30 && slow.getDuration() <= 20 + 10 * rank, "vanilla Bane duration");
                    }
                }
            } finally { zombie.discard(); spider.discard(); }
        });
    }

    public static void strengthWeaknessCritical(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);
            p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(p, Items.IRON_SWORD, Enchantments.SHARPNESS, 2));
            p.doTick();
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 0));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            rule(p, false);
            near(5, p.getAttributeValue(Attributes.ATTACK_DAMAGE), "vanilla combined effects");
            rule(p, true);
            near(6.5 * 2.3, p.getAttributeValue(Attributes.ATTACK_DAMAGE), "legacy combined effects");
            p.removeEffect(MobEffects.WEAKNESS);
            p.removeEffect(MobEffects.STRENGTH);
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
            near(7 * 3.6, p.getAttributeValue(Attributes.ATTACK_DAMAGE), "Strength II");
            p.removeAllEffects();
            p.setOnGround(false);
            p.fallDistance = 1;
            p.setSprinting(true);
            reset(f.victim());
            p.attack(f.victim());
            near(20 - (7 * 1.5 + 2.5), f.victim().getHealth(), "critical ordering excludes Sharpness from multiplier");
            rule(p, false);
            near(6, p.getAttributeValue(Attributes.ATTACK_DAMAGE), "disable restored attribute damage without rewriting effects");
        });
    }

    public static void armorAndDurability(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var v = f.victim();
            var source = v.damageSources().playerAttack(f.attacker());
            for (boolean enabled : new boolean[] {false, true}) {
                rule(v, enabled);
                for (int toughness : new int[] {0, 8, 12}) {
                    reset(v);
                    v.getAttribute(Attributes.ARMOR).setBaseValue(20);
                    v.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(toughness);
                    v.hurtServer(v.level(), source, 10);
                    double effective = Math.max(4, 20 - 10.0 / (2 + toughness / 4.0));
                    near(20 - 10 * (1 - (enabled ? 0.8 : effective / 25)), v.getHealth(), "armour/toughness");
                }
                ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
                v.setItemSlot(EquipmentSlot.CHEST, chest);
                v.doTick();
                reset(v);
                v.hurtServer(v.level(), source, 8);
                near(2, chest.getDamageValue(), "quarter-damage armour wear");
                chest.setDamageValue(chest.getMaxDamage() - 1);
                chest.hurtAndBreak(1, v, EquipmentSlot.CHEST);
                check(chest.isEmpty() != enabled, "break threshold must differ at exact max durability");
                if (enabled) {
                    near(chest.getMaxDamage(), chest.getDamageValue(), "stored max durability");
                    chest.hurtAndBreak(1, v, EquipmentSlot.CHEST);
                    check(chest.isEmpty(), "legacy item survived exceeding max durability");
                }
                v.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                v.doTick();
            }
        });
    }

    public static void protection(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var v = f.victim();
            var arrow = new TestArrow(f.attacker(), new ItemStack(Items.BOW));
            var sources = new net.minecraft.world.damagesource.DamageSource[] {v.damageSources().playerAttack(f.attacker()), v.damageSources().onFire(),
                    v.damageSources().explosion(f.attacker(), f.attacker()), v.damageSources().arrow(arrow, f.attacker()), v.damageSources().fall()};
            var keys = List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.FEATHER_FALLING);
            int[][] points = {{1,2,3,5}, {2,4,6,9}, {3,5,7,11}, {3,5,7,11}, {5,8,12,18}};
            int[] modern = {1,2,2,2,3};
            for (boolean enabled : new boolean[] {false, true}) {
                rule(v, enabled);
                for (int kind = 0; kind < keys.size(); kind++) {
                    for (int rank = 1; rank <= 4; rank++) {
                        for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, ItemStack.EMPTY);
                        v.setItemSlot(EquipmentSlot.FEET, enchanted(v, Items.DIAMOND_BOOTS, keys.get(kind), rank));
                        long seed = 800 + kind * 10 + rank;
                        v.getRandom().setSeed(seed);
                        int sum = points[kind][rank - 1];
                        int expected = enabled ? Math.min(20, (sum + 1) / 2 + RandomSource.create(seed).nextInt(sum / 2 + 1)) : modern[kind] * rank;
                        near(expected, EnchantmentHelper.getDamageProtection(v.level(), v, sources[kind]), "protection kind=" + kind + " rank=" + rank);
                    }
                }
                for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, enchanted(v, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 4));
                long seed = 999;
                v.getRandom().setSeed(seed);
                near(enabled ? 10 + RandomSource.create(seed).nextInt(11) : 16,
                        EnchantmentHelper.getDamageProtection(v.level(), v, sources[0]), "single full-set EPF roll");
                near(0, EnchantmentHelper.getDamageProtection(v.level(), v, v.damageSources().genericKill()), "protection bypass predicate");
            }
            arrow.discard();
        });
    }

    public static void fireProtectionAndAspect(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var v = f.victim();
            for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, enchanted(v, Items.DIAMOND_BOOTS, Enchantments.FIRE_PROTECTION, 2));
            v.doTick();
            for (boolean enabled : new boolean[] {false, true}) {
                rule(v, enabled);
                v.clearFire();
                v.igniteForTicks(101);
                near(enabled ? 71 : 0, v.getRemainingFireTicks(), "highest Fire Protection and floor");
            }
            for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, ItemStack.EMPTY);
            v.doTick();
            f.attacker().setItemInHand(InteractionHand.MAIN_HAND, enchanted(v, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 2));
            f.attacker().doTick();
            for (boolean enabled : new boolean[] {false, true}) {
                rule(v, enabled);
                for (int t = 0; t < 25; t++) f.attacker().doTick();
                reset(v);
                v.clearFire();
                f.attacker().attack(v);
                near(160, v.getRemainingFireTicks(), "Fire Aspect II duration");
                v.clearFire();
                f.attacker().attack(v);
                near(0, v.getRemainingFireTicks(), "failed attack left pre-hit fire");
            }
        });
    }

    public static void applesAndHead(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            ItemStack existingApple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            var headRecipe = UhcCrafting.recipes().stream().filter(r -> r.id().equals("golden_head")).findFirst().orElseThrow();
            ItemStack existingHead = UhcCrafting.output(p, headRecipe);
            for (boolean enabled : new boolean[] {false, true}) {
                rule(p, enabled);
                p.removeAllEffects();
                existingApple.copy().finishUsingItem(p.level(), p);
                effect(p, MobEffects.REGENERATION, enabled ? 600 : 400, enabled ? 4 : 1);
                effect(p, MobEffects.ABSORPTION, 2400, enabled ? 0 : 3);
                near(enabled ? 4 : 16, p.getAbsorptionAmount(), "enchanted apple absorption");
                effect(p, MobEffects.RESISTANCE, 6000, 0);
                effect(p, MobEffects.FIRE_RESISTANCE, 6000, 0);
                p.removeAllEffects();
                new ItemStack(Items.GOLDEN_APPLE).finishUsingItem(p.level(), p);
                effect(p, MobEffects.REGENERATION, 100, 1);
                effect(p, MobEffects.ABSORPTION, 2400, 0);
                near(4, p.getAbsorptionAmount(), "ordinary apple absorption");
                p.removeAllEffects();
                existingHead.copy().finishUsingItem(p.level(), p);
                effect(p, MobEffects.REGENERATION, 200, 1);
                effect(p, MobEffects.ABSORPTION, 2400, 0);
                near(4, existingHead.get(DataComponents.FOOD).nutrition(), "golden head food");
                near(9.6, existingHead.get(DataComponents.FOOD).saturation(), "golden head saturation");
            }
        });
    }

    public static void recipeAndCooldown(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.GOLD_BLOCK),
                    new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.APPLE),new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.GOLD_BLOCK),new ItemStack(Items.GOLD_BLOCK)));
            var menu = new CraftingMenu(72, p.getInventory(), ContainerLevelAccess.NULL);
            p.containerMenu = menu;
            for (int i = 0; i < 9; i++) menu.getSlot(i + 1).set(input.getItem(i).copy());
            rule(p, false);
            check(p.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, p.level()).isEmpty(), "apple recipe matched with rule off");
            rule(p, true);
            check(p.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, p.level()).orElseThrow().value().assemble(input).is(Items.ENCHANTED_GOLDEN_APPLE), "registered code recipe missing");
            p.doTick();
            check(menu.getSlot(0).getItem().is(Items.ENCHANTED_GOLDEN_APPLE), "open crafting result did not refresh on enable");
            rule(p, false);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
            check(p.getInventory().countItem(Items.ENCHANTED_GOLDEN_APPLE) == 0, "stale output shift-click escaped after disable");
            check(menu.getSlot(5).getItem().is(Items.APPLE), "stale output consumed ingredients");
            p.doTick();
            check(menu.getSlot(0).getItem().isEmpty(), "disabled crafting result remained visible");
            p.containerMenu = p.inventoryMenu;
            ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
            rule(p, true);
            pearl.get(DataComponents.USE_COOLDOWN).apply(pearl, p);
            check(!p.getCooldowns().isOnCooldown(pearl), "legacy pearl received a cooldown");
            rule(p, false);
            pearl.get(DataComponents.USE_COOLDOWN).apply(pearl, p);
            check(p.getCooldowns().isOnCooldown(pearl), "vanilla pearl lacked cooldown");
            rule(p, true);
            check(p.getCooldowns().isOnCooldown(pearl), "switch retroactively rewrote existing cooldown");
            for (int t = 0; t < 20; t++) p.getCooldowns().tick();
            check(!p.getCooldowns().isOnCooldown(pearl), "pearl cooldown did not expire at 20 ticks");
        });
    }

    public static void bowAndPunch(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var v = f.victim();
            near(0, BowItem.getPowerForTime(0), "zero bow draw");
            near((0.25 + 1) / 3, BowItem.getPowerForTime(10), "half bow draw");
            near(1, BowItem.getPowerForTime(20), "full bow draw");
            for (boolean enabled : new boolean[] {false, true}) {
                rule(p, enabled);
                ItemStack drawBow = new ItemStack(Items.BOW);
                p.setItemInHand(InteractionHand.MAIN_HAND, drawBow);
                p.getInventory().setItem(1, new ItemStack(Items.ARROW, 2));
                check(!((BowItem) Items.BOW).releaseUsing(drawBow, p.level(), p, drawBow.getUseDuration(p) - 1), "bow fired below 0.1 charge");
                near(2, p.getInventory().getItem(1).getCount(), "short draw consumed ammunition");
                p.setKnownMovement(new Vec3(0.4,0.2,-0.3));
                p.setOnGround(false);
                var launched = new TestArrow(p, new ItemStack(Items.BOW));
                launched.shootFromRotation(p, 0, 0, 0, 3, 0);
                near(enabled ? 0 : 0.4, launched.getDeltaMovement().x, "bow inherited horizontal motion");
                near(enabled ? 0 : 0.2, launched.getDeltaMovement().y, "bow inherited vertical motion");
                near(enabled ? 3 : 2.7, launched.getDeltaMovement().z, "bow launch speed");
                long seed = 1849;
                launched.getRandom().setSeed(seed);
                var random = RandomSource.create(seed);
                var movement = launched.getMovementToShoot(0,0,1,3,1);
                near((enabled ? random.nextGaussian() * .0075 : random.triangle(0,.0172275)) * 3, movement.x, "bow spread x");
                near((enabled ? random.nextGaussian() * .0075 : random.triangle(0,.0172275)) * 3, movement.y, "bow spread y");
                near((1 + (enabled ? random.nextGaussian() * .0075 : random.triangle(0,.0172275))) * 3, movement.z, "bow spread z");
                launched.discard();
                for (int rank = 0; rank <= 5; rank++) {
                    ItemStack bow = rank == 0 ? new ItemStack(Items.BOW) : enchanted(p, Items.BOW, Enchantments.POWER, rank);
                    var arrow = new TestArrow(p, bow);
                    arrow.setDeltaMovement(3,0,0);
                    reset(v);
                    arrow.impact(v);
                    near(20 - Math.ceil(3 * (rank == 0 ? 2 : 2 + .5 * (rank + 1))), v.getHealth(), "Power coefficient " + rank);
                    arrow.discard();
                }
                for (int critSeed = 1; critSeed <= 8; critSeed++) {
                    var arrow = new TestArrow(p, new ItemStack(Items.BOW));
                    arrow.setDeltaMovement(3,0,0);
                    arrow.setCritArrow(true);
                    arrow.getRandom().setSeed(critSeed);
                    reset(v);
                    arrow.impact(v);
                    near(20 - 6 - RandomSource.create(critSeed).nextInt(5), v.getHealth(), "critical-arrow additive random damage");
                    arrow.discard();
                }
                var punched = new TestArrow(p, enchanted(p, Items.BOW, Enchantments.PUNCH, 2));
                punched.setDeltaMovement(3,0,0);
                v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                v.setDeltaMovement(.2,-.2,.3);
                punched.punch(v);
                near(enabled ? 1.4 : .2, v.getDeltaMovement().x, "Punch resistance independence");
                near(enabled ? -.1 : -.2, v.getDeltaMovement().y, "Punch vertical addition");
                near(.3, v.getDeltaMovement().z, "Punch preserves existing motion");
                punched.discard();
            }
        });
    }

    public static void exhaustion(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            for (boolean enabled : new boolean[] {false, true}) {
                rule(p, enabled);
                p.setSprinting(false);
                double before = exhaustion(p);
                p.jumpFromGround();
                near(enabled ? .2 : .05, exhaustion(p) - before, "jump exhaustion");
                p.setSprinting(true);
                before = exhaustion(p);
                p.jumpFromGround();
                near(enabled ? .8 : .2, exhaustion(p) - before, "sprint-jump exhaustion");
                p.setOnGround(true);
                p.setSprinting(false);
                before = exhaustion(p);
                p.checkMovementStatistics(1,0,0);
                near(enabled ? .01 : 0, exhaustion(p) - before, "walk exhaustion/metre");
                p.setShiftKeyDown(true);
                before = exhaustion(p);
                p.checkMovementStatistics(1,0,0);
                near(enabled ? .01 : 0, exhaustion(p) - before, "sneak exhaustion/metre");
                p.setShiftKeyDown(false);
                p.setSprinting(true);
                before = exhaustion(p);
                p.checkMovementStatistics(1,0,0);
                near(.1, exhaustion(p) - before, "sprint exhaustion/metre");
                p.setSwimming(true);
                before = exhaustion(p);
                p.checkMovementStatistics(1,0,0);
                near(enabled ? .015 : .01, exhaustion(p) - before, "swim exhaustion/metre");
                p.setSwimming(false);
                p.setSprinting(false);
                for (int t = 0; t < 25; t++) p.doTick();
                reset(f.victim());
                before = exhaustion(p);
                p.attack(f.victim());
                near(enabled ? .3 : .1, exhaustion(p) - before, "attack exhaustion");
                before = exhaustion(p);
                p.invulnerableTime = 0;
                p.hurtServer(p.level(), p.damageSources().playerAttack(f.victim()), 1);
                near(enabled ? .3 : .1, exhaustion(p) - before, "damage exhaustion");
                before = exhaustion(p);
                Blocks.STONE.playerDestroy(p.level(), p, p.blockPosition(), Blocks.STONE.defaultBlockState(), null, new ItemStack(Items.IRON_PICKAXE));
                near(enabled ? .025 : .005, exhaustion(p) - before, "block-break exhaustion");
            }
        });
    }

    public static void naturalRegeneration(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var rules = p.level().getGameRules();
            boolean previous = rules.get(GameRules.NATURAL_HEALTH_REGENERATION);
            try {
                rules.set(GameRules.NATURAL_HEALTH_REGENERATION, true, p.level().getServer());
                for (boolean enabled : new boolean[] {false, true}) {
                    rule(p, enabled);
                    FoodData food = new FoodData();
                    p.setHealth(10);
                    food.setSaturation(6);
                    for (int t = 0; t < 10; t++) food.tick(p);
                    near(enabled ? 10 : 11, p.getHealth(), "fast saturation regeneration");
                    if (enabled) {
                        for (int t = 10; t < 79; t++) food.tick(p);
                        near(10, p.getHealth(), "legacy regeneration before 80 ticks");
                        food.tick(p);
                        near(11, p.getHealth(), "legacy regeneration at 80 ticks");
                        near(3, exhaustion(food), "legacy regeneration exhaustion");
                    }
                    food = new FoodData();
                    food.setSaturation(0);
                    food.setFoodLevel(18);
                    p.setHealth(10);
                    for (int t = 0; t < 80; t++) food.tick(p);
                    near(11, p.getHealth(), "slow regeneration amount");
                    near(enabled ? 3 : 6, exhaustion(food), "slow regeneration exhaustion");
                    rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, p.level().getServer());
                    p.setHealth(10);
                    for (int t = 0; t < 80; t++) food.tick(p);
                    near(10, p.getHealth(), "disabled natural regeneration");
                    rules.set(GameRules.NATURAL_HEALTH_REGENERATION, true, p.level().getServer());
                }
            } finally { rules.set(GameRules.NATURAL_HEALTH_REGENERATION, previous, p.level().getServer()); }
        });
    }

    public static void potionDurationsAndHealing(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var zombie = context.spawn(EntityTypes.ZOMBIE, new Vec3(1,2,1));
            try {
                for (boolean enabled : new boolean[] {false, true}) {
                    rule(p, enabled);
                    var potions = List.of(Potions.REGENERATION, Potions.STRONG_REGENERATION, Potions.LONG_REGENERATION);
                    int[] durations = {900,450,enabled ? 2400 : 1800};
                    for (int i = 0; i < potions.size(); i++) {
                        p.removeAllEffects();
                        new PotionContents(potions.get(i)).applyToLivingEntity(p,1);
                        effect(p, MobEffects.REGENERATION, durations[i], i == 1 ? 1 : 0);
                    }
                    p.removeAllEffects();
                    for (int amp : new int[] {0,1,4}) {
                        int interval = amp == 0 ? 50 : amp == 1 ? 25 : 3;
                        check(MobEffects.REGENERATION.value().shouldApplyEffectTickThisTick(interval,amp), "regeneration interval");
                        check(!MobEffects.REGENERATION.value().shouldApplyEffectTickThisTick(interval-1,amp), "regeneration off-interval");
                    }
                    for (int amp = 0; amp < 2; amp++) {
                        p.setHealth(1);
                        MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(p.level(),p,p,p,amp,1);
                        near(1 + (4 << amp), p.getHealth(), "instant healing");
                        reset(p);
                        MobEffects.INSTANT_DAMAGE.value().applyInstantaneousEffect(p.level(),p,p,p,amp,1);
                        near(20 - (6 << amp), p.getHealth(), "instant harming");
                        zombie.setHealth(1);
                        zombie.invulnerableTime = 0;
                        MobEffects.INSTANT_DAMAGE.value().applyInstantaneousEffect(p.level(),p,p,zombie,amp,1);
                        near(1 + (4 << amp), zombie.getHealth(), "undead harming reversal");
                        zombie.setHealth(20);
                        zombie.invulnerableTime = 0;
                        MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(p.level(),p,p,zombie,amp,1);
                        near(20 - (6 << amp), zombie.getHealth(), "undead healing reversal");
                    }
                }
            } finally { zombie.discard(); }
        });
    }

    public static void splash(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var v = f.victim();
            Vec3 origin = context.absoluteVec(new Vec3(1,2,1));
            p.setPos(origin.add(20,0,0));
            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.LONG_REGENERATION));
            for (boolean enabled : new boolean[] {false, true}) {
                rule(p, enabled);
                var potion = new ThrownSplashPotion(p.level(),p,stack);
                potion.setPos(origin);
                v.setPos(origin.add(2,0,0));
                v.removeAllEffects();
                potion.onHitAsPotion(p.level(),stack,new EntityHitResult(v,origin));
                var regen = v.getEffect(MobEffects.REGENERATION);
                check(regen != null, "direct splash missing");
                if (enabled) near(1800, regen.getDuration(), "direct old splash long-regeneration duration");
                else check(regen.getDuration() < 1800 && regen.getDuration() > 900, "vanilla splash hitbox falloff control");
                v.removeAllEffects();
                potion.onHitAsPotion(p.level(), stack, new BlockHitResult(origin, Direction.UP, net.minecraft.core.BlockPos.containing(origin), false));
                regen = v.getEffect(MobEffects.REGENERATION);
                check(regen != null, "non-direct splash missing");
                if (enabled) near(900, regen.getDuration(), "entity-position falloff after 75% base duration");
                v.removeAllEffects();
                ItemStack instant = new ItemStack(Items.SPLASH_POTION);
                instant.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
                v.setHealth(1);
                potion.onHitAsPotion(p.level(), instant, new EntityHitResult(v, origin));
                if (enabled) near(5, v.getHealth(), "direct instant splash must not get a 75% penalty");
                potion.discard();
            }
        });
    }

    public static void blastAndKnockback(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var v = f.victim();
            for (boolean enabled : new boolean[] {false, true}) {
                rule(v, enabled);
                for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, enchanted(v, Items.DIAMOND_BOOTS, Enchantments.BLAST_PROTECTION, 2));
                v.doTick();
                p.setPos(0, 100, 0);
                v.setPos(2, 100, 0);
                v.setDeltaMovement(Vec3.ZERO);
                var explosion = new ServerExplosion(v.level(), p, null, new ExplosionDamageCalculator() {
                    @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
                }, new Vec3(0,100,0), 4, false, Explosion.BlockInteraction.KEEP);
                explosion.explode();
                // Legacy floor(.75 * .3) is zero: protection does not scale this small impulse.
                near(enabled ? .75 : 0, v.getDeltaMovement().length(), "Blast Protection highest level/floor");
                for (EquipmentSlot slot : ARMOR) v.setItemSlot(slot, ItemStack.EMPTY);
                v.doTick();
                p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(p, Items.IRON_SWORD, Enchantments.KNOCKBACK, 2));
                for (int t = 0; t < 25; t++) p.doTick();
                p.setYRot(-90);
                p.setSprinting(false);
                p.setOnGround(true);
                reset(v);
                v.setOnGround(false);
                p.attack(v);
                near(enabled ? 1.4 : 1.2, v.getDeltaMovement().x, "Knockback II horizontal impulse");
                near(enabled ? .5 : 0, v.getDeltaMovement().y, "Knockback II airborne vertical impulse");
            }
        });
    }

    public static void fireAndLava(GameTestHelper context) {
        Combat18GameTestFunctions.withMatch(context, Minigames.CLASSIC, f -> {
            var p = f.attacker();
            var pos = context.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
            var saved = p.level().getBlockState(pos);
            try {
                for (boolean enabled : new boolean[] {false, true}) {
                    rule(p, enabled);
                    reset(p);
                    p.clearFire();
                    p.lavaHurt();
                    near(16, p.getHealth(), "lava raw damage");
                    p.lavaIgnite();
                    near(300, p.getRemainingFireTicks(), "lava ignition");
                    reset(p);
                    p.clearFire();
                    Blocks.FIRE.defaultBlockState().entityInside(p.level(), pos, p, new InsideBlockEffectApplier() {
                        @Override public void apply(InsideBlockEffectType type) {}
                        @Override public void runBefore(InsideBlockEffectType type, java.util.function.Consumer<Entity> action) { action.accept(p); }
                        @Override public void runAfter(InsideBlockEffectType type, java.util.function.Consumer<Entity> action) { action.accept(p); }
                    }, false);
                    near(19, p.getHealth(), "fire contact raw damage");
                    p.level().setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
                    p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                    p.baseTick();
                    check(p.isInLava(), "lava burning fixture not submerged");
                    reset(p);
                    p.setRemainingFireTicks(20);
                    p.baseTick();
                    near(enabled ? 19 : 20, p.getHealth(), "burning attempt while in lava");
                    p.level().setBlockAndUpdate(pos, saved);
                    p.setPos(0,100,0);
                    p.baseTick();
                    reset(p);
                    p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100,0));
                    p.lavaHurt();
                    near(20, p.getHealth(), "Fire Resistance must prevent lava damage");
                    p.removeAllEffects();
                    p.clearFire();
                }
            } finally { p.level().setBlockAndUpdate(pos, saved); }
        });
    }

    private static void rule(ServerPlayer player, boolean enabled) { player.level().getGameRules().set(CombatRules.COMBAT_1_8, enabled, player.level().getServer()); }
    private static ItemStack enchanted(ServerPlayer player, Item item, ResourceKey<Enchantment> key, int rank) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key),rank);
        return stack;
    }
    private static void reset(LivingEntity entity) { entity.setHealth(20); entity.setAbsorptionAmount(0); entity.invulnerableTime = 0; entity.setDeltaMovement(Vec3.ZERO); }
    private static void effect(LivingEntity entity, Holder<MobEffect> key, int duration, int amplifier) {
        var effect = entity.getEffect(key);
        check(effect != null, "effect missing: " + key);
        near(duration,effect.getDuration(),"effect duration " + key);
        near(amplifier,effect.getAmplifier(),"effect amplifier " + key);
    }
    private static double exhaustion(ServerPlayer player) { return exhaustion(player.getFoodData()); }
    private static double exhaustion(FoodData food) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        food.addAdditionalSaveData(output);
        return output.buildResult().getFloatOr("foodExhaustionLevel",0);
    }
    private static void near(double expected, double actual, String what) { check(Math.abs(expected-actual) < 1E-4, what + ": expected " + expected + ", got " + actual); }
    private static void check(boolean condition, String what) { if (!condition) throw new GameTestAssertException(net.minecraft.network.chat.Component.literal(what),0); }
    private static final class TestArrow extends Arrow {
        private TestArrow(ServerPlayer owner, ItemStack bow) { super(owner.level(),owner,new ItemStack(Items.ARROW),bow); }
        private void impact(LivingEntity victim) { onHitEntity(new EntityHitResult(victim)); }
        private void punch(LivingEntity victim) { doKnockback(victim,damageSources().arrow(this,getOwner())); }
    }
}
