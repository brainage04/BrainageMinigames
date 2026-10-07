package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Hypixel's SkyWars "Kits &amp; Perks": the kit menu (left-click selects an unlocked kit) and the
 * perk toggles (every unlocked perk is active unless turned off), per mode. Choices are saved per
 * player and apply from the next match's cage opening.
 */
public final class SkyWarsMenus {
    public static final String TITLE = "Kits & Perks";

    private SkyWarsMenus() {}

    /** The kits and perks menu, with "Go Back" to the main menu. */
    public static void open(ServerPlayer player) {
        open(player, MainMenu.TITLE, MainMenu::open);
    }

    /** The kits and perks menu, with "Go Back" to {@code parent}. */
    public static void open(ServerPlayer player, String parentTitle, Menu.Screen parent) {
        SkyWarsMode mode = SkyWarsGame.MODE;
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(TITLE, 4);
        SkyWarsKit selected = SkyWarsProgression.selectedKit(server, player.getUUID(), mode);
        menu.set(
                Menu.slot(1, 3),
                Icon.of(Items.IRON_SWORD)
                        .name(kitsTitle(mode), ChatFormatting.GREEN)
                        .text("Selection of unique kits for " + mode.displayName + " games.")
                        .blank()
                        .value("Selected", selected.name(), selected.rarity().color)
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> kits(clicker, 0, parentTitle, parent));
        long active = SkyWarsPerk.of(mode).stream()
                .filter(perk -> SkyWarsProgression.active(server, player.getUUID(), perk))
                .count();
        menu.set(
                Menu.slot(1, 5),
                Icon.of(Items.CAULDRON)
                        .name(perksTitle(mode), ChatFormatting.GREEN)
                        .text("All perks you own in this mode are enabled simultaneously.")
                        .blank()
                        .text("You can choose to disable any you don't want to have active.")
                        .blank()
                        .value("Active", active + "/" + SkyWarsPerk.of(mode).size(), ChatFormatting.AQUA)
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> perks(clicker, 0, parentTitle, parent));
        menu.back(parentTitle, parent).close();
        menu.open(player);
    }

    static String kitsTitle(SkyWarsMode mode) {
        return mode.displayName + " Kits";
    }

    static String perksTitle(SkyWarsMode mode) {
        return "Toggle " + mode.displayName + " Perks";
    }

    static void kits(ServerPlayer player, int page, String parentTitle, Menu.Screen parent) {
        SkyWarsMode mode = SkyWarsGame.MODE;
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(kitsTitle(mode), 6);
        SkyWarsKit selected = SkyWarsProgression.selectedKit(server, player.getUUID(), mode);
        menu.page(
                SkyWarsKits.of(mode),
                page,
                Menu.inner(1, 4),
                (target, slot, kit) -> {
                    boolean owned = SkyWarsProgression.owns(server, player.getUUID(), kit);
                    Icon icon = Icon.of(kit.icon()).name(kit.name(), kit.rarity().color);
                    for (Component line : kit.contents(server.registryAccess())) {
                        icon.line(line.copy());
                    }
                    icon.blank().value("Rarity", kit.rarity().label, kit.rarity().color);
                    if (kit == selected) {
                        icon.line(Component.literal("SELECTED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).glint(true);
                    } else if (owned) {
                        icon.line(Component.literal("UNLOCKED").withStyle(ChatFormatting.GREEN)).blank()
                                .action("Left-click to select!");
                    } else {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED))
                                .refusal("An operator can unlock it with /minigames skywars grant.");
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (!SkyWarsProgression.owns(server, clicker.getUUID(), kit)) {
                            throw new MatchException("You have not unlocked " + kit.name() + ".");
                        }
                        SkyWarsProgression.selectKit(server, clicker.getUUID(), kit);
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
        SkyWarsMode mode = SkyWarsGame.MODE;
        MinecraftServer server = player.level().getServer();
        boolean upgraded = SkyWarsProgression.maxPerks(server);
        Menu menu = new Menu(perksTitle(mode), 6);
        menu.page(
                SkyWarsPerk.of(mode),
                page,
                Menu.inner(1, 3),
                (target, slot, perk) -> {
                    boolean owned = SkyWarsProgression.owns(server, player.getUUID(), perk);
                    boolean enabled = SkyWarsProgression.enabled(server, player.getUUID(), perk);
                    Icon icon = Icon.of(perk.icon())
                            .name(perk.name(), perk.rarity().color)
                            .line(Component.literal("Perk").withStyle(ChatFormatting.DARK_GRAY))
                            .blank()
                            .text(perk.description());
                    if (perk.upgrade() != null) {
                        icon.blank()
                                .line(Component.literal(perk.upgrade().name() + " Upgrade").withStyle(ChatFormatting.GOLD))
                                .text(perk.upgrade().description())
                                .value("Upgrade", upgraded ? "MAXED" : "not owned", upgraded ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    }
                    icon.blank().value("Rarity", perk.rarity().label, perk.rarity().color).blank();
                    if (!owned) {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED))
                                .refusal("An operator can unlock it with /minigames skywars grant.");
                    } else {
                        icon.line(enabled
                                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED))
                                .blank()
                                .action("Click to toggle!")
                                .glint(enabled);
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (!SkyWarsProgression.owns(server, clicker.getUUID(), perk)) {
                            throw new MatchException("You have not unlocked " + perk.name() + ".");
                        }
                        SkyWarsProgression.setEnabled(server, clicker.getUUID(), perk,
                                !SkyWarsProgression.enabled(server, clicker.getUUID(), perk));
                        perks(clicker, page, parentTitle, parent);
                    });
                },
                (clicker, shown) -> perks(clicker, shown, parentTitle, parent));
        menu.back(TITLE, clicker -> open(clicker, parentTitle, parent)).close();
        menu.open(player);
    }
}
