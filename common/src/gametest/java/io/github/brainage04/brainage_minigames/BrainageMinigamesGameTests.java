package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.feedback.FeedbackGameTest;
import io.github.brainage04.brainage_minigames.hub.HubGameTest;
import io.github.brainage04.brainage_minigames.game.EloGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.MatchBotsGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.MenuGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcConcurrentGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcModeGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcRegionGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcLobbyPreparationGameTestFunctions;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Every GameTest function, keyed by its path in {@code brainage_minigames:}. Each loader registers them in
 * {@code minecraft:test_function}; the matching {@code data/brainage_minigames/test_instance/<name>.json} gives
 * the test its structure, environment and time limit, so both loaders run the same tests.
 */
public final class BrainageMinigamesGameTests {
    private BrainageMinigamesGameTests() {}

    /**
     * @param dimensions creates the natural game dimensions, which the flat GameTest world lacks, before
     *     each test
     */
    public static Map<Identifier, Consumer<GameTestHelper>> functions(Consumer<MinecraftServer> dimensions) {
        DuelGameTest duel = new DuelGameTest();
        MapFrameworkGameTest mapFramework = new MapFrameworkGameTest();
        RaceGameTest race = new RaceGameTest();
        BrainageMinigamesGameTest brainageMinigames = new BrainageMinigamesGameTest();
        SkyWarsGameTest skyWars = new SkyWarsGameTest();
        SpleefGameTest spleef = new SpleefGameTest();
        BridgeGameTest bridge = new BridgeGameTest();
        QuakePearlFightGameTest quakePearlFight = new QuakePearlFightGameTest();
        UhcVariantsGameTest uhcVariants = new UhcVariantsGameTest();
        UhcNetherGameTest uhcNether = new UhcNetherGameTest();
        MatchOwnershipGameTest ownership = new MatchOwnershipGameTest();
        FeedbackGameTest feedback = new FeedbackGameTest();
        HubGameTest hub = new HubGameTest();
        Map<String, Test> functions = Map.ofEntries(
                Map.entry("anti_janitor_combat", AntiJanitorGameTestFunctions::combat),
                Map.entry("anti_janitor_locations", AntiJanitorGameTestFunctions::locations),
                Map.entry("anti_janitor_loot", AntiJanitorGameTestFunctions::loot),
                Map.entry("anti_janitor_scope", AntiJanitorGameTestFunctions::scope),
                Map.entry("anti_janitor_shield_loot", AntiJanitorGameTestFunctions::shieldLoot),
                Map.entry("balance18_apples_and_head", CombatBalanceGameTestFunctions::applesAndHead),
                Map.entry("balance18_armor_and_durability", CombatBalanceGameTestFunctions::armorAndDurability),
                Map.entry("balance18_blast_and_knockback", CombatBalanceGameTestFunctions::blastAndKnockback),
                Map.entry("balance18_bow_and_punch", CombatBalanceGameTestFunctions::bowAndPunch),
                Map.entry("balance18_enchantments", CombatBalanceGameTestFunctions::enchantments),
                Map.entry("balance18_exhaustion", CombatBalanceGameTestFunctions::exhaustion),
                Map.entry("balance18_fire_and_lava", CombatBalanceGameTestFunctions::fireAndLava),
                Map.entry("balance18_fire_protection_and_aspect", CombatBalanceGameTestFunctions::fireProtectionAndAspect),
                Map.entry("balance18_natural_regeneration", CombatBalanceGameTestFunctions::naturalRegeneration),
                Map.entry("balance18_potion_durations_and_healing", CombatBalanceGameTestFunctions::potionDurationsAndHealing),
                Map.entry("balance18_protection", CombatBalanceGameTestFunctions::protection),
                Map.entry("balance18_recipe_and_cooldown", CombatBalanceGameTestFunctions::recipeAndCooldown),
                Map.entry("balance18_splash", CombatBalanceGameTestFunctions::splash),
                Map.entry("balance18_strength_weakness_critical", CombatBalanceGameTestFunctions::strengthWeaknessCritical),
                Map.entry("balance18_weapons", CombatBalanceGameTestFunctions::weapons),
                Map.entry("boxing_hits_score_without_damage", brainageMinigames::boxingHitsScoreWithoutDamage),
                Map.entry("bridge_every_map_has_goals_cages_and_build_limits", bridge::everyMapHasGoalsCagesAndBuildLimits),
                Map.entry("bridge_first_team_to_the_target_wins", bridge::firstTeamToTheTargetWins),
                Map.entry("bridge_only_placed_blocks_break_and_goals_stay_open", bridge::onlyPlacedBlocksBreakAndGoalsStayOpen),
                Map.entry("bridge_own_goal_scores_nothing_and_keeps_the_round", bridge::ownGoalScoresNothingAndKeepsTheRound),
                Map.entry("bridge_scoring_in_the_enemy_goal_scores_and_starts_a_new_round", bridge::scoringInTheEnemyGoalScoresAndStartsANewRound),
                Map.entry("bridge_void_death_respawns_without_eliminating", bridge::voidDeathRespawnsWithoutEliminating),
                Map.entry("combat18_cooldown", Combat18GameTestFunctions::cooldownAndScope),
                Map.entry("combat18_duels", Combat18GameTestFunctions::comboAndBoxing),
                Map.entry("combat18_eggs", Combat18GameTestFunctions::eggs),
                Map.entry("combat18_immunity", Combat18GameTestFunctions::hitImmunity),
                Map.entry("combat18_restrictions", Combat18GameTestFunctions::gameRestrictions),
                Map.entry("combat18_rod", Combat18GameTestFunctions::fishingRod),
                Map.entry("combat18_snowballs", Combat18GameTestFunctions::snowballs),
                Map.entry("combat18_sprint_knockback", Combat18GameTestFunctions::sprintKnockback),
                Map.entry("combat18_sweep_crit", Combat18GameTestFunctions::sweepAndCritical),
                Map.entry("combat18_sword_blocking", Combat18GameTestFunctions::swordBlocking),
                Map.entry("combat18_sword_elimination", Combat18GameTestFunctions::swordElimination),
                Map.entry("combat18_sword_lifecycle", Combat18GameTestFunctions::swordLifecycle),
                Map.entry("combat18_sword_respawn", Combat18GameTestFunctions::swordRespawn),
                Map.entry("combo_lands_ten_hits_per_second_without_attack_cooldown", brainageMinigames::comboLandsTenHitsPerSecondWithoutAttackCooldown),
                Map.entry("commands_are_registered", brainageMinigames::commandsAreRegistered),
                Map.entry("concurrent_matches_use_separate_arenas", brainageMinigames::concurrentMatchesUseSeparateArenas),
                Map.entry("container_automation", ContainerProtectionGameTestFunctions::automation),
                Map.entry("container_lifetime", ContainerProtectionGameTestFunctions::offAndLifetime),
                Map.entry("container_protection", ContainerProtectionGameTestFunctions::protection),
                Map.entry("disabled_regeneration_keeps_hunger_draining", brainageMinigames::disabledRegenerationKeepsHungerDraining),
                Map.entry("duel_accepting_fills_teams_in_listed_order_and_starts_the_match", duel::acceptingFillsTeamsInListedOrderAndStartsTheMatch),
                Map.entry("duel_eliminates_loser_and_restores_both_players", brainageMinigames::duelEliminatesLoserAndRestoresBothPlayers),
                Map.entry("duel_invalid_challenges_are_refused", duel::invalidChallengesAreRefused),
                Map.entry("duel_invite_carries_accept_command_and_player_list_and_deny_cancels", duel::inviteCarriesAcceptCommandAndPlayerListAndDenyCancels),
                Map.entry("editable_kit_persists_and_bundled_kit_equips_armour", brainageMinigames::editableKitPersistsAndBundledKitEquipsArmour),
                Map.entry("elo_match_lifecycle", EloGameTestFunctions::lifecycle),
                Map.entry("elo_updates", EloGameTestFunctions::updates),
                Map.entry("feedback_is_stored_and_logged", feedback::feedbackIsStoredAndLogged),
                Map.entry("feedback_reminders_welcome_remind_and_turn_off", feedback::remindersWelcomeRemindAndTurnOff),
                Map.entry("final_uhc_arena_is_dry_natural_ground_inside_its_border", uhcVariants::finalUhcArenaIsDryNaturalGroundInsideItsBorder),
                Map.entry("final_uhc_kit_is_the_minemen_loadout", uhcVariants::finalUhcKitIsTheMinemenLoadout),
                Map.entry("hub_builds_at_world_spawn", hub::buildsHubAtWorldSpawn),
                Map.entry("hub_protects_players_outside_matches", hub::protectsPlayersOutsideMatches),
                Map.entry("hub_returns_players_after_matches_and_by_command", hub::returnsPlayersAfterMatchesAndByCommand),
                Map.entry("map_block_rules_track_placed_blocks_and_reset_restores_the_map", mapFramework::blockRulesTrackPlacedBlocksAndResetRestoresTheMap),
                Map.entry("map_falling_into_the_void_eliminates_by_default", mapFramework::fallingIntoTheVoidEliminatesByDefault),
                Map.entry("map_pastes_with_markers_parsed_and_replaced_by_air", mapFramework::mapPastesWithMarkersParsedAndReplacedByAir),
                Map.entry("meetup_has_pvp_from_the_start_and_ashrinking_border", uhcVariants::meetupHasPvpFromTheStartAndAShrinkingBorder),
                Map.entry("menu_clicks_never_move_items", MenuGameTestFunctions::clicksNeverMoveItems),
                Map.entry("menu_kits_show_names_and_contents", MenuGameTestFunctions::kitsShowNamesAndContents),
                Map.entry("menu_custom_layout_with_bot_slots", MenuGameTestFunctions::customLayoutWithBotSlots),
                Map.entry("menu_duel_builder_challenges_and_starts_with_bots", MenuGameTestFunctions::duelBuilderChallengesAndStartsWithBots),
                Map.entry("menu_hub_item_opens_menu_and_stays", MenuGameTestFunctions::hubItemOpensMenuAndStays),
                Map.entry("menu_lobby_items_and_feedback_toggle", MenuGameTestFunctions::lobbyItemsAndFeedbackToggle),
                Map.entry("menu_match_list_joins_watches_and_manages", MenuGameTestFunctions::matchListJoinsWatchesAndManages),
                Map.entry("menu_open_flow_opens_chosen_match", MenuGameTestFunctions::openFlowOpensChosenMatch),
                Map.entry("match_bots_chosen_slots_in_any_layout", MatchBotsGameTestFunctions::chosenSlotsInAnyLayout),
                Map.entry("match_bots_eliminated_and_removed_bots_leave", MatchBotsGameTestFunctions::eliminatedAndRemovedBotsLeave),
                Map.entry("match_bots_lobby_timer_starts_thirty_seconds_after_first_wait", MatchBotsGameTestFunctions::lobbyTimerStartsThirtySecondsAfterFirstWait),
                Map.entry("match_bots_meetup_vote_fills_empty_slots", MatchBotsGameTestFunctions::meetupVoteFillsEmptySlots),
                Map.entry("match_bots_without_provider_start_with_humans", MatchBotsGameTestFunctions::withoutProviderStartWithHumans),
                Map.entry("meetup_kits_are_random_within_fair_tiers", uhcVariants::meetupKitsAreRandomWithinFairTiers),
                Map.entry("ownership_any_player_opens_and_manages_own_match", ownership::anyPlayerOpensAndManagesOwnMatch),
                Map.entry("ownership_open_match_limit", ownership::openMatchLimitPerPlayer),
                Map.entry("ownership_open_joins_the_opener_unless_nojoin", ownership::openJoinsTheOpenerUnlessNoJoin),
                Map.entry("player_teleported_to_another_dimension_loads_nothing_where_they_stood", TeleportFootingGameTest::playerTeleportedToAnotherDimensionLoadsNothingWhereTheyStood),
                Map.entry("player_snapshot_round_trips_state_and_rewards", brainageMinigames::playerSnapshotRoundTripsStateAndRewards),
                Map.entry("quake_pearl_every_map_has_spawns_respawns_and_solid_ground", quakePearlFight::everyMapHasSpawnsRespawnsAndSolidGround),
                Map.entry("quake_pearl_falling_off_scores_for_the_opponent_and_first_to_the_target_wins", quakePearlFight::fallingOffScoresForTheOpponentAndFirstToTheTargetWins),
                Map.entry("quake_pearl_knocking_into_the_void_scores_for_the_knocker_and_starts_a_new_round", quakePearlFight::knockingIntoTheVoidScoresForTheKnockerAndStartsANewRound),
                Map.entry("quake_pearl_railgun_kills_through_the_use_hook_but_not_through_walls", quakePearlFight::railgunKillsThroughTheUseHookButNotThroughWalls),
                Map.entry("quake_pearl_railgun_must_reload_and_first_to_the_target_wins", quakePearlFight::railgunMustReloadAndFirstToTheTargetWins),
                Map.entry("race_boat_race_counts_laps_replaces_lost_boats_and_cleans_up", race::boatRaceCountsLapsReplacesLostBoatsAndCleansUp),
                Map.entry("race_every_map_has_an_ordered_course_and_start_slots", race::everyMapHasAnOrderedCourseAndStartSlots),
                Map.entry("race_parkour_checkpoints_count_in_order_falls_return_and_finish_wins", race::parkourCheckpointsCountInOrderFallsReturnAndFinishWins),
                Map.entry("sidebar_is_per_player_and_restores_server_sidebar", brainageMinigames::sidebarIsPerPlayerAndRestoresServerSidebar),
                Map.entry("skywars_cages_open_and_chests_are_filled_at_start", skyWars::cagesOpenAndChestsAreFilledAtStart),
                Map.entry("skywars_every_map_has_islands_chests_and_void", skyWars::everyMapHasIslandsChestsAndVoid),
                Map.entry("skywars_layouts_beyond_the_maps_are_refused", skyWars::layoutsBeyondTheMapsAreRefused),
                Map.entry("skywars_refill_fills_an_emptied_chest", skyWars::refillFillsAnEmptiedChest),
                Map.entry("skywars_void_death_eliminates_and_last_standing_wins", skyWars::voidDeathEliminatesAndLastStandingWins),
                Map.entry("spleef_every_map_has_floors_spawns_and_void", spleef::everyMapHasFloorsSpawnsAndVoid),
                Map.entry("spleef_falling_through_the_floors_eliminates_and_last_standing_wins", spleef::fallingThroughTheFloorsEliminatesAndLastStandingWins),
                Map.entry("spleef_flaming_arrow_removes_floor_tnt_without_exploding", spleef::flamingArrowRemovesFloorTntWithoutExploding),
                Map.entry("spleef_perks_use_up_their_items", spleef::perksUseUpTheirItems),
                Map.entry("spleef_shovel_digs_floors_into_snowballs_but_not_walls", spleef::shovelDigsFloorsIntoSnowballsButNotWalls),
                Map.entry("team_layouts_parse_even_uneven_and_free_for_all", brainageMinigames::teamLayoutsParseEvenUnevenAndFreeForAll),
                Map.entry("uhc_advanced_crafts", UhcAdvancedGameTestFunctions::advanced),
                Map.entry("uhc_concurrent_borders", UhcConcurrentGameTestFunctions::borders),
                Map.entry("uhc_concurrent_cleanup", UhcConcurrentGameTestFunctions::cleanup),
                Map.entry("uhc_concurrent_deathmatch", UhcConcurrentGameTestFunctions::deathmatch),
                Map.entry("uhc_concurrent_mixed", UhcConcurrentGameTestFunctions::mixed),
                Map.entry("uhc_concurrent_nether", UhcConcurrentGameTestFunctions::nether),
                Map.entry("uhc_mode_badlion", UhcModeGameTestFunctions::badlionBorder),
                Map.entry("uhc_mode_chat", UhcModeGameTestFunctions::readableChat),
                Map.entry("uhc_mode_clocks", UhcModeGameTestFunctions::clocks),
                Map.entry("uhc_mode_deathmatch", UhcModeGameTestFunctions::deathmatch),
                Map.entry("uhc_mode_deathmatch_lifecycle", UhcModeGameTestFunctions::deathmatchLifecycle),
                Map.entry("uhc_mode_disabled", UhcModeGameTestFunctions::disabledDeathmatch),
                Map.entry("uhc_mode_disabled_setting", UhcModeGameTestFunctions::disabledDeathmatchSetting),
                Map.entry("uhc_mode_following", UhcModeGameTestFunctions::followingRule),
                Map.entry("uhc_mode_health", UhcModeGameTestFunctions::doubleHealth),
                Map.entry("uhc_mode_hypixel", UhcModeGameTestFunctions::hypixelBorder),
                Map.entry("uhc_mode_preparation_clock", UhcModeGameTestFunctions::preparationClock),
                Map.entry("uhc_mode_sidebar", UhcModeGameTestFunctions::sidebarText),
                Map.entry("uhc_mode_sunrise", UhcModeGameTestFunctions::sunriseGrace),
                Map.entry("uhc_nether_match", uhcNether::uhcPlayersInTheNetherStayInTheMatchUntilItCloses),
                Map.entry("uhc_nether_portals", uhcNether::portalsLinkTheUhcDimensionAndItsNether),
                Map.entry("uhc_places_players_on_dry_ground", brainageMinigames::uhcPlacesPlayersOnDryGround),
                Map.entry("uhc_progression", UhcProgressionGameTestFunctions::progression),
                Map.entry("uhc_lobby_prepares_while_opener_waits", UhcLobbyPreparationGameTestFunctions::uhc),
                Map.entry("uhc_stop_clears_deathmatch_over_ticks", UhcLobbyPreparationGameTestFunctions::uhcStopClearsDeathmatchOverTicks),
                Map.entry("meetup_lobby_prepares_while_opener_waits", UhcLobbyPreparationGameTestFunctions::meetup),
                Map.entry("uhc_region_seed", UhcRegionGameTestFunctions::regionSeed),
                Map.entry("uhc_rejects_border_that_grows_between_shrinks", brainageMinigames::uhcRejectsBorderThatGrowsBetweenShrinks),
                Map.entry("uhc_resource_drops", UhcResourceGameTestFunctions::drops),
                Map.entry("uhc_resource_generation", UhcResourceGameTestFunctions::generation),
                Map.entry("uhc_settings_borders", UhcSettingsGameTestFunctions::borders),
                Map.entry("uhc_settings_coin_badlion_border", UhcSettingsGameTestFunctions::coinBadlionBorder),
                Map.entry("uhc_settings_coin_combat", UhcSettingsGameTestFunctions::coinCombat),
                Map.entry("uhc_settings_coin_gathering", UhcSettingsGameTestFunctions::coinGathering),
                Map.entry("uhc_settings_coin_hypixel_border", UhcSettingsGameTestFunctions::coinHypixelBorder),
                Map.entry("uhc_settings_coin_logger_kill", UhcSettingsGameTestFunctions::coinLoggerKill),
                Map.entry("uhc_settings_coin_placement", UhcSettingsGameTestFunctions::coinPlacement),
                Map.entry("uhc_settings_coin_scope_duel", UhcSettingsGameTestFunctions::coinScopeDuel),
                Map.entry("uhc_settings_coin_scope_final_uhc", UhcSettingsGameTestFunctions::coinScopeFinalUhc),
                Map.entry("uhc_settings_coin_scope_meetup", UhcSettingsGameTestFunctions::coinScopeMeetup),
                Map.entry("uhc_settings_craft_prompts", UhcSettingsGameTestFunctions::craftPrompts),
                Map.entry("uhc_settings_end_portals", UhcSettingsGameTestFunctions::endPortals),
                Map.entry("uhc_settings_kits_coins", UhcSettingsGameTestFunctions::kitsAndCoins),
                Map.entry("uhc_settings_logger_final_uhc", UhcSettingsGameTestFunctions::loggerFinalUhc),
                Map.entry("uhc_settings_logger_meetup", UhcSettingsGameTestFunctions::loggerMeetup),
                Map.entry("uhc_settings_logger_uhc", UhcSettingsGameTestFunctions::loggerUhc),
                Map.entry("uhc_settings_nether_border", UhcSettingsGameTestFunctions::netherBorder),
                Map.entry("uhc_settings_presets_timing", UhcSettingsGameTestFunctions::presetsAndTiming),
                Map.entry("uhc_settings_progression_defaults", UhcSettingsGameTestFunctions::progressionDefaults),
                Map.entry("uhc_settings_pvp_announced_without_grace_final_uhc", UhcSettingsGameTestFunctions::pvpAnnouncedWithoutGraceFinalUhc),
                Map.entry("uhc_settings_pvp_announced_without_grace_meetup", UhcSettingsGameTestFunctions::pvpAnnouncedWithoutGraceMeetup),
                Map.entry("uhc_settings_pvp_announced_without_grace_uhc", UhcSettingsGameTestFunctions::pvpAnnouncedWithoutGraceUhc),
                Map.entry("uhc_settings_raw_ore_recipes", UhcSettingsGameTestFunctions::rawOreRecipes),
                Map.entry("uhc_spawn_fifty", UhcModeGameTestFunctions::fiftyPlayerSpread),
                Map.entry("uhc_spawn_tickets", UhcSpawnGameTestFunctions::ticketedSpread)
        );
        return functions.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> BrainageMinigames.id(entry.getKey()),
                entry -> context -> {
                    dimensions.accept(context.getLevel().getServer());
                    try {
                        entry.getValue().run(context);
                    } catch (RuntimeException | Error exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
    }

    /** A test body; checked exceptions fail the test. */
    @FunctionalInterface
    private interface Test {
        void run(GameTestHelper context) throws Exception;
    }
}
