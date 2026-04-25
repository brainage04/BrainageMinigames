package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.command.KitCommand;
import io.github.brainage04.brainage_minigames.command.core.ModCommands;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import io.github.brainage04.brainage_minigames.event.ModTickEvents;
import io.github.brainage04.brainage_minigames.uhc.UhcFeature;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrainageMinigames implements ModInitializer {
    public static final String MOD_ID = "brainage_minigames";
    public static final String MOD_NAME = "Brainage Minigames";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        ModCommands.initialize();
        KitCommand.initialize();
        ModServerEvents.initialize();
        ModTickEvents.initialize();
        UhcFeature.initialize();

        LOGGER.info("{} initialised.", MOD_NAME);
    }
}
