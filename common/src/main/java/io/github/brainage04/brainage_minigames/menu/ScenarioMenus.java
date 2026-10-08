package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The UHC scenarios: world-wide gamerules shared by every UHC-style game, shown from those games'
 * settings and toggled there by operators.
 */
final class ScenarioMenus {
    static final String TITLE = "UHC Scenarios";

    /** A scenario gamerule: its id path in the {@code brainage_minigames} namespace. */
    record Entry(String name, GameRule<?> rule, String description) {}

    static final List<Entry> ENTRIES = UhcResourceScenarios.RULES.stream()
            .map(rule -> new Entry(rule.name(), rule.rule(), rule.description()))
            .toList();

    private ScenarioMenus() {}

    /** The settings-page icon opening {@link #scenarios}: which scenarios are on. */
    static Icon icon(MinecraftServer server) {
        Icon icon = Icon.of(Items.ENCHANTED_BOOK).name(TITLE, ChatFormatting.GREEN)
                .text("Rules every UHC, Meetup and FinalUHC match plays with.")
                .blank();
        for (Entry entry : ENTRIES) {
            if (!isDefault(server, entry)) icon.value(PlayMenus.title(entry.name().replaceFirst("^uhc_", "")), value(server, entry), ChatFormatting.AQUA);
        }
        return icon.action("Click to view!");
    }

    static void scenarios(ServerPlayer player, Menu.Screen parent, String parentTitle, int page) {
        MinecraftServer server = player.level().getServer();
        boolean operator = MatchService.isOperator(player);
        Menu menu = new Menu(TITLE, 6);
        menu.set(
                4,
                Icon.of(Items.BOOK)
                        .name(TITLE, ChatFormatting.GREEN)
                        .text("Server-wide gamerules for every UHC, Meetup and FinalUHC match, applied at once.")
                        .blank()
                        .text(operator
                                ? "Left-click toggles or +1, right-click -1, shift-left +10, shift-right resets."
                                : "Only operators change scenarios."));
        menu.page(
                ENTRIES,
                page,
                Menu.inner(1, 4),
                (target, slot, entry) -> {
                    boolean toggle = entry.rule().defaultValue() instanceof Boolean;
                    boolean on = toggle && (Boolean) server.getGameRules().get(entry.rule());
                    Icon icon = Icon.of(toggle ? Items.DYE.pick(on ? DyeColor.LIME : DyeColor.GRAY) : Items.REPEATER)
                            .name(PlayMenus.title(entry.name().replaceFirst("^uhc_", "")), ChatFormatting.GREEN)
                            .text(entry.description())
                            .blank()
                            .line(Component.literal("Currently: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(value(server, entry))
                                            .withStyle(toggle && !on ? ChatFormatting.RED : ChatFormatting.GREEN)))
                            .value("Gamerule", "brainage_minigames:" + entry.name(), ChatFormatting.DARK_GRAY)
                            .value("Default", String.valueOf(entry.rule().defaultValue()), ChatFormatting.GRAY);
                    if (operator) icon.blank().action(toggle ? (on ? "Click to disable!" : "Click to enable!") : "Click to change!");
                    target.set(slot, icon, operator
                            ? (clicker, click) -> {
                                change(clicker, entry, click);
                                scenarios(clicker, parent, parentTitle, page);
                            }
                            : null);
                },
                (clicker, shown) -> scenarios(clicker, parent, parentTitle, shown));
        menu.back(parentTitle, parent).close();
        menu.open(player);
    }

    private static String value(MinecraftServer server, Entry entry) {
        Object value = server.getGameRules().get(entry.rule());
        return value instanceof Boolean on ? (on ? "ENABLED" : "DISABLED") : String.valueOf(value);
    }

    private static boolean isDefault(MinecraftServer server, Entry entry) {
        return server.getGameRules().get(entry.rule()).equals(entry.rule().defaultValue());
    }

    @SuppressWarnings("unchecked")
    private static void change(ServerPlayer player, Entry entry, Click click) throws MatchException {
        if (!MatchService.isOperator(player)) throw new MatchException("Only operators change scenarios.");
        MinecraftServer server = player.level().getServer();
        GameRules rules = server.getGameRules();
        if (entry.rule().defaultValue() instanceof Boolean) {
            GameRule<Boolean> rule = (GameRule<Boolean>) entry.rule();
            rules.set(rule, click == Click.SHIFT_RIGHT ? rule.defaultValue() : !rules.get(rule), server);
            return;
        }
        GameRule<Integer> rule = (GameRule<Integer>) entry.rule();
        int value = rules.get(rule);
        int next = click == Click.SHIFT_RIGHT ? rule.defaultValue()
                : click == Click.SHIFT_LEFT ? value + 10
                : click.right() ? value - 1
                : value + 1;
        // The rule's own codec range, as /gamerule enforces it.
        if (rule.deserialize(String.valueOf(next)).isSuccess()) rules.set(rule, next, server);
    }
}
