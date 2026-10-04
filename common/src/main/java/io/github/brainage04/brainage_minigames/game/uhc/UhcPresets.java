package io.github.brainage04.brainage_minigames.game.uhc;

import net.minecraft.server.MinecraftServer;

/** Named rule bundles; individual gamerules remain editable after applying a preset. */
public final class UhcPresets {
    private UhcPresets() {}

    public static void apply(MinecraftServer server, boolean hypixel) {
        var rules = server.getGameRules();
        rules.set(UhcModeRules.BORDER_STYLE, hypixel ? 0 : 1, server);
        rules.set(UhcModeRules.DOUBLE_HEALTH, hypixel, server);
        rules.set(UhcModeRules.DEATHMATCH, true, server);
        rules.set(UhcModeRules.DEATHMATCH_AFTER_GRACE, hypixel ? 35 : 0, server);
        rules.set(UhcModeRules.DEATHMATCH_SKIP_PLAYERS, hypixel ? 15 : 0, server);
        rules.set(UhcModeRules.DEATHMATCH_SKIP_MINUTES, 10, server);
        rules.set(UhcModeRules.DEATHMATCH_DURATION, hypixel ? 15 : 0, server);
        rules.set(UhcModeRules.TIMEOUT_MOST_KILLS, hypixel, server);
        rules.set(UhcModeRules.COMBAT_LOGGER, hypixel, server);
    }
}
