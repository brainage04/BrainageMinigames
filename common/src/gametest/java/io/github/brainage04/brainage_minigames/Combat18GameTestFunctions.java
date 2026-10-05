package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual attacks/projectile impacts on connected participants, on both loaders. */
public final class Combat18GameTestFunctions {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private Combat18GameTestFunctions() {}

    public static void cooldownAndScope(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            ServerPlayer attacker = fixture.attacker();
            ServerPlayer victim = fixture.victim();
            var rules = attacker.level().getGameRules();
            rules.set(CombatRules.COMBAT_1_8, false, attacker.level().getServer());
            attacker.resetAttackStrengthTicker();
            victim.invulnerableTime = 0;
            attacker.attack(victim);
            check(victim.getHealth() > 19, "disabled rule did not preserve a weak modern attack");
            ItemStack chargedWeapon = new ItemStack(Items.STICK);
            chargedWeapon.set(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0F);
            attacker.setItemInHand(InteractionHand.MAIN_HAND, chargedWeapon);
            attacker.doTick();
            attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1);
            attacker.resetAttackStrengthTicker();
            victim.setHealth(20);
            victim.invulnerableTime = 0;
            attacker.connection.handleAttack(new ServerboundAttackPacket(victim.getId()));
            near(20, victim.getHealth(), "disabled rule bypassed the item's minimum attack charge");
            rules.set(CombatRules.COMBAT_1_8, true, attacker.level().getServer());
            victim.setHealth(20);
            victim.invulnerableTime = 0;
            attacker.resetAttackStrengthTicker();
            attacker.connection.handleAttack(new ServerboundAttackPacket(victim.getId()));
            near(16, victim.getHealth(), "first same-tick full-strength swing");
            victim.invulnerableTime = 10;
            attacker.connection.handleAttack(new ServerboundAttackPacket(victim.getId()));
            near(12, victim.getHealth(), "second same-tick full-strength swing");
            attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4);
            attacker.resetAttackStrengthTicker();
            attacker.setGameMode(GameType.SPECTATOR);
            // Match membership alone must not make an actual spectator's attacks stronger.
            near(0.1, attacker.getAttackStrengthScale(0.5F), "spectator's cooldown");
            MatchManager.stop(fixture.match());
            near(0.1, attacker.getAttackStrengthScale(0.5F), "released player's cooldown");
            var outsiderChannels = new ArrayList<EmbeddedChannel>();
            ServerPlayer outsider = player(context, outsiderChannels);
            try {
                outsider.resetAttackStrengthTicker();
                near(0.1, outsider.getAttackStrengthScale(0.5F), "outside player's cooldown");
            } finally {
                disconnect(outsider);
                for (var channel : outsiderChannels) channel.releaseOutbound();
            }
        });
    }

    public static void hitImmunity(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var victim = fixture.victim();
            var source = victim.damageSources().playerAttack(fixture.attacker());
            check(victim.hurtServer(victim.level(), source, 2), "initial hit rejected");
            check(!victim.hurtServer(victim.level(), source, 2), "equal damage bypassed hit immunity");
            near(18, victim.getHealth(), "equal damage immunity");
            victim.setDeltaMovement(Vec3.ZERO);
            check(victim.hurtServer(victim.level(), source, 5), "stronger legacy hit rejected");
            near(15, victim.getHealth(), "stronger hit must apply only damage difference");
            near(0, victim.getDeltaMovement().lengthSqr(), "stronger hit must not repeat base knockback");
            check(victim.invulnerableTime == 20, "stronger hit restarted/shortened immunity");
            victim.invulnerableTime = 11;
            check(!victim.hurtServer(victim.level(), source, 5), "hit landed before ten ticks");
            victim.invulnerableTime = 10;
            check(victim.hurtServer(victim.level(), source, 5), "hit failed at ten ticks");
            near(10, victim.getHealth(), "ten-tick full hit");
        });
    }

    public static void snowballs(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> thrownHits(fixture, false));
    }

    public static void eggs(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> thrownHits(fixture, true));
    }

    private static void thrownHits(Fixture fixture, boolean egg) {
        var attacker = fixture.attacker();
        var victim = fixture.victim();
        var server = attacker.level().getServer();
        server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
        impact(thrown(fixture, egg), victim);
        near(0, victim.getDeltaMovement().lengthSqr(), "disabled projectile knockback");
        server.getGameRules().set(CombatRules.COMBAT_1_8, true, server);
        victim.setOnGround(false);
        victim.setDeltaMovement(0.2, -0.2, 0);
        impact(thrown(fixture, egg), victim);
        near(20, victim.getHealth(), "projectile must not damage health");
        near(0.5, victim.getDeltaMovement().x, "projectile knockback away from thrower");
        near(0.3, victim.getDeltaMovement().y, "legacy airborne knockback");
        victim.setDeltaMovement(Vec3.ZERO);
        impact(thrown(fixture, egg), victim);
        near(0, victim.getDeltaMovement().lengthSqr(), "projectile repeated during immunity");
        victim.invulnerableTime = 10;
        impact(thrown(fixture, egg), victim);
        near(0.4, victim.getDeltaMovement().x, "projectile at immunity boundary");
        near(20, victim.getHealth(), "repeated projectile damaged health");
        server.getGameRules().set(GameRules.PVP, false, server);
        victim.invulnerableTime = 0;
        victim.setDeltaMovement(Vec3.ZERO);
        impact(thrown(fixture, egg), victim);
        near(0, victim.getDeltaMovement().lengthSqr(), "projectile bypassed pvp false");
    }

    private static Projectile thrown(Fixture fixture, boolean egg) {
        // Flight is deliberately opposite to the owner->victim direction: 1.8 uses the owner.
        Projectile projectile = egg
                ? new TestEgg(fixture.attacker())
                : new TestSnowball(fixture.attacker());
        projectile.setDeltaMovement(-1, 0, 0);
        return projectile;
    }

    public static void fishingRod(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var owner = fixture.attacker();
            var victim = fixture.victim();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
            FishingHook hook = new TestFishingHook(owner);
            try {
                impact(hook, victim);
                check(hook.getHookedIn() == victim, "rod failed to hook player");
                hook.setPos(victim.position());
                near(20, victim.getHealth(), "rod inflicted damage");
                near(0.4, victim.getDeltaMovement().x, "rod did not knock player away");
                Vec3 before = victim.getDeltaMovement();
                hook.retrieve(owner.getMainHandItem());
                check(hook.isRemoved() && owner.fishing == null, "rod failed to retract");
                check(victim.getDeltaMovement().x < before.x, "legacy rod retraction did not pull the player");
                FishingHook immune = new TestFishingHook(owner);
                impact(immune, victim);
                check(immune.getHookedIn() == null, "rod hooked during hit immunity");
                immune.discard();
                owner.level().getGameRules().set(CombatRules.COMBAT_1_8, false, owner.level().getServer());
                FishingHook modern = new TestFishingHook(owner);
                modern.setPos(victim.position());
                impact(modern, victim);
                victim.setDeltaMovement(Vec3.ZERO);
                modern.retrieve(owner.getMainHandItem());
                check(victim.getDeltaMovement().x < 0, "disabled rule did not retain modern rod pulling");
            } finally {
                hook.discard();
                if (owner.fishing != null) owner.fishing.discard();
            }
        });
    }

    public static void sweepAndCritical(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var attacker = fixture.attacker();
            var victim = fixture.victim();
            var bystander = context.spawn(EntityTypes.COW, new Vec3(3, 2, 1.5));
            try {
                attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                for (int tick = 0; tick < 25; tick++) attacker.doTick();
                Vec3 origin = context.absoluteVec(new Vec3(1, 2, 1));
                attacker.setPos(origin);
                victim.setPos(origin.add(2, 0, 0));
                attacker.setOnGround(true);
                attacker.setKnownMovement(Vec3.ZERO);
                attacker.setSpeed((float) attacker.getAttributeValue(Attributes.MOVEMENT_SPEED));
                var server = attacker.level().getServer();
                server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
                attacker.attack(victim);
                check(bystander.getHealth() < 10, "modern control did not sweep the nearby cow");
                bystander.setHealth(10);
                bystander.invulnerableTime = 0;
                victim.setHealth(20);
                victim.invulnerableTime = 0;
                server.getGameRules().set(CombatRules.COMBAT_1_8, true, server);
                attacker.attack(victim);
                near(10, bystander.getHealth(), "legacy sword swept a bystander");
                attacker.getInventory().clearContent();
                attacker.doTick();
                attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
                attacker.setSprinting(true);
                attacker.setOnGround(false);
                attacker.fallDistance = 1;
                victim.setHealth(20);
                victim.invulnerableTime = 0;
                attacker.attack(victim);
                near(14, victim.getHealth(), "sprinting fall did not critical hit");
            } finally {
                bystander.discard();
            }
        });
    }

    public static void comboAndBoxing(GameTestHelper context) {
        withMatch(context, Minigames.COMBO, fixture -> {
            var attacker = fixture.attacker();
            var victim = fixture.victim();
            attacker.level().getGameRules().set(CombatRules.COMBAT_1_8, false, attacker.level().getServer());
            attacker.attack(victim);
            near(16, victim.getHealth(), "Combo without gamerule lost full attack strength");
            victim.invulnerableTime = 19;
            attacker.attack(victim);
            near(16, victim.getHealth(), "Combo hit before two ticks");
            attacker.level().getGameRules().set(CombatRules.COMBAT_1_8, true, attacker.level().getServer());
            victim.invulnerableTime = 18;
            attacker.attack(victim);
            near(12, victim.getHealth(), "gamerule overrode Combo's two-tick delay");
        }, () -> withMatch(context, Minigames.BOXING, fixture -> {
            var attacker = fixture.attacker();
            var victim = fixture.victim();
            attacker.attack(victim);
            attacker.attack(victim);
            near(20, victim.getHealth(), "legacy Boxing dealt health damage");
            check(fixture.match().teamOf(attacker.getUUID()).orElseThrow().score() == 1,
                    "Boxing counted hits inside immunity");
            victim.invulnerableTime = 10;
            impact(thrown(fixture, false), victim);
            check(fixture.match().teamOf(attacker.getUUID()).orElseThrow().score() == 1,
                    "Boxing scored a projectile instead of a melee hit");
            victim.invulnerableTime = 10;
            attacker.attack(victim);
            check(fixture.match().teamOf(attacker.getUUID()).orElseThrow().score() == 2,
                    "Boxing failed to score its next full hit");
        }));
    }

    public static void gameRestrictions(GameTestHelper context) {
        withMatch(context, Minigames.BOW_SPLEEF, fixture -> {
            impact(thrown(fixture, false), fixture.victim());
            near(0, fixture.victim().getDeltaMovement().lengthSqr(), "snowball bypassed noncombat game rules");
            FishingHook hook = new TestFishingHook(fixture.attacker());
            try {
                impact(hook, fixture.victim());
                check(hook.getHookedIn() == null, "rod bypassed noncombat game rules");
            } finally {
                hook.discard();
            }
        });
    }

    public static void sprintKnockback(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var attacker = fixture.attacker();
            var victim = fixture.victim();
            attacker.setYRot(-90);
            attacker.setSprinting(true);
            attacker.setDeltaMovement(1, 0, 0);
            // The actual Player.attack sends this velocity to its client then restores server motion.
            // Exercise its public knockback phase directly to inspect the vector before restoration.
            victim.setDeltaMovement(0.4, 0.4, 0);
            victim.hurtMarked = false;
            attacker.causeExtraKnockback(victim, 0.5F, victim.getDeltaMovement(),
                    victim.damageSources().playerAttack(attacker), 4, true);
            near(0.9, victim.getDeltaMovement().x, "sprint knockback must add, not halve existing motion");
            near(0.5, victim.getDeltaMovement().y, "sprint knockback vertical addition");
            near(0.6, attacker.getDeltaMovement().x, "sprint hit slowdown");
            check(!attacker.isSprinting(), "sprint hit failed to reset sprint for W tapping");
            var resistance = victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            var source = victim.damageSources().playerAttack(attacker);
            resistance.setBaseValue(1);
            Vec3 motion = new Vec3(0.2, -0.1, 0.3);
            victim.setDeltaMovement(motion);
            victim.knockback(0.4, -1, 0, source, 0, false);
            check(motion.equals(victim.getDeltaMovement()), "full resistance changed existing motion");
            resistance.setBaseValue(0.5);
            victim.getRandom().setSeed(0);
            boolean applied = false;
            boolean resisted = false;
            for (int hit = 0; hit < 32 && !(applied && resisted); hit++) {
                victim.setDeltaMovement(Vec3.ZERO);
                victim.knockback(0.4, -1, 0, source, 0, false);
                if (victim.getDeltaMovement().x == 0) {
                    resisted = true;
                    check(Vec3.ZERO.equals(victim.getDeltaMovement()), "resisted hit changed motion");
                } else {
                    applied = true;
                    near(0.4, victim.getDeltaMovement().x, "resistance scaled instead of admitting full knockback");
                    near(0.4, victim.getDeltaMovement().y, "admitted knockback lost its full vertical impulse");
                }
            }
            check(applied && resisted, "partial resistance did not exercise both chance outcomes");
        });
    }

    public static void swordBlocking(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var player = fixture.victim();
            var server = player.level().getServer();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
            MatchManager.tick();
            check(player.getOffhandItem().is(Items.GOLDEN_APPLE), "sword blocking displaced offhand food");
            check(player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS), "sword input was not synchronized");
            player.setYRot(-90); // The attacker is behind the blocking player.
            player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            check(player.isUsingItem(), "sword right click did not begin blocking");
            var source = player.damageSources().playerAttack(fixture.attacker());
            player.hurtServer(player.level(), source, 9);
            near(15, player.getHealth(), "immediate rear sword block must deal (9+1)/2");
            check(!player.hurtServer(player.level(), source, 9), "blocking reordered equal-hit immunity");
            player.hurtServer(player.level(), source, 11);
            near(13.5, player.getHealth(), "blocking stronger hit must transform the raw excess (2+1)/2");
            player.invulnerableTime = 0;
            player.getAttribute(Attributes.ARMOR).setBaseValue(10);
            player.hurtServer(player.level(), source, 9);
            near(10.5, player.getHealth(), "blocking must precede the 40% armour reduction");
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.getOffhandItem().use(player.level(), player, InteractionHand.OFF_HAND);
            check(!player.isUsingItem(), "legacy participant could use a modern shield");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
            player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
            MatchManager.tick();
            check(!player.isUsingItem() && !player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS), "disable retained sword use metadata");
            player.getAttribute(Attributes.ARMOR).setBaseValue(0);
            player.setHealth(20);
            player.invulnerableTime = 0;
            player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            player.hurtServer(player.level(), source, 9);
            near(11, player.getHealth(), "disabled rule still sword-blocked");
            swapOffhand(player);
            check(player.getMainHandItem().is(Items.GOLDEN_APPLE), "offhand swaps were locked");
        });
    }

    public static void swordLifecycle(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var player = fixture.attacker();
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            MatchManager.tick();
            check(sword.has(DataComponents.BLOCKS_ATTACKS), "active sword lacks use component");
            ItemEntity dropped = player.drop(sword.copy(), false, true);
            check(dropped != null && !dropped.getItem().has(DataComponents.BLOCKS_ATTACKS), "dropped sword retained match-only blocking");
            dropped.discard();
            try { MatchManager.leave(player); } catch (MatchException exception) { throw failure(exception.getMessage()); }
            check(!sword.has(DataComponents.BLOCKS_ATTACKS), "release retained sword blocking metadata");
        });
    }

    public static void swordRespawn(GameTestHelper context) {
        withMatch(context, Minigames.PEARL_FIGHT, fixture -> {
            var victim = fixture.victim();
            victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE));
            victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            MatchManager.tick();
            ItemStack sword = victim.getMainHandItem();
            victim.removeAllEffects();
            victim.setHealth(5);
            victim.invulnerableTime = 0;
            victim.hurtServer(victim.level(), victim.damageSources().playerAttack(fixture.attacker()), 100);
            check(fixture.match().isActiveParticipant(victim.getUUID()) && !victim.isSpectator(), "respawning game eliminated the player");
            near(20, victim.getHealth(), "death did not restore health");
            check(!sword.has(DataComponents.BLOCKS_ATTACKS), "respawn left blocking metadata on discarded gear");
            check(!victim.getOffhandItem().is(Items.SHIELD), "respawn issued a substitute shield");
        });
    }

    public static void swordElimination(GameTestHelper context) {
        withMatch(context, Minigames.SKYWARS, fixture -> {
            var victim = fixture.victim();
            victim.setPos(context.absoluteVec(new Vec3(1, 2, 1)));
            var area = victim.getBoundingBox().inflate(4);
            try {
                victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
                MatchManager.tick();
                victim.removeAllEffects();
                victim.invulnerableTime = 0;
                victim.hurtServer(victim.level(), victim.damageSources().playerAttack(fixture.attacker()), 100);
                check(victim.isSpectator(), "SkyWars death did not eliminate the participant");
                var drops = victim.level().getEntitiesOfClass(ItemEntity.class, area);
                check(drops.stream().anyMatch(item -> item.getItem().is(Items.GOLDEN_APPLE)), "offhand food did not drop");
                check(drops.stream().anyMatch(item -> item.getItem().is(Items.IRON_SWORD) && !item.getItem().has(DataComponents.BLOCKS_ATTACKS)), "dropped sword retained use metadata");
                check(drops.stream().noneMatch(item -> item.getItem().is(Items.SHIELD)), "elimination created a substitute shield");
            } finally {
                for (ItemEntity item : victim.level().getEntitiesOfClass(ItemEntity.class, area)) item.discard();
            }
        });
    }

    private static void swapOffhand(ServerPlayer player) {
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    record Fixture(Match match, ServerPlayer attacker, ServerPlayer victim, EmbeddedChannel victimChannel) {}

    static void withMatch(GameTestHelper context, Minigame game, Consumer<Fixture> body) {
        withMatch(context, game, body, context::succeed);
    }

    private static void withMatch(GameTestHelper context, Minigame game, Consumer<Fixture> body, Runnable after) {
        var server = context.getLevel().getServer();
        var countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, game, countdown, 0);
        var channels = new ArrayList<EmbeddedChannel>();
        ServerPlayer attacker = player(context, channels);
        ServerPlayer victim = player(context, channels);
        Match match;
        try {
            match = MatchManager.open(server, game, TeamLayout.parse("1v1").orElseThrow(),
                    BrainageMinigames.id("kits/barebones"), (ignored, settings) ->
                            BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()));
            MatchManager.join(attacker, match, 1);
            MatchManager.join(victim, match, 2);
        } catch (MatchException exception) {
            disconnect(attacker);
            disconnect(victim);
            for (var channel : channels) channel.releaseOutbound();
            throw failure(exception.getMessage());
        } finally {
            SettingsStorage.reset(server, game, countdown);
        }
        context.runAfterDelay(2, () -> {
            boolean previous = server.getGameRules().get(CombatRules.COMBAT_1_8);
            boolean pvp = server.getGameRules().get(GameRules.PVP);
            try {
                server.getGameRules().set(CombatRules.COMBAT_1_8, true, server);
                server.getGameRules().set(GameRules.PVP, true, server);
                for (ServerPlayer player : new ServerPlayer[] {attacker, victim}) {
                    player.getInventory().clearContent();
                    player.doTick();
                    player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
                    player.setAbsorptionAmount(0);
                    player.setHealth(20);
                    player.invulnerableTime = 0;
                    player.setOnGround(true);
                    player.setSprinting(false);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0;
                }
                attacker.setPos(0, 100, 0);
                victim.setPos(2, 100, 0);
                body.accept(new Fixture(match, attacker, victim, channels.get(1)));
            } finally {
                MatchManager.stop(match);
                disconnect(attacker);
                disconnect(victim);
                for (var channel : channels) channel.releaseOutbound();
                server.getGameRules().set(CombatRules.COMBAT_1_8, previous, server);
                server.getGameRules().set(GameRules.PVP, pvp, server);
            }
            after.run();
        });
    }

    private static void impact(Projectile projectile, ServerPlayer victim) {
        ((ImpactProjectile) projectile).impact(victim);
    }

    /** Expose the protected vanilla collision entrypoint; no hit behavior is replaced. */
    private interface ImpactProjectile {
        void impact(ServerPlayer victim);
    }

    private static final class TestSnowball extends Snowball implements ImpactProjectile {
        private TestSnowball(ServerPlayer owner) {
            super(owner.level(), owner, new ItemStack(Items.SNOWBALL));
        }

        @Override
        public void impact(ServerPlayer victim) {
            hitTargetOrDeflectSelf(new EntityHitResult(victim));
        }
    }

    private static final class TestEgg extends ThrownEgg implements ImpactProjectile {
        private TestEgg(ServerPlayer owner) {
            super(owner.level(), owner, new ItemStack(Items.EGG));
        }

        @Override
        public void impact(ServerPlayer victim) {
            hitTargetOrDeflectSelf(new EntityHitResult(victim));
        }
    }

    private static final class TestFishingHook extends FishingHook implements ImpactProjectile {
        private TestFishingHook(ServerPlayer owner) {
            super(owner, owner.level(), 0, 0);
        }

        @Override
        public void impact(ServerPlayer victim) {
            hitTargetOrDeflectSelf(new EntityHitResult(victim));
        }
    }

    private static ServerPlayer player(GameTestHelper context, List<EmbeddedChannel> channels) {
        var level = context.getLevel();
        var server = level.getServer();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "combat18_" + NAMES.incrementAndGet()), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1.0E-5, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
