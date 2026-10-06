package io.github.brainage04.brainage_minigames.feedback;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /feedback <message>} records feedback for the server team; {@code /feedback reminders
 * on|off} switches the hourly reminder. Every player may use both.
 */
public final class FeedbackCommand {
    private static final String MESSAGE = "message";

    private FeedbackCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("feedback")
                        .executes(context -> usage(context.getSource()))
                        .then(literal("reminders")
                                .executes(context -> reminderStatus(context.getSource()))
                                .then(literal("on").executes(context -> reminders(context.getSource(), true)))
                                .then(literal("off").executes(context -> reminders(context.getSource(), false))))
                        .then(argument(MESSAGE, StringArgumentType.greedyString())
                                .executes(context -> send(
                                        context.getSource(), StringArgumentType.getString(context, MESSAGE)))));
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Send bugs and ideas with /feedback <message>. /feedback reminders on|off switches the hourly reminder."), false);
        return 1;
    }

    private static int send(CommandSourceStack source, String message) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String trimmed = message.strip();
        if (trimmed.isEmpty()) return usage(source);
        try {
            FeedbackLog.record(player, trimmed);
        } catch (IOException exception) {
            BrainageMinigames.LOGGER.error("Could not store feedback from {}", player.getScoreboardName(), exception);
            source.sendFailure(Component.literal(
                    "Your feedback reached the server log but could not be saved to the feedback file."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Thanks! Your feedback was recorded.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int reminderStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = FeedbackReminders.enabled(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.literal("Feedback reminders are " + (enabled ? "on. " : "off. "))
                .append(enabled ? FeedbackReminders.turnOffButton() : FeedbackReminders.turnOnButton()), false);
        return enabled ? 1 : 0;
    }

    private static int reminders(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FeedbackReminders.setEnabled(source.getServer(), player.getUUID(), enabled);
        source.sendSuccess(() -> enabled
                ? Component.literal("Feedback reminders are on.").withStyle(ChatFormatting.GREEN)
                : Component.literal("Feedback reminders are off; /feedback still works. ")
                        .append(FeedbackReminders.turnOnButton()), false);
        return 1;
    }
}
