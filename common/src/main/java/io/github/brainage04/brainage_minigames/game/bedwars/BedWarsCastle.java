package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.TeamState;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Trap;
import io.github.brainage04.brainage_minigames.menu.Icon;
import io.github.brainage04.brainage_minigames.menu.Menu;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 40v40 Castle (v2), after Hypixel's announcements: two teams of up to 40, each with three beds (its
 * castle and two towers, named on the scoreboard and in announcements) that it respawns from while
 * any stands; launch pads between a team's own buildings; a Banker that keeps the team's resources,
 * which shops spend when a player runs short and which collects what full island generators would
 * drop; streak points from kills, beds, diamonds and emeralds and banking, spent on five streak
 * powers; three free Alarm Traps, every trap lasting five triggers and naming the bed attacked; and
 * no TNT next to a team's own standing beds. Point awards, launch strength and the powers' numbers
 * the announcements leave open are this mod's choice.
 */
final class BedWarsCastle {
    private BedWarsCastle() {}

    /** Blocks around a team's own standing bed where it may not place TNT. */
    private static final double TNT_KEEP_OUT = 8.0;
    static final int TRAP_USES = 5;

    enum Power {
        GOLDEN_KNIGHT("Golden Knight", Items.GOLDEN_BOOTS, 1, 30, 5 * 60 * 20, 0,
                "Spawns you with a trusty horse and the Sword of Justice!"),
        WITHER_RIDER("Wither Rider", Items.QUARTZ, 2, 60, 3 * 60 * 20, 2,
                "Soar through the sky on your majestic wither and obliterate your enemies by spitting feisty fireballs!"),
        LONE_WOLF("Lone Wolf", Items.BONE, 1, 30, 5 * 60 * 20, 0,
                "Spawns trusty wolves to help you in a battle by knocking players away from you!"),
        BLOCK_WIZARD("Block Wizard", Items.STICK, 2, 60, 6 * 60 * 20, 2,
                "Gives you a wand which can summon nearby blocks to help you in your battle!"),
        HOT_FLOOR("Hot Floor", Items.BLAZE_POWDER, 1, 30, 3 * 60 * 20, 0, "Sets the floor by you on fire!");

        final String displayName;
        final net.minecraft.world.item.Item icon;
        final int tier;
        final int cost;
        final int ticks;
        /** How many of a team may have it at once; 0 for any number. */
        final int teamLimit;
        final String description;

        Power(String displayName, net.minecraft.world.item.Item icon, int tier, int cost, int ticks, int teamLimit, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.tier = tier;
            this.cost = cost;
            this.ticks = ticks;
            this.teamLimit = teamLimit;
            this.description = description;
        }
    }

    /** A player's power while it lasts, with the entities it brought. */
    static final class Active {
        final Power power;
        final int until;
        final List<Entity> entities = new ArrayList<>();
        final List<Vec3> trail = new ArrayList<>();

        Active(Power power, int until) {
            this.power = power;
            this.until = until;
        }
    }

    static final class CastleState {
        final Map<Integer, EnumMap<Currency, Integer>> banks = new HashMap<>();
        final Map<UUID, Integer> points = new HashMap<>();
        final Map<UUID, Active> powers = new HashMap<>();
        final Map<UUID, Integer> padCooldowns = new HashMap<>();
        final Map<UUID, int[]> collected = new HashMap<>();
        final List<MapArena.Point> pads;
        final Map<String, BlockPos> beacons = new HashMap<>();

        CastleState(List<MapArena.Point> pads) {
            this.pads = pads;
        }

        int banked(int team, Currency currency) {
            return banks.getOrDefault(team, new EnumMap<>(Currency.class)).getOrDefault(currency, 0);
        }

        void bank(int team, Currency currency, int amount) {
            banks.computeIfAbsent(team, ignored -> new EnumMap<>(Currency.class)).merge(currency, amount, Integer::sum);
        }
    }

