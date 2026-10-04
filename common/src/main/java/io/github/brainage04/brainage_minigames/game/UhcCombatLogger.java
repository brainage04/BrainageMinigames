package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcModeRules;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import net.minecraft.server.level.TicketType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Offline participants remain attackable until they return, die, or their match ends. */
public final class UhcCombatLogger {
    public static final String TAG = "brainage_minigames:combat_logger";
    private static final Map<UUID, Entry> LOGGERS = new HashMap<>();
    private static final Map<Match, List<Entry>> BY_MATCH = new HashMap<>();
    private static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final Map<ServerLevel, Map<Long, Ticket>> CHUNKS = new HashMap<>();
    private static final class Ticket { int users; }
    private static final class Entry {
        final Match match;
        final ServerPlayer player;
        LoggerZombie zombie;
        long chunk;
        Entry(Match match, ServerPlayer player) {
            this.match = match;
            this.player = player;
            zombie = new LoggerZombie(this, player.level());
        }
    }
    private UhcCombatLogger() {}

    public static @Nullable UUID participant(Entity entity) {
        return entity instanceof LoggerZombie zombie ? zombie.entry.player.getUUID() : null;
    }
    public static @Nullable Match match(Entity entity) {
        return entity instanceof LoggerZombie zombie ? zombie.entry.match : null;
    }
    public static @Nullable Zombie zombie(UUID participant) {
        Entry entry = LOGGERS.get(participant);
        return entry == null ? null : entry.zombie;
    }

    /** Checks match, team, PvP and anti-janitor protection without recording an attack. */
    public static boolean canAttack(ServerPlayer attacker, Entity logger) {
        if (!(logger instanceof LoggerZombie zombie) || !zombie.isAlive() || zombie.isRemoved()
                || !attacker.isAlive() || attacker.isSpectator() || attacker.level() != zombie.level()) return false;
        Entry entry = zombie.entry;
        Match match = entry.match;
        if (LOGGERS.get(entry.player.getUUID()) != entry || entry.zombie != zombie
                || MatchManager.activeMatch(attacker.getUUID()) != match
                || !match.isActiveParticipant(entry.player.getUUID())
                || match.teamOf(attacker.getUUID()).equals(match.teamOf(entry.player.getUUID()))) return false;
        entry.player.setServerLevel((ServerLevel) zombie.level());
        entry.player.setPos(zombie.position());
        return match.canDamage(entry.player, attacker.damageSources().playerAttack(attacker));
    }

    static boolean disconnect(Match match, ServerPlayer player) {
        if (!(match.game().id().equals("uhc") || match.game().id().equals("meetup") || match.game().id().equals("final_uhc"))
                || !match.isActiveParticipant(player.getUUID())
                || !match.server().getGameRules().get(UhcModeRules.COMBAT_LOGGER)) return false;
        if (LOGGERS.containsKey(player.getUUID())) return true;
        player.closeContainer();
        Entry entry = new Entry(match, player);
        Zombie zombie = entry.zombie;
        zombie.copyPosition(player);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(player.getMaxHealth());
        zombie.setHealth(player.getHealth());
        zombie.setAbsorptionAmount(player.getAbsorptionAmount());
        for (var effect : player.getActiveEffects()) zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
        for (var slot : EquipmentSlot.values()) {
            zombie.setItemSlot(slot, player.getItemBySlot(slot).copy());
            zombie.setDropChance(slot, 0);
        }
        zombie.setItemSlot(EquipmentSlot.HEAD, UhcCrafting.playerHead(player));
        if (!player.level().addFreshEntity(zombie)) return false;
        LOGGERS.put(player.getUUID(), entry);
        BY_MATCH.computeIfAbsent(match, key -> new ArrayList<>()).add(entry);
        force(entry);
        match.broadcast(Component.literal(player.getScoreboardName() + " disconnected. Their combat logger remains in the match."));
        return true;
    }

    static boolean reconnect(Match match, ServerPlayer player) {
        Entry entry = LOGGERS.get(player.getUUID());
        if (entry == null || entry.match != match || !entry.zombie.isAlive()) return false;
        Zombie zombie = entry.zombie;
        syncEquipment(entry);
        player.restoreFrom(entry.player, true);
        if (match.game() instanceof UhcGame) UhcGame.restoreMatchHealth(player, entry.player.getMaxHealth() > 20);
        PlayerUtils.teleport(player, (ServerLevel) zombie.level(), zombie.position(), zombie.getYRot());
        player.setHealth(Math.min(zombie.getHealth(), player.getMaxHealth()));
        player.setAbsorptionAmount(zombie.getAbsorptionAmount());
        player.removeAllEffects();
        for (var effect : zombie.getActiveEffects()) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
        remove(entry);
        entry.player.getInventory().clearContent();
        match.broadcast(Component.literal(player.getScoreboardName() + " rejoined."));
        return true;
    }

    public static void move(Match match, UUID participant, ServerLevel destination, Vec3 position, float yaw) {
        Entry entry = LOGGERS.get(participant);
        if (entry == null || entry.match != match) return;
        Zombie previous = entry.zombie;
        if (previous.level() == destination) {
            previous.snapTo(position, yaw, previous.getXRot());
            return;
        }
        LoggerZombie replacement = new LoggerZombie(entry, destination);
        replacement.copyPosition(previous);
        replacement.getAttribute(Attributes.MAX_HEALTH).setBaseValue(previous.getMaxHealth());
        replacement.setHealth(previous.getHealth());
        replacement.setAbsorptionAmount(previous.getAbsorptionAmount());
        for (var effect : previous.getActiveEffects()) replacement.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
        for (var slot : EquipmentSlot.values()) {
            replacement.setItemSlot(slot, previous.getItemBySlot(slot).copy());
            replacement.setDropChance(slot, 0);
        }
        replacement.snapTo(position, yaw, previous.getXRot());
        if (!destination.addFreshEntity(replacement)) {
            throw new IllegalStateException("Combat logger could not reach the match arena.");
        }
        release(entry);
        entry.zombie = replacement;
        previous.discard();
        force(entry);
    }

