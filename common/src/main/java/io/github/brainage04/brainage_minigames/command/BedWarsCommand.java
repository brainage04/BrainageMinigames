package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsMenus;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUltimates;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUltimates.Ultimate;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /minigames bedwars quickbuy}: the Bed Wars Quick Buy editor, and {@code /minigames bedwars
 * hotbar}: the Hotbar Manager, as Hypixel's Bed Wars Settings; {@code /minigames bedwars ultimate
 * [ultimate]}: the Ultimate picker, or picks one.
 */
public final class BedWarsCommand {
    private BedWarsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("bedwars")
                .then(literal("quickbuy").executes(context -> {
                    BedWarsMenus.openEditor(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(literal("hotbar").executes(context -> {
                    BedWarsMenus.openHotbar(context.getSource().getPlayerOrException(), null, null);
                    return 1;
                }))
                .then(literal("ultimate")
                        .executes(context -> {
                            BedWarsMenus.openUltimates(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(argument("ultimate", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Ultimate.values()).map(Ultimate::id), builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Ultimate ultimate = Ultimate.of(StringArgumentType.getString(context, "ultimate"))
                                            .orElseThrow(() -> new SimpleCommandExceptionType(
                                                    Component.literal("No such ultimate.")).create());
                                    BedWarsUltimates.select(player.level().getServer(), player.getUUID(), ultimate);
                                    player.sendSystemMessage(Component.literal("Ultimate: " + ultimate.displayName
                                            + " (from your next Bed Wars Ultimate match)").withStyle(ChatFormatting.GREEN));
                                    return 1;
                                })));
    }
}
