package io.github.brainage04.brainage_minigames.uhc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class UhcCommand {
    private UhcCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerCommands(dispatcher);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("uhc")
                .executes(context -> status(context.getSource()))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("open")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> result(context.getSource(), UhcManager.openLobby(context.getSource().getServer()),
                                "Opened the UHC lobby.", "A minigame is already running or the UHC dimension is unavailable.")))
                .then(literal("start")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> result(context.getSource(), UhcManager.startCountdown(context.getSource().getServer()),
                                "Started the UHC countdown.", "The lobby needs at least two players.")))
                .then(literal("stop")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> result(context.getSource(), UhcManager.stop(context.getSource().getServer()),
                                "Stopped the UHC.", "There is no UHC session running.")))
                .then(literal("join").executes(context -> join(context.getSource())))
                .then(literal("leave").executes(context -> leave(context.getSource()))));
    }

    private static int join(CommandSourceStack source) throws CommandSyntaxException {
        return result(source, UhcManager.join(source.getPlayerOrException()),
                "Joined the UHC lobby.", "You cannot join the UHC lobby now.");
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        return result(source, UhcManager.leave(source.getPlayerOrException()),
                "Left the UHC.", "You are not participating in the UHC.");
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("UHC: state=%s, players=%d, elapsed=%ds"
                .formatted(UhcManager.state(), UhcManager.participantCount(), UhcManager.elapsedSeconds()))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int result(CommandSourceStack source, boolean success, String successMessage, String failureMessage) {
        if (success) {
            source.sendSuccess(() -> Component.literal(successMessage).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal(failureMessage).withStyle(ChatFormatting.RED));
        return 0;
    }
}
