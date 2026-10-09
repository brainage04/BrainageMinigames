package io.github.brainage04.brainage_minigames.game.bedwars;

import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Entry;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hypixel's Hotbar Manager: each player's preferred hotbar slot for every item category, saved per
 * UUID in the world's command storage. A bought item goes into a free slot (or a stack of its own
 * kind) its category prefers, moving an item of another category out of the way; on spawn the
 * player's items are put into their slots, and the compass goes into the Compass slot (no Compass
 * slot, no compass). A player who never changed it has the Compass in the last slot and nothing
 * else, as Hypixel's default.
 */
public final class BedWarsHotbar {
    public static final Identifier STORAGE = BrainageMinigames.id("bedwars_hotbar");
    private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();
    public static final int SLOTS = 9;

    private BedWarsHotbar() {}

    /** The categories a hotbar slot can prefer, in the Hotbar Manager's order. */
    public enum Category {
        BLOCKS("Blocks", Items.TERRACOTTA),
        MELEE("Melee", Items.GOLDEN_SWORD),
        TOOLS("Tools", Items.STONE_PICKAXE),
        RANGED("Ranged", Items.BOW),
        POTIONS("Potions", Items.BREWING_STAND),
        UTILITY("Utility", Items.TNT),
        COMPASS("Compass", Items.COMPASS);

        public final String title;
        public final Item icon;

        Category(String title, Item icon) {
            this.title = title;
            this.icon = icon;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Category> of(String id) {
            for (Category category : values()) if (category.id().equals(id)) return Optional.of(category);
            return Optional.empty();
        }
    }

    /** Hypixel's default: the compass in the last slot, nothing else preferred. */
    public static final List<String> DEFAULT = List.of("", "", "", "", "", "", "", "", "compass");

    public static List<String> get(MinecraftServer server, UUID player) {
        List<String> saved = server.getCommandStorage().get(STORAGE).read(player.toString(), STRINGS).orElse(List.of());
        if (saved.size() != SLOTS) return DEFAULT;
        List<String> slots = new ArrayList<>(saved);
        slots.replaceAll(id -> id.isEmpty() || Category.of(id).isPresent() ? id : "");
        return List.copyOf(slots);
    }

    /** Makes hotbar slot {@code slot} (from 0) prefer {@code category}, or nothing for {@code ""}. */
    public static void set(MinecraftServer server, UUID player, int slot, String category) {
        List<String> slots = new ArrayList<>(get(server, player));
        slots.set(slot, category);
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.store(player.toString(), STRINGS, slots);
        server.getCommandStorage().set(STORAGE, root);
    }

    /** Back to {@link #DEFAULT}. */
    public static void reset(MinecraftServer server, UUID player) {
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.remove(player.toString());
        server.getCommandStorage().set(STORAGE, root);
    }

    /** The hotbar slots that prefer {@code category}, left to right. */
    static List<Integer> slotsFor(MinecraftServer server, UUID player, Category category) {
        List<String> slots = get(server, player);
        List<Integer> preferred = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) if (slots.get(slot).equals(category.id())) preferred.add(slot);
        return preferred;
    }

    /** The Hotbar Manager category of a shop item, or empty for armour, which is worn. */
    static Optional<Category> of(Entry entry) {
        return switch (entry.category()) {
            case BLOCKS -> Optional.of(Category.BLOCKS);
            case MELEE -> Optional.of(Category.MELEE);
            case TOOLS -> Optional.of(Category.TOOLS);
            case RANGED -> Optional.of(Category.RANGED);
            case POTIONS -> Optional.of(Category.POTIONS);
            case UTILITY, ROTATING -> Optional.of(Category.UTILITY);
            case ARMOR, QUICK_BUY -> Optional.empty();
        };
    }

    /** The category an item in the inventory belongs to, judged by what it is. */
    static Optional<Category> of(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        String ability = BedWarsShop.ability(stack);
        if (ability.equals("tracker") || stack.is(Items.COMPASS)) return Optional.of(Category.COMPASS);
        if (BedWarsGuns.isGun(ability) || stack.is(Items.BOW) || stack.is(Items.ARROW)) return Optional.of(Category.RANGED);
        if (stack.is(ItemTags.SWORDS) || stack.is(Items.STICK) && ability.isEmpty()) return Optional.of(Category.MELEE);
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(Items.SHEARS)) return Optional.of(Category.TOOLS);
        if (stack.is(Items.POTION)) return Optional.of(Category.POTIONS);
        if (ability.isEmpty() && stack.getItem() instanceof BlockItem
                && !stack.is(Items.TNT) && !stack.is(Items.SPONGE) && !stack.is(Items.COBWEB)) {
            return Optional.of(Category.BLOCKS);
        }
        return Optional.of(Category.UTILITY);
    }

    /**
     * Gives {@code stack} to the player in a hotbar slot {@code category} prefers: an empty one, a
     * stack of the same item with room, or one holding another category's item, which moves to a free
     * slot elsewhere. Whatever is left goes where vanilla puts it.
     */
    static void give(ServerPlayer player, ItemStack stack, Optional<Category> category) {
        if (category.isPresent()) {
            Inventory inventory = player.getInventory();
            for (int slot : slotsFor(player.level().getServer(), player.getUUID(), category.get())) {
                if (stack.isEmpty()) return;
                ItemStack held = inventory.getItem(slot);
                if (held.isEmpty()) {
                    inventory.setItem(slot, stack.copy());
                    stack.setCount(0);
                } else if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(moved);
                    stack.shrink(moved);
                } else if (!of(held).equals(category) && !pinned(held) && moveAway(inventory, slot)) {
                    inventory.setItem(slot, stack.copy());
                    stack.setCount(0);
                }
            }
        }
        if (!stack.isEmpty()) PlayerUtils.giveOrDrop(player, stack);
    }

    /** Moves the item in {@code slot} to a free slot of the main inventory, else of the hotbar; whether it could. */
    private static boolean moveAway(Inventory inventory, int slot) {
        int free = -1;
        for (int other = SLOTS; other < Inventory.INVENTORY_SIZE && free < 0; other++) if (inventory.getItem(other).isEmpty()) free = other;
        for (int other = 0; other < SLOTS && free < 0; other++) if (other != slot && inventory.getItem(other).isEmpty()) free = other;
        if (free < 0) return false;
        inventory.setItem(free, inventory.getItem(slot));
        inventory.setItem(slot, ItemStack.EMPTY);
        return true;
    }

    /**
     * Puts the player's items into their preferred slots, as at a spawn: every slot that prefers a
     * category and does not hold one of its items swaps with the first such item found elsewhere.
     */
    static void arrange(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<String> slots = get(player.level().getServer(), player.getUUID());
        List<Integer> claimed = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            Optional<Category> wanted = Category.of(slots.get(slot));
            if (wanted.isEmpty()) continue;
            ItemStack current = inventory.getItem(slot);
            if (pinned(current) || of(current).equals(wanted)) {
                claimed.add(slot);
                continue;
            }
            for (int other = 0; other < Inventory.INVENTORY_SIZE; other++) {
                ItemStack candidate = inventory.getItem(other);
                if (other == slot || claimed.contains(other) || pinned(candidate) || !of(candidate).equals(wanted)) continue;
                ItemStack moving = inventory.getItem(other);
                inventory.setItem(other, inventory.getItem(slot));
                inventory.setItem(slot, moving);
                claimed.add(slot);
                break;
            }
        }
    }

    /** Ultimate's ability item keeps the last hotbar slot, whatever the player prefers. */
    private static boolean pinned(ItemStack stack) {
        return BedWarsShop.ability(stack).equals("ultimate");
    }
}
