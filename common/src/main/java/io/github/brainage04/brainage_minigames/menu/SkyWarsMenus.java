package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Hypixel's SkyWars "Kits &amp; Perks", per mode: the kit menu (left-click selects an unlocked kit)
 * and the perk menu, which toggles perks in Insane (every unlocked perk is active unless turned
 * off) and fills perk slots in Mini and Mega, next to the mode's global perks. Choices are saved per
 * player and apply from the next match's cage opening.
 */
public final class SkyWarsMenus {
    public static final String TITLE = "Kits & Perks";
    /** The kit menu icons of each mode, as on Hypixel. */
    private static final List<Item> KIT_ICONS = List.of(Items.IRON_SWORD, Items.WOODEN_SWORD, Items.DIAMOND_SWORD);

    private SkyWarsMenus() {}

    /**
     * Kits &amp; Perks with "Go Back" to the main menu: of the lobby's mode when the player waits in
     * a SkyWars lobby, otherwise of every mode.
     */
    public static void open(ServerPlayer player) {
        SkyWarsMode lobby = MatchManager.matchOf(player.getUUID())
                .map(Match::game)
                .filter(game -> game instanceof SkyWarsGame)
                .map(game -> ((SkyWarsGame) game).mode())
                .orElse(null);
        if (lobby != null) {
            open(player, lobby, MainMenu.TITLE, MainMenu::open);
        } else {
            all(player, MainMenu.TITLE, MainMenu::open);
        }
    }

    /** Kits &amp; Perks of every mode: each mode's kit menu above its perk menu. */
    public static void all(ServerPlayer player, String parentTitle, Menu.Screen parent) {
        Menu menu = new Menu(TITLE, 4);
        Menu.Screen self = clicker -> all(clicker, parentTitle, parent);
        int column = 2;
        for (SkyWarsMode mode : List.of(SkyWarsMode.MINI, SkyWarsMode.INSANE, SkyWarsMode.MEGA)) {
            kitsButton(menu, Menu.slot(1, column), player, mode, self);
            perksButton(menu, Menu.slot(2, column), player, mode, self);
            column += 2;
        }
        menu.back(parentTitle, parent).close();
        menu.open(player);
    }

    /** Kits &amp; Perks of one mode, with "Go Back" to {@code parent}. */
    public static void open(ServerPlayer player, SkyWarsMode mode, String parentTitle, Menu.Screen parent) {
        Menu menu = new Menu(TITLE, 4);
        Menu.Screen self = clicker -> open(clicker, mode, parentTitle, parent);
        kitsButton(menu, Menu.slot(1, 3), player, mode, self);
        perksButton(menu, Menu.slot(1, 5), player, mode, self);
        menu.back(parentTitle, parent).close();
        menu.open(player);
    }

    private static void kitsButton(Menu menu, int slot, ServerPlayer player, SkyWarsMode mode, Menu.Screen root) {
        SkyWarsKit selected = SkyWarsProgression.selectedKit(player.level().getServer(), player.getUUID(), mode);
        menu.set(
                slot,
                Icon.of(KIT_ICONS.get(mode.ordinal()))
                        .name(mode.kitsTitle(), ChatFormatting.GREEN)
                        .text("Selection of unique kits for " + mode.displayName + " games.")
                        .blank()
                        .value("Selected", selected.name(), selected.color())
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> kits(clicker, mode, 0, root));
    }

    private static void perksButton(Menu menu, int slot, ServerPlayer player, SkyWarsMode mode, Menu.Screen root) {
        MinecraftServer server = player.level().getServer();
        Icon icon = Icon.of(Items.CAULDRON).name(mode.perksTitle(), ChatFormatting.GREEN);
        if (mode.perkChoice == SkyWarsMode.PerkChoice.TOGGLE) {
            long active = SkyWarsPerk.choosable(mode).stream()
                    .filter(perk -> SkyWarsProgression.active(server, player.getUUID(), perk))
                    .count();
            icon.text("All perks you own in this mode are enabled simultaneously.")
                    .blank()
                    .text("You can choose to disable any you don't want to have active.")
                    .blank()
                    .value("Active", active + "/" + SkyWarsPerk.choosable(mode).size(), ChatFormatting.AQUA);
        } else {
            icon.text("Selection of unique perks for " + mode.displayName + " games.");
        }
        menu.set(slot, icon.blank().action("Click to browse!"), (clicker, click) -> perks(clicker, mode, 0, root));
    }