    private static void syncEquipment(Entry entry) {
        for (var slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.HEAD) entry.player.setItemSlot(slot, entry.zombie.getItemBySlot(slot).copy());
        }
    }

    static void tick(Match match) {
        List<Entry> entries = BY_MATCH.get(match);
        if (entries == null) return;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            Zombie zombie = entry.zombie;
            if (match.arena() instanceof UhcArena arena && zombie.level().dimension().equals(ModDimensions.UHC_NETHER)
                    && arena.openNether().isEmpty() && !arena.inDeathmatch()) {
                Vec3 position = arena.surfaceReturnPosition((ServerLevel) zombie.level(), zombie.getX(), zombie.getZ());
                move(match, entry.player.getUUID(), match.server().getLevel(ModDimensions.UHC), position, zombie.getYRot());
                zombie = entry.zombie;
            }
            if (match.arena() instanceof UhcArena arena && UhcGame.deathmatchFrozen(match)) {
                var spawn = arena.deathmatchSpawn(entry.player.getUUID());
                if (spawn != null) {
                    zombie.setPos(spawn.position());
                    zombie.setDeltaMovement(Vec3.ZERO);
                }
            }
            long current = zombie.chunkPosition().pack();
            if (current != entry.chunk) { release(entry); force(entry); }
            WorldBorder border = match.arena() instanceof UhcArena arena && zombie.level() == arena.level()
                    ? arena.border() : match.arena() instanceof NaturalArena natural
                    ? natural.border() : zombie.level().getWorldBorder();
            double outside = -border.getDistanceToBorder(zombie);
            if (outside > border.getSafeZone()) {
                zombie.hurtServer((ServerLevel) zombie.level(), zombie.damageSources().outOfBorder(),
                        (float) Math.max(1, (outside - border.getSafeZone()) * border.getDamagePerBlock()));
            }
        }
    }

    static void end(Match match) {
        List<Entry> entries = BY_MATCH.get(match);
        if (entries == null) return;
        while (!entries.isEmpty()) {
            Entry entry = entries.getLast();
            remove(entry);
            match.loggerEnded(entry.player);
        }
    }

    private static void remove(Entry entry) {
        forget(entry);
        entry.zombie.discard();
    }

    private static void forget(Entry entry) {
        LOGGERS.remove(entry.player.getUUID());
        List<Entry> entries = BY_MATCH.get(entry.match);
        if (entries != null) {
            entries.remove(entry);
            if (entries.isEmpty()) BY_MATCH.remove(entry.match);
        }
        release(entry);
    }

    private static void force(Entry entry) {
        ServerLevel level = (ServerLevel) entry.zombie.level();
        entry.chunk = entry.zombie.chunkPosition().pack();
        Ticket ticket = CHUNKS.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(entry.chunk, key -> new Ticket());
        if (ticket.users++ == 0) level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.unpack(entry.chunk), 2);
    }
    private static void release(Entry entry) {
        ServerLevel level = (ServerLevel) entry.zombie.level();
        Map<Long, Ticket> chunks = CHUNKS.get(level);
        if (chunks == null) return;
        Ticket ticket = chunks.get(entry.chunk);
        if (ticket != null && --ticket.users == 0) {
            level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.unpack(entry.chunk), 2);
            chunks.remove(entry.chunk);
            if (chunks.isEmpty()) CHUNKS.remove(level);
        }
    }

    private static final class LoggerZombie extends Zombie {
        final Entry entry;
        LoggerZombie(Entry entry, ServerLevel level) {
            super(level);
            this.entry = entry;
            setPersistenceRequired();
            setNoAi(true);
            setCanPickUpLoot(false);
            setCustomName(Component.literal(entry.player.getScoreboardName()));
            setCustomNameVisible(true);
            addTag(TAG);
            addTag("brainage_minigames:participant=" + entry.player.getUUID());
        }
        @Override protected boolean isSunSensitive() { return false; }
        @Override protected boolean convertsInWater() { return false; }
        @Override public void checkDespawn() {}
        @Override public boolean shouldBeSaved() { return false; }
        @Override public boolean canUsePortal(boolean passengers) { return false; }
        @Override protected boolean shouldDropLoot(ServerLevel level) { return false; }
        @Override public boolean shouldDropExperience() { return false; }
        @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            entry.player.setServerLevel(level);
            entry.player.setPos(position());
            if (source.getEntity() instanceof ServerPlayer attacker
                    && entry.match.teamOf(attacker.getUUID()).equals(entry.match.teamOf(entry.player.getUUID()))) return false;
            if (!entry.match.allowDamage(entry.player, source)) return false;
            float before = getHealth() + getAbsorptionAmount();
            boolean accepted = super.hurtServer(level, source, amount);
            if (accepted && getHealth() + getAbsorptionAmount() < before && isAlive()
                    && source.getEntity() instanceof ServerPlayer attacker) entry.match.damaged(entry.player, attacker);
            return accepted;
        }
        @Override public void die(DamageSource source) {
            if (!LOGGERS.containsKey(entry.player.getUUID())) return;
            entry.player.setServerLevel((ServerLevel) level());
            entry.player.setPos(position());
            syncEquipment(entry);
            if (source.getEntity() instanceof ServerPlayer attacker) entry.match.damaged(entry.player, attacker);
            entry.match.loggerKilled(entry.player);
            forget(entry);
            super.die(source);
        }
    }
}
