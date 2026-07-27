package io.github.brainage04.brainage_minigames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class KitCommand {
    private static final String KIT_ARGUMENT = "kit";

    private KitCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerCommands(dispatcher);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("minigames")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("kit")
                        .then(literal("list").executes(context -> list(context.getSource())))
                        .then(literal("edit")
                                .then(kitArgument().executes(context -> edit(
                                        context.getSource(),
                                        IdentifierArgument.getId(context, KIT_ARGUMENT)
                                ))))
                        .then(literal("delete")
                                .then(kitArgument().executes(context -> delete(
                                        context.getSource(),
                                        IdentifierArgument.getId(context, KIT_ARGUMENT)
                                ))))
                        .then(literal("give")
                                .then(kitArgument()
                                        .executes(context -> give(
                                                context.getSource(),
                                                IdentifierArgument.getId(context, KIT_ARGUMENT),
                                                List.of(context.getSource().getPlayerOrException())
                                        ))
                                        .then(argument("players", EntityArgument.players())
                                                .executes(context -> give(
                                                        context.getSource(),
                                                        IdentifierArgument.getId(context, KIT_ARGUMENT),
                                                        EntityArgument.getPlayers(context, "players")
                                                )))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> kitArgument() {
        return argument(KIT_ARGUMENT, IdentifierArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        KitStorage.suggestions(context.getSource().getServer()),
                        builder
                ));
    }

    private static int edit(CommandSourceStack source, Identifier kitId) throws CommandSyntaxException {
        KitStorage.openEditor(source.getPlayerOrException(), kitId);
        source.sendSuccess(() -> Component.literal("Editing " + kitId + "; close the menu to save it.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int delete(CommandSourceStack source, Identifier kitId) {
        if (!KitStorage.delete(source.getServer(), kitId)) {
            source.sendFailure(Component.literal("No editable kit named " + kitId + " exists."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Deleted editable kit " + kitId + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int give(
            CommandSourceStack source,
            Identifier kitId,
            Collection<ServerPlayer> players
    ) {
        if (!KitStorage.give(source.getServer(), kitId, players)) {
            source.sendFailure(Component.literal("No editable kit named " + kitId + " exists."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Gave kit %s to %d player%s."
                        .formatted(kitId, players.size(), players.size() == 1 ? "" : "s")
        ).withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int list(CommandSourceStack source) {
        List<Identifier> ids = KitStorage.ids(source.getServer());
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No editable kits have been saved."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Editable kits: " + String.join(", ", ids.stream()
                .map(Identifier::toString)
                .toList())), false);
        return ids.size();
    }
}
