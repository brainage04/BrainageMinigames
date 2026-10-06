package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.hub.Hub;
import io.github.brainage04.brainage_minigames.menu.MenuItems;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrainageMinigames {
    public static final String MOD_ID = "brainage_minigames";
    public static final String MOD_NAME = "Brainage Minigames";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void initialize() {
        LOGGER.info("{} initialising...", MOD_NAME);
        Hub.onEnter(MenuItems::giveHubItems);

        LOGGER.info("{} initialised.", MOD_NAME);
    }
}
