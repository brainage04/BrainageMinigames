package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.Match;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Ingredient-change prompts and a vanilla-client-compatible virtual crafting table. */
public final class UhcCraftPrompts {
    private static final Map<Match, Map<UUID, State>> STATES = new HashMap<>();
    private static final class State {
        final Map<String, Ingredients> recipes = new HashMap<>();
        final int[] remaining;
        final int[] slots = new int[9];
        State(ServerPlayer player) { remaining = new int[player.getInventory().getNonEquipmentItems().size()]; }
    }
    private static final class Ingredients {
        final List<ItemStack> stacks;
        final boolean ready;
        boolean prompted;
        Ingredients(List<ItemStack> stacks, boolean ready) { this.stacks = stacks; this.ready = ready; }
    }
    private UhcCraftPrompts() {}

    public static void tick(Match match) {
        if (match.activeTicks() % 20 != 0) return;
        for (ServerPlayer player : match.alivePlayers()) tick(player);
    }

    public static void tick(ServerPlayer player) {
        Match match = UhcProgression.match(player);
        if (match == null) return;
        State state = state(match, player);
        for (var recipe : UhcCrafting.recipes()) {
            Ingredients previous = state.recipes.get(recipe.id());
            if (previous == null || !sameIngredients(player, recipe, previous.stacks)) {
                List<ItemStack> stacks = ingredients(player, recipe);
                previous = new Ingredients(stacks, !stacks.isEmpty() && plan(player, recipe, state) != null);
                state.recipes.put(recipe.id(), previous);
            }
            if (!previous.prompted && previous.ready
                    && UhcProgression.canCraft(player, recipe.tree(), recipe.slot(), recipe.id())) {
                previous.prompted = true;
                player.sendSystemMessage(prompt(recipe));
            }
        }
    }

    private static State state(Match match, ServerPlayer player) {
        return STATES.computeIfAbsent(match, key -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), key -> new State(player));
    }

    public static Component prompt(UhcCrafting.Recipe recipe) {
        return Component.literal("You can craft " + recipe.id().replace('_', ' ') + ". [Craft]")
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/minigames uhc craft " + recipe.id())));
    }

    private static boolean accepts(ItemStack stack, Item item, UhcCrafting.Recipe recipe, boolean smelted) {
        if (recipe.id().equals("fusion_armor")) {
            return stack.is(Items.DIAMOND_HELMET) || stack.is(Items.DIAMOND_CHESTPLATE)
                    || stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.DIAMOND_BOOTS);
        }
        return UhcCrafting.ingredient(stack, item, recipe.id(), smelted);
    }

    private static boolean relevant(ItemStack stack, UhcCrafting.Recipe recipe, boolean smelted) {
        if (stack.isEmpty()) return false;
        for (Item item : recipe.grid()) if (item != Items.AIR && accepts(stack, item, recipe, smelted)) return true;
        return false;
    }

    private static List<ItemStack> ingredients(ServerPlayer player, UhcCrafting.Recipe recipe) {
        List<ItemStack> result = new ArrayList<>();
        boolean smelted = UhcCrafting.smelted(player);
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!relevant(stack, recipe, smelted)) continue;
            ItemStack existing = null;
            for (ItemStack saved : result) if (ItemStack.isSameItemSameComponents(stack, saved)) { existing = saved; break; }
            if (existing == null) result.add(stack.copy());
            else existing.grow(stack.getCount());
        }
        return result;
    }

    private static boolean sameIngredients(ServerPlayer player, UhcCrafting.Recipe recipe, List<ItemStack> previous) {
        for (ItemStack expected : previous) {
            int count = 0;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (ItemStack.isSameItemSameComponents(stack, expected)) count += stack.getCount();
            }
            if (count != expected.getCount()) return false;
        }
        boolean smelted = UhcCrafting.smelted(player);
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!relevant(stack, recipe, smelted)) continue;
            boolean found = false;
            for (ItemStack expected : previous) if (ItemStack.isSameItemSameComponents(stack, expected)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    /** One inventory slot per occupied grid cell; no mutation until the complete plan succeeds. */
    private static int @Nullable [] plan(ServerPlayer player, UhcCrafting.Recipe recipe, State state) {
        var inventory = player.getInventory();
        int size = inventory.getNonEquipmentItems().size();
        int[] remaining = state.remaining;
        for (int i = 0; i < size; i++) remaining[i] = inventory.getItem(i).getCount();
        int[] slots = state.slots;
        Arrays.fill(slots, -1);
        boolean smelted = UhcCrafting.smelted(player);
        for (int cell = 0; cell < 9; cell++) {
            Item item = recipe.grid()[cell];
            if (item == Items.AIR) continue;
            for (int slot = 0; slot < size; slot++) {
                if (remaining[slot] > 0 && accepts(inventory.getItem(slot), item, recipe, smelted)) {
                    slots[cell] = slot;
                    remaining[slot]--;
                    break;
                }
            }
            if (slots[cell] == -1) return null;
        }
        return slots;
    }

    public static boolean open(ServerPlayer player, String id) {
        Match match = UhcProgression.match(player);
        if (match == null) return false;
        var recipe = UhcCrafting.recipes().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (recipe == null || !UhcProgression.canCraft(player, recipe.tree(), recipe.slot(), id)) return false;
        player.closeContainer();
        int[] plan = plan(player, recipe, state(match, player));
        if (plan == null) return false;
        player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) ->
                new CraftingMenu(containerId, inventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
                    @Override public boolean stillValid(Player user) { return true; }
                }, Component.literal(recipe.id().replace('_', ' '))));
        if (!(player.containerMenu instanceof CraftingMenu menu)) return false;
        for (int cell = 0; cell < 9; cell++) if (plan[cell] >= 0) {
            menu.getInputGridSlots().get(cell).set(player.getInventory().removeItem(plan[cell], 1));
        }
        menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
        menu.broadcastChanges();
        return true;
    }

    public static void close(Match match) { STATES.remove(match); }
}
