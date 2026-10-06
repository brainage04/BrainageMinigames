package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Messages and sidebar lines UHC, Meetup and FinalUHC share. */
final class UhcRules {
    private UhcRules() {}

    /** The line that tells players PvP is on: at the end of a grace period, or at the start of a match without one. */
    static final String PVP_ENABLED = "PvP is now enabled!";

    static void announcePvpEnabled(Match match) {
        match.broadcast(Component.literal(PVP_ENABLED).withStyle(ChatFormatting.YELLOW));
    }

    static void announceShrink(Match match, int size) {
        match.broadcast(
                Component.literal("The border is shrinking to %d blocks across.".formatted(size))
                        .withStyle(ChatFormatting.YELLOW));
    }

    static Component borderLine(double size) {
        return MatchSidebar.label("Border: ", (int) Math.round(size) + " blocks");
    }

    /** Players alive, and in a team layout how many teams they are on. */
    static Component aliveLine(Match match) {
        String alive = String.valueOf(match.aliveCount());
        if (!match.layout().isFreeForAll() && match.layout().teamSizes().stream().anyMatch(size -> size > 1)) {
            int teams = match.standingTeams().size();
            alive += " (" + teams + (teams == 1 ? " team)" : " teams)");
        }
        return MatchSidebar.label("Alive: ", alive);
    }
}