    static void start(BedWarsGame game, Match match, State state) {
        MapArena arena = (MapArena) match.arena();
        CastleState castle = new CastleState(arena.points("pad_"));
        for (MapArena.Point beacon : arena.points("beacon_")) {
            castle.beacons.put(beacon.name().substring("beacon_".length()), BlockPos.containing(beacon.position()));
        }
        state.castle = castle;
        for (TeamState team : state.teams.values()) {
            if (team.eliminated) continue;
            for (int index = 0; index < 3; index++) team.traps.add(Trap.ALARM);
            for (MapArena.Point point : arena.points("banker_" + team.number + "_")) {
                game.spawnShopkeeper(match, state, point, team.number, BedWarsGame.Shopkeeper.BANKER);
            }
            for (MapArena.Point point : arena.points("streak_" + team.number + "_")) {
                game.spawnShopkeeper(match, state, point, team.number, BedWarsGame.Shopkeeper.STREAKS);
            }
        }
    }

    /** The beacon over a broken bed goes out, and the team's wool on the building that held it turns grey. */
    static void bedBroken(Match match, State state, Bed bed, @Nullable ServerPlayer breaker) {
        if (state.castle == null) return;
        ServerLevel level = match.arena().level();
        BlockPos beacon = state.castle.beacons.get(bed.team() + "_" + bed.name());
        if (beacon != null) level.setBlock(beacon, Blocks.STAINED_GLASS.pick(DyeColor.GRAY).defaultBlockState(), Block.UPDATE_ALL);
        Block wool = Blocks.WOOL.pick(match.teamNumbered(bed.team()).map(BedWarsGame::dye).orElse(DyeColor.WHITE));
        String building = "base_" + bed.team() + "_" + bed.name();
        for (MapArena.Region region : state.layout.bases().getOrDefault(bed.team(), List.of())) {
            if (!region.name().equals(building)) continue;
            AABB box = region.box();
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4))) {
                if (!match.isPlacedBlock(pos) && level.getBlockState(pos).is(wool)) {
                    level.setBlock(pos, Blocks.WOOL.pick(DyeColor.GRAY).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (breaker != null) points(match, state, breaker, 10, "Bed destroyed");
    }

    static boolean allowPlace(Match match, State state, ServerPlayer player, BlockPos pos, BlockState block) {
        if (!block.is(Blocks.TNT)) return true;
        TeamState team = state.teams.get(BedWarsGame.teamOf(match, player));
        if (team == null) return true;
        for (Bed bed : team.standing) {
            if (bed.foot().closerToCenterThan(Vec3.atCenterOf(pos), TNT_KEEP_OUT)) {
                player.sendSystemMessage(Component.literal("You can't place TNT next to your own bed!").withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        return true;
    }

    static void points(Match match, State state, ServerPlayer player, int amount, String reason) {
        if (state.castle == null || amount <= 0) return;
        int total = state.castle.points.merge(player.getUUID(), amount, Integer::sum);
        player.sendSystemMessage(Component.literal("+" + amount + " Streak Points (" + reason + ")").withStyle(ChatFormatting.GOLD), true);
        for (Power power : Power.values()) {
            if (total >= power.cost && total - amount < power.cost) {
                player.sendSystemMessage(Component.literal("You have enough streak points for " + power.displayName
                        + "! Use it at a Streak Powers NPC.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    static int points(State state, ServerPlayer player) {
        return state.castle == null ? 0 : state.castle.points.getOrDefault(player.getUUID(), 0);
    }

    static void tick(BedWarsGame game, Match match, State state, int now) {
        CastleState castle = state.castle;
        if (castle == null) return;
        ServerLevel level = match.arena().level();
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator()) continue;
            launch(match, castle, player, now);
            if (now % 20 == 0) collect(match, state, castle, player);
        }
        for (Iterator<Map.Entry<UUID, Active>> entries = castle.powers.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<UUID, Active> entry = entries.next();
            ServerPlayer player = match.server().getPlayerList().getPlayer(entry.getKey());
            Active active = entry.getValue();
            if (player == null || now >= active.until || active.entities.stream().anyMatch(entity -> !entity.isAlive()) && active.power == Power.WITHER_RIDER) {
                end(active, player);
                entries.remove();
                continue;
            }
            switch (active.power) {
                case WITHER_RIDER -> steer(player, active);
                case HOT_FLOOR -> hotFloor(match, player, active, now);
                case BLOCK_WIZARD -> blocks(match, player, active);
                default -> {}
            }
        }
    }

    /** A team's own launch pads throw its players the way the pad faces. */
    private static void launch(Match match, CastleState castle, ServerPlayer player, int now) {
        if (castle.padCooldowns.getOrDefault(player.getUUID(), 0) > now || !player.onGround()) return;
        int team = BedWarsGame.teamOf(match, player);
        for (MapArena.Point pad : castle.pads) {
            if (!pad.name().startsWith("pad_" + team + "_") || player.position().distanceToSqr(pad.position()) > 0.8) continue;
            Vec3 forward = Vec3.directionFromRotation(0.0F, pad.yaw());
            player.setDeltaMovement(forward.x * 2.2, 1.1, forward.z * 2.2);
            player.hurtMarked = true;
            player.resetFallDistance();
            castle.padCooldowns.put(player.getUUID(), now + 40);
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
    }

    /** Streak points for diamonds (1) and emeralds (2) picked up since the last look. */
    private static void collect(Match match, State state, CastleState castle, ServerPlayer player) {
        int diamonds = BedWarsGame.count(player, Currency.DIAMOND);
        int emeralds = BedWarsGame.count(player, Currency.EMERALD);
        int[] last = castle.collected.put(player.getUUID(), new int[] {diamonds, emeralds});
        if (last == null) return;
        int gained = Math.max(0, diamonds - last[0]) + 2 * Math.max(0, emeralds - last[1]);
        if (gained > 0) points(match, state, player, gained, "Resources");
    }

    // ---------------------------------------------------------------- Banker

    /** Streak points a deposit is worth: a point per 32 iron, 8 gold, diamond or emerald. */
    private static int depositPoints(Currency currency, int amount) {
        return switch (currency) {
            case IRON -> amount / 32;
            case GOLD -> amount / 8;
            case DIAMOND, EMERALD -> amount;
        };
    }

    static void openBanker(BedWarsGame game, Match match, ServerPlayer player) {
        State state = game.states.get(match);
        if (state == null || state.castle == null) return;
        int team = BedWarsGame.teamOf(match, player);
        Menu menu = new Menu("Banker", 6);
        Currency[] currencies = Currency.values();
        for (int row = 0; row < currencies.length; row++) {
            Currency currency = currencies[row];
            menu.set(Menu.slot(row + 1, 1), Icon.of(currency.item).name(currency.displayName + " Team Resource", currency.color)
                    .text("Add resources with the buttons beside. The shops will automatically use resources from the Banker if you don't have enough.")
                    .blank().value("Stored", String.valueOf(state.castle.banked(team, currency)), ChatFormatting.AQUA));
            menu.set(Menu.slot(row + 1, 2), Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY))
                    .name(Component.literal("⬅ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("Resource Info").withStyle(ChatFormatting.GRAY)))
                    .line(Component.literal("➡ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("Add to bank").withStyle(ChatFormatting.GRAY))));
            int column = 3;
            for (int amount : new int[] {4, 8, 16, 32, 64}) {
                menu.set(Menu.slot(row + 1, column++), Icon.of(currency.item).count(amount)
                                .name("Add " + amount + "x " + currency.displayName + " to bank", currency.color),
                        (clicker, click) -> {
                            deposit(game, match, clicker, currency, amount);
                            openBanker(game, match, clicker);
                        });
            }
        }
        menu.close();
        menu.open(player);
    }

    static void deposit(BedWarsGame game, Match match, ServerPlayer player, Currency currency, int amount) throws MatchException {
        State state = game.states.get(match);
        if (state == null || state.castle == null) throw new MatchException("This match has no Banker.");
        int have = BedWarsGame.count(player, currency);
        if (have < amount) throw new MatchException("You don't have " + amount + " " + currency.displayName + "!");
        BedWarsGame.take(player, new BedWarsShop.Cost(currency, amount));
        state.castle.bank(BedWarsGame.teamOf(match, player), currency, amount);
        points(match, state, player, depositPoints(currency, amount), "Banked");
    }

    // ---------------------------------------------------------------- streak powers

    static void openPowers(BedWarsGame game, Match match, ServerPlayer player) {
        State state = game.states.get(match);
        if (state == null || state.castle == null) return;
        Menu menu = new Menu("Streak Powers", 5);
        int[] slots = {Menu.slot(1, 3), Menu.slot(1, 5), Menu.slot(2, 2), Menu.slot(2, 4), Menu.slot(2, 6)};
        int have = points(state, player);
        Power[] powers = {Power.GOLDEN_KNIGHT, Power.WITHER_RIDER, Power.LONE_WOLF, Power.BLOCK_WIZARD, Power.HOT_FLOOR};
        for (int index = 0; index < powers.length; index++) {
            Power power = powers[index];
            Icon icon = Icon.of(power.icon).name(power.displayName, power.tier == 2 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD)
                    .text(power.description).blank();
            if (power.teamLimit > 0) icon.value("Team Limit", power.teamLimit + " at the same time", ChatFormatting.AQUA);
            icon.value("Tier", "Tier " + power.tier, ChatFormatting.AQUA).value("Cost", power.cost + " Streak Points", ChatFormatting.AQUA).blank();
            if (have >= power.cost) icon.action("Click to activate!");
            else icon.refusal("You need " + (power.cost - have) + " more streak points!");
            menu.set(slots[index], icon, (clicker, click) -> {
                activate(game, match, clicker, power);
                clicker.closeContainer();
            });
        }
        menu.close();
        menu.open(player);
    }

    static void activate(BedWarsGame game, Match match, ServerPlayer player, Power power) throws MatchException {
        State state = game.states.get(match);
        if (state == null || state.castle == null) throw new MatchException("This match has no streak powers.");
        CastleState castle = state.castle;
        if (castle.powers.containsKey(player.getUUID())) throw new MatchException("You already have a streak power!");
        int have = points(state, player);
        if (have < power.cost) throw new MatchException("You need " + (power.cost - have) + " more streak points!");
        int team = BedWarsGame.teamOf(match, player);
        if (power.teamLimit > 0) {
            long using = castle.powers.entrySet().stream().filter(entry -> entry.getValue().power == power)
                    .filter(entry -> match.teamOf(entry.getKey()).map(t -> t.number() == team).orElse(false)).count();
            if (using >= power.teamLimit) throw new MatchException("Your team already has " + power.teamLimit + " " + power.displayName + "s!");
        }
        castle.points.put(player.getUUID(), have - power.cost);
        Active active = new Active(power, match.activeTicks() + power.ticks);
        castle.powers.put(player.getUUID(), active);
        ServerLevel level = player.level();
        switch (power) {
            case GOLDEN_KNIGHT -> PlayerUtils.giveOrDrop(player, sword());
            case LONE_WOLF -> {
                for (int index = 0; index < 3; index++) {
                    Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.TRIGGERED);
                    if (wolf == null) continue;
                    wolf.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                    wolf.tame(player);
                    wolf.setInvulnerable(true);
                    wolf.addTag(BedWarsGame.ENTITY_TAG);
                    wolf.addTag("brainage_minigames:bedwars_team=" + team);
                    level.addFreshEntity(wolf);
                    active.entities.add(wolf);
                }
            }
            case WITHER_RIDER -> {
                WitherBoss wither = EntityTypes.WITHER.create(level, EntitySpawnReason.TRIGGERED);
                if (wither != null) {
                    wither.snapTo(player.getX(), player.getY() + 1, player.getZ(), player.getYRot(), 0.0F);
                    wither.setNoAi(true);
                    wither.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10.0);
                    wither.setHealth(10.0F);
                    wither.addTag(BedWarsGame.ENTITY_TAG);
                    wither.addTag("brainage_minigames:bedwars_team=" + team);
                    level.addFreshEntity(wither);
                    player.startRiding(wither, true, true);
                    active.entities.add(wither);
                    PlayerUtils.giveOrDrop(player, BedWarsShop.tagged(Items.WITHER_SKELETON_SKULL, "wither_fireball", "Wither Fireball (Right Click)"));
                }
            }
            case BLOCK_WIZARD -> PlayerUtils.giveOrDrop(player, BedWarsShop.tagged(Items.STICK, "block_wizard", "Block Wizard Wand (Right Click)"));
            case HOT_FLOOR -> {}
        }
        match.broadcast(BedWarsGame.name(match, player.getUUID()).append(Component.literal(" activated ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(power.displayName).withStyle(ChatFormatting.YELLOW)).append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
    }

    private static ItemStack sword() {
        ItemStack sword = BedWarsShop.tagged(Items.GOLDEN_SWORD, "sword_of_justice", "Sword of Justice");
        return BedWarsShop.unbreakable(sword);
    }

    /** A power ends with its owner's death. */
    static void endPowers(Match match, State state, ServerPlayer victim) {
        if (state.castle == null) return;
        Active active = state.castle.powers.remove(victim.getUUID());
        if (active != null) end(active, victim);
    }

    private static void end(Active active, @Nullable ServerPlayer player) {
        for (Entity entity : active.entities) entity.discard();
        if (player == null) return;
        player.stopRiding();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            String ability = BedWarsShop.ability(player.getInventory().getItem(slot));
            if (ability.equals("sword_of_justice") || ability.equals("wither_fireball") || ability.equals("block_wizard")) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.sendSystemMessage(Component.literal("Your " + active.power.displayName + " has ended.").withStyle(ChatFormatting.GRAY));
    }

    static InteractionResult useItem(BedWarsGame game, Match match, State state, ServerPlayer player, ItemStack stack) {
        if (state.castle == null) return InteractionResult.PASS;
        Active active = state.castle.powers.get(player.getUUID());
        String ability = BedWarsShop.ability(stack);
        ServerLevel level = player.level();
        switch (ability) {
            case "sword_of_justice" -> {
                if (active == null || active.power != Power.GOLDEN_KNIGHT) return InteractionResult.PASS;
                if (active.entities.stream().noneMatch(Entity::isAlive)) {
                    Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.TRIGGERED);
                    if (horse == null) return InteractionResult.FAIL;
                    horse.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                    horse.setTamed(true);
                    horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                    horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
                    horse.addTag(BedWarsGame.ENTITY_TAG);
                    level.addFreshEntity(horse);
                    active.entities.clear();
                    active.entities.add(horse);
                }
                player.startRiding(active.entities.getFirst(), true, true);
                return InteractionResult.SUCCESS;
            }
            case "wither_fireball" -> {
                if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
                Vec3 look = player.getLookAngle();
                LargeFireball fireball = new LargeFireball(level, player, look, 0);
                fireball.setPos(player.getX() + look.x * 3, player.getEyeY() + look.y * 3, player.getZ() + look.z * 3);
                fireball.setDeltaMovement(look.scale(1.5));
                fireball.addTag(BedWarsGame.ENTITY_TAG);
                level.addFreshEntity(fireball);
                state.tracked.add(new BedWarsGame.Tracked(fireball, "wither_fireball", BedWarsGame.teamOf(match, player),
                        player.getUUID(), match.activeTicks() + 200));
                player.getCooldowns().addCooldown(stack, 20);
                return InteractionResult.SUCCESS;
            }
            case "block_wizard" -> {
                if (player.getCooldowns().isOnCooldown(stack) || active == null) return InteractionResult.FAIL;
                Display.BlockDisplay block = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
                if (block == null) return InteractionResult.FAIL;
                BlockState under = level.getBlockState(player.blockPosition().below());
                ((io.github.brainage04.brainage_minigames.mixin.BlockDisplayAccess) block)
                        .brainage_minigames$setBlockState(under.isAir() ? Blocks.STONE.defaultBlockState() : under);
                block.snapTo(player.getX() - 0.5, player.getEyeY() - 0.5, player.getZ() - 0.5);
                block.addTag(BedWarsGame.ENTITY_TAG);
                level.addFreshEntity(block);
                active.entities.add(block);
                block.setDeltaMovement(player.getLookAngle().scale(1.2));
                player.getCooldowns().addCooldown(stack, 40);
                return InteractionResult.SUCCESS;
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
    }

    /** A hit with the Sword of Justice (Castle's Golden Knight, or a Lucky Blocks find) heals its holder a heart. */
    static void damaged(Match match, State state, ServerPlayer victim, ServerPlayer attacker) {
        if (!BedWarsShop.ability(attacker.getMainHandItem()).equals("sword_of_justice")) return;
        attacker.heal(2.0F);
    }

    /** The wither flies where its rider looks. */
    private static void steer(ServerPlayer player, Active active) {
        Entity wither = active.entities.isEmpty() ? null : active.entities.getFirst();
        if (wither == null) return;
        if (player.getVehicle() != wither) player.startRiding(wither, true, true);
        Vec3 look = player.getLookAngle();
        Vec3 next = wither.position().add(look.scale(0.6));
        wither.setYRot(player.getYRot());
        wither.setPos(next.x, next.y, next.z);
    }

    /** Hot Floor: a trail of flames for three seconds behind the player that burns enemies who step on it. */
    private static void hotFloor(Match match, ServerPlayer player, Active active, int now) {
        ServerLevel level = player.level();
        if (player.onGround()) active.trail.add(player.position());
        if (active.trail.size() > 60) active.trail.removeFirst();
        int team = BedWarsGame.teamOf(match, player);
        for (int index = 0; index < active.trail.size(); index += 3) {
            Vec3 at = active.trail.get(index);
            if (now % 4 == 0) level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 2, 0.2, 0.0, 0.2, 0.0);
        }
        if (now % 10 != 0) return;
        for (ServerPlayer enemy : match.alivePlayers()) {
            if (enemy.isSpectator() || BedWarsGame.teamOf(match, enemy) == team) continue;
            for (Vec3 at : active.trail) {
                if (enemy.position().distanceToSqr(at) < 1.0) {
                    enemy.igniteForSeconds(3.0F);
                    break;
                }
            }
        }
    }

    /** Block Wizard's blocks fly until they hit an enemy (five hearts and a throw back) or a block. */
    private static void blocks(Match match, ServerPlayer player, Active active) {
        int team = BedWarsGame.teamOf(match, player);
        for (Iterator<Entity> iterator = active.entities.iterator(); iterator.hasNext(); ) {
            Entity block = iterator.next();
            Vec3 motion = block.getDeltaMovement();
            Vec3 next = block.position().add(motion);
            block.setPos(next.x, next.y, next.z);
            AABB box = new AABB(next, next.add(1, 1, 1));
            ServerPlayer struck = match.alivePlayers().stream()
                    .filter(enemy -> !enemy.isSpectator() && BedWarsGame.teamOf(match, enemy) != team && box.intersects(enemy.getBoundingBox()))
                    .findFirst().orElse(null);
            boolean landed = !block.level().getBlockState(BlockPos.containing(next.add(0.5, 0.5, 0.5))).isAir();
            if (struck != null) {
                struck.invulnerableTime = 0;
                struck.hurtServer(struck.level(), player.damageSources().playerAttack(player), 10.0F);
                struck.push(motion.normalize().scale(1.5).add(0, 0.4, 0));
                struck.hurtMarked = true;
            }
            if (struck != null || landed || block.tickCount > 60) {
                block.discard();
                iterator.remove();
            }
        }
    }

    /** The trap that went off in a Castle names the bed whose building it guards. */
    static String trapPlace(State state, int team, Vec3 position) {
        for (MapArena.Region region : state.layout.bases().getOrDefault(team, List.of())) {
            if (region.contains(position) && region.name().length() > ("base_" + team).length()) {
                return region.name().substring(("base_" + team + "_").length()).replace('_', ' ');
            }
        }
        return "";
    }

}
