package io.github.brainage04.brainage_minigames.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;

/**
 * {@code /shout <message>}: chat to everyone on the server, as plain chat does outside a team
 * match; in a team match plain chat only reaches the player's team.
 */
public final class ShoutCommand {
    private ShoutCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("shout")
                        .then(
                                Commands.argument("message", MessageArgument.message())
                                        .executes(
                                                context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    MessageArgument.resolveChatMessage(
                                                            context,
                                                            "message",
                                                            message ->
                                                                    source.getServer()
                                                                            .getPlayerList()
                                                                            .broadcastChatMessage(
                                                                                    message,
                                                                                    source,
                                                                                    ChatType.bind(
                                                                                            ChatType.CHAT,
                                                                                            source)));
                                                    return 1;
                                                })));
    }
}
