package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.TestPlayers.ChatPlayer;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcModeRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Team layouts in real UHC, Meetup and FinalUHC matches, one test per layout: teams of one to four
 * and an uneven layout. Each checks that teammates start together and apart from other teams, chat
 * reaches only the team unless it starts with {@code !}, teammates cannot hurt each other with
 * weapons, arrows, lava or fire, anti-janitor locks whole teams against each other, the sidebar
 * shows the team, and the match ends when one team is left.
 */
public final class TeamsGameTestFunctions {
    private static final int SEED = 942026;

    private TeamsGameTestFunctions() {}

    public static void uhcUneven(GameTestHelper context) {
        new Fixture(context, Minigames.UHC, "1v2v3").run();
    }

    public static void uhcSolo(GameTestHelper context) {
        new Fixture(context, Minigames.UHC, "ffa").run();
    }

    public static void meetupTeamsOfFour(GameTestHelper context) {
        new Fixture(context, Minigames.MEETUP, "4v4").run();
    }

    public static void finalUhcTeamsOfTwo(GameTestHelper context) {
        new Fixture(context, Minigames.FINAL_UHC, "2v2v2").run();
    }

    private static final class Fixture {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final Minigame game;
        private final TeamLayout layout;
        private final CompoundTag settings;
        private final GameRules rules;
        private final List<ChatPlayer> players = new ArrayList<>();
        /** The players of each team, in team order; a free-for-all player is a team of one. */
        private final List<List<ChatPlayer>> teams = new ArrayList<>();
        private Match match;
        private final List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
        private ServerLevel forcedLevel;

        Fixture(GameTestHelper context, Minigame game, String layout) {
            this.context = context;
            this.server = context.getLevel().getServer();
            this.game = game;
            this.layout = TeamLayout.parse(layout).orElseThrow();
            settings = server.getCommandStorage().get(BrainageMinigames.id("settings")).copy();
            rules = server.getGameRules().copy(context.getLevel().enabledFeatures());
            GameTestLifecycle.afterTest(context, this::close);
            server.getGameRules().set(AntiJanitor.ENABLED, true, server);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, 0, server);
            server.getGameRules().set(UhcModeRules.ALWAYS_DAY, true, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH, false, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH_AFTER_GRACE, 0, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH_SKIP_PLAYERS, 0, server);
            // Passive perks (absorption) would soak up the damage these checks measure.
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            server.getGameRules().set(UhcProgression.MAX_ALL_KITS, false, server);
            for (GameSetting setting : game.settings()) SettingsStorage.reset(server, game, setting);
            SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
            SettingsStorage.set(server, game, UhcGame.REGION_SEED, SEED);
            if (game == Minigames.UHC) {
                SettingsStorage.set(server, game, UhcGame.GRACE_PERIOD, 0);
                SettingsStorage.set(server, game, UhcGame.BORDER_START_SIZE, 200);
                SettingsStorage.set(server, game, UhcGame.FIRST_SHRINK_SIZE, 150);
                SettingsStorage.set(server, game, UhcGame.SECOND_SHRINK_SIZE, 100);
                SettingsStorage.set(server, game, UhcGame.THIRD_SHRINK_SIZE, 50);
                SettingsStorage.set(server, game, UhcGame.FINAL_SHRINK_SIZE, 25);
            }
            List<Integer> sizes = this.layout.isFreeForAll() ? List.of(1, 1, 1) : this.layout.teamSizes();
            for (int size : sizes) {
                List<ChatPlayer> team = new ArrayList<>();
                for (int member = 0; member < size; member++) {
                    ChatPlayer player = TestPlayers.chat(context, "tm" + UUID.randomUUID().toString().substring(0, 8));
                    team.add(player);
                    players.add(player);
                }
                teams.add(team);
            }
            try {
                match = MatchManager.open(server, game, this.layout, null);
            } catch (MatchException exception) {
                throw failure(exception.getMessage());
            }
        }

