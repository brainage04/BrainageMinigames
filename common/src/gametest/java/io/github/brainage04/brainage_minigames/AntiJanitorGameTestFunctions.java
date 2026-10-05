package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/** Real damage, death, menus, mining and hopper paths, shared by both loaders. */
public final class AntiJanitorGameTestFunctions {
    private AntiJanitorGameTestFunctions() {}

    public static void combat(GameTestHelper context) {
        Fixture f = new Fixture(context, "uhc", "2v1v1", false, 2);
        context.runAfterDelay(2, () -> f.run(() -> {
            ServerPlayer a = f.players.get(0), teammate = f.players.get(1), b = f.players.get(2), c = f.players.get(3);
            check(MatchManager.allowDamage(b, b.damageSources().playerAttack(a)), "permission probe refused");
            check(MatchManager.allowDamage(b, b.damageSources().playerAttack(c)), "permission probe started a lock");
            zeroProjectiles(b, a);
            check(MatchManager.allowDamage(b, b.damageSources().playerAttack(c)), "zero damage started a lock");
            b.hurtServer(b.level(), b.damageSources().fall(), 1);
            b.invulnerableTime = 20;
            b.hurtServer(b.level(), b.damageSources().playerAttack(a), 1);
            check(MatchManager.allowDamage(b, b.damageSources().playerAttack(c)), "rejected invulnerable hit started a lock");
            b.setAbsorptionAmount(2);
            hurt(b, a, 1);
            blocked(b, c, "third player damaged locked victim");
            blocked(a, c, "third player damaged locked attacker");
            blocked(c, a, "locked player damaged third player");
            blocked(b, teammate, "teammate interfered in the duel");
            blocked(teammate, a, "friendly fire started another duel");
            check(MatchManager.allowDamage(b, b.damageSources().fall()), "fall damage was protected");
            var zombie = EntityTypes.ZOMBIE.create(b.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            b.invulnerableTime = 0;
            float before = b.getHealth() + b.getAbsorptionAmount();
            b.hurtServer(b.level(), b.damageSources().mobAttack(zombie), 1);
            check(b.getHealth() + b.getAbsorptionAmount() < before, "mob damage was blocked");
            check(MatchManager.allowDamage(a, a.damageSources().playerAttack(b)), "partner reverse damage refused");
            context.runAfterDelay(20, () -> f.run(() -> {
                hurt(a, b, 1); // Reverse damage resets the shared countdown.
                context.runAfterDelay(19, () -> f.run(() -> {
                    blocked(a, c, "countdown did not reset on reverse damage");
                    zeroProjectiles(a, b);
                    a.invulnerableTime = 0;
                    a.hurtServer(a.level(), a.damageSources().fall(), 1);
                    context.runAfterDelay(20, () -> f.run(() -> {
                        blocked(a, c, "timer expired one tick early");
                        context.runAfterDelay(1, () -> f.run(() -> {
                            float hp = a.getHealth();
                            hurt(a, c, 1);
                            check(a.getHealth() < hp, "zero damage reset the timer or boundary did not release combat");
                            f.finish();
                        }));
                    }));
                }));
            }));
        }));
    }

    public static void loot(GameTestHelper context) {
        Fixture f = new Fixture(context, "meetup", "1v1v1", false, 2);
        context.runAfterDelay(2, () -> f.run(() -> {
            ServerPlayer owner = f.players.get(0), victim = f.players.get(1), other = f.players.get(2);
            hurt(victim, owner, 1);
            BlockPos death = victim.blockPosition();
            victim.getInventory().clearContent();
            int slots = victim.getInventory().getContainerSize();
            for (int slot = 0; slot < slots; slot++) victim.getInventory().setItem(slot, new ItemStack(Items.DIAMOND, slot + 1));
            victim.hurtServer(victim.level(), victim.damageSources().genericKill(), Float.MAX_VALUE);
            check(victim.isSpectator(), "environmental lethal damage did not eliminate");
            Container chest = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, victim.level().getBlockState(death), victim.level(), death, true);
            check(chest != null && chest.getContainerSize() == 54, "death did not create connected double chest at death spot");
            int total = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) total += chest.getItem(slot).getCount();
            check(total == slots * (slots + 1) / 2 && victim.getInventory().isEmpty(), "inventory/armor/offhand were lost or duplicated");
            ChestBlockEntity half = (ChestBlockEntity) victim.level().getBlockEntity(death);
            check(half.canOpen(owner) && !half.canOpen(other) && !half.canOpen(victim), "chest access was not exclusive to partner");
            Vec3 center = Vec3.atCenterOf(death);
            owner.snapTo(center.x(), center.y(), center.z() + 1, 0, 0);
            other.snapTo(center.x(), center.y(), center.z() + 1, 0, 0);
            useChest(other, death);
            check(other.containerMenu == other.inventoryMenu, "other player opened death loot through interaction");
            useChest(owner, death);
            check(owner.containerMenu != owner.inventoryMenu, "duel partner could not open physical chest");
            owner.closeContainer();
            check(!owner.gameMode.destroyBlock(death) && !other.gameMode.destroyBlock(death.east()), "protected chest could be mined");
            check(!victim.level().destroyBlock(death, true), "environment could destroy protected chest");
            check(!victim.level().setBlockAndUpdate(death, Blocks.LAVA.defaultBlockState()), "fluid could replace protected chest");
            owner.snapTo(center.x() + 16, center.y(), center.z(), 0, 0);
            other.snapTo(center.x() + 16, center.y(), center.z(), 0, 0);
            victim.level().explode(null, death.getX() + .5, death.getY() + .5, death.getZ() + .5, 3, Level.ExplosionInteraction.TNT);
            check(victim.level().getBlockState(death).is(Blocks.CHEST), "explosion destroyed protected chest");
            check(chest.countItem(Items.DIAMOND) == total, "explosion changed private loot");
            BlockPos hopperPos = death.below();
            victim.level().setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
            HopperBlockEntity hopper = (HopperBlockEntity) victim.level().getBlockEntity(hopperPos);
            HopperBlockEntity.suckInItems(victim.level(), hopper);
            check(hopper.countItem(Items.DIAMOND) == 0 && chest.countItem(Items.DIAMOND) == slots * (slots + 1) / 2,
                    "hopper extracted private loot: hopper=" + hopper.countItem(Items.DIAMOND)
                    + ", chest=" + chest.countItem(Items.DIAMOND) + ", expected=" + total);
            blocked(owner, other, "survivor lost protection on death");
            context.runAfterDelay(40, () -> f.run(() -> {
                check(half.canOpen(other), "public chest access did not unlock at timer expiry");
                hurt(owner, other, 1);
                check(owner.getHealth() < owner.getMaxHealth(), "survivor remained protected after expiry");
                HopperBlockEntity.suckInItems(victim.level(), hopper);
                check(hopper.countItem(Items.DIAMOND) > 0, "expired chest stayed automation-locked");
                check(other.gameMode.destroyBlock(death), "expired chest stayed unbreakable");
                f.finish();
            }));
        }));
    }

    public static void shieldLoot(GameTestHelper context) {
        Fixture f = new Fixture(context, "meetup", "1v1v1", false, 30);
        context.runAfterDelay(2, () -> f.run(() -> {
            var server = context.getLevel().getServer();
            boolean previousCombat = server.getGameRules().get(CombatRules.COMBAT_1_8);
            boolean previousAntiJanitor = server.getGameRules().get(AntiJanitor.ENABLED);
            try {
                server.getGameRules().set(AntiJanitor.ENABLED, true, server);
                server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
                MatchManager.tick();
                ServerPlayer owner = f.players.get(0), victim = f.players.get(1), other = f.players.get(2);
                victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
                victim.getInventory().setItem(0, new ItemStack(Items.SHIELD));
                server.getGameRules().set(CombatRules.COMBAT_1_8, true, server);
                MatchManager.tick();
                check(victim.getOffhandItem().is(Items.GOLDEN_APPLE), "legacy sword blocking displaced the kit offhand");
                hurt(victim, owner, 1);
                blocked(victim, other, "combined legacy combat did not lock the duel");
                BlockPos death = victim.blockPosition();
                victim.hurtServer(victim.level(), victim.damageSources().genericKill(), Float.MAX_VALUE);
                check(victim.isSpectator(), "locked legacy-combat death did not eliminate");
                Container chest = ChestBlock.getContainer((ChestBlock) Blocks.CHEST,
                        victim.level().getBlockState(death), victim.level(), death, true);
                check(chest != null && chest.getContainerSize() == 54, "locked legacy-combat death did not create a double chest");
                check(chest.countItem(Items.GOLDEN_APPLE) == 3, "partner chest lost offhand food");
                check(chest.countItem(Items.SHIELD) == 1, "partner chest lost an ordinary inventory shield");
                check(victim.getInventory().isEmpty(), "eliminated participant retained or duplicated chest loot");
                ChestBlockEntity half = (ChestBlockEntity) victim.level().getBlockEntity(death);
                check(half.canOpen(owner) && !half.canOpen(other), "combined combat loot was not private to the duel partner");
                f.finish();
            } finally {
                server.getGameRules().set(CombatRules.COMBAT_1_8, previousCombat, server);
                server.getGameRules().set(AntiJanitor.ENABLED, previousAntiJanitor, server);
            }
        }));
    }

    public static void locations(GameTestHelper context) {
        Fixture f = new Fixture(context, "skywars", "ffa", false, 2, 10);
        context.runAfterDelay(2, () -> f.run(() -> {
            var level = context.getLevel();
            BlockPos base = f.players.getFirst().blockPosition().above(8);
            for (int pair = 0; pair < 5; pair++) {
                ServerPlayer owner = f.players.get(pair * 2), victim = f.players.get(pair * 2 + 1);
                BlockPos death = base.offset(pair * 3, 0, 0);
                if (pair == 1) level.setBlockAndUpdate(death, Blocks.WATER.defaultBlockState());
                if (pair == 2) level.setBlockAndUpdate(death, Blocks.LAVA.defaultBlockState());
                if (pair == 3) death = new BlockPos(death.getX(), level.getMinY() - 20, death.getZ());
                victim.snapTo(death.getX() + .5, death.getY(), death.getZ() + .5, 0, 0);
                hurt(victim, owner, 1);
                victim.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
                if (pair == 4) MatchManager.handleDisconnect(victim);
                else if (pair == 0) hurt(victim, owner, Float.MAX_VALUE);
                else victim.hurtServer(level, victim.damageSources().genericKill(), Float.MAX_VALUE);
                if (pair == 3) death = new BlockPos(death.getX(), Math.max(level.getMinY() + 1, (int) Math.ceil(f.match.arena().voidY() + 1)), death.getZ());
                check(level.getBlockState(death).is(Blocks.CHEST), "missing air/water/lava/void/disconnect chest for case " + pair);
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(death);
                check(chest.canOpen(owner) && chest.countItem(Items.EMERALD) == 7, "wrong owner or lost loot for case " + pair);
                if (pair == 1) check(level.getBlockState(death).getValue(ChestBlock.WATERLOGGED), "water death chest was not waterlogged");
                ServerPlayer outsider = f.players.get((pair * 2 + 2) % f.players.size());
                blocked(owner, outsider, "disconnect/death released survivor early");
            }
            context.runAfterDelay(40, () -> f.run(f::finish));
        }));
    }

    public static void scope(GameTestHelper context) {
        var server = context.getLevel().getServer();
        boolean previous = server.getGameRules().get(AntiJanitor.ENABLED);
        List<Fixture> fixtures = new ArrayList<>();
        fixtures.add(new Fixture(context, "classic", "1v1v1", false, 2));
        fixtures.add(new Fixture(context, "uhc", "2v2", false, 2));
        fixtures.add(new Fixture(context, "meetup", "1v1v1", true, 2));
        fixtures.add(new Fixture(context, "final_uhc", "1v1v1", false, 2));
        context.runAfterDelay(2, () -> {
            try {
                for (int i = 0; i < fixtures.size(); i++) {
                    Fixture f = fixtures.get(i);
                    check(f.match.phase() == MatchPhase.ACTIVE,
                            f.match.game().id() + " scope fixture was " + f.match.phase()
                                    + " with countdown " + f.match.settings().get(GameSetting.COUNTDOWN_SECONDS));
                    ServerPlayer a = f.players.get(0), b = f.players.get(i == 1 ? 2 : 1), c = f.players.get(i == 1 ? 3 : 2);
                    hurt(b, a, 1);
                    if (i == 3) blocked(b, c, "FinalUHC multi-team match did not qualify");
                    else check(MatchManager.allowDamage(b, b.damageSources().playerAttack(c)),
                            f.match.game().id() + " excluded game/layout/private duel received a lock");
                }
                server.getGameRules().set(AntiJanitor.ENABLED, false, server);
                Fixture f = fixtures.getLast();
                check(MatchManager.allowDamage(f.players.get(1), f.players.get(1).damageSources().playerAttack(f.players.get(2))), "gamerule off did not immediately release lock");
                ServerPlayer victim = f.players.get(1);
                BlockPos death = victim.blockPosition();
                victim.hurtServer(victim.level(), victim.damageSources().genericKill(), Float.MAX_VALUE);
                check(!victim.level().getBlockState(death).is(Blocks.CHEST), "disabled gamerule created a protected chest");
                context.succeed();
            } finally {
                fixtures.forEach(Fixture::close);
                server.getGameRules().set(AntiJanitor.ENABLED, previous, server);
            }
        });
    }

    private static void hurt(ServerPlayer victim, ServerPlayer attacker, float amount) {
        victim.invulnerableTime = 0;
        victim.hurtServer(victim.level(), victim.damageSources().playerAttack(attacker), amount);
    }

    private static void zeroProjectiles(ServerPlayer victim, ServerPlayer attacker) {
        var reason = net.minecraft.world.entity.EntitySpawnReason.COMMAND;
        var snowball = EntityTypes.SNOWBALL.create(victim.level(), reason);
        var egg = EntityTypes.EGG.create(victim.level(), reason);
        var rod = new net.minecraft.world.entity.projectile.FishingHook(attacker, victim.level(), 0, 0);
        for (var projectile : List.of(snowball, egg, rod)) {
            projectile.setOwner(attacker);
            victim.invulnerableTime = 0;
            victim.hurtServer(victim.level(), victim.damageSources().thrown(projectile, attacker), 0);
        }
        rod.discard();
    }

    private static void blocked(ServerPlayer victim, ServerPlayer attacker, String message) {
        float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
        hurt(victim, attacker, 1);
        check(victim.getHealth() == health && victim.getAbsorptionAmount() == absorption, message);
    }

    private static void useChest(ServerPlayer player, BlockPos pos) {
        player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }

    private static final class Fixture {
        private final GameTestHelper context;
        private final Match match;
        private final List<ServerPlayer> players = new ArrayList<>();

        Fixture(GameTestHelper context, String id, String layout, boolean privateMatch, int seconds) {
            this(context, id, layout, privateMatch, seconds, TeamLayout.parse(layout).orElseThrow().isFreeForAll() ? 4 : TeamLayout.parse(layout).orElseThrow().capacity());
        }

        Fixture(GameTestHelper context, String id, String layout, boolean privateMatch, int seconds, int count) {
            this.context = context;
            var server = context.getLevel().getServer();
            var settings = new ArrayList<>(GameSetting.common(0, 0, false));
            settings.add(new GameSetting(AntiJanitor.SECONDS.key(), seconds, 1, 3600, AntiJanitor.SECONDS.description()));
            Minigame game = new Minigame() {
                public String id() { return id; }
                public String displayName() { return "AntiJanitor " + id; }
                public List<GameSetting> settings() { return settings; }
                public Identifier defaultKit() { return BrainageMinigames.id("empty"); }
                public GameType playerGameMode() { return GameType.SURVIVAL; }
                public Arena openArena(MinecraftServer ignored, GameSettings values) { return BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()); }
            };
            for (int i = 0; i < count; i++) players.add(connected(context));
            Identifier storage = BrainageMinigames.id("settings");
            var savedSettings = server.getCommandStorage().get(storage).copy();
            try {
                for (GameSetting setting : settings) {
                    SettingsStorage.set(server, game, setting, setting.defaultValue());
                }
                match = privateMatch
                        ? MatchManager.openPrivate(server, game, TeamLayout.parse(layout).orElseThrow(),
                                game::openArena, players.stream().map(ServerPlayer::getUUID).toList())
                        : MatchManager.open(server, game, TeamLayout.parse(layout).orElseThrow(), null, game::openArena);
                for (int i = 0; i < players.size(); i++) {
                    int team = layout.equals("2v1v1") ? (i < 2 ? 1 : i) : layout.equals("2v2") ? (i < 2 ? 1 : 2) : 0;
                    MatchManager.join(players.get(i), match, team);
                }
                if (match.phase() == MatchPhase.LOBBY) match.start();
            } catch (MatchException exception) {
                players.forEach(player -> server.getPlayerList().remove(player));
                throw new GameTestAssertException(Component.literal(exception.getMessage()), 0);
            } finally {
                server.getCommandStorage().set(storage, savedSettings);
            }
        }

        void run(Runnable body) {
            try { body.run(); }
            catch (RuntimeException | Error exception) { close(); throw exception; }
        }

        void finish() { close(); context.succeed(); }

        void close() {
            MatchManager.stop(match);
            for (ServerPlayer player : players) {
                MatchManager.handleConnect(player);
                player.level().getServer().getPlayerList().remove(player);
            }
        }
    }

    private static ServerPlayer connected(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "AJ" + UUID.randomUUID().toString().substring(0, 8)), false);
        var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }
}
