package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.github.brainage04.brainage_minigames.game.duel.DuelGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

public final class BrainageMinigamesGameTest {
    @GameTest
    public void commandsAreRegistered(GameTestHelper context) {
        var minigames =
                context.getLevel()
                        .getServer()
                        .getCommands()
                        .getDispatcher()
                        .getRoot()
                        .getChild("minigames");
        assertTrue(minigames != null, "Expected /minigames to be registered.");
        for (String child :
                new String[] {
                    "list",
                    "status",
                    "join",
                    "watch",
                    "leave",
                    "open",
                    "start",
                    "stop",
                    "settings",
                    "kit"
                }) {
            assertTrue(
                    minigames.getChild(child) != null,
                    "Expected /minigames " + child + " to be registered.");
        }
        var kit = minigames.getChild("kit");
        for (String child : new String[] {"edit", "give", "delete", "list"}) {
            assertTrue(
                    kit.getChild(child) != null,
                    "Expected /minigames kit " + child + " to be registered.");
        }
        context.succeed();
    }

    @GameTest
    public void teamLayoutsParseEvenUnevenAndFreeForAll(GameTestHelper context) {
        TeamLayout uneven =
                TeamLayout.parse("2v3v4").orElseThrow(() -> failure("Expected 2v3v4 to parse."));
        assertEquals(List.of(2, 3, 4), uneven.teamSizes(), "2v3v4 team sizes");
        assertEquals(9, uneven.requiredPlayers(), "2v3v4 required players");
        TeamLayout freeForAll =
                TeamLayout.parse("FFA").orElseThrow(() -> failure("Expected FFA to parse."));
        assertTrue(freeForAll.isFreeForAll(), "Expected FFA to be free-for-all.");
        assertEquals(2, freeForAll.requiredPlayers(), "free-for-all required players");
        for (String invalid : new String[] {"1", "0v1", "1vv1", "v1", "1v", "2x2", "101v1", ""}) {
            assertTrue(
                    TeamLayout.parse(invalid).isEmpty(),
                    "Expected '" + invalid + "' to be rejected.");
        }
        context.succeed();
    }

