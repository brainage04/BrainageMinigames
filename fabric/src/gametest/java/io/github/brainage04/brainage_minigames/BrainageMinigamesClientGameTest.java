package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.feedback.FeedbackLog;
import io.github.brainage04.brainage_minigames.hub.Hub;
import io.github.brainage04.brainage_minigames.menu.MainMenu;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;

@SuppressWarnings("UnstableApiUsage")
public final class BrainageMinigamesClientGameTest implements FabricClientGameTest {
    private static final String CLASSIC_KIT = "kits/classic";
    private static final String FEEDBACK = "client GameTest feedback";

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "Brainage Minigames scoreboard GameTest",
                server -> {
                    try {
                        server.runOnServer(BrainageMinigamesClientGameTest::prepareMinigameState);
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.waitTicks(20);
                        assertClientState(context);
                        server.runOnServer(BrainageMinigamesClientGameTest::assertPlayerInHub);
                        context.runOnClient(client -> client.player.connection.sendCommand("feedback " + FEEDBACK));
                        context.waitTicks(10);
                        server.runOnServer(BrainageMinigamesClientGameTest::assertFeedbackStored);
                        clickThroughMenu(context, server);

                        ClientGameTestRecorder.startRecording(context);
                        ClientGameTestRecorder.showStep(
                                context,
                                "minigames.classic-kit",
                                "Classic minigame kit",
                                "The bundled classic kit is granted to the player and rendered in the hotbar");
                        context.waitTicks(40);
                        ClientGameTestRecorder.showStep(
                                context,
                                "minigames.games-won",
                                "Games Won scoreboard",
                                "A won match increments the visible Games Won sidebar score");
                        context.waitTicks(50);
                    } finally {
                        server.runOnServer(BrainageMinigamesClientGameTest::cleanupMinigameState);
                        ;
                    }
                });
    }

    /** The dedicated server built a hub at the spawn of its new world, where the player arrived. */
    private static void assertPlayerInHub(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (!Hub.enabled() || !Hub.inHub(player)) {
            throw new AssertionError("Expected a new world to get a hub at its spawn with the player in it.");
        }
        if (player.gameMode() != GameType.ADVENTURE) {
            throw new AssertionError("Expected adventure mode in the hub, found " + player.gameMode() + ".");
        }
    }

    /**
     * {@code /minigames} opens the game menu on a vanilla client; swapping or throwing one of its
     * items leaves both inventories untouched, and clicking a button opens the next menu.
     */
    private static void clickThroughMenu(ClientGameTestContext context, TestDedicatedServerContext server) {
        context.runOnClient(client -> client.player.connection.sendCommand("minigames"));
        context.waitTicks(10);
        assertScreenTitle(context, MainMenu.TITLE);
        context.runOnClient(client -> {
            int containerId = client.player.containerMenu.containerId;
            client.gameMode.handleContainerInput(containerId, 10, 0, ContainerInput.SWAP, client.player);
            client.gameMode.handleContainerInput(containerId, 10, 1, ContainerInput.THROW, client.player);
        });
        context.waitTicks(10);
        server.runOnServer(BrainageMinigamesClientGameTest::assertMenuKeptItsItems);
        context.runOnClient(client -> {
            if (client.player.getInventory().contains(stack -> stack.is(Items.DIAMOND_SWORD))
                    || !client.player.getInventory().getSelectedItem().is(Items.IRON_SWORD)) {
                throw new AssertionError("Expected the client's hotbar to be corrected after clicking the menu.");
            }
            client.gameMode.handleContainerInput(
                    client.player.containerMenu.containerId, 10, 0, ContainerInput.PICKUP, client.player);
        });
        context.waitTicks(10);
        assertScreenTitle(context, "Play a Game");
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(5);
    }

    private static void assertScreenTitle(ClientGameTestContext context, String title) {
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                    || !screen.getTitle().getString().equals(title)) {
                throw new AssertionError("Expected the '" + title + "' menu, found " + client.gui.screen() + ".");
            }
        });
    }

    private static void assertMenuKeptItsItems(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (player.getInventory().contains(stack -> stack.is(Items.DIAMOND_SWORD))
                || !player.getInventory().getSelectedItem().is(Items.IRON_SWORD)
                || !player.containerMenu.getCarried().isEmpty()) {
            throw new AssertionError("Expected menu clicks to leave the player's inventory alone.");
        }
    }

    private static void assertFeedbackStored(MinecraftServer server) {
        try {
            Path file = FeedbackLog.file(server);
            if (!Files.exists(file) || Files.readAllLines(file).stream().noneMatch(line -> line.contains(FEEDBACK))) {
                throw new AssertionError("Expected /feedback from the client in " + file + ".");
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not read the feedback file.", exception);
        }
    }

    private static void prepareMinigameState(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        if (!KitStorage.give(
                server, BrainageMinigames.id(CLASSIC_KIT), java.util.List.of(player))) {
            throw new AssertionError("Expected the bundled classic kit to be granted.");
        }
        if (!player.getInventory().getSelectedItem().is(Items.IRON_SWORD)) {
            throw new AssertionError(
                    "Expected the classic kit to put an iron sword in the selected hotbar slot.");
        }

        var scoreboard = server.getScoreboard();
        var objective = ModScoreboard.registerGamesWon(scoreboard);
        int gamesWon = ModScoreboard.incrementGamesWon(scoreboard, player);
        if (gamesWon != 1) {
            throw new AssertionError(
                    "Expected the first win to set Games Won to one, got " + gamesWon + ".");
        }
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }

    private static void assertClientState(ClientGameTestContext context) {
        context.runOnClient(
                client -> {
                    if (!client.player.getInventory().getSelectedItem().is(Items.IRON_SWORD)) {
                        throw new AssertionError(
                                "Expected the classic kit's iron sword to synchronize to the client hotbar.");
                    }
                    var objective =
                            client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                    if (objective == null
                            || !objective.getName().equals(ModScoreboard.GAMES_WON_OBJECTIVE)) {
                        throw new AssertionError(
                                "Expected the Games Won objective to synchronize to the sidebar.");
                    }
                    if (client.level
                                    .getScoreboard()
                                    .getOrCreatePlayerScore(client.player, objective)
                                    .get()
                            != 1) {
                        throw new AssertionError("Expected the visible Games Won score to be one.");
                    }
                });
    }

    private static void cleanupMinigameState(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(ModScoreboard.GAMES_WON_OBJECTIVE);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }
}
