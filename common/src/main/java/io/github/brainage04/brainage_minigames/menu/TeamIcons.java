package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.Match;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Wool and glass in each match team's colour, and the matching chat colour. */
final class TeamIcons {
    private TeamIcons() {}

    /** The dye of team {@code number}'s colour, as matches colour wool and terracotta. */
    static DyeColor dye(int team) {
        return switch (Match.teamColor(team)) {
            case RED, DARK_RED -> DyeColor.RED;
            case BLUE, DARK_BLUE -> DyeColor.BLUE;
            case GREEN -> DyeColor.LIME;
            case DARK_GREEN -> DyeColor.GREEN;
            case YELLOW -> DyeColor.YELLOW;
            case AQUA -> DyeColor.LIGHT_BLUE;
            case DARK_AQUA -> DyeColor.CYAN;
            case LIGHT_PURPLE -> DyeColor.MAGENTA;
            case DARK_PURPLE -> DyeColor.PURPLE;
            case GOLD -> DyeColor.ORANGE;
            case GRAY -> DyeColor.LIGHT_GRAY;
            case DARK_GRAY -> DyeColor.GRAY;
            case BLACK -> DyeColor.BLACK;
            case WHITE -> DyeColor.WHITE;
        };
    }

    static Item wool(int team) {
        return Items.WOOL.pick(dye(team));
    }

    static Item glass(int team) {
        return Items.STAINED_GLASS_PANE.pick(dye(team));
    }

    static ChatFormatting color(int team) {
        return ChatFormatting.valueOf(Match.teamColor(team).name());
    }

    static String name(int team) {
        return Match.teamName(team) + " Team";
    }
}
