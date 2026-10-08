package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcKit;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcMastery;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcPerk;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcProgression;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Hypixel's Speed UHC Shop: the kit menu (click an unlocked kit to select it), the perk toggles
 * (every unlocked perk is on unless turned off) and the Mastery row (click one to make it your
 * single active Mastery). Choices are saved per player and apply from the next match's start.
 */
public final class SpeedUhcMenus {
    public static final String TITLE = "Speed UHC Shop";
    static final String KITS_TITLE = "Speed UHC Kits";
    static final String PERKS_TITLE = "Speed UHC Perks";
    private static final String LOCKED = "An operator can unlock it with /minigames speed_uhc grant.";

    private SpeedUhcMenus() {}

    /** The shop, with "Go Back" to the main menu. */
    public static void open(ServerPlayer player) {
        open(player, MainMenu.TITLE, MainMenu::open);
    }

    /** The shop, with "Go Back" to {@code parent}. */
    public static void open(ServerPlayer player, String parentTitle, Menu.Screen parent) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(TITLE, 6);
        SpeedUhcKit kit = SpeedUhcProgression.selectedKit(server, player.getUUID());
        menu.set(
                Menu.slot(1, 2),
                Icon.of(Items.IRON_SWORD)
                        .name("Kits", ChatFormatting.GREEN)
                        .text("Selection of unique kits for Speed UHC.")
                        .blank()
                        .value("Selected", kit.name(), kit.color())
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> kits(clicker, 0, parentTitle, parent));
        long active = Arrays.stream(SpeedUhcPerk.values())
                .filter(perk -> SpeedUhcProgression.active(server, player.getUUID(), perk))
                .count();
        menu.set(
                Menu.slot(1, 6),
                Icon.of(Items.BREWING_STAND)
                        .name("Perks", ChatFormatting.GREEN)
                        .text("Every perk you own is on; turn off any you don't want.")
                        .blank()
                        .value("Active", active + "/" + SpeedUhcPerk.values().length, ChatFormatting.AQUA)
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> perks(clicker, 0, parentTitle, parent));
        SpeedUhcMastery selected = SpeedUhcProgression.mastery(server, player.getUUID());
        SpeedUhcMastery[] masteries = SpeedUhcMastery.values();
        for (int index = 0; index < masteries.length; index++) {
            SpeedUhcMastery mastery = masteries[index];
            boolean owned = SpeedUhcProgression.owns(server, player.getUUID(), mastery);
            Icon icon = Icon.of(mastery.icon)
                    .name("Mastery " + mastery.displayName, ChatFormatting.GOLD)
                    .text(mastery.description)
                    .blank();
            Icon status;
            if (mastery == selected) {
                icon.line(Component.literal("SELECTED!").withStyle(ChatFormatting.GREEN)).glint(true);
                status = Icon.of(Items.DYE.pick(DyeColor.LIME)).name("Mastery " + mastery.displayName, ChatFormatting.GREEN)
                        .line(Component.literal("SELECTED!").withStyle(ChatFormatting.GREEN));
            } else if (owned) {
                icon.action("Click to select!");
                status = Icon.of(Items.DYE.pick(DyeColor.GREEN)).name("Mastery " + mastery.displayName, ChatFormatting.GREEN)
                        .action("Click to select!");
            } else {
                icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED)).refusal(LOCKED);
                status = Icon.of(Items.DYE.pick(DyeColor.GRAY)).name("Mastery " + mastery.displayName, ChatFormatting.GRAY)
                        .value("Cost", String.format(Locale.ROOT, "%,d Coins", SpeedUhcMastery.COST), ChatFormatting.GOLD)
                        .refusal(LOCKED);
            }
            Menu.Action select = (clicker, click) -> {
                if (!SpeedUhcProgression.owns(server, clicker.getUUID(), mastery)) {
                    throw new MatchException("You have not unlocked " + mastery.displayName + ".");
                }
                SpeedUhcProgression.selectMastery(server, clicker.getUUID(), mastery);
                clicker.sendSystemMessage(Component.literal("Selected the " + mastery.displayName + " Mastery.")
                        .withStyle(ChatFormatting.GREEN));
                open(clicker, parentTitle, parent);
            };
            menu.set(Menu.slot(3, index), icon, select);
            menu.set(Menu.slot(4, index), status, select);
        }
        menu.back(parentTitle, parent).close();
        menu.open(player);
    }

    static void kits(ServerPlayer player, int page, String parentTitle, Menu.Screen parent) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(KITS_TITLE, 6);
        SpeedUhcKit selected = SpeedUhcProgression.selectedKit(server, player.getUUID());
        menu.page(
                SpeedUhcKit.ALL,
                page,
                Menu.inner(1, 4),
                (target, slot, kit) -> {
                    boolean owned = SpeedUhcProgression.owns(server, player.getUUID(), kit);
                    Icon icon = Icon.of(kit.icon()).name(kit.name(), kit.color());
                    for (Component line : kit.contents(server.registryAccess())) icon.line(line.copy());
                    icon.blank();
                    if (kit.rarity() != null) {
                        icon.value("Rarity", kit.rarity().label, kit.rarity().color)
                                .value("Cost", String.valueOf(kit.cost()), ChatFormatting.GOLD)
                                .blank();
                    }
                    if (kit == selected) {
                        icon.line(Component.literal("SELECTED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).glint(true);
                    } else if (owned) {
                        icon.line(Component.literal("UNLOCKED").withStyle(ChatFormatting.GREEN)).blank()
                                .action("Click to select!");
                    } else {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED)).refusal(LOCKED);
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (!SpeedUhcProgression.owns(server, clicker.getUUID(), kit)) {
                            throw new MatchException("You have not unlocked " + kit.name() + ".");
                        }
                        SpeedUhcProgression.selectKit(server, clicker.getUUID(), kit);
                        clicker.sendSystemMessage(Component.literal("Selected " + kit.name() + ".")
                                .withStyle(ChatFormatting.GREEN));
                        kits(clicker, page, parentTitle, parent);
                    });
                },
                (clicker, shown) -> kits(clicker, shown, parentTitle, parent));
        menu.back(TITLE, clicker -> open(clicker, parentTitle, parent)).close();
        menu.open(player);
    }

    static void perks(ServerPlayer player, int page, String parentTitle, Menu.Screen parent) {
        MinecraftServer server = player.level().getServer();
        boolean maxed = SpeedUhcProgression.maxPerks(server);
        Menu menu = new Menu(PERKS_TITLE, 6);
        menu.page(
                List.of(SpeedUhcPerk.values()),
                page,
                Menu.inner(1, 4),
                (target, slot, perk) -> {
                    boolean owned = SpeedUhcProgression.owns(server, player.getUUID(), perk);
                    boolean enabled = SpeedUhcProgression.enabled(server, player.getUUID(), perk);
                    Icon icon = Icon.of(perk.icon)
                            .name(perk.title(maxed), perk.rarity.color)
                            .text(perk.description(maxed))
                            .blank()
                            .value("Rarity", perk.rarity.label, perk.rarity.color)
                            .value("Cost", perk.cost + " Coins", ChatFormatting.GOLD)
                            .blank();
                    if (!owned) {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED)).refusal(LOCKED);
                    } else {
                        icon.line(enabled
                                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED))
                                .blank()
                                .action("Click to toggle!")
                                .glint(enabled);
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (!SpeedUhcProgression.owns(server, clicker.getUUID(), perk)) {
                            throw new MatchException("You have not unlocked " + perk.displayName + ".");
                        }
                        SpeedUhcProgression.setEnabled(server, clicker.getUUID(), perk,
                                !SpeedUhcProgression.enabled(server, clicker.getUUID(), perk));
                        perks(clicker, page, parentTitle, parent);
                    });
                },
                (clicker, shown) -> perks(clicker, shown, parentTitle, parent));
        menu.back(TITLE, clicker -> open(clicker, parentTitle, parent)).close();
        menu.open(player);
    }
}
