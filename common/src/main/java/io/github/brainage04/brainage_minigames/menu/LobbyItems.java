package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/** The hotbar of a player waiting in a lobby: vote to start, the game menu, and leave. */
public final class LobbyItems {
    static final int VOTE_SLOT = 0;
    static final int MENU_SLOT = 4;
    static final int LEAVE_SLOT = 8;

    private LobbyItems() {}

    static ItemStack voteItem() {
        return MenuItems.mark(
                Icon.of(Items.DYE.pick(DyeColor.LIME))
                        .name("Vote to Start", ChatFormatting.GREEN)
                        .text("Start now instead of waiting for a full lobby. Once most players here"
                                + " vote, the match starts; bots fill the empty slots where the game"
                                + " has them.")
                        .blank()
                        .action("Right-click to vote!")
                        .build(),
                MenuItems.Kind.VOTE_START);
    }

    static ItemStack leaveItem() {
        return MenuItems.mark(
                Icon.of(Items.BED.pick(DyeColor.RED))
                        .name("Leave", ChatFormatting.RED)
                        .text("Leave the lobby and get your inventory back.")
                        .blank()
                        .action("Right-click to leave!")
                        .build(),
                MenuItems.Kind.LEAVE);
    }

    /** Gives a player who just entered a lobby its hotbar items; their inventory is empty there. */
    public static void give(ServerPlayer player) {
        player.getInventory().setItem(VOTE_SLOT, voteItem());
        player.getInventory().setItem(MENU_SLOT, MenuItems.gameMenu());
        player.getInventory().setItem(LEAVE_SLOT, leaveItem());
    }

    static void voteStart(ServerPlayer player) {
        run(player, () -> {
            Match match = MatchManager.matchOf(player.getUUID())
                    .orElseThrow(() -> new MatchException("You are not in a lobby."));
            match.voteStart(player);
        });
    }

    static void leave(ServerPlayer player) {
        run(player, () -> {
            Match match = MatchManager.leave(player);
            player.sendSystemMessage(Component.literal("You left ").append(match.title()).append("."));
        });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws MatchException;
    }

    private static void run(ServerPlayer player, Action action) {
        try {
            action.run();
        } catch (MatchException exception) {
            player.sendSystemMessage(
                    Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    /** The lobby the player waits in, if any. */
    static Optional<Match> lobbyOf(ServerPlayer player) {
        return MatchManager.matchOf(player.getUUID()).filter(match -> match.isWaiting(player.getUUID()));
    }
}
