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
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.netty.channel.embedded.EmbeddedChannel;
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
            ServerPlayer outsider = player(context);
            try {
                outsider.resetAttackStrengthTicker();
                near(0.1, outsider.getAttackStrengthScale(0.5F), "outside player's cooldown");
            } finally {
                disconnect(outsider);
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
                near(20, victim.getHealth(), "rod inflicted damage");
                near(0.4, victim.getDeltaMovement().x, "rod did not knock player away");
                Vec3 before = victim.getDeltaMovement();
                hook.retrieve(owner.getMainHandItem());
                check(hook.isRemoved() && owner.fishing == null, "rod failed to retract");
                check(before.equals(victim.getDeltaMovement()), "retraction pulled the player");
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

    public static void shieldInventoryLocks(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            var player = fixture.victim();
            var server = player.level().getServer();
            server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
            MatchManager.tick();
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
            server.getGameRules().set(CombatRules.COMBAT_1_8, true, server);
            MatchManager.tick();
            blockingShield(player);
            blockingShield(fixture.attacker());
            ItemStack shield = player.getOffhandItem().copy();
            player.getOffhandItem().hurtAndBreak(10_000, player, EquipmentSlot.OFFHAND);
            blockingShield(player);
            near(0, player.getOffhandItem().getDamageValue(), "unbreakable shield took durability damage");
            player.setYRot(90);
            player.getOffhandItem().use(player.level(), player, InteractionHand.OFF_HAND);
            for (int tick = 0; tick < 6; tick++) player.doTick();
            player.invulnerableTime = 0;
            fixture.attacker().attack(player);
            near(20, player.getHealth(), "provided shield did not block a frontal sword-style attack");
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            var inventoryMenu = player.inventoryMenu;
            inventoryMenu.clicked(InventoryMenu.SHIELD_SLOT, 0, ContainerInput.PICKUP, player);
            check(inventoryMenu.getCarried().isEmpty(), "shield escaped to the cursor");
            inventoryMenu.clicked(InventoryMenu.SHIELD_SLOT, 0, ContainerInput.QUICK_MOVE, player);
            inventoryMenu.clicked(InventoryMenu.SHIELD_SLOT, 1, ContainerInput.THROW, player);
            inventoryMenu.clicked(InventoryMenu.SHIELD_SLOT, 0, ContainerInput.SWAP, player);
            blockingShield(player);
            check(player.getMainHandItem().is(Items.DIAMOND), "hotbar swap moved the shield");
            check(player.drop(shield.copy(), false, true) == null, "provided shield could be dropped directly");
            ItemEntity regular = player.drop(new ItemStack(Items.SHIELD), false, true);
            check(regular != null, "ordinary shields were also prohibited from dropping");
            regular.discard();
            swapOffhand(player);
            blockingShield(player);
            check(player.getMainHandItem().is(Items.DIAMOND), "F-key swap moved the shield");
            SimpleContainer chest = new SimpleContainer(27);
            chest.setItem(0, new ItemStack(Items.EMERALD));
            var chestMenu = ChestMenu.threeRows(1, player.getInventory(), chest);
            chestMenu.clicked(0, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
            check(chest.getItem(0).is(Items.EMERALD), "shield moved into a chest");
            inventoryMenu.clicked(1, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
            check(inventoryMenu.getSlot(1).getItem().isEmpty(), "shield moved into crafting");
            player.setGameMode(GameType.CREATIVE);
            inventoryMenu.clicked(InventoryMenu.SHIELD_SLOT, 2, ContainerInput.CLONE, player);
            check(inventoryMenu.getCarried().isEmpty(), "creative clone copied the provided shield");
            player.connection.handleSetCreativeModeSlot(
                    new ServerboundSetCreativeModeSlotPacket(InventoryMenu.SHIELD_SLOT, ItemStack.EMPTY));
            player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(36, shield.copy()));
            blockingShield(player);
            check(player.getMainHandItem().is(Items.DIAMOND), "creative packet copied the shield into the hotbar");
            player.setGameMode(GameType.SURVIVAL);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot != Inventory.SLOT_OFFHAND) {
                    check(!ItemStack.isSameItemSameComponents(shield, player.getInventory().getItem(slot)),
                            "provided shield escaped outside the offhand");
                }
            }
            server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
            MatchManager.tick();
            check(player.getOffhandItem().is(Items.GOLDEN_APPLE) && player.getOffhandItem().getCount() == 3,
                    "disabling combat did not restore the displaced kit offhand");
            swapOffhand(player);
            check(player.getMainHandItem().is(Items.GOLDEN_APPLE), "disabled rule kept the ordinary offhand locked");
            check(player.getOffhandItem().is(Items.DIAMOND), "disabled-rule offhand swap lost the main-hand item");
        });
    }

    public static void shieldLifecycle(GameTestHelper context) {
        withMatch(context, Minigames.CLASSIC, fixture -> {
            MatchManager.stop(fixture.match());
            var attacker = fixture.attacker();
            var victim = fixture.victim();
            var server = attacker.level().getServer();
            attacker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
            victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CARROT, 2));
            var countdown = Minigames.CLASSIC.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
            SettingsStorage.set(server, Minigames.CLASSIC, countdown, 0);
            Match match;
            try {
                match = MatchManager.open(server, Minigames.CLASSIC, TeamLayout.parse("1v1").orElseThrow(),
                        BrainageMinigames.id("kits/barebones"), (ignored, settings) ->
                                BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()));
            } catch (MatchException exception) {
                throw failure(exception.getMessage());
            } finally {
                SettingsStorage.reset(server, Minigames.CLASSIC, countdown);
            }
            try {
                MatchManager.join(attacker, match, 1);
                MatchManager.join(victim, match, 2);
                MatchManager.tick();
                blockingShield(attacker);
                blockingShield(victim);
                MatchManager.leave(attacker);
                check(attacker.getOffhandItem().is(Items.GOLDEN_APPLE) && attacker.getOffhandItem().getCount() == 3,
                        "leaving did not remove the shield and restore the pre-match offhand");
                MatchManager.handleDisconnect(victim);
                check(!victim.getOffhandItem().is(Items.SHIELD), "disconnect kept the provided shield equipped");
                check(PlayerSnapshotStorage.restore(victim), "disconnected player's snapshot could not restore");
                check(victim.getOffhandItem().is(Items.CARROT) && victim.getOffhandItem().getCount() == 2,
                        "reconnect restoration lost the pre-match offhand");
            } catch (MatchException exception) {
                throw failure(exception.getMessage());
            } finally {
                MatchManager.stop(match);
                for (ServerPlayer player : new ServerPlayer[] {attacker, victim}) {
                    if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
                        PlayerSnapshotStorage.restore(player);
                    }
                }
            }
        });
    }

    public static void shieldRespawn(GameTestHelper context) {
        withMatch(context, Minigames.PEARL_FIGHT, fixture -> {
            MatchManager.tick();
            var victim = fixture.victim();
            blockingShield(victim);
            victim.removeAllEffects();
            victim.setHealth(5);
            victim.invulnerableTime = 0;
            victim.hurtServer(victim.level(), victim.damageSources().playerAttack(fixture.attacker()), 100);
            check(fixture.match().isActiveParticipant(victim.getUUID()) && !victim.isSpectator(),
                    "respawning game eliminated the player");
            near(20, victim.getHealth(), "death did not restore the respawning player's health");
            blockingShield(victim);
            check(victim.getOffhandItem().getDamageValue() == 0, "respawn did not provide an intact shield");
        });
    }

    public static void shieldElimination(GameTestHelper context) {
        withMatch(context, Minigames.SKYWARS, fixture -> {
            MatchManager.tick();
            var victim = fixture.victim();
            blockingShield(victim);
            victim.setPos(context.absoluteVec(new Vec3(1, 2, 1)));
            var area = victim.getBoundingBox().inflate(4);
            try {
                victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                victim.removeAllEffects();
                victim.invulnerableTime = 0;
                victim.hurtServer(victim.level(), victim.damageSources().playerAttack(fixture.attacker()), 100);
                check(victim.isSpectator(), "SkyWars death did not eliminate the participant");
                var drops = victim.level().getEntitiesOfClass(ItemEntity.class, area);
                check(drops.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND)
                                && item.getItem().getCount() == 3), "ordinary inventory did not drop on elimination");
                check(drops.stream().noneMatch(item -> item.getItem().is(Items.SHIELD)),
                        "provided shield dropped on death");
                check(!victim.getOffhandItem().is(Items.SHIELD), "eliminated player retained the blocking shield");
            } finally {
                for (ItemEntity item : victim.level().getEntitiesOfClass(ItemEntity.class, area)) item.discard();
            }
        });
    }

    private static void blockingShield(ServerPlayer player) {
        check(player.getOffhandItem().is(Items.SHIELD) && player.getOffhandItem().has(DataComponents.UNBREAKABLE),
                "participant did not receive an unbreakable offhand shield");
    }

    private static void swapOffhand(ServerPlayer player) {
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    private record Fixture(Match match, ServerPlayer attacker, ServerPlayer victim) {}

    private static void withMatch(GameTestHelper context, Minigame game, Consumer<Fixture> body) {
        withMatch(context, game, body, context::succeed);
    }

    private static void withMatch(GameTestHelper context, Minigame game, Consumer<Fixture> body, Runnable after) {
        var server = context.getLevel().getServer();
        var countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, game, countdown, 0);
        ServerPlayer attacker = player(context);
        ServerPlayer victim = player(context);
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
                body.accept(new Fixture(match, attacker, victim));
            } finally {
                MatchManager.stop(match);
                disconnect(attacker);
                disconnect(victim);
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

    private static ServerPlayer player(GameTestHelper context) {
        var level = context.getLevel();
        var server = level.getServer();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "combat18_" + NAMES.incrementAndGet()), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
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
