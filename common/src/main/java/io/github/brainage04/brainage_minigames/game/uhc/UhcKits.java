package io.github.brainage04.brainage_minigames.game.uhc;

import static net.minecraft.world.item.Items.*;

import io.github.brainage04.brainage_minigames.storage.KitStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/** Historical levels plus the released 2017/2019 changes; source gaps are listed in the catalog. */
public final class UhcKits {
    public enum Kit {
        LEATHER("leather", 150_000), ARCHER("archer", 250_000), ENCHANTING("enchanting", 250_000),
        STONE("stone", 250_000), LUNCH("lunch", 200_000), LOOTER("looter", 250_000),
        ECOLOGIST("ecologist", 250_000), FARMER("farmer", 200_000), HORSEMAN("horseman", 200_000), TRAPPER("trapper", 250_000);
        public final String id; public final int prestigeCost;
        Kit(String id, int prestigeCost) { this.id = id; this.prestigeCost = prestigeCost; }
        public static @Nullable Kit find(String id) { for (Kit kit : values()) if (kit.id.equals(id)) return kit; return null; }
        public List<UhcProgression.Node> nodes() {
            return List.of(new UhcProgression.Node("level1", 2_500, List.of(), false),
                    new UhcProgression.Node("level2", 7_500, List.of("level1"), false),
                    new UhcProgression.Node("level3", 15_000, List.of("level2"), false),
                    new UhcProgression.Node("prestige", prestigeCost, List.of("level3"), false));
        }
    }
    private UhcKits() {}

    public static int level(ServerPlayer player, Kit kit) {
        if (player.level().getGameRules().get(UhcProgression.MAX_ALL)) return 3;
        var owned = UhcProgression.purchases(player.level().getServer(), player.getUUID());
        for (int level = 3; level > 0; level--) if (owned.containsKey("kits/" + kit.id + "/level" + level)) return level;
        return 0;
    }

    public static void equip(ServerPlayer player) {
        Kit kit = Kit.find(UhcProgression.selectedKit(player.level().getServer(), player.getUUID()));
        if (kit == null) return;
        boolean prestige = player.level().getGameRules().get(UhcProgression.MAX_ALL)
                || UhcProgression.purchases(player.level().getServer(), player.getUUID()).containsKey("kits/" + kit.id + "/prestige");
        List<ItemStack> items = items(player, kit, level(player, kit));
        if (prestige) prestige(player, kit, items);
        player.getInventory().clearContent();
        for (ItemStack item : items) KitStorage.equipOrGive(player, item);
    }

