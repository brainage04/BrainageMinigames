package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Hotbar items that open menus or act as buttons: the hub's game menu compass and the lobby's
 * vote and leave items. They are marked in their custom data; using one runs its button instead of
 * the item, and they cannot be dropped.
 */
public final class MenuItems {
    private static final String KEY = BrainageMinigames.MOD_ID + ":menu_item";
    static final int MENU_SLOT = 0;

    /** The server tick each player last used a menu item in. */
    private static final Map<ServerPlayer, Integer> LAST_USE = new WeakHashMap<>();

    /** What a hotbar menu item does when used. */
    public enum Kind {
        GAME_MENU("game_menu"),
        VOTE_START("vote_start"),
        LEAVE("leave");

        private final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    private MenuItems() {}

    /** The hub's "Game Menu" compass, which opens {@link MainMenu}. */
    public static ItemStack gameMenu() {
        return mark(
                Icon.of(Items.COMPASS)
                        .name("Game Menu", ChatFormatting.GREEN)
                        .text("Browse games, open or join matches, watch them, and build duels.")
                        .blank()
                        .action("Right-click to open!")
                        .build(),
                Kind.GAME_MENU);
    }

    static ItemStack mark(ItemStack stack, Kind kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, kind.id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Optional<Kind> kind(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        String id = data.copyTag().getStringOr(KEY, "");
        for (Kind kind : Kind.values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public static boolean isMenuItem(ItemStack stack) {
        return kind(stack).isPresent();
    }

    /**
     * Gives a player entering the hub the game menu compass unless they already carry one: in the
     * first hotbar slot when it is free, otherwise in any free slot.
     */
    public static void giveHubItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (kind(inventory.getItem(slot)).orElse(null) == Kind.GAME_MENU) {
                return;
            }
        }
        if (inventory.getItem(MENU_SLOT).isEmpty()) {
            inventory.setItem(MENU_SLOT, gameMenu());
        } else {
            inventory.add(gameMenu());
        }
    }

    /**
     * Runs the item's button when it is a menu item and returns whether it was one; vanilla then
     * does nothing with it. A right-click on a block reaches the server as a use on the block and
     * then a use in the air, so a second use in the same tick is ignored.
     */
    public static boolean use(ServerPlayer player, ItemStack stack) {
        Optional<Kind> kind = kind(stack);
        if (kind.isEmpty()) {
            return false;
        }
        int tick = player.level().getServer().getTickCount();
        Integer previous = LAST_USE.put(player, tick);
        if (previous == null || previous != tick) {
            switch (kind.get()) {
                case GAME_MENU -> MainMenu.open(player);
                case VOTE_START -> LobbyItems.voteStart(player);
                case LEAVE -> LobbyItems.leave(player);
            }
        }
        PlayerUtils.resyncInventory(player);
        return true;
    }

    /**
     * Right-clicking another player with the game menu opens the duel builder against them, as
     * long as neither is in a match. Returns whether it did; the use of the item in the air that
     * follows in the same tick is then ignored.
     */
    public static boolean interact(ServerPlayer player, ServerPlayer target, ItemStack stack) {
        if (kind(stack).orElse(null) != Kind.GAME_MENU
                || MatchManager.matchOf(player.getUUID()).isPresent()
                || MatchManager.matchOf(target.getUUID()).isPresent()) {
            return false;
        }
        LAST_USE.put(player, player.level().getServer().getTickCount());
        DuelMenus.challenge(player, target);
        return true;
    }

    /**
     * Keeps a menu item from being dropped: an alive player gets it back (it is lost if their
     * inventory has no room), and one who died loses it rather than leaving it on the ground.
     * Returns whether {@code stack} was a menu item.
     */
    public static boolean refuseDrop(ServerPlayer player, ItemStack stack) {
        if (!isMenuItem(stack)) {
            return false;
        }
        if (player.isAlive() && !player.hasDisconnected()) {
            player.getInventory().add(stack);
            PlayerUtils.resyncInventory(player);
        }
        return true;
    }
}
