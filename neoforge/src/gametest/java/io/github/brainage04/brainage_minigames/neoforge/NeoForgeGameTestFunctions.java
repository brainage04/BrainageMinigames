package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.CustomFreeForAllMinigame;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class NeoForgeGameTestFunctions {
    private NeoForgeGameTestFunctions() {}
    public static void commands(GameTestHelper context) {
        var root = context.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String command : new String[]{"startevent", "joinevent", "watchevent", "leaveevent", "nextphase", "stopevent", "stopcountdown", "teamup", "matchteams", "spawn", "uhc", "minigames"}) if (root.getChild(command) == null) throw new AssertionError(command);
        context.succeed();
    }
    public static void snapshot(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        var server = context.getLevel().getServer();
        Vec3 position = context.absoluteVec(new Vec3(2.5, 3, 2.5));
        player.setGameMode(GameType.CREATIVE); player.snapTo(position.x(), position.y(), position.z(), 37, -12); player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        if (!PlayerSnapshotStorage.save(player, PlayerSnapshotStorage.CUSTOM_EVENT_SCOPE) || !PlayerSnapshotStorage.addRewards(player, java.util.List.of(new ItemStack(Items.EMERALD, 2)))) throw new AssertionError("save");
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        if (!PlayerSnapshotStorage.restore(player) || player.gameMode() != GameType.CREATIVE || count(player, Items.DIAMOND) != 3 || count(player, Items.EMERALD) != 2 || !player.position().equals(position) || PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) throw new AssertionError("snapshot");
        context.succeed();
    }
    public static void kit(GameTestHelper context) {
        var server = context.getLevel().getServer(); ServerPlayer player = context.makeMockServerPlayerInLevel(); var id = BrainageMinigames.id("gametest/editable"); var builtin = BrainageMinigames.id("kits/classic"); KitStorage.delete(server, id); KitStorage.delete(server, builtin);
        try { KitStorage.openEditor(player, id); player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2)); player.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 3)); player.closeContainer(); if (KitStorage.get(server, id).orElseThrow().size() != 2) throw new AssertionError("persist"); player.getInventory().clearContent(); new CustomFreeForAllMinigame(1, 1, 1, 1, id).giveKit(context.getLevel(), player); if (count(player, Items.DIAMOND) != 2 || count(player, Items.EMERALD) != 3 || !KitStorage.delete(server, id)) throw new AssertionError("editable kit"); player.getInventory().clearContent(); if (!KitStorage.give(server, builtin, java.util.List.of(player)) || count(player, Items.IRON_SWORD) != 1) throw new AssertionError("builtin kit"); } finally { KitStorage.delete(server, id); KitStorage.delete(server, builtin); }
        context.succeed();
    }
    private static int count(ServerPlayer player, Item item) { int count = 0; for (int i = 0; i < player.getInventory().getContainerSize(); i++) { ItemStack stack = player.getInventory().getItem(i); if (stack.is(item)) count += stack.getCount(); } return count; }
}