    @GameTest
    public void playerSnapshotRoundTripsStateAndRewards(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        MinecraftServer server = context.getLevel().getServer();
        Vec3 originalPosition = context.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        Vec3 originalVelocity = new Vec3(0.125, 0.25, -0.375);

        player.setGameMode(GameType.CREATIVE);
        player.snapTo(
                originalPosition.x(), originalPosition.y(), originalPosition.z(), 37.0F, -12.0F);
        player.setDeltaMovement(originalVelocity);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));

        try {
            assertTrue(
                    PlayerSnapshotStorage.save(player),
                    "Expected the player snapshot to be saved.");
            assertTrue(
                    !PlayerSnapshotStorage.save(player),
                    "Expected a pending snapshot never to be overwritten.");
            assertTrue(
                    PlayerSnapshotStorage.addRewards(
                            player, List.of(new ItemStack(Items.EMERALD, 2))),
                    "Expected rewards to be appended to the snapshot.");

            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.snapTo(
                    originalPosition.x() + 8.0,
                    originalPosition.y(),
                    originalPosition.z(),
                    0.0F,
                    0.0F);
            player.setDeltaMovement(Vec3.ZERO);

            assertTrue(
                    PlayerSnapshotStorage.restore(player),
                    "Expected the player snapshot to be restored.");
            assertEquals(GameType.CREATIVE, player.gameMode.getGameModeForPlayer(), "game mode");
            assertItemCount(player, Items.DIAMOND, 3);
            assertItemCount(player, Items.EMERALD, 2);
            assertNear(originalPosition, player.position(), "position");
            assertNear(originalVelocity, player.getDeltaMovement(), "velocity");
            assertTrue(
                    !PlayerSnapshotStorage.hasSnapshot(server, player.getUUID()),
                    "Expected a successful restore to consume the snapshot.");
        } finally {
            if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
                PlayerSnapshotStorage.restore(player);
            }
        }
        context.succeed();
    }

    @GameTest
    public void editableKitPersistsAndBundledKitEquipsArmour(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        var kitId = BrainageMinigames.id("gametest/editable");
        var builtInKitId = BrainageMinigames.id("kits/barebones");
        KitStorage.delete(server, builtInKitId);
        KitStorage.delete(server, kitId);

        try {
            KitStorage.openEditor(player, kitId);
            player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 2));
            player.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 3));
            player.closeContainer();
            var stored =
                    KitStorage.get(server, kitId)
                            .orElseThrow(() -> failure("Expected the edited kit to persist."));
            assertEquals(2, stored.size(), "edited kit stack count");

            player.getInventory().clearContent();
            assertTrue(
                    KitStorage.give(server, kitId, List.of(player)),
                    "Expected the edited kit to be given.");
            assertItemCount(player, Items.DIAMOND, 2);
            assertItemCount(player, Items.EMERALD, 3);
            assertTrue(KitStorage.delete(server, kitId), "Expected the edited kit to be deleted.");
            assertTrue(!KitStorage.exists(server, kitId), "Expected the deleted kit to be gone.");

            player.getInventory().clearContent();
            assertTrue(
                    KitStorage.give(server, builtInKitId, List.of(player)),
                    "Expected the bundled kit to resolve.");
            assertItemCount(player, Items.IRON_SWORD, 1);
            assertTrue(
                    player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "Expected the bundled kit's chestplate to be worn, not carried.");
            assertItemCount(player, Items.IRON_CHESTPLATE, 0);

            KitStorage.openEditor(player, builtInKitId);
            assertTrue(
                    player.containerMenu.getSlot(0).getItem().is(Items.IRON_SWORD),
                    "Expected the bundled kit editor to load its loot-table contents.");
            player.closeContainer();
            assertTrue(
                    KitStorage.get(server, builtInKitId).isPresent(),
                    "Expected closing the bundled kit editor to save an override.");
        } finally {
            KitStorage.delete(server, kitId);
            KitStorage.delete(server, builtInKitId);
        }
        context.succeed();
    }

    @GameTest
    public void uhcRejectsBorderThatGrowsBetweenShrinks(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        Minigame uhc = Minigames.UHC;
        assertTrue(
                uhc.validate(SettingsStorage.resolve(server, uhc)).isEmpty(),
                "Expected the default UHC settings to be valid.");
        SettingsStorage.set(server, uhc, UhcGame.FINAL_SHRINK_SIZE, 500);
        try {
            MatchManager.open(server, uhc, TeamLayout.FREE_FOR_ALL, null);
            throw failure("Expected a final border larger than the first shrink to be rejected.");
        } catch (MatchException expected) {
            assertTrue(
                    expected.getMessage().contains("final_shrink_size"),
                    "Unexpected rejection: " + expected.getMessage());
        } finally {
            SettingsStorage.reset(server, uhc, UhcGame.FINAL_SHRINK_SIZE);
        }
        context.succeed();
    }

    @GameTest
    public void concurrentMatchesUseSeparateArenas(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Match first = openInTestLevel(context, Minigames.GAPPLE, "ffa");
        Match second = openInTestLevel(context, Minigames.GAPPLE, "ffa");
        try {
            var firstBounds = ((BoxArena) first.arena()).bounds();
            var secondBounds = ((BoxArena) second.arena()).bounds();
            assertTrue(
                    !firstBounds.intersects(secondBounds),
                    "Expected concurrent arenas not to overlap.");
            assertEquals(MatchPhase.LOBBY, first.phase(), "first match phase");
            assertEquals(MatchPhase.LOBBY, second.phase(), "second match phase");
        } finally {
            MatchManager.stop(first);
            MatchManager.stop(second);
        }
        context.succeed();
    }

    /**
     * A full 1v1: both join, the match starts itself, a death eliminates, the winner is announced,
     * both are restored.
     */
    @GameTest(maxTicks = 300)
    public void duelEliminatesLoserAndRestoresBothPlayers(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Minigame classic = Minigames.CLASSIC;
        SettingsStorage.set(
                server, classic, classic.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        ServerPlayer winner = loadedPlayer(context);
        Client loserClient = connect(context, "duel_loser");
        ServerPlayer loser = loserClient.player();
        Vec3 home = context.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        winner.snapTo(home.x(), home.y(), home.z(), 0.0F, 0.0F);
        loser.snapTo(home.x(), home.y(), home.z(), 0.0F, 0.0F);
        winner.setGameMode(GameType.CREATIVE);
        winner.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        loser.setGameMode(GameType.CREATIVE);

        Match match = openInTestLevel(context, classic, "1v1");
        MatchManager.join(winner, match, 1);
        MatchManager.join(loser, match, 2);
        assertEquals(MatchPhase.COUNTDOWN, match.phase(), "phase once the last slot fills");

        context.runAfterDelay(
                2,
                () -> {
                    assertEquals(
                            MatchPhase.ACTIVE,
                            match.phase(),
                            "phase after a zero-second countdown");
                    assertEquals(
                            match.arena().level(),
                            winner.level(),
                            "winner's level during the match");
                    assertEquals(
                            GameType.ADVENTURE,
                            winner.gameMode.getGameModeForPlayer(),
                            "winner's game mode during the match");
                    assertItemCount(winner, Items.DIAMOND, 0);
                    assertItemCount(winner, Items.IRON_SWORD, 1);

                    loser.hurtServer(
                            loser.level(), loser.damageSources().genericKill(), Float.MAX_VALUE);
                    assertTrue(loser.isAlive(), "Expected the death to be cancelled.");
                    assertEquals(
                            GameType.SPECTATOR,
                            loser.gameMode.getGameModeForPlayer(),
                            "eliminated player's game mode");
                    assertEquals(
                            MatchPhase.ACTIVE, match.phase(), "phase until the next match tick");

                    context.runAfterDelay(
                            2,
                            () -> {
                                assertEquals(
                                        MatchPhase.ENDED,
                                        match.phase(),
                                        "phase after the last opponent is eliminated");
                                assertEquals(
                                        List.of(match.teamOf(winner.getUUID()).orElseThrow()),
                                        match.winners(),
                                        "winning teams");
                                // Only what the client receives from here on is the restore.
                                loserClient.received();

                                context.runAfterDelay(
                                        110,
                                        () -> {
                                            assertTrue(
                                                    MatchManager.get(match.id()).isEmpty(),
                                                    "Expected the finished match to close.");
                                            assertEquals(
                                                    GameType.CREATIVE,
                                                    winner.gameMode.getGameModeForPlayer(),
                                                    "winner's restored game mode");
                                            assertItemCount(winner, Items.DIAMOND, 5);
                                            assertItemCount(winner, Items.IRON_SWORD, 0);
                                            assertNear(
                                                    home,
                                                    winner.position(),
                                                    "winner's restored position");
                                            assertNear(
                                                    home,
                                                    loser.position(),
                                                    "loser's restored position");
                                            assertTrue(
                                                    !PlayerSnapshotStorage.hasSnapshot(
                                                            server, loser.getUUID()),
                                                    "Expected the loser's snapshot to be consumed.");
                                            assertEquals(
                                                    GameType.CREATIVE,
                                                    loser.gameMode.getGameModeForPlayer(),
                                                    "eliminated player's restored game mode");
                                            assertTrue(
                                                    loser.getAbilities().mayfly
                                                            && loser.getAbilities().instabuild,
                                                    "Expected the restored creative player to have creative abilities.");
                                            List<Packet<?>> restorePackets = loserClient.received();
                                            assertTrue(
                                                    restorePackets.stream()
                                                            .anyMatch(
                                                                    packet ->
                                                                            packet
                                                                                            instanceof
                                                                                            ClientboundGameEventPacket
                                                                                                    event
                                                                                    && event
                                                                                                    .getEvent()
                                                                                            == ClientboundGameEventPacket
                                                                                                    .CHANGE_GAME_MODE
                                                                                    && event
                                                                                                    .getParam()
                                                                                            == GameType
                                                                                                    .CREATIVE
                                                                                                    .getId()),
                                                    "Expected the client to be told it is back in creative mode.");
                                            assertTrue(
                                                    restorePackets.stream()
                                                            .anyMatch(
                                                                    packet ->
                                                                            packet
                                                                                            instanceof
                                                                                            ClientboundPlayerAbilitiesPacket
                                                                                                    abilities
                                                                                    && abilities
                                                                                            .canFly()
                                                                                    && abilities
                                                                                            .canInstabuild()),
                                                    "Expected the client to receive creative abilities.");
                                            loserClient.disconnect();
                                            SettingsStorage.reset(
                                                    server,
                                                    classic,
                                                    classic.setting(GameSetting.COUNTDOWN_SECONDS)
                                                            .orElseThrow());
                                            context.succeed();
                                        });
                            });
                });
    }

    @GameTest(maxTicks = 100)
    public void boxingHitsScoreWithoutDamage(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Minigame boxing = Minigames.BOXING;
        SettingsStorage.set(
                server, boxing, boxing.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, boxing, DuelGame.HITS_TO_WIN, 2);
        // Player hits are gated on the pvp game rule before any damage event fires.
        boolean pvp = server.getGameRules().get(GameRules.PVP);
        server.getGameRules().set(GameRules.PVP, true, server);
        ServerPlayer attacker = loadedPlayer(context);
        ServerPlayer target = loadedPlayer(context);
        Match match = openInTestLevel(context, boxing, "1v1");
        MatchManager.join(attacker, match, 1);
        MatchManager.join(target, match, 2);
        context.runAfterDelay(
                2,
                () -> {
                    assertEquals(
                            MatchPhase.ACTIVE,
                            match.phase(),
                            "phase after a zero-second countdown");
                    // Every mock player is named test-mock-player, so all of them share whichever
                    // scoreboard team one joined last and vanilla friendly-fire rules would block
                    // the hits. Real players have distinct names; matches track teams by UUID.
                    server.getScoreboard().removePlayerFromTeam(attacker.getScoreboardName());
                    hit(attacker, target);
                    hit(attacker, target);
                    assertEquals(
                            1,
                            match.teamOf(attacker.getUUID()).orElseThrow().score(),
                            "score after a second hit inside the target's damage immunity");
                    assertTrue(
                            target.getHealth() == target.getMaxHealth(),
                            "Expected boxing hits to deal no damage.");
                    target.invulnerableTime = 0;
                    hit(attacker, target);
                    context.runAfterDelay(
                            2,
                            () -> {
                                assertEquals(
                                        MatchPhase.ENDED,
                                        match.phase(),
                                        "phase once hits_to_win is reached");
                                assertEquals(
                                        List.of(match.teamOf(attacker.getUUID()).orElseThrow()),
                                        match.winners(),
                                        "winning teams");
                                MatchManager.stop(match);
                                SettingsStorage.reset(
                                        server,
                                        boxing,
                                        boxing.setting(GameSetting.COUNTDOWN_SECONDS)
                                                .orElseThrow());
                                SettingsStorage.reset(server, boxing, DuelGame.HITS_TO_WIN);
                                server.getGameRules().set(GameRules.PVP, pvp, server);
                                context.succeed();
                            });
                });
    }

    /**
     * Combo hits land every hit_delay_ticks (2) ticks, so ten in twenty ticks, and attacks are
     * always at full strength. Attacks run after the server tick, where real attack packets are
     * handled.
     */
    @GameTest(maxTicks = 200)
    public void comboLandsTenHitsPerSecondWithoutAttackCooldown(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Minigame combo = Minigames.COMBO;
        GameSetting countdown = combo.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, combo, countdown, 0);
        boolean pvp = server.getGameRules().get(GameRules.PVP);
        server.getGameRules().set(GameRules.PVP, true, server);
        Client attacker = connect(context, "combo_attacker");
        Client target = connect(context, "combo_target");
        Match match = openInTestLevel(context, combo, "1v1");
        MatchManager.join(attacker.player(), match, 1);
        MatchManager.join(target.player(), match, 2);

        int attempts = 20;
        int[] swings = {0};
        int[] hits = {0};
        float[] weakestSwing = {1.0F};
        RuntimeException[] error = {null};
        context.runAfterDelay(
                2,
                () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after the countdown");
                    afterServerTick(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        ServerPlayer victim = target.player();
                                        victim.setHealth(victim.getMaxHealth());
                                        attacker.player().attack(victim);
                                        if (victim.invulnerableTime == 20) {
                                            hits[0]++;
                                        }
                                        weakestSwing[0] =
                                                Math.min(
                                                        weakestSwing[0],
                                                        attacker.player()
                                                                .getAttackStrengthScale(0.5F));
                                    } catch (RuntimeException exception) {
                                        error[0] = exception;
                                        return;
                                    }
                                    if (++swings[0] < attempts) {
                                        afterServerTick(this);
                                    }
                                }
                            });
                });
        context.succeedWhen(
                () -> {
                    if (error[0] != null) {
                        throw error[0];
                    }
                    assertTrue(swings[0] == attempts, "Still attacking.");
                    try {
                        assertEquals(
                                10, hits[0], "hits landed by one attack every tick for 20 ticks");
                        assertTrue(
                                weakestSwing[0] == 1.0F,
                                "Expected every attack at full strength, found "
                                        + weakestSwing[0]
                                        + ".");
                    } finally {
                        MatchManager.stop(match);
                        SettingsStorage.reset(server, combo, countdown);
                        server.getGameRules().set(GameRules.PVP, pvp, server);
                    }
                    assertTrue(
                            attacker.player()
                                            .getAttribute(Attributes.ATTACK_SPEED)
                                            .getModifier(BrainageMinigames.id("no_attack_cooldown"))
                                    == null,
                            "Expected the restore to remove the attack cooldown modifier.");
                    attacker.disconnect();
                    target.disconnect();
                });
    }

    /**
     * With natural_regeneration 0, food no longer heals a match member but still drains, while a
     * player outside the match keeps vanilla regeneration.
     */
    @GameTest(maxTicks = 100)
    public void disabledRegenerationKeepsHungerDraining(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Minigame classic = Minigames.CLASSIC;
        GameSetting countdown = classic.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        GameSetting regeneration = classic.setting(GameSetting.NATURAL_REGENERATION).orElseThrow();
        SettingsStorage.set(server, classic, countdown, 0);
        SettingsStorage.set(server, classic, regeneration, 0);
        boolean gameRule = server.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, true, server);
        ServerPlayer player = loadedPlayer(context);
        ServerPlayer opponent = loadedPlayer(context);
        ServerPlayer outsider = context.makeMockServerPlayerInLevel();
        Match match = openInTestLevel(context, classic, "1v1");
        MatchManager.join(player, match, 1);
        MatchManager.join(opponent, match, 2);
        context.runAfterDelay(
                2,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after the countdown");
                        for (ServerPlayer each : List.of(player, outsider)) {
                            each.setHealth(10.0F);
                            each.getFoodData().setFoodLevel(20);
                            each.getFoodData().setSaturation(5.0F);
                            for (int tick = 0; tick < 40; tick++) {
                                each.getFoodData().tick(each);
                            }
                        }
                        assertTrue(
                                outsider.getHealth() > 10.0F,
                                "Expected a full hunger bar to heal a player outside the match.");
                        assertTrue(
                                player.getHealth() == 10.0F,
                                "Expected a full hunger bar not to heal a match member, found "
                                        + player.getHealth()
                                        + " health.");

                        player.getFoodData().setSaturation(0.0F);
                        player.getFoodData().addExhaustion(8.5F);
                        player.getFoodData().tick(player);
                        player.getFoodData().tick(player);
                        assertEquals(
                                18,
                                player.getFoodData().getFoodLevel(),
                                "food level after 8.5 exhaustion without saturation");
                    } finally {
                        MatchManager.stop(match);
                        SettingsStorage.reset(server, classic, countdown);
                        SettingsStorage.reset(server, classic, regeneration);
                        server.getGameRules()
                                .set(GameRules.NATURAL_HEALTH_REGENERATION, gameRule, server);
                    }
                    context.succeed();
                });
    }

    /**
     * UHC placement generates the chunks it reads and stands everyone on solid, dry ground: the
     * lobby, the start spread and moveToSurface all avoid a lake flooding the middle of the arena,
     * and lobby players who wander off are brought back.
     */
    @GameTest(maxTicks = 200)
    public void uhcPlacesPlayersOnDryGround(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        int centerX = 4_000_000 + level.getRandom().nextInt(10_000) * 64;
        int centerZ = -4_000_000 - level.getRandom().nextInt(10_000) * 64;
        assertTrue(
                !level.hasChunk(
                        SectionPos.blockToSectionCoord(centerX),
                        SectionPos.blockToSectionCoord(centerZ)),
                "Expected the arena's chunks not to be loaded yet.");

        // Flood every column within 20 blocks of the centre: where the lobby and a spread of a
        // 48-block border would otherwise put players.
        int lake = 20;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centerX - lake; x <= centerX + lake; x++) {
            for (int z = centerZ - lake; z <= centerZ + lake; z++) {
                level.getChunk(
                        SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                level.setBlock(pos.set(x, top - 1, z), Blocks.WATER.defaultBlockState(), 2);
            }
        }

        UhcArena arena = UhcArena.at(level, centerX, centerZ, 48);
        assertOnDryGround(level, arena.lobbyPosition(), centerX, centerZ, lake, "lobby");
        List<Arena.Spawn> spawns = arena.spawns(4);
        assertEquals(4, spawns.size(), "spawn count");
        for (Arena.Spawn spawn : spawns) {
            assertOnDryGround(level, spawn.position(), centerX, centerZ, lake, "spawn");
        }

        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.snapTo(centerX + 0.5, 200.0, centerZ + 0.5, 0.0F, 0.0F);
        arena.moveToSurface(player);
        assertOnDryGround(
                level, player.position(), centerX, centerZ, lake, "moved-to-surface player");

        Vec3 lobby = arena.lobbyPosition();
        player.snapTo(lobby.x() + 40.0, lobby.y(), lobby.z(), 0.0F, 0.0F);
        arena.holdInLobby(player);
        assertNear(lobby, player.position(), "position of a lobby player who wandered off");
        context.succeed();
    }

    private static void assertOnDryGround(
            ServerLevel level, Vec3 position, int centerX, int centerZ, int lake, String what) {
        BlockPos ground = BlockPos.containing(position).below();
        assertTrue(
                ground.getY() >= level.getMinY()
                        && level.getFluidState(ground).isEmpty()
                        && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP),
                "Expected the "
                        + what
                        + " "
                        + position
                        + " to stand on solid, dry ground, found "
                        + level.getBlockState(ground)
                        + ".");
        assertTrue(
                Math.max(Math.abs(ground.getX() - centerX), Math.abs(ground.getZ() - centerZ))
                        > lake,
                "Expected the " + what + " " + position + " outside the lake.");
    }

    /**
     * Each member gets a sidebar objective only their client knows about; later refreshes resend
     * only changed lines, and leaving removes it and puts the server's own sidebar back.
     */
    @GameTest(maxTicks = 200)
    public void sidebarIsPerPlayerAndRestoresServerSidebar(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective serverSidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        Objective gamesWon = ModScoreboard.registerGamesWon(scoreboard);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, gamesWon);
        // No other test plays Bow, so its settings are not changed under this one.
        Minigame bow = Minigames.BOW;
        GameSetting countdown = bow.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, bow, countdown, 0);
        Client red = connect(context, "sidebar_red");
        Client blue = connect(context, "sidebar_blue");
        Client outsider = connect(context, "sidebar_out");
        Match match = openInTestLevel(context, bow, "1v1");
        MatchManager.join(red.player(), match, 1);
        MatchManager.join(blue.player(), match, 2);
        outsider.received();

        Runnable cleanup =
                () -> {
                    MatchManager.stop(match);
                    SettingsStorage.reset(server, bow, countdown);
                    scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, serverSidebar);
                    red.disconnect();
                    blue.disconnect();
                    outsider.disconnect();
                };
        context.runAfterDelay(
                2 + MatchSidebar.REFRESH_TICKS,
                () -> {
                    List<Packet<?>> first = red.received();
                    assertTrue(
                            first.stream()
                                    .anyMatch(
                                            packet ->
                                                    packet
                                                                    instanceof
                                                                    ClientboundSetObjectivePacket
                                                                            objective
                                                            && objective.getMethod()
                                                                    == ClientboundSetObjectivePacket
                                                                            .METHOD_ADD
                                                            && objective
                                                                    .getObjectiveName()
                                                                    .equals(
                                                                            MatchSidebar
                                                                                    .OBJECTIVE_NAME)),
                            "Expected the sidebar objective to be added on the client.");
                    assertTrue(
                            first.stream()
                                    .anyMatch(
                                            packet ->
                                                    packet
                                                                    instanceof
                                                                    ClientboundSetDisplayObjectivePacket
                                                                            display
                                                            && display.getSlot()
                                                                    == DisplaySlot.SIDEBAR
                                                            && display.getObjectiveName()
                                                                    .equals(
                                                                            MatchSidebar
                                                                                    .OBJECTIVE_NAME)),
                            "Expected the sidebar objective to be displayed.");
                    List<ClientboundSetScorePacket> lines = sidebarScores(first);
                    assertTrue(
                            !lines.isEmpty()
                                    && lines.stream()
                                                    .map(ClientboundSetScorePacket::owner)
                                                    .distinct()
                                                    .count()
                                            <= MatchSidebar.MAX_LINES,
                            "Expected at most 15 sidebar lines, found " + lines + ".");
                    assertTrue(
                            lines.stream()
                                    .allMatch(
                                            line ->
                                                    line.numberFormat()
                                                            .filter(
                                                                    format ->
                                                                            format
                                                                                    == BlankFormat
                                                                                            .INSTANCE)
                                                            .isPresent()),
                            "Expected every sidebar line to hide its score.");
                    assertTrue(
                            lines.stream()
                                    .anyMatch(
                                            line ->
                                                    text(line).contains("sidebar_blue")
                                                            && text(line).contains("10❤")),
                            "Expected the opponent's health in hearts on the sidebar: " + lines);
                    assertTrue(
                            lines.stream()
                                    .anyMatch(
                                            line ->
                                                    text(line).startsWith("Team: ")
                                                            && !text(line).equals("Team: ")),
                            "Expected the viewer's team on the sidebar: " + lines);
                    assertTrue(
                            scoreboard.getObjective(MatchSidebar.OBJECTIVE_NAME) == null
                                    && scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
                                            == gamesWon,
                            "Expected the server scoreboard to be untouched.");
                    assertTrue(
                            outsider.received().stream()
                                    .noneMatch(
                                            packet ->
                                                    packet.toString()
                                                            .contains(MatchSidebar.OBJECTIVE_NAME)),
                            "Expected players outside the match not to receive the sidebar.");

                    blue.player().setHealth(12.0F);
                    context.runAfterDelay(
                            MatchSidebar.REFRESH_TICKS,
                            () -> {
                                List<ClientboundSetScorePacket> changed =
                                        sidebarScores(red.received());
                                assertTrue(
                                        changed.stream()
                                                .anyMatch(
                                                        line ->
                                                                text(line).contains("sidebar_blue")
                                                                        && text(line)
                                                                                .contains("6❤")),
                                        "Expected the opponent's new health: " + changed);
                                assertTrue(
                                        changed.stream()
                                                .noneMatch(
                                                        line ->
                                                                text(line).contains("Match #")
                                                                        || text(line)
                                                                                .contains(
                                                                                        "sidebar_red")),
                                        "Expected unchanged lines not to be resent: " + changed);

                                MatchManager.stop(match);
                                List<Packet<?>> removal = red.received();
                                int removed =
                                        indexOf(
                                                removal,
                                                packet ->
                                                        packet
                                                                        instanceof
                                                                        ClientboundSetObjectivePacket
                                                                                objective
                                                                && objective.getMethod()
                                                                        == ClientboundSetObjectivePacket
                                                                                .METHOD_REMOVE
                                                                && objective
                                                                        .getObjectiveName()
                                                                        .equals(
                                                                                MatchSidebar
                                                                                        .OBJECTIVE_NAME));
                                int restored =
                                        indexOf(
                                                removal,
                                                packet ->
                                                        packet
                                                                        instanceof
                                                                        ClientboundSetDisplayObjectivePacket
                                                                                display
                                                                && display.getSlot()
                                                                        == DisplaySlot.SIDEBAR
                                                                && display.getObjectiveName()
                                                                        .equals(
                                                                                ModScoreboard
                                                                                        .GAMES_WON_OBJECTIVE));
                                cleanup.run();
                                assertTrue(
                                        removed >= 0 && restored > removed,
                                        "Expected the sidebar to be removed and Games Won shown again: "
                                                + removal);
                                context.succeed();
                            });
                });
    }

    private static List<ClientboundSetScorePacket> sidebarScores(List<Packet<?>> packets) {
        return packets.stream()
                .filter(packet -> packet instanceof ClientboundSetScorePacket)
                .map(packet -> (ClientboundSetScorePacket) packet)
                .filter(score -> score.objectiveName().equals(MatchSidebar.OBJECTIVE_NAME))
                .toList();
    }

    private static String text(ClientboundSetScorePacket score) {
        return score.display().map(Component::getString).orElse("");
    }

    private static int indexOf(List<Packet<?>> packets, Predicate<Packet<?>> predicate) {
        for (int index = 0; index < packets.size(); index++) {
            if (predicate.test(packets.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Tasks run once, between this server tick and the next, where real clients' packets are
     * handled; GameTest callbacks instead run in the middle of a tick, after the levels tick.
     */
    private static final Queue<Runnable> AFTER_SERVER_TICK = new ConcurrentLinkedQueue<>();

    static {
        ServerTickEvents.START_SERVER_TICK.register(
                server -> {
                    for (int remaining = AFTER_SERVER_TICK.size(); remaining > 0; remaining--) {
                        AFTER_SERVER_TICK.poll().run();
                    }
                });
    }

    private static void afterServerTick(Runnable task) {
        AFTER_SERVER_TICK.add(task);
    }

    /** A player with a connection that keeps every packet the server sends to its client. */
    private record Client(ServerPlayer player, EmbeddedChannel channel) {
        /** Every packet sent since the last call, with bundles unpacked. */
        List<Packet<?>> received() {
            // The server holds back flushes while it ticks; take what it has written so far.
            channel.flushOutbound();
            List<Packet<?>> packets = new ArrayList<>();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof Packet<?> packet) {
                    unbundle(packet, packets);
                }
            }
            return packets;
        }

        void disconnect() {
            player.level().getServer().getPlayerList().remove(player);
        }

        private static void unbundle(Packet<?> packet, List<Packet<?>> packets) {
            if (packet instanceof BundlePacket<?> bundle) {
                for (Packet<?> part : bundle.subPackets()) {
                    unbundle(part, packets);
                }
            } else {
                packets.add(packet);
            }
        }
    }

    /**
     * Connects a player the way GameTestHelper#makeMockServerPlayerInLevel does, but with a
     * distinct name and a channel the test can read.
     */
    private static Client connect(GameTestHelper context, String name) {
        MinecraftServer server = context.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player =
                new ServerPlayer(server, context.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        Client client = new Client(player, channel);
        client.received();
        return client;
    }

    /**
     * The GameTest server has no custom dimensions, so matches are played in arenas built in the
     * test level.
     */
    private static Match openInTestLevel(GameTestHelper context, Minigame game, String layout)
            throws MatchException {
        return MatchManager.open(
                context.getLevel().getServer(),
                game,
                TeamLayout.parse(layout).orElseThrow(),
                null,
                (server, settings) ->
                        BoxArena.open(
                                context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()));
    }

    private static void assertItemCount(ServerPlayer player, Item item, int expected) {
        int actual = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                actual += stack.getCount();
            }
        }
        if (actual != expected) {
            throw failure(
                    "Expected "
                            + expected
                            + " "
                            + item
                            + " in the inventory, found "
                            + actual
                            + ".");
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String description) {
        if (expected.distanceToSqr(actual) > 1.0E-6) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw failure(message);
        }
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    /**
     * Real clients report when they have loaded the world; until then the server makes them immune
     * to damage.
     */
    private static ServerPlayer loadedPlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    /**
     * Lands a melee hit the way Player#attack does, without its attack-charge gate on an unticked
     * mock player.
     */
    private static void hit(ServerPlayer attacker, ServerPlayer target) {
        target.hurtServer(target.level(), target.damageSources().playerAttack(attacker), 1.0F);
    }
}
