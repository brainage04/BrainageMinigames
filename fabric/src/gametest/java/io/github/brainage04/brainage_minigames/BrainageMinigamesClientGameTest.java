package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;

@SuppressWarnings("UnstableApiUsage")
public final class BrainageMinigamesClientGameTest implements FabricClientGameTest {
    private static final String CLASSIC_KIT = "kits/classic";

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