    public static List<ItemStack> items(ServerPlayer player, Kit kit, int level) {
        List<ItemStack> items = new ArrayList<>();
        switch (kit) {
            case LEATHER -> { for (Item item : List.of(LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS)) { var stack = new ItemStack(item); if (level > 0) UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, level); items.add(stack); } }
            case ARCHER -> { add(items, STRING, 3 + level); add(items, FEATHER, 3 + 2 * level); if (level == 3) tool(player, items, STONE_SHOVEL); }
            case ENCHANTING -> { add(items, BOOK, 1 + level); add(items, EXPERIENCE_BOTTLE, level == 3 ? 15 : 7 + 3 * level); if (level == 3) { add(items, LAPIS_LAZULI, 18); tool(player, items, STONE_PICKAXE); } }
            case STONE -> { for (Item item : List.of(STONE_SWORD, STONE_PICKAXE, STONE_AXE, STONE_SHOVEL)) { var stack = new ItemStack(item); if (level > 0) UhcCrafting.enchant(player, stack, Enchantments.EFFICIENCY, level); if (level == 3) UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 1); items.add(stack); } }
            case LUNCH -> { add(items, APPLE, Math.min(3, level + 1)); if (level < 3) add(items, COOKED_BEEF, 3 + 2 * level); else { add(items, CARROT, 12); add(items, MELON_SLICE, 2); add(items, GOLD_INGOT, 2); } }
            case LOOTER -> { add(items, BONE, Math.min(3, level + 1)); add(items, SLIME_BALL, level == 0 ? 1 : level == 3 ? 3 : 2); if (level > 0) add(items, GUNPOWDER, level == 3 ? 2 : 1); if (level >= 2) add(items, SPIDER_EYE, level == 3 ? 2 : 1); if (level == 3) { var sword = new ItemStack(STONE_SWORD); UhcCrafting.enchant(player, sword, Enchantments.LOOTING, 1); items.add(sword); } }
            case ECOLOGIST -> { add(items, OAK_LOG, 8 << level); add(items, LILY_PAD, 8 << level); if (level == 3) { add(items, SUGAR_CANE, 12); add(items, VINE, 21); tool(player, items, STONE_AXE); } }
            case FARMER -> { add(items, level == 0 ? STONE_HOE : level == 1 ? GOLDEN_HOE : IRON_HOE, 1); add(items, level == 0 ? MELON_SEEDS : MELON_SLICE, Math.max(1, level)); if (level > 0) add(items, CARROT, level); add(items, BONE_MEAL, level + 1); }
            case HORSEMAN -> { add(items, LEATHER, 3 * (level + 1)); add(items, level == 3 ? HAY_BLOCK : WHEAT, level == 3 ? 1 : 3 + 2 * level); if (level >= 2) add(items, STRING, level == 3 ? 4 : 1); if (level == 3) { add(items, GOLDEN_HORSE_ARMOR, 1); items.add(UhcCrafting.marked(new ItemStack(HORSE_SPAWN_EGG), "kit_horse")); } }
            case TRAPPER -> { add(items, PISTON, 2 * (level + 1)); add(items, STICKY_PISTON, 2 * (level + 1)); add(items, REDSTONE, 10 + 5 * level); add(items, OAK_LOG, 4 * (level + 1)); }
        }
        return items;
    }

    private static void add(List<ItemStack> items, Item item, int count) { items.add(new ItemStack(item, count)); }
    private static void tool(ServerPlayer player, List<ItemStack> items, Item item) { var stack = new ItemStack(item); UhcCrafting.enchant(player, stack, Enchantments.EFFICIENCY, 3); UhcCrafting.enchant(player, stack, Enchantments.UNBREAKING, 1); items.add(stack); }
    private static void prestige(ServerPlayer player, Kit kit, List<ItemStack> items) {
        int roll = player.getRandom().nextInt(100); ItemStack bonus;
        switch (kit) {
            case LEATHER -> {
                Item armor = roll < 35 ? IRON_HELMET : roll < 70 ? IRON_BOOTS : roll < 90 ? IRON_LEGGINGS : IRON_CHESTPLATE;
                bonus = new ItemStack(armor); UhcCrafting.enchant(player, bonus, Enchantments.PROTECTION, roll < 70 ? 2 : 1);
                if (armor == IRON_HELMET) UhcCrafting.enchant(player, bonus, Enchantments.AQUA_AFFINITY, 1);
                if (armor == IRON_BOOTS) UhcCrafting.enchant(player, bonus, Enchantments.FEATHER_FALLING, 1);
                var slot = player.getEquipmentSlotForItem(bonus);
                for (int i = 0; i < items.size(); i++) if (player.getEquipmentSlotForItem(items.get(i)) == slot) { items.set(i, bonus); return; }
                return;
            }
            case ARCHER -> bonus = new ItemStack(roll < 25 ? SUGAR_CANE : roll < 50 ? FLINT : roll < 75 ? ARROW : BONE, roll < 25 ? 6 : roll < 50 ? 16 : roll < 75 ? 32 : 1);
            case ENCHANTING -> { bonus = new ItemStack(roll < 50 ? SUGAR_CANE : roll < 70 ? OBSIDIAN : ENCHANTED_BOOK, roll < 50 ? 9 : roll < 70 ? 4 : 1); if (roll >= 70) { UhcCrafting.enchant(player, bonus, roll < 85 ? Enchantments.SHARPNESS : Enchantments.PROTECTION, 1); UhcCrafting.enchant(player, bonus, roll < 85 ? Enchantments.POWER : Enchantments.FEATHER_FALLING, 1); } }
            case STONE -> { bonus = new ItemStack(roll < 35 ? IRON_SHOVEL : roll < 65 ? IRON_AXE : roll < 90 ? IRON_PICKAXE : IRON_SWORD); UhcCrafting.enchant(player, bonus, roll < 90 ? Enchantments.EFFICIENCY : Enchantments.LOOTING, roll < 90 ? 2 : 1); if (roll < 90) UhcCrafting.enchant(player, bonus, Enchantments.UNBREAKING, 1); Item replace = roll < 35 ? STONE_SHOVEL : roll < 65 ? STONE_AXE : roll < 90 ? STONE_PICKAXE : STONE_SWORD; for (int i = 0; i < items.size(); i++) if (items.get(i).is(replace)) { items.set(i, bonus); return; } return; }
            case LUNCH -> bonus = new ItemStack(roll < 25 ? CARROT : roll < 50 ? GLISTERING_MELON_SLICE : roll < 75 ? GOLD_INGOT : COCOA_BEANS, roll < 25 ? 4 : 2);
            case LOOTER -> bonus = new ItemStack(roll < 15 ? MAGMA_CREAM : roll < 50 ? FERMENTED_SPIDER_EYE : roll < 75 ? INK_SAC : FEATHER, roll < 50 ? 1 : roll < 75 ? 2 : 3);
            case ECOLOGIST -> bonus = roll < 40 ? new ItemStack(COW_SPAWN_EGG, 6) : roll < 70 ? new ItemStack(COAL_BLOCK, 5) : roll < 90 ? UhcCrafting.marked(new ItemStack(WOLF_SPAWN_EGG, 2), "kit_wolf") : new ItemStack(EMERALD);
            case FARMER -> { if (roll < 40) { add(items, BROWN_MUSHROOM, 4); add(items, RED_MUSHROOM, 4); return; } bonus = new ItemStack(roll < 75 ? APPLE : roll < 90 ? MELON : BONE, roll < 75 ? 4 : roll < 90 ? 1 : 2); }
            case HORSEMAN -> bonus = new ItemStack(roll < 40 ? HAY_BLOCK : roll < 75 ? SADDLE : roll < 90 ? GOLDEN_CARROT : DIAMOND_HORSE_ARMOR, roll < 40 ? 4 : roll < 75 ? 1 : roll < 90 ? 12 : 1);
            // No published Trapper prestige probabilities: local equal four-way policy, not Hypixel-confirmed.
            case TRAPPER -> { int choice = roll / 25; bonus = new ItemStack(choice == 0 ? STICKY_PISTON : choice == 1 ? OAK_LOG : choice == 2 ? TNT_MINECART : STONE_PICKAXE, choice == 0 ? 4 : choice == 1 ? 16 : 1); if (choice == 3) { UhcCrafting.enchant(player, bonus, Enchantments.EFFICIENCY, 3); UhcCrafting.enchant(player, bonus, Enchantments.UNBREAKING, 1); } }
            default -> throw new IllegalStateException();
        }
        items.add(bonus);
    }
}
