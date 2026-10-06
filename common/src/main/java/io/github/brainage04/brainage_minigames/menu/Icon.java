package io.github.brainage04.brainage_minigames.menu;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;

/**
 * Builds the item a menu shows in a slot: a coloured name, grey description lines, a blank line,
 * then a yellow "Click to ...!" line, without the item's own attribute or enchantment tooltip.
 */
public final class Icon {
    /** Longest lore line before a description wraps. */
    private static final int WRAP = 36;

    private static final List<DataComponentType<?>> HIDDEN =
            List.of(
                    DataComponents.ATTRIBUTE_MODIFIERS,
                    DataComponents.ENCHANTMENTS,
                    DataComponents.STORED_ENCHANTMENTS,
                    DataComponents.UNBREAKABLE,
                    DataComponents.POTION_CONTENTS,
                    DataComponents.DYED_COLOR,
                    DataComponents.CHARGED_PROJECTILES,
                    DataComponents.FIREWORKS,
                    DataComponents.TRIM,
                    DataComponents.JUKEBOX_PLAYABLE,
                    DataComponents.BANNER_PATTERNS);

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();

    private Icon(ItemStack stack) {
        this.stack = stack;
    }

    public static Icon of(ItemLike item) {
        return new Icon(new ItemStack(item));
    }

    /** A copy of {@code stack} (keeping its enchantments, potion and so on) to label. */
    public static Icon of(ItemStack stack) {
        return new Icon(stack.copyWithCount(1));
    }

    /** A player head showing {@code profile}'s skin. */
    public static Icon head(GameProfile profile) {
        Icon icon = of(Items.PLAYER_HEAD);
        icon.stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return icon;
    }

    public Icon name(String name, ChatFormatting color) {
        return name(Component.literal(name).withStyle(color));
    }

    public Icon name(MutableComponent name) {
        stack.set(DataComponents.CUSTOM_NAME, name.withStyle(style -> style.withItalic(false)));
        return this;
    }

    /** A grey description, wrapped into lines of about 36 characters. */
    public Icon text(String text) {
        for (String line : wrap(text)) {
            line(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return this;
    }

    /** {@code label} in grey followed by {@code value} in {@code color}, e.g. "Players: 3/4". */
    public Icon value(String label, String value, ChatFormatting color) {
        return line(
                Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(value).withStyle(color)));
    }

    public Icon line(MutableComponent line) {
        lore.add(line.withStyle(style -> style.withItalic(false)));
        return this;
    }

    public Icon blank() {
        lore.add(Component.empty());
        return this;
    }

    /** The yellow call to action ending the lore, e.g. {@code "Click to join!"}. */
    public Icon action(String action) {
        return line(Component.literal(action).withStyle(ChatFormatting.YELLOW));
    }

    /** A red line explaining why clicking does nothing right now. */
    public Icon refusal(String reason) {
        for (String line : wrap(reason)) {
            line(Component.literal(line).withStyle(ChatFormatting.RED));
        }
        return this;
    }

    /** The stack size, clamped to what one slot shows. */
    public Icon count(int count) {
        stack.setCount(Math.clamp(count, 1, stack.getMaxStackSize()));
        return this;
    }

    public Icon glint(boolean glint) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
        return this;
    }

    public ItemStack build() {
        ItemStack built = stack.copy();
        built.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
        TooltipDisplay display = TooltipDisplay.DEFAULT;
        for (DataComponentType<?> hidden : HIDDEN) {
            display = display.withHidden(hidden, true);
        }
        built.set(DataComponents.TOOLTIP_DISPLAY, display);
        return built;
    }

    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > WRAP) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