        void run() {
            GameTestLifecycle.awaitPreparation(context, () -> match.arena().lobbyReady(), () -> {
                try {
                    for (int team = 0; team < teams.size(); team++) {
                        for (ChatPlayer player : teams.get(team)) {
                            MatchManager.join(player, match, layout.isFreeForAll() ? 0 : team + 1);
                        }
                    }
                    if (match.phase() == MatchPhase.LOBBY) match.start();
                } catch (MatchException exception) {
                    throw failure(exception.getMessage());
                }
                GameTestLifecycle.awaitPreparation(context, () -> match.phase() == MatchPhase.ACTIVE, () -> {
                    for (ServerPlayer player : players) {
                        player.hasChangedDimension();
                        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                    }
                    checkTeamsAndSpawns();
                    checkSidebar();
                    checkChat();
                    checkWeaponFriendlyFire();
                    checkHazards();
                });
            });
        }

        /** Each team is the listed players; teammates start on one spot, other teams well apart. */
        private void checkTeamsAndSpawns() {
            check(match.contestingTeams() == teams.size(), "contesting teams " + match.contestingTeams());
            for (int index = 0; index < teams.size(); index++) {
                MatchTeam team = match.teamNumbered(index + 1).orElseThrow();
                List<UUID> expected = teams.get(index).stream().map(ServerPlayer::getUUID).toList();
                check(team.members().equals(expected) || layout.isFreeForAll() && team.members().size() == 1,
                        "team " + (index + 1) + " is " + team.members() + ", expected " + expected);
            }
            for (ChatPlayer a : players) {
                for (ChatPlayer b : players) {
                    if (a == b) continue;
                    boolean mates = match.teamOf(a.getUUID()).equals(match.teamOf(b.getUUID()));
                    double distance = a.position().subtract(b.position()).horizontalDistance();
                    check(a.level() == match.arena().level(), a.getScoreboardName() + " is not in the arena");
                    if (mates) {
                        check(distance <= 1.5, "teammates started " + distance + " blocks apart");
                        check(a.isAlliedTo(b) && a.getTeam() == b.getTeam(), "teammates are not on one scoreboard team");
                    } else {
                        check(distance >= 8, "two teams started only " + distance + " blocks apart");
                        check(!a.isAlliedTo(b), "opponents are allied");
                    }
                }
            }
        }

        /** The sidebar names the viewer's team, each teammate's health, and the players and teams alive. */
        private void checkSidebar() {
            for (List<ChatPlayer> team : teams) {
                ChatPlayer viewer = team.getFirst();
                List<String> lines = MatchSidebar.lines(viewer, match).stream().map(Component::getString).toList();
                String teamName = match.teamOf(viewer.getUUID()).orElseThrow().displayName().getString();
                check(lines.contains("Team: " + teamName), "no team line in " + lines);
                for (ChatPlayer mate : team.subList(1, team.size())) {
                    check(lines.stream().anyMatch(line -> line.contains(mate.getScoreboardName()) && line.contains("\u2764")),
                            "no health line for teammate " + mate.getScoreboardName() + " in " + lines);
                }
                String alive = "Alive: " + players.size()
                        + (layout.isFreeForAll() || layout.commonTeamSize() == 1 ? "" : " (" + teams.size() + " teams)");
                check(lines.contains(alive), "no '" + alive + "' line in " + lines);
            }
        }

