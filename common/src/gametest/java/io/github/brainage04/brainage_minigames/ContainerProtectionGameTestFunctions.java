package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.ContainerProtection;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ContainerProtectionGameTestFunctions {
    private ContainerProtectionGameTestFunctions() {}

    public static void protection(GameTestHelper context) {
        withFixture(context, fixture -> {
            var rule = ContainerProtection.ENABLED;
            context.assertTrue(rule.getIdentifier().toString().equals("brainage_minigames:container_protection")
                    && new net.minecraft.world.level.gamerules.GameRules(List.of(rule)).get(rule),
                    "Container protection must be a namespaced, default-on boolean");
            var owner = fixture.players.getFirst();
            var teammate = fixture.players.get(1);
            var opponent = fixture.players.get(2);
            int index = 0;
            for (var item : List.of(Items.CHEST, Items.FURNACE, Items.BARREL, Items.BREWING_STAND,
                    Items.HOPPER, Items.DISPENSER, Items.SHULKER_BOX, Items.CRAFTER)) {
                BlockPos pos = fixture.base.offset(index++ * 2 % 10, 0, index / 5 * 3);
                fixture.place(owner, pos, new ItemStack(item));
                context.assertTrue(owner.getUUID().equals(ContainerProtection.owner(owner.level(), pos)),
                        "Actual block-item placement did not record container ownership: " + item);
                var container = (BaseContainerBlockEntity) owner.level().getBlockEntity(pos);
                context.assertTrue(container.canOpen(owner), "The owner cannot open their container");
                context.assertFalse(container.canOpen(teammate), "A teammate can open another participant's container");
                context.assertFalse(container.canOpen(opponent), "An opponent can open another participant's container");
                context.assertFalse(opponent.gameMode.destroyBlock(pos), "An opponent broke a protected container");
                context.assertFalse(new Slot(container, 0, 0, 0).mayPickup(opponent), "Foreign UI extraction bypassed ownership");
            }
            BlockPos ender = fixture.base.offset(2, 0, 8);
            fixture.place(owner, ender, new ItemStack(Items.ENDER_CHEST));
            context.assertTrue(owner.getUUID().equals(ContainerProtection.owner(owner.level(), ender)),
                    "Personal-inventory ender chests did not record physical ownership");
            context.assertFalse(ContainerProtection.canOpen(owner.level(), ender, opponent), "A foreign ender chest can be used");
            context.assertFalse(opponent.gameMode.destroyBlock(ender), "A foreign ender chest can be broken");
            BlockPos unowned = fixture.base.offset(0, 0, 8);
            owner.level().setBlockAndUpdate(unowned, Blocks.BARREL.defaultBlockState());
            context.assertTrue(((BaseContainerBlockEntity) owner.level().getBlockEntity(unowned)).canOpen(opponent),
                    "A natural/map container became protected");
        });
    }

    public static void automation(GameTestHelper context) {
        withFixture(context, fixture -> {
            var owner = fixture.players.getFirst();
            var intruder = fixture.players.get(2);
            BlockPos chestPos = fixture.base.above();
            owner.level().setBlockAndUpdate(chestPos.below(), Blocks.STONE.defaultBlockState());
            fixture.place(owner, chestPos, new ItemStack(Items.CHEST));
            var chest = (Container) owner.level().getBlockEntity(chestPos);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
            owner.level().setBlockAndUpdate(chestPos.below(), Blocks.HOPPER.defaultBlockState());
            var hopper = (HopperBlockEntity) owner.level().getBlockEntity(chestPos.below());
            context.assertFalse(HopperBlockEntity.suckInItems(owner.level(), hopper), "An unowned hopper extracted protected contents");
            MatchManager.blockPlaced(intruder, chestPos.below());
            context.assertFalse(HopperBlockEntity.suckInItems(owner.level(), hopper), "A foreign hopper extracted protected contents");
            MatchManager.blockPlaced(owner, chestPos.below());
            context.assertTrue(HopperBlockEntity.suckInItems(owner.level(), hopper), "The owner's hopper cannot extract their own contents");
            context.assertTrue(chest.getItem(0).getCount() == 3, "Allowed hopper extraction did not move exactly one item");

            BlockPos second = chestPos.east();
            owner.level().setBlockAndUpdate(second.below(), Blocks.STONE.defaultBlockState());
            fixture.place(intruder, second, new ItemStack(Items.CHEST));
            context.assertFalse(ContainerProtection.canOpen(owner.level(), chestPos, owner), "Mixed double chest exposed the foreign half");
            context.assertFalse(ContainerProtection.canOpen(owner.level(), second, intruder), "Opening the other half bypassed double-chest ownership");
            context.assertFalse(HopperBlockEntity.suckInItems(owner.level(), hopper), "Mixed double chest exposed its foreign half to automation");

            // Equal coordinates in a different dimension are unowned.
            context.assertTrue(ContainerProtection.owner(owner.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER), chestPos) == null,
                    "Container ownership leaked across dimensions");
        });
    }

    public static void offAndLifetime(GameTestHelper context) {
        withFixture(context, fixture -> {
            var owner = fixture.players.getFirst();
            var intruder = fixture.players.get(2);
            BlockPos pos = fixture.base;
            fixture.place(owner, pos, new ItemStack(Items.BARREL));
            var barrel = (BaseContainerBlockEntity) owner.level().getBlockEntity(pos);
            owner.level().getServer().getGameRules().set(ContainerProtection.ENABLED, false, owner.level().getServer());
            context.assertTrue(intruder.openMenu(barrel).isPresent(), "Disabled protection did not permit vanilla opening");
            var access = ContainerProtection.lastAccess(owner);
            context.assertTrue(access != null && access.actor().equals(intruder.getUUID())
                    && access.position().equals(pos) && access.action().equals("open"), "The reflective bot access query missed a foreign opening");
            owner.level().getServer().getGameRules().set(ContainerProtection.ENABLED, true, owner.level().getServer());
            context.assertFalse(new Slot(barrel, 0, 0, 0).mayPickup(intruder), "Enabling protection left an already-open menu extractable");
            intruder.closeContainer();
            owner.level().getServer().getGameRules().set(ContainerProtection.ENABLED, false, owner.level().getServer());
            context.assertTrue(intruder.gameMode.destroyBlock(pos), "Disabled protection did not permit vanilla breaking");
            // The broken barrel drops itself; leave no item lying in the test level.
            owner.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2)).forEach(net.minecraft.world.entity.Entity::discard);
            access = ContainerProtection.lastAccess(owner);
            context.assertTrue(access != null && access.actor().equals(intruder.getUUID()) && access.action().equals("break"),
                    "The bot access query lost the owner when their container was broken");
            context.assertTrue(ContainerProtection.owner(owner.level(), pos) == null, "Breaking retained stale ownership");
            fixture.place(intruder, pos, new ItemStack(Items.BARREL));
            context.assertTrue(intruder.getUUID().equals(ContainerProtection.owner(owner.level(), pos)), "Replacement inherited the old owner");
            MatchManager.stop(fixture.match);
            context.assertTrue(ContainerProtection.owner(context.getLevel(), pos) == null && ContainerProtection.lastAccess(owner) == null,
                    "Match closure retained ownership/access records");
        });
    }

    private static void withFixture(GameTestHelper context, java.util.function.Consumer<Fixture> action) {
        Fixture fixture = new Fixture(context);
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, fixture::close);
        context.runAfterDelay(2, () -> {
            try {
                context.assertTrue(fixture.match.phase() == MatchPhase.ACTIVE, "Fixture did not start");
                action.accept(fixture);
                context.succeed();
            } finally { fixture.close(); }
        });
    }

    private static final class Fixture {
        private final List<ServerPlayer> players = new ArrayList<>();
        private final Match match;
        private final BlockPos base;
        private final boolean previous;
        private boolean closed;

        Fixture(GameTestHelper context) {
            var server = context.getLevel().getServer();
            previous = server.getGameRules().get(ContainerProtection.ENABLED);
            server.getGameRules().set(ContainerProtection.ENABLED, true, server);
            Minigame game = new Minigame() {
                public String id() { return "container_test"; }
                public String displayName() { return "Container test"; }
                public List<GameSetting> settings() { return GameSetting.common(0, 0, false); }
                public Identifier defaultKit() { return Minigames.BUILD_UHC.defaultKit(); }
                public GameType playerGameMode() { return GameType.SURVIVAL; }
                public Arena openArena(MinecraftServer ignored, GameSettings values) {
                    return BoxArena.open(context.getLevel(), 25, Blocks.STONE.defaultBlockState());
                }
            };
            try {
                match = MatchManager.open(server, game, TeamLayout.parse("2v1").orElseThrow(), null, game::openArena);
                for (int i = 0; i < 3; i++) {
                    var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CP" + UUID.randomUUID().toString().substring(0, 8)), false);
                    var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), ClientInformation.createDefault());
                    TestPlayers.connect(player, cookie);
                    players.add(player);
                    MatchManager.join(player, match, i < 2 ? 1 : 2);
                }
                base = BlockPos.containing(match.arena().lobbyPosition()).offset(-5, 0, -5);
            } catch (MatchException exception) { throw new IllegalStateException(exception); }
        }

        void place(ServerPlayer player, BlockPos pos, ItemStack item) {
            player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2, 180, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
            player.gameMode.useItemOn(player, player.level(), item, InteractionHand.MAIN_HAND, hit);
            if (player.level().getBlockEntity(pos) == null) throw new IllegalStateException("Placement failed at " + pos);
        }

        void close() {
            if (closed) return;
            closed = true;
            MatchManager.stop(match);
            var server = match.server();
            players.forEach(player -> server.getPlayerList().remove(player));
            server.getGameRules().set(ContainerProtection.ENABLED, previous, server);
        }
    }
}
