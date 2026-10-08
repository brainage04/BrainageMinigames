package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import io.github.brainage04.brainage_minigames.menu.MainMenu;
import io.github.brainage04.brainage_minigames.menu.SkyWarsMenus;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /minigames skywars}: the kits and perks menu, kit choice, perk toggles or perk slots and
 * operator grants. The commands under {@code skywars} itself are Insane's (also played by Lucky
 * Block SkyWars); {@code skywars mini ...} and {@code skywars mega ...} are those modes'.
 */
public final class SkyWarsCommand {
    private static final String CLEAR = "clear";

    private SkyWarsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("skywars")
                .executes(context -> {
                    SkyWarsMenus.open(context.getSource().getPlayerOrException());
                    return 1;
                });
        modeCommands(root, SkyWarsMode.INSANE);
        for (SkyWarsMode mode : List.of(SkyWarsMode.MINI, SkyWarsMode.MEGA)) {
            LiteralArgumentBuilder<CommandSourceStack> node = literal(mode.id)
                    .executes(context -> {
                        SkyWarsMenus.open(context.getSource().getPlayerOrException(), mode, MainMenu.TITLE, MainMenu::open);
                        return 1;
                    });
            modeCommands(node, mode);
            root.then(node);
        }
        return root;
    }

    /** {@code kit}, {@code perk}, {@code grant} and {@code revoke} for one mode. */
    private static void modeCommands(LiteralArgumentBuilder<CommandSourceStack> node, SkyWarsMode mode) {
        node.then(literal("kit")
                .executes(context -> kits(context.getSource(), mode))
                .then(argument("kit", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(kitIds(mode), builder))
                        .executes(context -> selectKit(context.getSource(), mode, StringArgumentType.getString(context, "kit")))));
        if (mode.perkChoice == SkyWarsMode.PerkChoice.TOGGLE) {
            node.then(literal("perk")
                    .executes(context -> perks(context.getSource(), mode))
                    .then(argument("perk", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(perkIds(mode), builder))
                            .then(argument("enabled", BoolArgumentType.bool())
                                    .executes(context -> togglePerk(context.getSource(), mode,
                                            StringArgumentType.getString(context, "perk"),
                                            BoolArgumentType.getBool(context, "enabled"))))));
        } else {
            node.then(literal("perk")
                    .executes(context -> slots(context.getSource(), mode))
                    .then(argument("slot", IntegerArgumentType.integer(1, mode.perkSlots))
                            .then(argument("perk", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            Stream.concat(perkIds(mode), Stream.of(CLEAR)), builder))
                                    .executes(context -> setSlot(context.getSource(), mode,
                                            IntegerArgumentType.getInteger(context, "slot"),
                                            StringArgumentType.getString(context, "perk"))))));
        }
        node.then(grantNode("grant", mode, true));
        node.then(grantNode("revoke", mode, false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grantNode(String name, SkyWarsMode mode, boolean owned) {
        LiteralArgumentBuilder<CommandSourceStack> node = literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        var targets = argument("targets", EntityArgument.players())
                .then(literal("kit").then(argument("kit", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(kitIds(mode), builder))
                        .executes(context -> grant(context.getSource(), mode, EntityArgument.getPlayers(context, "targets"),
                                "kit", StringArgumentType.getString(context, "kit"), owned))));
        targets.then(literal("perk").then(argument("perk", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(perkIds(mode), builder))
                .executes(context -> grant(context.getSource(), mode, EntityArgument.getPlayers(context, "targets"),
                        "perk", StringArgumentType.getString(context, "perk"), owned))));
        return node.then(targets);
    }

    private static Stream<String> kitIds(SkyWarsMode mode) {
        return SkyWarsKits.of(mode).stream().map(SkyWarsKit::id);
    }

    private static Stream<String> perkIds(SkyWarsMode mode) {
        return SkyWarsPerk.choosable(mode).stream().map(SkyWarsPerk::id);
    }

    /** The command prefix of the mode's commands. */
    private static String prefix(SkyWarsMode mode) {
        return "/minigames skywars" + (mode == SkyWarsMode.INSANE ? "" : " " + mode.id);
    }

    private static int kits(CommandSourceStack source, SkyWarsMode mode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        SkyWarsKit selected = SkyWarsProgression.selectedKit(server, player.getUUID(), mode);
        StringBuilder owned = new StringBuilder();
        for (SkyWarsKit kit : SkyWarsKits.of(mode)) {
            if (SkyWarsProgression.owns(server, player.getUUID(), kit)) {
                owned.append(owned.isEmpty() ? "" : ", ").append(kit.id());
            }
        }
        source.sendSuccess(() -> Component.literal(mode.displayName + " kits you own: " + owned
                + ". Selected: " + selected.id() + ". Choose one with " + prefix(mode) + " kit <kit>."), false);
        return 1;
    }

    private static int selectKit(CommandSourceStack source, SkyWarsMode mode, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkyWarsKit kit = SkyWarsKits.find(mode, id);
        if (kit == null) {
            source.sendFailure(Component.literal("Unknown " + mode.displayName + " kit " + id + "."));
            return 0;
        }
        if (!SkyWarsProgression.owns(source.getServer(), player.getUUID(), kit)) {
            source.sendFailure(Component.literal("You have not unlocked " + kit.name() + "."));
            return 0;
        }
        SkyWarsProgression.selectKit(source.getServer(), player.getUUID(), kit);
        source.sendSuccess(() -> Component.literal("Selected " + kit.name() + " for your next " + mode.displayName
                + " SkyWars match.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int perks(CommandSourceStack source, SkyWarsMode mode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        for (SkyWarsPerk perk : SkyWarsPerk.choosable(mode)) {
            boolean owned = SkyWarsProgression.owns(server, player.getUUID(), perk);
            boolean on = SkyWarsProgression.enabled(server, player.getUUID(), perk);
            source.sendSuccess(() -> Component.literal(perk.id() + ": ")
                    .append(!owned ? Component.literal("LOCKED").withStyle(ChatFormatting.GRAY)
                            : on ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                            : Component.literal("DISABLED").withStyle(ChatFormatting.RED)), false);
        }
        return 1;
    }

    private static int togglePerk(CommandSourceStack source, SkyWarsMode mode, String id, boolean enabled)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkyWarsPerk perk = SkyWarsPerk.find(mode, id);
        if (perk == null || perk.global()) {
            source.sendFailure(Component.literal("Unknown " + mode.displayName + " perk " + id + "."));
            return 0;
        }
        SkyWarsProgression.setEnabled(source.getServer(), player.getUUID(), perk, enabled);
        source.sendSuccess(() -> Component.literal(perk.name() + (enabled ? " enabled" : " disabled")
                + " for your next SkyWars match."), false);
        return 1;
    }

    /** Lists the mode's perk slots and global perks. */
    private static int slots(CommandSourceStack source, SkyWarsMode mode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        int usable = mode.usableSlots(SkyWarsProgression.maxPerks(server));
        List<@Nullable SkyWarsPerk> slots = SkyWarsProgression.slots(server, player.getUUID(), mode);
        for (int index = 0; index < usable; index++) {
            SkyWarsPerk perk = slots.get(index);
            int number = index + 1;
            source.sendSuccess(() -> Component.literal("Perk slot #" + number + ": "
                    + (perk == null ? "empty" : perk.id())), false);
        }
        String global = String.join(", ", SkyWarsPerk.global(mode).stream().map(SkyWarsPerk::id).toList());
        source.sendSuccess(() -> Component.literal("Global perks: " + global + "."), false);
        source.sendSuccess(() -> Component.literal("Change one with " + prefix(mode) + " perk <slot> <perk|clear>."), false);
        return 1;
    }

    private static int setSlot(CommandSourceStack source, SkyWarsMode mode, int slot, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkyWarsPerk perk = id.equals(CLEAR) ? null : SkyWarsPerk.find(mode, id);
        if (perk == null && !id.equals(CLEAR)) {
            source.sendFailure(Component.literal("Unknown " + mode.displayName + " perk " + id + "."));
            return 0;
        }
        return MinigamesCommand.run(source, () -> {
            SkyWarsProgression.setSlot(source.getServer(), player.getUUID(), mode, slot - 1, perk);
            source.sendSuccess(() -> Component.literal("Perk slot #" + slot + ": "
                    + (perk == null ? "empty" : perk.name()) + ", from your next " + mode.displayName + " SkyWars match.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        });
    }

    private static int grant(CommandSourceStack source, SkyWarsMode mode, Collection<ServerPlayer> targets, String kind,
            String id, boolean owned) {
        SkyWarsKit kit = kind.equals("kit") ? SkyWarsKits.find(mode, id) : null;
        SkyWarsPerk perk = kind.equals("perk") ? SkyWarsPerk.find(mode, id) : null;
        if (kit == null && (perk == null || perk.global())) {
            source.sendFailure(Component.literal("Unknown " + mode.displayName + " " + kind + " " + id + "."));
            return 0;
        }
        String key = kit != null ? SkyWarsProgression.entry(kit) : SkyWarsProgression.entry(perk);
        for (ServerPlayer target : targets) {
            SkyWarsProgression.setOwned(source.getServer(), target.getUUID(), key, owned);
        }
        source.sendSuccess(() -> Component.literal((owned ? "Granted " : "Revoked ") + mode.displayName + " " + kind + " "
                + id + " for " + targets.size() + (targets.size() == 1 ? " player." : " players.")), true);
        return targets.size();
    }
}