        /** Chat goes to the team; '!' and solo players' chat goes to everyone in the match. */
        private void checkChat() {
            for (List<ChatPlayer> team : teams) {
                ChatPlayer sender = team.getFirst();
                String text = "plan " + sender.getScoreboardName();
                say(sender, text);
                String teamName = match.teamOf(sender.getUUID()).orElseThrow().displayName().getString();
                for (ChatPlayer player : players) {
                    boolean heard = player.chat.stream().anyMatch(line -> line.getString().contains(text));
                    boolean shouldHear = team.size() == 1 || team.contains(player);
                    check(heard == shouldHear, player.getScoreboardName() + (heard ? " overheard " : " missed ") + "'" + text + "'");
                    if (heard && team.size() > 1) {
                        check(player.chat.stream().anyMatch(line -> line.getString().contains(text)
                                        && line.getString().contains(teamName)),
                                "team chat did not name the team " + teamName + ": " + player.chat.getLast().getString());
                    }
                }
                String shout = Match.SHOUT_PREFIX + "hello from " + sender.getScoreboardName();
                say(sender, shout);
                for (ChatPlayer player : players) {
                    check(player.chat.stream().anyMatch(line -> line.getString().contains(shout)),
                            player.getScoreboardName() + " missed the shout '" + shout + "'");
                }
                String command = "all hear " + sender.getScoreboardName();
                server.getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "shout " + command);
                for (ChatPlayer player : players) {
                    check(player.chat.stream().anyMatch(line -> line.getString().contains(command)),
                            player.getScoreboardName() + " missed /shout '" + command + "'");
                }
            }
        }

        /** Melee, arrow, thrown and potion damage from a teammate does nothing. */
        private void checkWeaponFriendlyFire() {
            for (List<ChatPlayer> team : teams) {
                if (team.size() < 2) continue;
                ServerPlayer attacker = team.get(0), mate = team.get(1);
                var arrow = new Arrow(mate.level(), attacker, new ItemStack(Items.ARROW), null);
                var snowball = EntityTypes.SNOWBALL.create(mate.level(), EntitySpawnReason.COMMAND);
                snowball.setOwner(attacker);
                var sources = mate.damageSources();
                for (var source : List.of(sources.playerAttack(attacker), sources.arrow(arrow, attacker),
                        sources.thrown(snowball, attacker), sources.indirectMagic(snowball, attacker))) {
                    float health = mate.getHealth();
                    mate.invulnerableTime = 0;
                    mate.hurtServer(mate.level(), source, 4);
                    check(mate.getHealth() == health, "a teammate's " + source.getMsgId() + " did damage");
                }
                arrow.discard();
                snowball.discard();
            }
        }

        /**
         * On a platform above the first team's spawn: a teammate's poured lava and lit fire do not
         * hurt or keep burning, an opponent's lava does; a real arrow passes through a teammate into
         * the opponent behind. Then anti-janitor and the win.
         */
        private void checkHazards() {
            List<ChatPlayer> own = teams.stream().filter(team -> team.size() > 1).findFirst().orElse(null);
            if (own == null) {
                checkAntiJanitor(null, teams.get(0).getFirst(), teams.get(1).getFirst());
                return;
            }
            ServerLevel level = match.arena().level();
            ChatPlayer shooter = own.get(0), mate = own.get(1);
            ChatPlayer foe = teams.stream().filter(team -> team != own).findFirst().orElseThrow().getFirst();
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    shooter.blockPosition()).above(12);
            // The test's players are not moved by a client, so keep the platform's entities (the arrow) ticking.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var chunk = net.minecraft.world.level.ChunkPos.containing(base.offset(dx * 16, 0, dz * 16));
                if (level.setChunkForced(chunk.x(), chunk.z(), true)) forced.add(chunk);
            }
            forcedLevel = level;
            for (int x = -4; x <= 4; x++) for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            place(mate, base);
            place(shooter, base.offset(2, 0, 0));
            place(foe, base.offset(-3, 0, 0));
            for (ServerPlayer player : List.of(shooter, mate, foe)) {
                // UHC's starting fire resistance would hide whether lava and fire are allowed to hurt.
                player.removeAllEffects();
                player.setHealth(player.getMaxHealth());
            }
            float mateHealth = mate.getHealth();
            // A client's connection ticks its player, which is when standing in lava or fire hurts.
            context.onEachTick(() -> {
                for (ServerPlayer player : List.of(shooter, mate, foe)) {
                    if (match.isAlive(player.getUUID())) player.doTick();
                }
            });

            pour(shooter, base);
            check(level.getFluidState(base).is(net.minecraft.tags.FluidTags.LAVA), "the teammate's lava was not poured");
            context.runAfterDelay(30, () -> {
                check(mate.getHealth() == mateHealth && !mate.isOnFire(),
                        "a teammate's lava hurt (" + mate.getHealth() + "/" + mateHealth + ") or burned " + mate.getScoreboardName());
                level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
                shooter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
                BlockPos floor = base.below();
                shooter.gameMode.useItemOn(shooter, level, shooter.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
                check(level.getBlockState(base).is(net.minecraft.tags.BlockTags.FIRE), "the teammate's fire was not lit");
                context.runAfterDelay(30, () -> {
                    check(mate.getHealth() == mateHealth && !mate.isOnFire(),
                            "a teammate's fire hurt (" + mate.getHealth() + "/" + mateHealth + ") or burned " + mate.getScoreboardName());
                    level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
                    place(foe, base.offset(2, 0, 0));
                    place(shooter, base.offset(-3, 0, 0));
                    pour(foe, base);
                    context.runAfterDelay(15, () -> {
                        check(mate.getHealth() < mateHealth, "an opponent's lava did not hurt " + mate.getScoreboardName()
                                + ": health " + mate.getHealth() + ", in lava " + mate.isInLava() + ", lava there "
                                + level.getFluidState(base).is(net.minecraft.tags.FluidTags.LAVA) + ", hand "
                                + foe.getMainHandItem() + ", at " + mate.blockPosition() + " vs " + base);
                        level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
                        mate.clearFire();
                        mate.setHealth(mate.getMaxHealth());
                        float healthBefore = mate.getHealth(), foeBefore = foe.getHealth();
                        // Shooter, teammate and opponent in a line: the arrow must pass the teammate.
                        place(shooter, base.offset(-3, 0, 0));
                        place(mate, base);
                        place(foe, base.offset(3, 0, 0));
                        Vec3 from = shooter.getEyePosition();
                        Vec3 to = foe.position().add(0, 1.2, 0);
                        var arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
                        arrow.setPos(from.add(to.subtract(from).normalize()));
                        Vec3 direction = to.subtract(from);
                        arrow.shoot(direction.x, direction.y, direction.z, 2.0F, 0.0F);
                        level.addFreshEntity(arrow);
                        context.runAfterDelay(20, () -> {
                            check(mate.getHealth() == healthBefore, "an arrow hit the shooter's teammate");
                            check(foe.getHealth() < foeBefore, "the arrow did not pass the teammate to hit the opponent: arrow "
                                    + (arrow.isRemoved() ? "removed (" + arrow.getRemovalReason() + ")"
                                            : "at " + arrow.position() + " moving " + arrow.getDeltaMovement())
                                    + ", from " + from + " to " + to + ", foe at " + foe.position() + " health " + foe.getHealth()
                                    + "/" + foeBefore + " hurt by " + foe.getLastHurtByMob());
                            checkAntiJanitor(mate, shooter, foe);
                        });
                    });
                });
            });
        }

        /**
         * After {@code attacker}'s team hit {@code victim}'s, the two teams fight each other alone
         * when three or more teams compete: {@code mate} may join, a third team may not touch
         * either, and the victim's death loot belongs to the whole attacking team.
         */
        private void checkAntiJanitor(ChatPlayer mate, ChatPlayer attacker, ChatPlayer victim) {
            ServerLevel level = match.arena().level();
            if (mate == null) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, victim.damageSources().playerAttack(attacker), 1);
            }
            ChatPlayer third = teams.stream().map(List::getFirst)
                    .filter(player -> !match.teamOf(player.getUUID()).equals(match.teamOf(attacker.getUUID()))
                            && !match.teamOf(player.getUUID()).equals(match.teamOf(victim.getUUID())))
                    .findFirst().orElse(null);
            boolean locks = match.contestingTeams() > 2;
            check(locks == (third != null), "anti-janitor applies to " + match.contestingTeams() + " teams");
            if (third != null) {
                place(third, victim.position().add(1, 0, 1));
                blocked(attacker, third, "a third team hit a locked fighter");
                blocked(third, attacker, "a locked fighter hit a third team");
                if (mate != null) {
                    blocked(mate, third, "a third team hit a locked fighter's teammate");
                    float before = victim.getHealth();
                    victim.invulnerableTime = 0;
                    victim.hurtServer(level, victim.damageSources().playerAttack(mate), 1);
                    check(victim.getHealth() < before, "a teammate could not join the team fight");
                }
            }
            BlockPos death = victim.blockPosition();
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            victim.hurtServer(level, victim.damageSources().genericKill(), Float.MAX_VALUE);
            check(!match.isAlive(victim.getUUID()), "the victim was not eliminated");
            if (third != null) {
                check(level.getBlockState(death).is(Blocks.CHEST), "no protected death chest at " + death);
                check(DeathLoot.canOpen(level, death, attacker), "the attacker cannot open the loot");
                if (mate != null) check(DeathLoot.canOpen(level, death, mate), "the attacker's teammate cannot open the loot");
                check(!DeathLoot.canOpen(level, death, third), "a third team can open the loot");
            }
            context.runAfterDelay(1, this::checkWin);
        }

        /** The match goes on while two teams stand, even with members of each gone, and ends with one. */
        private void checkWin() {
            List<ChatPlayer> winners = teams.stream().max(java.util.Comparator.comparingInt(List::size)).orElseThrow();
            List<ChatPlayer> last = null;
            for (List<ChatPlayer> team : teams) {
                if (team == winners) continue;
                if (last != null) last.forEach(this::kill);
                last = team;
            }
            if (winners.size() > 1) kill(winners.getLast());
            List<ChatPlayer> finalTeam = last;
            // One player of the last opposing team keeps it standing.
            finalTeam.subList(0, finalTeam.size() - 1).forEach(this::kill);
            context.runAfterDelay(2, () -> {
                check(match.phase() == MatchPhase.ACTIVE, "the match ended with two teams standing: " + match.phase());
                check(match.standingTeams().size() == 2, "standing teams " + match.standingTeams().size());
                kill(finalTeam.getLast());
                context.runAfterDelay(2, () -> {
                    check(match.phase() == MatchPhase.ENDED || match.isClosed(), "one team left but the match is " + match.phase());
                    check(match.winners().size() == 1 && match.winners().getFirst().members().contains(winners.getFirst().getUUID()),
                            "the wrong team won: " + match.winners());
                    context.succeed();
                });
            });
        }

        private void kill(ServerPlayer player) {
            if (!match.isAlive(player.getUUID())) return;
            player.invulnerableTime = 0;
            player.hurtServer(match.arena().level(), player.damageSources().genericKill(), Float.MAX_VALUE);
        }

        void close() {
            if (match != null) MatchManager.stop(match);
            for (var chunk : forced) forcedLevel.setChunkForced(chunk.x(), chunk.z(), false);
            for (ServerPlayer player : players) TestPlayers.disconnect(player);
            server.getCommandStorage().set(BrainageMinigames.id("settings"), settings);
            server.getGameRules().setAll(rules, server);
        }

        private static void place(ServerPlayer player, BlockPos pos) {
            place(player, Vec3.atBottomCenterOf(pos));
        }

        private static void place(ServerPlayer player, Vec3 pos) {
            player.snapTo(pos.x(), pos.y(), pos.z(), player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }

        /** Empties a lava bucket the way its user would: looking at the top of the block below {@code at}. */
        private static void pour(ServerPlayer player, BlockPos at) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(at.below()).add(0, 0.5, 0));
            player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }

        private static void blocked(ServerPlayer victim, ServerPlayer attacker, String message) {
            float health = victim.getHealth();
            victim.invulnerableTime = 0;
            victim.hurtServer(victim.level(), victim.damageSources().playerAttack(attacker), 1);
            check(victim.getHealth() == health, message);
        }

        /** Chat as a client sending it: the server's own chat broadcast for the sender. */
        private static void say(ServerPlayer sender, String text) {
            try {
                Method broadcast = ServerGamePacketListenerImpl.class.getDeclaredMethod("broadcastChatMessage", PlayerChatMessage.class);
                broadcast.setAccessible(true);
                broadcast.invoke(sender.connection, PlayerChatMessage.unsigned(sender.getUUID(), text));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot send chat", exception);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
