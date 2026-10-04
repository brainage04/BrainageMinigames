package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcKits;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import io.github.brainage04.brainage_minigames.game.uhc.UhcPresets;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCraftPrompts;
import net.minecraft.commands.Commands;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class UhcCommand {
    private UhcCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("uhc")
                .executes(context -> balance(context.getSource()))
                .then(literal("coins").executes(context -> balance(context.getSource())))
                .then(literal("preset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("hypixel").executes(context -> preset(context.getSource(), true)))
                        .then(literal("badlion").executes(context -> preset(context.getSource(), false))))
                .then(literal("craft").then(argument("recipe", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(UhcCrafting.recipes().stream().map(UhcCrafting.Recipe::id), builder))
                        .executes(context -> craft(context.getSource(), StringArgumentType.getString(context, "recipe")))))
                .then(literal("kit").executes(context -> kits(context.getSource()))
                        .then(argument("kit", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("default"), Arrays.stream(UhcKits.Kit.values()).map(kit -> kit.id)), builder))
                                .executes(context -> selectKit(context.getSource(), StringArgumentType.getString(context, "kit")))))
                .then(literal("prestige_bonus").then(argument("kit", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(UhcKits.Kit.values()).map(kit -> kit.id), builder))
                        .executes(context -> prestigeBonuses(context.getSource(), StringArgumentType.getString(context, "kit")))
                        .then(argument("bonus", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    var kit = UhcKits.Kit.find(StringArgumentType.getString(context, "kit"));
                                    return SharedSuggestionProvider.suggest(kit == null ? java.util.List.of() : UhcKits.bonuses(kit).stream().map(UhcKits.Bonus::id).toList(), builder);
                                })
                                .executes(context -> selectPrestige(context.getSource(), StringArgumentType.getString(context, "kit"), StringArgumentType.getString(context, "bonus"))))))
                .then(literal("kit_upgrade").then(argument("kit", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(UhcKits.Kit.values()).map(kit -> kit.id), builder))
                        .then(argument("node", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.List.of("level1", "level2", "level3", "prestige"), builder))
                                .executes(context -> upgradeKit(context.getSource(), StringArgumentType.getString(context, "kit"), StringArgumentType.getString(context, "node"))))))
                .then(literal("trees").executes(context -> trees(context.getSource()))
                        .then(argument("tree", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(UhcProgression.Tree.values()).map(tree -> tree.id), builder))
                                .executes(context -> tree(context.getSource(), StringArgumentType.getString(context, "tree")))))
                .then(literal("unlock").then(argument("tree", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(UhcProgression.Tree.values()).map(tree -> tree.id), builder))
                        .then(argument("node", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    var tree = UhcProgression.Tree.find(StringArgumentType.getString(context, "tree"));
                                    return SharedSuggestionProvider.suggest(tree == null ? java.util.List.of() : UhcProgression.nodes(tree).stream().map(UhcProgression.Node::id).toList(), builder);
                                })
                                .executes(context -> unlock(context.getSource(), StringArgumentType.getString(context, "tree"), StringArgumentType.getString(context, "node"))))));
    }

    private static int balance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal("UHC coins: " + UhcProgression.coins(source.getServer(), player.getUUID()) + ". /minigames uhc trees; /minigames uhc unlock <tree> <node>"), false);
        return 1;
    }

    private static int trees(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("UHC profession trees: " + String.join(", ", Arrays.stream(UhcProgression.Tree.values()).map(tree -> tree.id).toList()) + ". Inspect: /minigames uhc trees <tree>"), false);
        return 1;
    }

    private static int tree(CommandSourceStack source, String id) throws CommandSyntaxException {
        var tree = UhcProgression.Tree.find(id);
        if (tree == null) { source.sendFailure(Component.literal("Unknown UHC tree.")); return 0; }
        ServerPlayer player = source.getPlayerOrException();
        for (var node : UhcProgression.nodes(tree)) {
            String recipe = node.id().startsWith("recipe") ? UhcCrafting.recipes().stream()
                    .filter(r -> r.tree() == tree && r.slot() == Integer.parseInt(node.id().substring(6)))
                    .map(UhcCrafting.Recipe::id).findFirst().orElse("") : "";
            boolean owned = UhcProgression.bought(source.getServer(), player.getUUID(), tree, node.id());
            source.sendSuccess(() -> Component.literal(node.id() + (recipe.isEmpty() ? "" : " (" + recipe + ")") + ": " + (owned ? "unlocked" : node.cost() + " coins; requires " + node.requires())), false);
        }
        return 1;
    }

    private static int unlock(CommandSourceStack source, String id, String node) throws CommandSyntaxException {
        var tree = UhcProgression.Tree.find(id);
        if (tree == null) { source.sendFailure(Component.literal("Unknown UHC tree.")); return 0; }
        String error = UhcProgression.unlock(source.getServer(), source.getPlayerOrException().getUUID(), tree, node);
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("Unlocked " + id + "/" + node + "."), false);
        return 1;
    }

    private static int kits(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal("UHC kits: default (Stone Gear), " + String.join(", ", Arrays.stream(UhcKits.Kit.values()).map(kit -> kit.id).toList()) + ". Selected: " + UhcProgression.selectedKit(source.getServer(), player.getUUID()) + ". Upgrade: /minigames uhc kit_upgrade <kit> <level1|level2|level3|prestige>. Prestige choice: /minigames uhc prestige_bonus <kit>"), false);
        return 1;
    }

    private static int selectKit(CommandSourceStack source, String id) throws CommandSyntaxException {
        if (!id.equals("default") && UhcKits.Kit.find(id) == null) { source.sendFailure(Component.literal("Unknown UHC kit.")); return 0; }
        UhcProgression.selectKit(source.getServer(), source.getPlayerOrException().getUUID(), id.equals("default") ? "stone" : id);
        source.sendSuccess(() -> Component.literal("Selected " + id + " for your next UHC match. Explicit match kit overrides take precedence."), false);
        return 1;
    }

    private static int upgradeKit(CommandSourceStack source, String id, String node) throws CommandSyntaxException {
        var kit = UhcKits.Kit.find(id);
        if (kit == null) { source.sendFailure(Component.literal("Unknown UHC kit.")); return 0; }
        String error = UhcProgression.purchase(source.getServer(), source.getPlayerOrException().getUUID(), "kits/" + id, kit.nodes().stream().filter(n -> n.id().equals(node)).findFirst().orElse(null));
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("Unlocked kit " + id + "/" + node + "."), false);
        return 1;
    }

    private static int prestigeBonuses(CommandSourceStack source, String id) throws CommandSyntaxException {
        var kit = UhcKits.Kit.find(id);
        if (kit == null) { source.sendFailure(Component.literal("Unknown UHC kit.")); return 0; }
        for (var bonus : UhcKits.bonuses(kit)) {
            source.sendSuccess(() -> Component.literal("[Choose " + bonus.id().replace('_', ' ') + "]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/minigames uhc prestige_bonus " + id + " " + bonus.id()))), false);
        }
        return 1;
    }

    private static int selectPrestige(CommandSourceStack source, String id, String bonus) throws CommandSyntaxException {
        var kit = UhcKits.Kit.find(id);
        if (kit == null) { source.sendFailure(Component.literal("Unknown UHC kit.")); return 0; }
        ServerPlayer player = source.getPlayerOrException();
        if (!source.getServer().getGameRules().get(UhcProgression.CHOOSE_PRESTIGE)) {
            source.sendFailure(Component.literal("Prestige bonus selection is disabled.")); return 0;
        }
        if (!UhcKits.prestiged(player, kit)) {
            source.sendFailure(Component.literal("Prestige this kit first.")); return 0;
        }
        var bonuses = UhcKits.bonuses(kit);
        for (int i = 0; i < bonuses.size(); i++) if (bonuses.get(i).id().equals(bonus)) {
            UhcProgression.selectPrestige(source.getServer(), player.getUUID(), kit, i);
            source.sendSuccess(() -> Component.literal("Selected " + bonus + " for " + id + "."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Unknown prestige bonus.")); return 0;
    }
    private static int preset(CommandSourceStack source, boolean hypixel) {
        UhcPresets.apply(source.getServer(), hypixel);
        source.sendSuccess(() -> Component.literal("Applied UHC " + (hypixel ? "Hypixel" : "Badlion")
                + " preset. Individual gamerules can still be changed."), true);
        return 1;
    }
    private static int craft(CommandSourceStack source, String id) throws CommandSyntaxException {
        if (UhcCraftPrompts.open(source.getPlayerOrException(), id)) return 1;
        source.sendFailure(Component.literal("You cannot craft that recipe with your current ingredients and unlocks."));
        return 0;
    }
}
