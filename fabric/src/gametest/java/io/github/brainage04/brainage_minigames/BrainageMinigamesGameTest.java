package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.event.custom.CustomFreeForAllMinigame;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class BrainageMinigamesGameTest {
    @GameTest
    public void commandsAreRegistered(GameTestHelper context) {
        var root = context.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String command : new String[]{
                "startevent", "joinevent", "watchevent", "leaveevent", "nextphase", "stopevent",
                "stopcountdown", "teamup", "matchteams", "spawn", "uhc", "minigames"
        }) {
            if (root.getChild(command) == null) {
                throw new AssertionError("Expected /" + command + " to be registered.");
            }
        }
        if (root.getChild("uhc").getChild("open") == null
                || root.getChild("uhc").getChild("start") == null
                || root.getChild("uhc").getChild("join") == null
                || root.getChild("uhc").getChild("leave") == null
                || root.getChild("uhc").getChild("stop") == null) {
            throw new AssertionError("Expected the complete /uhc command surface.");
        }
        var kit = root.getChild("minigames").getChild("kit");
        if (kit == null
                || kit.getChild("edit") == null
                || kit.getChild("give") == null
                || kit.getChild("delete") == null
                || kit.getChild("list") == null) {
            throw new AssertionError("Expected the complete kit editor command surface.");
        }
        context.succeed();
    }

    @GameTest
    public void playerSnapshotRoundTripsStateAndRewards(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        MinecraftServer server = context.getLevel().getServer();
        Vec3 originalPosition = context.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        Vec3 originalVelocity = new Vec3(0.125, 0.25, -0.375);

        player.setGameMode(GameType.CREATIVE);
        player.snapTo(originalPosition.x(), originalPosition.y(), originalPosition.z(), 37.0F, -12.0F);
        player.setDeltaMovement(originalVelocity);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));

        try {
            assertTrue(PlayerSnapshotStorage.save(player, PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE),
                    "Expected the player snapshot to be saved.");
            assertTrue(PlayerSnapshotStorage.addRewards(player, java.util.List.of(new ItemStack(Items.EMERALD, 2))),
                    "Expected rewards to be appended to the snapshot.");

            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.snapTo(originalPosition.x() + 8.0, originalPosition.y(), originalPosition.z(), 0.0F, 0.0F);
            player.setDeltaMovement(Vec3.ZERO);

            assertTrue(PlayerSnapshotStorage.restore(player), "Expected the player snapshot to be restored.");
            assertEquals(GameType.CREATIVE, player.gameMode(), "game mode");
            assertItemCount(player, Items.DIAMOND, 3);
            assertItemCount(player, Items.EMERALD, 2);
            assertNear(originalPosition, player.position(), "position");
            assertNear(originalVelocity, player.getDeltaMovement(), "velocity");
            assertFalse(PlayerSnapshotStorage.hasSnapshot(server, player.getUUID()),
                    "Expected a successful restore to consume the snapshot.");
        } finally {
            if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
                PlayerSnapshotStorage.restore(player);
            }
        }

        context.succeed();
    }

    @GameTest
    public void editableKitPersistsAndCanBeGiven(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        var kitId = BrainageMinigames.id("gametest/editable");
        var builtInKitId = BrainageMinigames.id("kits/classic");
        KitStorage.delete(server, builtInKitId);
        KitStorage.delete(server, kitId);

        try {
            KitStorage.openEditor(player, kitId);
            player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2));
            player.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 3));
            player.closeContainer();
            var stored = KitStorage.get(server, kitId).orElseThrow(
                    () -> new AssertionError("Expected the editable kit to persist.")
            );
            if (stored.size() != 2) {
                throw new AssertionError("Expected two stacks in the editable kit, found " + stored.size() + ".");
            }

            player.getInventory().clearContent();
            new CustomFreeForAllMinigame(1, 1, 1, 1, kitId).giveKit(context.getLevel(), player);
            assertItemCount(player, Items.DIAMOND, 2);
            assertItemCount(player, Items.EMERALD, 3);
            assertTrue(KitStorage.delete(server, kitId), "Expected the editable kit to be deleted.");
            assertTrue(KitStorage.get(server, kitId).isEmpty(), "Expected the deleted kit to remain absent.");

            player.getInventory().clearContent();
            assertTrue(KitStorage.give(server, builtInKitId, java.util.List.of(player)),
                    "Expected the bundled kit to resolve from its loot table.");
            assertItemCount(player, Items.IRON_SWORD, 1);

            KitStorage.openEditor(player, builtInKitId);
            assertTrue(player.containerMenu.getSlot(0).getItem().is(Items.IRON_SWORD),
                    "Expected the bundled kit editor to load its loot-table contents.");
            player.closeContainer();
            assertTrue(KitStorage.get(server, builtInKitId).isPresent(),
                    "Expected closing the bundled kit editor to save an override.");
            assertTrue(KitStorage.delete(server, builtInKitId),
                    "Expected deleting the bundled kit override to restore loot-table resolution.");
        } finally {
            KitStorage.delete(server, kitId);
            KitStorage.delete(server, builtInKitId);
        }

        context.succeed();
    }


    private static void assertItemCount(ServerPlayer player, Item item, int expected) {
        int actual = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                actual += stack.getCount();
            }
        }
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " " + item + ", found " + actual + ".");
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceToSqr(actual) > 1.0E-8) {
            throw new AssertionError("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
