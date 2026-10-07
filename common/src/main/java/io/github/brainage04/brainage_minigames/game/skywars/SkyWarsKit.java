package io.github.brainage04.brainage_minigames.game.skywars;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * A SkyWars kit of one mode: what the kit menu shows and what a player gets when the cages open.
 * {@code notes} are extra description lines for what the kit does besides its items, such as a
 * Mini kit's perk. {@code rarity} is null in modes whose kit menu shows none (Mini, Mega).
 */
public record SkyWarsKit(
        String id,
        String name,
        @Nullable Rarity rarity,
        Item icon,
        SkyWarsMode mode,
        List<Part> parts,
        List<String> notes) {

    /** The colour of the kit's name: its rarity's, or green without one. */
    public ChatFormatting color() {
        return rarity == null ? ChatFormatting.GREEN : rarity.color;
    }

    public enum Rarity {
        COMMON("COMMON", ChatFormatting.GREEN),
        RARE("RARE", ChatFormatting.BLUE),
        LEGENDARY("LEGENDARY", ChatFormatting.GOLD),
        MYTHICAL("MYTHICAL", ChatFormatting.LIGHT_PURPLE);

        public final String label;
        public final ChatFormatting color;

        Rarity(String label, ChatFormatting color) {
            this.label = label;
            this.color = color;
        }
    }

    /** Makes one kit item; random parts (a random sword or music disc) roll {@code random}. */
    @FunctionalInterface
    public interface Factory {
        ItemStack make(HolderLookup.Provider registries, RandomSource random);
    }

    /**
     * One kit item and, for random items, the line the menu shows instead of a sample; an empty
     * label shows nothing, for a random item an earlier part's label already describes.
     */
    public record Part(@Nullable String label, Factory factory) {}

    /** The kit's items, in the order they are given. */
    public List<ItemStack> items(HolderLookup.Provider registries, RandomSource random) {
        List<ItemStack> items = new ArrayList<>(parts.size());
        for (Part part : parts) {
            items.add(part.factory().make(registries, random));
        }
        return items;
    }

    /** The menu's description: each item with its count and enchantments, then the notes. */
    public List<Component> contents(HolderLookup.Provider registries) {
        List<Component> lines = new ArrayList<>();
        for (Part part : parts) {
            if (part.label() != null) {
                if (!part.label().isEmpty()) lines.add(Component.literal(part.label()).withStyle(ChatFormatting.GRAY));
                continue;
            }
            ItemStack sample = part.factory().make(registries, RandomSource.create(0));
            String label = sample.getHoverName().getString()
                    + (sample.getCount() > 1 ? " x" + sample.getCount() : "");
            lines.add(Component.literal(label).withStyle(ChatFormatting.GRAY));
            ItemEnchantments enchantments = sample.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (enchantments.isEmpty()) {
                enchantments = sample.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            }
            for (var entry : enchantments.entrySet()) {
                lines.add(Component.literal("   ∙ ")
                        .append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).copy()
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        for (String note : notes) {
            lines.add(Component.literal(note).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** A fixed kit item, built fluently. */
    public static final class Stack implements Factory {
        private final Item item;
        private int count = 1;
        private final List<ResourceKey<Enchantment>> enchantments = new ArrayList<>();
        private final List<Integer> levels = new ArrayList<>();
        private @Nullable String name;
        private @Nullable Integer dye;
        private @Nullable Holder<Potion> potion;
        private final List<MobEffectInstance> effects = new ArrayList<>();
        private @Nullable String ability;
        private boolean glint;

        public Stack(Item item) {
            this.item = item;
        }

        public Stack count(int count) {
            this.count = count;
            return this;
        }

        /** Enchants the item; books store the enchantment instead. */
        public Stack enchant(ResourceKey<Enchantment> enchantment, int level) {
            enchantments.add(enchantment);
            levels.add(level);
            return this;
        }

        public Stack name(String name) {
            this.name = name;
            return this;
        }

        public Stack dye(int rgb) {
            this.dye = rgb;
            return this;
        }

        public Stack potion(Holder<Potion> potion) {
            this.potion = potion;
            return this;
        }

        public Stack effect(MobEffectInstance effect) {
            effects.add(effect);
            return this;
        }

        /** Marks the item as one of {@link SkyWarsItems}' ability items. */
        public Stack ability(String id) {
            this.ability = id;
            return this;
        }

        public Stack glint() {
            this.glint = true;
            return this;
        }

        @Override
        public ItemStack make(HolderLookup.Provider registries, RandomSource random) {
            ItemStack stack = new ItemStack(item, count);
            var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            boolean book = item == net.minecraft.world.item.Items.ENCHANTED_BOOK;
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (int index = 0; index < enchantments.size(); index++) {
                mutable.set(lookup.getOrThrow(enchantments.get(index)), levels.get(index));
            }
            if (!enchantments.isEmpty()) {
                stack.set(book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, mutable.toImmutable());
            }
            if (name != null) {
                // A custom name: potions and heads ignore a plain item name.
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
            }
            if (dye != null) {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dye));
            }
            if (potion != null || !effects.isEmpty()) {
                stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                        java.util.Optional.ofNullable(potion),
                        java.util.Optional.empty(),
                        List.copyOf(effects),
                        java.util.Optional.empty()));
            }
            if (ability != null) {
                CompoundTag tag = new CompoundTag();
                tag.putString(SkyWarsItems.KEY, ability);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            if (glint) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            return stack;
        }
    }
}
