package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Category;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Cost;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Entry;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Trap;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.menu.Icon;
import io.github.brainage04.brainage_minigames.menu.Menu;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Bed Wars chest menus, laid out as Hypixel's: the Item Shop (category tabs over a separator row
 * and the items, Quick Buy first, with the Tracker Shop and Hotbar Manager buttons under Quick Buy
 * and the week's Rotating Items last), "Upgrades &amp; Traps", the Tracker Shop, the Hotbar Manager,
 * and the Quick Buy editor with its "Adding to Quick Buy..." pages. Sneak-clicking an item in the
 * shop adds it to Quick Buy; sneak-clicking it in Quick Buy takes it out.
 */
public final class BedWarsMenus {
    public static final String UPGRADES_TITLE = "Upgrades & Traps";
    public static final String EDIT_TITLE = "Edit Quick Buy";
    public static final String ADDING_TITLE = "Adding to Quick Buy...";
    public static final String HOTBAR_TITLE = "Hotbar Manager";

    private BedWarsMenus() {}

    // ---------------------------------------------------------------- Item Shop

    public static void openShop(BedWarsGame game, Match match, ServerPlayer player, Category category) {
        Menu menu = new Menu(category.title, 6);
        Category[] categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            Category shown = categories[index];
            Icon icon = Icon.of(shown.icon).name(shown.title, shown == Category.QUICK_BUY ? ChatFormatting.AQUA : ChatFormatting.GREEN);
            if (shown != category) icon.action("Click to view!");
            menu.set(index, icon.glint(shown == Category.QUICK_BUY), (clicker, click) -> openShop(game, match, clicker, shown));
        }
        for (int column = 0; column < Menu.WIDTH; column++) {
            int tab = column;
            menu.set(Menu.slot(1, column),
                    Icon.of(column == category.ordinal() ? Items.STAINED_GLASS_PANE.pick(DyeColor.LIME) : Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
                            .name(Component.literal("⬆ ").withStyle(ChatFormatting.DARK_GRAY)
                                    .append(Component.literal("Categories").withStyle(ChatFormatting.GRAY)))
                            .line(Component.literal("⬇ ").withStyle(ChatFormatting.DARK_GRAY)
                                    .append(Component.literal("Items").withStyle(ChatFormatting.GRAY))),
                    tab < categories.length ? (clicker, click) -> openShop(game, match, clicker, categories[tab]) : null);
        }
        int[] slots = Menu.inner(2, 4);
        if (category == Category.QUICK_BUY) {
            List<String> quickBuy = BedWarsQuickBuy.get(match.server(), player.getUUID());
            for (int index = 0; index < slots.length; index++) {
                String id = quickBuy.get(index);
                Entry entry = id.isEmpty() ? null : BedWarsShop.find(id).filter(found -> game.sells(match, found)).orElse(null);
                if (entry == null) {
                    menu.set(slots[index], Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.RED)).name("Empty slot!", ChatFormatting.RED)
                            .text("This is a Quick Buy Slot! Sneak Click any item in the shop to add it here."));
                } else {
                    item(game, match, player, menu, slots[index], entry, category, index);
                }
            }
            menu.set(menu.bottom(), Icon.of(Items.COMPASS).name("Tracker Shop", ChatFormatting.GREEN)
                            .text("Purchase tracking upgrade for your compass which will track each player on a specific team until you die."),
                    (clicker, click) -> openTracker(game, match, clicker, () -> openShop(game, match, clicker, Category.QUICK_BUY)));
            menu.set(menu.bottom() + 8, Icon.of(Items.BLAZE_POWDER).name(HOTBAR_TITLE, ChatFormatting.GREEN)
                            .text("Edit preferred slots for your items per category.").blank().action("Click to edit!"),
                    (clicker, click) -> openHotbar(clicker, null, () -> openShop(game, match, clicker, Category.QUICK_BUY)));
        } else if (category == Category.ROTATING) {
            List<Entry> entries = game.rotation(match);
            int first = 4 - (entries.size() - 1);
            for (int index = 0; index < entries.size(); index++) {
                item(game, match, player, menu, Menu.slot(2, first + 2 * index), entries.get(index), category, -1);
            }
            menu.set(Menu.slot(4, 4), Icon.of(Items.OAK_SIGN).name("What are Rotating Items?", ChatFormatting.GREEN)
                    .text("Rotating Items are items that are only available for a limited amount of time. They may disappear and be "
                            + "replaced with another temporary item at any time."));
        } else {
            List<Entry> entries = BedWarsShop.category(category).stream().filter(entry -> game.sells(match, entry)).toList();
            for (int index = 0; index < entries.size() && index < slots.length; index++) {
                item(game, match, player, menu, slots[index], entries.get(index), category, -1);
            }
        }
        menu.close();
        menu.open(player);
    }

    /** One shop item: click to buy; sneak-click to add it to Quick Buy, or in Quick Buy ({@code quickSlot} ≥ 0) to remove it. */
    private static void item(BedWarsGame game, Match match, ServerPlayer player, Menu menu, int slot, Entry entry,
            Category category, int quickSlot) {
        int tier = game.nextTier(match, player, entry);
        Cost cost = game.price(match, player, entry);
        boolean affordable = game.available(match, player, cost.currency()) >= cost.amount();
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        int shownTier = tier == 0 ? (entry.tiers() > 1 ? entry.tiers() : 1) : tier;
        Icon icon = Icon.of(BedWarsShop.icon(entry, dye, player.registryAccess(), shownTier))
                .name(BedWarsShop.displayName(entry, shownTier), tier != 0 && affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                .count(BedWarsShop.stack(entry, dye, player.registryAccess()).getCount())
                .line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(cost.describe()).withStyle(cost.currency().color)));
        if (entry.tiers() > 1) {
            icon.line(Component.literal("Tier: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(BedWarsUpgrades.roman(shownTier)).withStyle(ChatFormatting.YELLOW)));
        }
        if (!entry.description().isEmpty()) icon.blank().text(entry.description());
        icon.blank();
        icon.line(Component.literal(quickSlot >= 0 ? "Sneak Click to remove from Quick Buy!" : "Sneak Click to add to Quick Buy")
                .withStyle(ChatFormatting.AQUA));
        if (tier == 0) {
            icon.line(Component.literal(entry.kind() == BedWarsShop.Kind.PICKAXE || entry.kind() == BedWarsShop.Kind.AXE
                    ? "MAXED!" : "UNLOCKED!").withStyle(ChatFormatting.GREEN));
        } else if (affordable) {
            icon.action("Click to purchase!");
        } else {
            icon.refusal("You don't have enough " + cost.currency().displayName + "!");
        }
        menu.set(slot, icon, (clicker, click) -> {
            if (click.shift()) {
                if (quickSlot >= 0) {
                    BedWarsQuickBuy.set(match.server(), clicker.getUUID(), quickSlot, "");
                    openShop(game, match, clicker, Category.QUICK_BUY);
                } else {
                    adding(clicker, entry, () -> openShop(game, match, clicker, category));
                }
                return;
            }
            game.buy(match, clicker, entry);
            openShop(game, match, clicker, category);
        });
    }

    /** Choose the Quick Buy slot for {@code entry}; {@code back} returns to where the player came from. */
    private static void adding(ServerPlayer player, Entry entry, Runnable back) {
        Menu menu = new Menu(ADDING_TITLE, 6);
        menu.set(4, Icon.of(BedWarsShop.icon(entry, DyeColor.WHITE, player.registryAccess(), 1))
                .name(BedWarsShop.displayName(entry, 1), ChatFormatting.GREEN)
                .text("Click a slot below to put this item there."));
        List<String> quickBuy = BedWarsQuickBuy.get(player.level().getServer(), player.getUUID());
        int[] slots = Menu.inner(2, 4);
        for (int index = 0; index < slots.length; index++) {
            int quickSlot = index;
            String id = quickBuy.get(index);
            Icon icon = id.isEmpty() ? Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.RED)).name("Empty slot!", ChatFormatting.RED)
                    : BedWarsShop.find(id).map(found -> Icon.of(BedWarsShop.icon(found, DyeColor.WHITE, player.registryAccess(), 1))
                            .name(BedWarsShop.displayName(found, 1), ChatFormatting.RED)).orElseThrow();
            menu.set(slots[index], icon.text("This is a Quick Buy Slot!").blank().action("Click to replace!"),
                    (clicker, click) -> {
                        BedWarsQuickBuy.set(clicker.level().getServer(), clicker.getUUID(), quickSlot, entry.id());
                        back.run();
                    });
        }
        menu.set(menu.bottom() + 3, Icon.of(Items.ARROW).name("Go Back", ChatFormatting.GREEN), (clicker, click) -> back.run());
        menu.open(player);
    }

    // ---------------------------------------------------------------- Upgrades & Traps

    public static void openUpgrades(BedWarsGame game, Match match, ServerPlayer player) {
        Menu menu = new Menu(UPGRADES_TITLE, 6);
        upgrade(game, match, player, menu, Menu.slot(1, 1), Upgrade.SHARPENED_SWORDS);
        upgrade(game, match, player, menu, Menu.slot(1, 2), Upgrade.REINFORCED_ARMOR);
        upgrade(game, match, player, menu, Menu.slot(1, 3), Upgrade.MANIAC_MINER);
        upgrade(game, match, player, menu, Menu.slot(2, 1), Upgrade.FORGE);
        upgrade(game, match, player, menu, Menu.slot(2, 2), Upgrade.HEAL_POOL);
        upgrade(game, match, player, menu, Menu.slot(2, 3), Upgrade.DRAGON_BUFF);
        upgrade(game, match, player, menu, Menu.slot(2, 4), Upgrade.CUSHIONED_BOOTS);
        if (game.offers(Upgrade.DEADSHOT)) upgrade(game, match, player, menu, Menu.slot(1, 4), Upgrade.DEADSHOT);
        List<Trap> queued = game.traps(match, player);
        int nextTrap = BedWarsUpgrades.trapCost(game.prices(match), queued.size());
        trap(game, match, player, menu, Menu.slot(1, 5), Trap.ITS_A_TRAP, nextTrap, queued.size());
        trap(game, match, player, menu, Menu.slot(1, 6), Trap.COUNTER_OFFENSIVE, nextTrap, queued.size());
        trap(game, match, player, menu, Menu.slot(1, 7), Trap.ALARM, nextTrap, queued.size());
        trap(game, match, player, menu, Menu.slot(2, 5), Trap.MINER_FATIGUE, nextTrap, queued.size());
        for (int column = 0; column < Menu.WIDTH; column++) {
            menu.set(Menu.slot(3, column), Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
                    .name(Component.literal("⬆ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("Purchasable").withStyle(ChatFormatting.GRAY)))
                    .line(Component.literal("⬇ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("Traps Queue").withStyle(ChatFormatting.GRAY))));
        }
        String[] ordinals = {"first", "second", "third"};
        for (int index = 0; index < BedWarsUpgrades.TRAP_QUEUE; index++) {
            int slot = Menu.slot(4, 3 + index);
            if (index < queued.size()) {
                Trap trap = queued.get(index);
                menu.set(slot, Icon.of(trap.icon).name("Trap #" + (index + 1) + ": " + trap.displayName, ChatFormatting.GREEN)
                        .count(index + 1).text(trap.description));
            } else {
                menu.set(slot, Icon.of(Items.STAINED_GLASS.pick(DyeColor.LIGHT_GRAY)).name("Trap #" + (index + 1) + ": No Trap!", ChatFormatting.RED)
                        .count(index + 1)
                        .text("The " + ordinals[index] + " enemy to walk into your base will trigger this trap!")
                        .blank().text("Purchasing a trap will queue it here. Its cost will scale based on the number of traps queued.")
                        .blank().line(Component.literal("Next trap: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(nextTrap + " Diamond" + (nextTrap == 1 ? "" : "s")).withStyle(ChatFormatting.AQUA))));
            }
        }
        menu.close();
        menu.open(player);
    }

    private static void upgrade(BedWarsGame game, Match match, ServerPlayer player, Menu menu, int slot, Upgrade upgrade) {
        int level = game.upgradeLevel(match, player, upgrade);
        int next = level + 1;
        boolean maxed = next > upgrade.tiers();
        var prices = game.prices(match);
        int cost = maxed ? 0 : upgrade.cost(prices, next);
        boolean affordable = !maxed && game.available(match, player, BedWarsShop.Currency.DIAMOND) >= cost;
        Icon icon = Icon.of(upgrade.icon).name(upgrade.nameAt(maxed ? upgrade.tiers() : next),
                affordable ? ChatFormatting.GREEN : ChatFormatting.RED).text(upgrade.description).blank();
        if (upgrade.tiers() > 1) {
            for (int tier = 1; tier <= upgrade.tiers(); tier++) {
                icon.line(Component.literal("Tier " + tier + ": " + upgrade.tierNames.get(tier - 1) + ", ")
                        .withStyle(tier <= level ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .append(Component.literal(upgrade.cost(prices, tier) + " Diamonds").withStyle(ChatFormatting.AQUA)));
            }
        } else {
            icon.line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(upgrade.cost(prices, 1) + " Diamond" + (upgrade.cost(prices, 1) == 1 ? "" : "s"))
                            .withStyle(ChatFormatting.AQUA)));
        }
        icon.blank();
        if (maxed) {
            icon.line(Component.literal("UNLOCKED").withStyle(ChatFormatting.GREEN));
        } else if (affordable) {
            icon.action("Click to purchase!");
        } else {
            icon.refusal("You don't have enough Diamonds!");
        }
        menu.set(slot, icon.glint(level > 0), (clicker, click) -> {
            game.buyUpgrade(match, clicker, upgrade);
            openUpgrades(game, match, clicker);
        });
    }

    private static void trap(BedWarsGame game, Match match, ServerPlayer player, Menu menu, int slot, Trap trap, int cost,
            int queued) {
        boolean full = queued >= BedWarsUpgrades.TRAP_QUEUE;
        boolean affordable = !full && game.available(match, player, BedWarsShop.Currency.DIAMOND) >= cost;
        Icon icon = Icon.of(trap.icon).name(trap.displayName, affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                .text(trap.description).blank()
                .line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(cost + " Diamond" + (cost == 1 ? "" : "s")).withStyle(ChatFormatting.AQUA)))
                .blank();
        if (full) icon.refusal("Trap queue full!");
        else if (affordable) icon.action("Click to purchase!");
        else icon.refusal("You don't have enough Diamonds!");
        menu.set(slot, icon, (clicker, click) -> {
            game.buyTrap(match, clicker, trap);
            openUpgrades(game, match, clicker);
        });
    }

    // ---------------------------------------------------------------- Tracker Shop

    /**
     * "Purchase Enemy Tracker": one button per enemy team still in the game, buyable once every enemy
     * bed is gone; {@code back}, when given, returns to the shop.
     */
    public static void openTracker(BedWarsGame game, Match match, ServerPlayer player, @Nullable Runnable back) {
        BedWarsGame.State state = game.states.get(match);
        if (state == null) return;
        Menu menu = new Menu(BedWarsTracker.TITLE, 4);
        int own = BedWarsGame.teamOf(match, player);
        int[] slots = Menu.inner(1, 2);
        int index = 0;
        for (BedWarsGame.TeamState team : state.teams.values()) {
            if (team.number == own || team.eliminated || index >= slots.length) continue;
            int number = team.number;
            Optional<String> refusal = BedWarsTracker.refusal(match, state, player, number);
            Cost cost = BedWarsTracker.COST;
            boolean affordable = game.available(match, player, cost.currency()) >= cost.amount();
            DyeColor dye = match.teamNumbered(number).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
            Icon icon = Icon.of(Items.WOOL.pick(dye))
                    .name("Track Team " + Match.teamName(number), refusal.isEmpty() && affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .text("Purchase tracking upgrade for your compass which will track each player on a specific team until you die.")
                    .blank().line(Component.literal("Cost: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(cost.describe()).withStyle(cost.currency().color)))
                    .blank();
            if (refusal.isPresent()) icon.refusal(refusal.get());
            else if (affordable) icon.action("Click to purchase!");
            else icon.refusal("You don't have enough " + cost.currency().displayName + "s!");
            menu.set(slots[index++], icon, (clicker, click) -> {
                BedWarsTracker.buy(game, match, state, clicker, number);
                openTracker(game, match, clicker, back);
            });
        }
        if (back != null) {
            menu.set(Menu.slot(3, 4), Icon.of(Items.ARROW).name("Go Back", ChatFormatting.GREEN)
                    .line(Component.literal("To Quick Buy").withStyle(ChatFormatting.GRAY)), (clicker, click) -> back.run());
        }
        menu.open(player);
    }

    // ---------------------------------------------------------------- Hotbar Manager

    /**
     * "Hotbar Manager": the categories above the nine hotbar slots. Click a category to pick it up,
     * then a hotbar slot to make that slot prefer it; click a filled slot to clear it. {@code back},
     * when given, returns to the shop.
     */
    public static void openHotbar(ServerPlayer player, BedWarsHotbar.@Nullable Category selected, @Nullable Runnable back) {
        Menu menu = new Menu(HOTBAR_TITLE, 5);
        var server = player.level().getServer();
        if (back != null) {
            menu.set(3, Icon.of(Items.ARROW).name("Go Back", ChatFormatting.GREEN)
                    .line(Component.literal("To Quick Buy").withStyle(ChatFormatting.GRAY)), (clicker, click) -> back.run());
        }
        menu.set(5, Icon.of(Items.BARRIER).name("Reset to Default", ChatFormatting.RED).text("Reset your hotbar to the default."),
                (clicker, click) -> {
                    BedWarsHotbar.reset(server, clicker.getUUID());
                    openHotbar(clicker, null, back);
                });
        BedWarsHotbar.Category[] categories = BedWarsHotbar.Category.values();
        for (int index = 0; index < categories.length; index++) {
            BedWarsHotbar.Category category = categories[index];
            Icon icon = Icon.of(category.icon).name(category.title, ChatFormatting.GREEN);
            if (category == BedWarsHotbar.Category.COMPASS) {
                icon.text("Drag this to the slot your compass will be set to on spawn.").blank()
                        .line(Component.literal("If no slot has a compass, you will not be given one.").withStyle(ChatFormatting.RED));
            } else {
                icon.text("Drag this to a hotbar slot below to favor that slot when purchasing an item in this category or on spawn.");
            }
            icon.blank().action(category == selected ? "Selected! Click a hotbar slot below." : "Click to drag!").glint(category == selected);
            menu.set(Menu.slot(2, 1 + index), icon, (clicker, click) -> openHotbar(clicker, category == selected ? null : category, back));
        }
        menu.separators(3, -1, "Categories", "Hotbar");
        List<String> slots = BedWarsHotbar.get(server, player.getUUID());
        for (int slot = 0; slot < BedWarsHotbar.SLOTS; slot++) {
            int hotbarSlot = slot;
            Optional<BedWarsHotbar.Category> preferred = BedWarsHotbar.Category.of(slots.get(slot));
            Icon icon = preferred.map(category -> Icon.of(category.icon).name(category.title, ChatFormatting.GREEN)
                            .text(category.title + " items will prioritize this slot!").blank()
                            .action(selected == null ? "Click to remove!" : "Click to replace!"))
                    .orElseGet(() -> Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY))
                            .name("Hotbar Slot " + (hotbarSlot + 1), ChatFormatting.GRAY)
                            .text(selected == null ? "Pick up a category above, then click here." : "Click to put " + selected.title + " here!"));
            menu.set(Menu.slot(4, slot), icon, (clicker, click) -> {
                if (selected != null) {
                    BedWarsHotbar.set(server, clicker.getUUID(), hotbarSlot, selected.id());
                } else if (preferred.isPresent()) {
                    BedWarsHotbar.set(server, clicker.getUUID(), hotbarSlot, "");
                }
                openHotbar(clicker, null, back);
            });
        }
        menu.open(player);
    }

    // ---------------------------------------------------------------- Quick Buy editor (outside matches)

    /** "Edit Quick Buy": the 21 slots; click one to choose its item, right-click to empty it. */
    public static void openEditor(ServerPlayer player) {
        Menu menu = new Menu(EDIT_TITLE, 6);
        List<String> quickBuy = BedWarsQuickBuy.get(player.level().getServer(), player.getUUID());
        int[] slots = Menu.inner(1, 3);
        for (int index = 0; index < slots.length; index++) {
            int quickSlot = index;
            String id = quickBuy.get(index);
            Icon icon = id.isEmpty() ? Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.RED)).name("Empty slot!", ChatFormatting.RED)
                    : BedWarsShop.find(id).map(entry -> Icon.of(BedWarsShop.icon(entry, DyeColor.WHITE, player.registryAccess(), 1))
                            .name(BedWarsShop.displayName(entry, 1), ChatFormatting.GREEN)
                            .count(BedWarsShop.stack(entry, DyeColor.WHITE, player.registryAccess()).getCount())).orElseThrow();
            icon.text("This is a Quick Buy Slot!").blank()
                    .line(Component.literal("Left Click ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("to replace the item in this slot.").withStyle(ChatFormatting.GRAY)));
            if (!id.isEmpty()) {
                icon.line(Component.literal("Right Click ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("to remove this item.").withStyle(ChatFormatting.GRAY)));
            }
            menu.set(slots[index], icon, (clicker, click) -> {
                if (click.right()) {
                    BedWarsQuickBuy.set(clicker.level().getServer(), clicker.getUUID(), quickSlot, "");
                    openEditor(clicker);
                } else {
                    choose(clicker, quickSlot, 0);
                }
            });
        }
        menu.set(menu.bottom(), Icon.of(Items.BARRIER).name("Reset to Default", ChatFormatting.RED).text("Reset your Quick Buy to the default."),
                (clicker, click) -> {
                    BedWarsQuickBuy.reset(clicker.level().getServer(), clicker.getUUID());
                    openEditor(clicker);
                });
        menu.close();
        menu.open(player);
    }

    /** The seven ultimates of Bed Wars Ultimate; clicking one picks it for the player's next Ultimate match. */
    public static void openUltimates(ServerPlayer player) {
        Menu menu = new Menu("Ultimates", 4);
        BedWarsUltimates.Ultimate chosen = BedWarsUltimates.selected(player.level().getServer(), player.getUUID());
        BedWarsUltimates.Ultimate[] ultimates = BedWarsUltimates.Ultimate.values();
        for (int index = 0; index < ultimates.length; index++) {
            BedWarsUltimates.Ultimate ultimate = ultimates[index];
            menu.set(Menu.slot(1, 1 + index), Icon.of(ultimate.icon)
                            .name(ultimate.displayName, ultimate == chosen ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                            .text(ultimate.description).blank().action(ultimate == chosen ? "Selected" : "Click to select!")
                            .glint(ultimate == chosen),
                    (clicker, click) -> {
                        BedWarsUltimates.select(clicker.level().getServer(), clicker.getUUID(), ultimate);
                        openUltimates(clicker);
                    });
        }
        menu.close();
        menu.open(player);
    }

    /** "Adding to Quick Buy...": every item of the shop over two pages; clicking one puts it into {@code quickSlot}. */
    private static void choose(ServerPlayer player, int quickSlot, int page) {
        Menu menu = new Menu(page == 0 ? ADDING_TITLE : "(2/2) " + ADDING_TITLE, 6);
        menu.page(BedWarsShop.quickBuyItems(), page, Menu.inner(1, 3),
                (target, slot, entry) -> target.set(slot,
                        Icon.of(BedWarsShop.icon(entry, DyeColor.WHITE, player.registryAccess(), 1))
                                .name(BedWarsShop.displayName(entry, 1), ChatFormatting.GREEN)
                                .count(BedWarsShop.stack(entry, DyeColor.WHITE, player.registryAccess()).getCount())
                                .text(entry.description())
                                .blank().action("Click to add this item to your Quick Buy!"),
                        (clicker, click) -> {
                            BedWarsQuickBuy.set(clicker.level().getServer(), clicker.getUUID(), quickSlot, entry.id());
                            openEditor(clicker);
                        }),
                (clicker, shown) -> choose(clicker, quickSlot, shown));
        menu.back(EDIT_TITLE, BedWarsMenus::openEditor);
        menu.open(player);
    }
}