    /** The command operators unlock the mode's kits and perks with. */
    private static String grantCommand(SkyWarsMode mode) {
        return "/minigames skywars " + (mode == SkyWarsMode.INSANE ? "" : mode.id + " ") + "grant";
    }

    static void kits(ServerPlayer player, SkyWarsMode mode, int page, Menu.Screen root) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(mode.kitsTitle(), 6);
        SkyWarsKit selected = SkyWarsProgression.selectedKit(server, player.getUUID(), mode);
        menu.page(
                SkyWarsKits.of(mode),
                page,
                Menu.inner(1, 4),
                (target, slot, kit) -> {
                    boolean owned = SkyWarsProgression.owns(server, player.getUUID(), kit);
                    Icon icon = Icon.of(kit.icon()).name(kit.name(), kit.color());
                    for (Component line : kit.contents(server.registryAccess())) {
                        icon.line(line.copy());
                    }
                    icon.blank();
                    if (kit.rarity() != null) {
                        icon.value("Rarity", kit.rarity().label, kit.rarity().color);
                    }
                    if (kit == selected) {
                        icon.line(Component.literal("SELECTED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).glint(true);
                    } else if (owned) {
                        icon.line(Component.literal("UNLOCKED").withStyle(ChatFormatting.GREEN)).blank()
                                .action("Left-click to select!");
                    } else {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED))
                                .refusal("An operator can unlock it with " + grantCommand(mode) + ".");
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (!SkyWarsProgression.owns(server, clicker.getUUID(), kit)) {
                            throw new MatchException("You have not unlocked " + kit.name() + ".");
                        }
                        SkyWarsProgression.selectKit(server, clicker.getUUID(), kit);
                        clicker.sendSystemMessage(Component.literal("Selected " + kit.name() + ".")
                                .withStyle(ChatFormatting.GREEN));
                        kits(clicker, mode, page, root);
                    });
                },
                (clicker, shown) -> kits(clicker, mode, shown, root));
        menu.back(TITLE, root).close();
        menu.open(player);
    }

    static void perks(ServerPlayer player, SkyWarsMode mode, int page, Menu.Screen root) {
        if (mode.perkChoice == SkyWarsMode.PerkChoice.SLOTS) {
            slots(player, mode, root);
            return;
        }
        MinecraftServer server = player.level().getServer();
        boolean upgraded = SkyWarsProgression.maxPerks(server);
        Menu menu = new Menu(mode.perksTitle(), 6);
        menu.page(
                SkyWarsPerk.choosable(mode),
                page,
                Menu.inner(1, 3),
                (target, slot, perk) -> {
                    boolean owned = SkyWarsProgression.owns(server, player.getUUID(), perk);
                    boolean enabled = SkyWarsProgression.enabled(server, player.getUUID(), perk);
                    Icon icon = perkIcon(perk, "Perk");
                    if (perk.upgrade() != null) {
                        icon.blank()
                                .line(Component.literal(perk.upgrade().name() + " Upgrade").withStyle(ChatFormatting.GOLD))
                                .text(perk.upgrade().description())
                                .value("Upgrade", upgraded ? "MAXED" : "not owned", upgraded ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    }
                    icon.blank().value("Rarity", perk.rarity().label, perk.rarity().color).blank();
                    if (!owned) {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED))
                                .refusal("An operator can unlock it with " + grantCommand(mode) + ".");
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
                        perks(clicker, mode, page, root);
                    });
                },
                (clicker, shown) -> perks(clicker, mode, shown, root));
        menu.back(TITLE, root).close();
        menu.open(player);
    }

    private static Icon perkIcon(SkyWarsPerk perk, String subtitle) {
        return Icon.of(perk.icon())
                .name(perk.name(), perk.rarity().color)
                .line(Component.literal(subtitle).withStyle(ChatFormatting.DARK_GRAY))
                .blank()
                .text(perk.description());
    }

    /**
     * Hypixel's "Select Mini/Mega Perks": the mode's perk slots in the second row (left-click a
     * slot to choose its perk, right-click to empty it) and its global perks in the fourth.
     */
    static void slots(ServerPlayer player, SkyWarsMode mode, Menu.Screen root) {
        MinecraftServer server = player.level().getServer();
        int usable = mode.usableSlots(SkyWarsProgression.maxPerks(server));
        Menu menu = new Menu(mode.perksTitle(), 6);
        menu.set(Menu.slot(1, 0), Icon.of(Items.GOLD_BLOCK).name("Perk Slots", ChatFormatting.GREEN)
                .text("Your selected perks will be active during your " + mode.displayName + " SkyWars games."));
        List<@Nullable SkyWarsPerk> slots = SkyWarsProgression.slots(server, player.getUUID(), mode);
        for (int index = 0; index < mode.perkSlots; index++) {
            int slot = index;
            String number = "Perk Slot #" + (index + 1);
            SkyWarsPerk perk = slots.get(index);
            if (usable == 0) {
                menu.set(Menu.slot(1, 2 + index), Icon.of(Items.BARRIER).name(number, ChatFormatting.RED)
                        .text("This mode does not allow the use of perk slots!"));
            } else if (index >= usable) {
                menu.set(Menu.slot(1, 2 + index), Icon.of(Items.BARRIER).name("Locked", ChatFormatting.RED)
                        .line(Component.literal(number).withStyle(ChatFormatting.DARK_GRAY))
                        .blank()
                        .refusal("Unlocks when every perk is maxed (gamerule skywars_max_all_perks)."));
            } else if (perk == null) {
                menu.set(Menu.slot(1, 2 + index), Icon.of(Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.GRAY))
                                .name("Empty Slot", ChatFormatting.GRAY)
                                .line(Component.literal(number).withStyle(ChatFormatting.DARK_GRAY))
                                .blank()
                                .action("Click to choose a perk!"),
                        (clicker, click) -> choose(clicker, mode, slot, 0, root));
            } else {
                menu.set(Menu.slot(1, 2 + index), perkIcon(perk, number)
                                .blank()
                                .value("Rarity", perk.rarity().label, perk.rarity().color)
                                .blank()
                                .action("Left-click to replace!")
                                .action("Right-click to clear!"),
                        (clicker, click) -> {
                            if (click.right()) {
                                SkyWarsProgression.setSlot(server, clicker.getUUID(), mode, slot, null);
                                slots(clicker, mode, root);
                            } else {
                                choose(clicker, mode, slot, 0, root);
                            }
                        });
            }
        }
        menu.set(Menu.slot(3, 0), Icon.of(Items.DIAMOND_BLOCK).name("Global Perks", ChatFormatting.AQUA)
                .text("All players will have these perks active during " + mode.displayName + " SkyWars games."));
        int column = 2;
        for (SkyWarsPerk perk : SkyWarsPerk.global(mode)) {
            menu.set(Menu.slot(3, column++), perkIcon(perk, "Global Perk")
                    .blank()
                    .value("Rarity", perk.rarity().label, perk.rarity().color));
        }
        menu.back(TITLE, root).close();
        menu.open(player);
    }

    /** The perks that can go into a perk slot; choosing one puts it there (moving it from another slot). */
    static void choose(ServerPlayer player, SkyWarsMode mode, int slot, int page, Menu.Screen root) {
        MinecraftServer server = player.level().getServer();
        List<@Nullable SkyWarsPerk> slots = SkyWarsProgression.slots(server, player.getUUID(), mode);
        Menu menu = new Menu("Perk Slot #" + (slot + 1), 6);
        menu.page(
                SkyWarsPerk.choosable(mode),
                page,
                Menu.inner(1, 4),
                (target, index, perk) -> {
                    Icon icon = perkIcon(perk, "Perk").blank().value("Rarity", perk.rarity().label, perk.rarity().color).blank();
                    int in = slots.indexOf(perk);
                    if (!SkyWarsProgression.owns(server, player.getUUID(), perk)) {
                        icon.line(Component.literal("LOCKED").withStyle(ChatFormatting.RED))
                                .refusal("An operator can unlock it with " + grantCommand(mode) + ".");
                    } else if (in == slot) {
                        icon.line(Component.literal("SELECTED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).glint(true);
                    } else {
                        if (in >= 0) icon.line(Component.literal("In Perk Slot #" + (in + 1)).withStyle(ChatFormatting.YELLOW));
                        icon.action("Click to select!");
                    }
                    target.set(index, icon, (clicker, click) -> {
                        SkyWarsProgression.setSlot(server, clicker.getUUID(), mode, slot, perk);
                        slots(clicker, mode, root);
                    });
                },
                (clicker, shown) -> choose(clicker, mode, slot, shown, root));
        menu.back(mode.perksTitle(), clicker -> slots(clicker, mode, root)).close();
        menu.open(player);
    }
}
