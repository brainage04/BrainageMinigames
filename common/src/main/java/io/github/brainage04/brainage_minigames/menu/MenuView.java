package io.github.brainage04.brainage_minigames.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The open window of a {@link Menu}. No click ever moves an item: plain and shift clicks on a
 * menu slot run its button, and every other input (drags, number keys, the offhand key, throws,
 * double-click collecting, creative cloning, clicks on the player's own inventory) does nothing.
 * The packet handler then resends whatever the client predicted, so nothing leaves the menu and
 * nothing enters the player's inventory.
 */
public final class MenuView extends ChestMenu {
    private final SimpleContainer icons;
    private Menu menu;

    MenuView(int containerId, Inventory inventory, Menu menu) {
        this(containerId, inventory, new SimpleContainer(menu.size()), menu);
    }

    private MenuView(int containerId, Inventory inventory, SimpleContainer icons, Menu menu) {
        super(type(menu.rows()), containerId, inventory, icons, menu.rows());
        this.icons = icons;
        show(menu);
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public Menu menu() {
        return menu;
    }

    boolean fits(Menu other) {
        return other.rows() == menu.rows() && other.title().equals(menu.title());
    }

    /** Replaces what the window shows; the next broadcast sends the changed slots. */
    void show(Menu next) {
        this.menu = next;
        for (int slot = 0; slot < next.size(); slot++) {
            icons.setItem(slot, next.icon(slot).copy());
        }
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        setCarried(ItemStack.EMPTY);
        Click click = Click.of(input, buttonNum);
        if (click != null
                && player instanceof ServerPlayer serverPlayer
                && slotIndex >= 0
                && slotIndex < menu.size()) {
            menu.click(serverPlayer, slotIndex, click);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
