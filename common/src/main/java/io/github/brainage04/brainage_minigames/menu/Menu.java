package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.MatchException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * One screen of a chest menu: a title, a number of rows and, per slot, the item shown and what
 * clicking it does. Menus only ever show items; {@link MenuView} refuses every attempt to take,
 * move or drop them.
 *
 * <p>The bottom row follows one convention everywhere: page arrows in its corners, "Go Back" left
 * of the middle and "Close" in the middle.
 */
public final class Menu {
    public static final int WIDTH = 9;

    /** What a click on a slot does; a refused request is told to the player in chat. */
    @FunctionalInterface
    public interface Action {
        void click(ServerPlayer player, Click click) throws MatchException;
    }

    /** Opens a screen for a player; menus link to each other with these. */
    @FunctionalInterface
    public interface Screen {
        void open(ServerPlayer player);
    }

    private final Component title;
    private final int rows;
    private final ItemStack[] icons;
    private final @Nullable Action[] actions;

    public Menu(String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("A chest menu has 1 to 6 rows, not " + rows);
        }
        this.title = Component.literal(title);
        this.rows = rows;
        this.icons = new ItemStack[rows * WIDTH];
        this.actions = new Action[rows * WIDTH];
        Arrays.fill(icons, ItemStack.EMPTY);
    }

    public Component title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return icons.length;
    }

    public ItemStack icon(int slot) {
        return icons[slot];
    }

    /** Shows {@code icon} in {@code slot}; clicking it does nothing. */
    public Menu set(int slot, Icon icon) {
        return set(slot, icon, null);
    }

    public Menu set(int slot, Icon icon, @Nullable Action action) {
        icons[slot] = icon.build();
        actions[slot] = action;
        return this;
    }

    /** The slot at {@code row} and {@code column}, both counted from 0. */
    public static int slot(int row, int column) {
        return row * WIDTH + column;
    }

    /** The first slot of the bottom row. */
    public int bottom() {
        return slot(rows - 1, 0);
    }

    /** Slots inside the border between {@code firstRow} and {@code lastRow}, left to right. */
    public static int[] inner(int firstRow, int lastRow) {
        int[] slots = new int[(lastRow - firstRow + 1) * 7];
        int index = 0;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = 1; column <= 7; column++) {
                slots[index++] = slot(row, column);
            }
        }
        return slots;
    }

    /**
     * Fills {@code slots} with page {@code page} of {@code entries}, adds page arrows when there
     * is more than one page, and returns the page actually shown.
     */
    public <T> int page(
            List<T> entries, int page, int[] slots, PageEntry<T> entry, PageOpener opener) {
        int pages = Math.max(1, (entries.size() + slots.length - 1) / slots.length);
        int shown = Math.clamp(page, 0, pages - 1);
        for (int index = 0; index < slots.length; index++) {
            int entryIndex = shown * slots.length + index;
            if (entryIndex >= entries.size()) {
                break;
            }
            entry.show(this, slots[index], entries.get(entryIndex));
        }
        if (shown > 0) {
            set(
                    bottom(),
                    Icon.of(Items.ARROW)
                            .name("Left-click for previous page!", ChatFormatting.YELLOW)
                            .line(Component.literal("Right-click for first page!")
                                    .withStyle(ChatFormatting.AQUA)),
                    (player, click) -> opener.open(player, click.right() ? 0 : shown - 1));
        }
        if (shown < pages - 1) {
            set(
                    bottom() + 8,
                    Icon.of(Items.ARROW)
                            .name("Left-click for next page!", ChatFormatting.YELLOW)
                            .line(Component.literal("Right-click for last page!")
                                    .withStyle(ChatFormatting.AQUA)),
                    (player, click) -> opener.open(player, click.right() ? pages - 1 : shown + 1));
        }
        return shown;
    }

    @FunctionalInterface
    public interface PageEntry<T> {
        void show(Menu menu, int slot, T entry);
    }

    @FunctionalInterface
    public interface PageOpener {
        void open(ServerPlayer player, int page);
    }

    /** "Go Back" to {@code parent}, left of the middle of the bottom row. */
    public Menu back(String parentTitle, Screen parent) {
        return set(
                bottom() + 3,
                Icon.of(Items.ARROW)
                        .name("Go Back", ChatFormatting.GREEN)
                        .line(Component.literal("To " + parentTitle).withStyle(ChatFormatting.GRAY)),
                (player, click) -> parent.open(player));
    }

    /** "Close" in the middle of the bottom row. */
    public Menu close() {
        return set(
                bottom() + 4,
                Icon.of(Items.BARRIER).name("Close", ChatFormatting.RED),
                (player, click) -> player.closeContainer());
    }

    /**
     * A row of glass panes separating a category row above from the content below, lime under
     * the selected category.
     */
    public Menu separators(int row, int selectedColumn, String above, String below) {
        for (int column = 0; column < WIDTH; column++) {
            set(
                    slot(row, column),
                    Icon.of(column == selectedColumn
                                    ? Items.STAINED_GLASS_PANE.pick(DyeColor.LIME)
                                    : Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
                            .name(Component.literal("⬆ ")
                                    .withStyle(ChatFormatting.DARK_GRAY)
                                    .append(Component.literal(above)
                                            .withStyle(ChatFormatting.GRAY)))
                            .line(Component.literal("⬇ ")
                                    .withStyle(ChatFormatting.DARK_GRAY)
                                    .append(Component.literal(below)
                                            .withStyle(ChatFormatting.GRAY))));
        }
        return this;
    }

    /**
     * Shows this screen to the player: in place when their open menu has the same title and size,
     * which keeps the cursor where it was, otherwise in a new window.
     */
    public void open(ServerPlayer player) {
        if (player.containerMenu instanceof MenuView view && view.fits(this)) {
            view.show(this);
            return;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new MenuView(containerId, inventory, this),
                        title));
    }

    /** Runs the slot's action; a refused request is told to the player in chat. */
    void click(ServerPlayer player, int slot, Click click) {
        Action action = actions[slot];
        if (action == null) {
            return;
        }
        try {
            action.click(player, click);
        } catch (MatchException exception) {
            player.sendSystemMessage(
                    Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }
}
