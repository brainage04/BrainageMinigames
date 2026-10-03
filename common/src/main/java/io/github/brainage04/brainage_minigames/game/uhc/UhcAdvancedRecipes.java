package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/** Stateful crafts; unknown Hypixel policies are identified here and in the source catalog. */
public final class UhcAdvancedRecipes {
    private static final String SWAN = "brainage_swan_song";
    private static final Map<Match, State> STATES = new HashMap<>();
    private static final class State {
        final Map<UUID, Integer> quietSince = new HashMap<>();
        final Map<UUID, Integer> goldNuggets = new HashMap<>();
        final Map<UUID, Integer> fusionRolls = new HashMap<>();
        final Map<UUID, Integer> fusionUsed = new HashMap<>();
        final Map<UUID, Integer> pandoraUsed = new HashMap<>();
        final Map<ServerLevel, Map<BlockPos, Forge>> forges = new HashMap<>();
        final Map<UUID, List<BlockPos>> treasure = new HashMap<>();
        boolean deathmatch;
    }
    private record Forge(AbstractFurnaceBlockEntity entity, int remaining) {}
    private UhcAdvancedRecipes() {}
    private static State state(Match match) { return STATES.computeIfAbsent(match, key -> new State()); }

    public static boolean strategistEligible(ServerPlayer player, int recipe) {
        if (recipe == 2) return true;
        if (player.getHealth() >= 10) return false;
        if (recipe == 1) return true;
        Match match = UhcProgression.match(player);
        if (match == null) return false;
        var team = match.teamOf(player.getUUID()).orElseThrow();
        for (ServerPlayer other : match.alivePlayers()) if (other != player && team.members().contains(other.getUUID())) return false;
        return true;
    }

    public static void hurt(ServerPlayer player) {
        Match match = UhcProgression.match(player);
        if (match != null) state(match).quietSince.put(player.getUUID(), match.activeTicks());
    }

    public static void tick(Match match) {
        State state = state(match);
        int ticks = match.activeTicks();
        for (ServerPlayer player : match.alivePlayers()) {
            int level = UhcProgression.level(player, UhcProgression.Tree.STRATEGIST);
            int since = state.quietSince.computeIfAbsent(player.getUUID(), key -> ticks);
            boolean prestige2 = UhcProgression.maxed(player) || UhcProgression.bought(match.server(), player.getUUID(), UhcProgression.Tree.STRATEGIST, "prestige2");
            int interval = (60 - Math.max(0, level - 1) * 3 - (prestige2 ? 5 : 0)) * 20;
            if (level > 0 && player.getHealth() < 10 && ticks - since >= interval) {
                player.heal(1); state.quietSince.put(player.getUUID(), ticks);
            }
            if (ticks % 20 != 0) continue;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) updateSword(player, stack);
            boolean fusion = true;
            for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET)) {
                if (!UhcCrafting.kind(player.getItemBySlot(slot)).equals("fusion_armor")) { fusion = false; break; }
            }
            if (fusion) player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0));
            List<BlockPos> targets = state.treasure.get(player.getUUID());
            if (targets != null && player.level() == match.arena().level()) {
                var iterator = targets.iterator();
                while (iterator.hasNext()) {
                    BlockPos target = iterator.next();
                    if (Math.abs(player.getX() - target.getX()) > 8 || Math.abs(player.getZ() - target.getZ()) > 8) continue;
                    dropPackage(player.level(), target); iterator.remove();
                }
            }
        }
    }

    public static void deathmatch(Match match) { state(match).deathmatch = true; for (ServerPlayer player : match.alivePlayers()) for (ItemStack stack : player.getInventory().getNonEquipmentItems()) updateSword(player, stack); }
    public static void crafted(ServerPlayer player, String recipe) {
        Match match = UhcProgression.match(player);
        if (match != null && recipe.equals("fusion_armor")) {
            State state = state(match);
            Integer roll = state.fusionRolls.remove(player.getUUID());
            if (roll != null) state.fusionUsed.merge(player.getUUID(), 1 << roll, (a, b) -> a | b);
        }
    }
    public static void close(Match match) { STATES.remove(match); }

    public static void updateSword(ServerPlayer player, ItemStack stack) {
        String kind = UhcCrafting.kind(stack);
        if (!kind.equals("apprentice_sword") && !kind.equals("apprentice_bow")) return;
        Match match = UhcProgression.match(player);
        if (match == null) return;
        int level = state(match).deathmatch ? 3 : match.activeTicks() >= match.settings().minutesInTicks(UhcGame.GRACE_PERIOD) + 15 * 60 * 20 ? 2 : 1;
        var key = kind.equals("apprentice_sword") ? Enchantments.SHARPNESS : Enchantments.POWER;
        Holder<Enchantment> enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        if (EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) != level) stack.enchant(enchantment, level);
    }

    public static void placed(ServerPlayer player, BlockPos pos, boolean forge) {
        Match match = UhcProgression.match(player);
        if (forge && match != null && player.level().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            state(match).forges.computeIfAbsent(player.level(), key -> new HashMap<>()).put(pos.immutable(), new Forge(furnace, 10));
        }
    }

    /** Called instead of vanilla furnace ticking: no fuel use, ten successful smelts total. */
    public static boolean forgeTick(ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity furnace) {
        for (State state : STATES.values()) {
            Map<BlockPos, Forge> forges = state.forges.get(level);
            if (forges == null) continue;
            Forge forge = forges.get(pos);
            if (forge == null) continue;
            if (forge.entity != furnace) { forges.remove(pos); continue; }
            ItemStack input = furnace.getItem(0);
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
            if (recipe.isEmpty()) return true;
            ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input));
            ItemStack output = furnace.getItem(2);
            if (result.isEmpty() || !output.isEmpty() && !ItemStack.isSameItemSameComponents(output, result)) return true;
            int count = Math.min(Math.min(forge.remaining, input.getCount()), (result.getMaxStackSize() - output.getCount()) / result.getCount());
            if (count <= 0) return true;
            if (output.isEmpty()) furnace.setItem(2, result.copyWithCount(count * result.getCount()));
            else output.grow(count * result.getCount());
            input.shrink(count); furnace.setChanged();
            int remaining = forge.remaining - count;
            if (remaining == 0) {
                forges.remove(pos);
                // No block loot means modern vanilla does not release the container contents.
                net.minecraft.world.Containers.dropContents(level, pos, furnace);
                level.destroyBlock(pos, false);
            }
            else forges.put(pos.immutable(), new Forge(furnace, remaining));
            return true;
        }
        return false;
    }

    /** Ten uses means ten whole trees. The 256 connected-log bound is a local server-safety policy. */
    public static void mined(ServerPlayer player, BlockPos origin, BlockState broken) {
        if (!broken.is(net.minecraft.tags.BlockTags.LOGS) || !UhcCrafting.kind(player.getMainHandItem()).equals("lumberjacks_axe") || UhcProgression.match(player) == null) return;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(); Set<BlockPos> seen = new HashSet<>(); queue.add(origin); seen.add(origin);
        while (!queue.isEmpty() && seen.size() < 256) {
            BlockPos current = queue.removeFirst();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                BlockPos next = current.offset(x, y, z);
                if (seen.contains(next) || seen.size() >= 256) continue;
                BlockState block = player.level().getBlockState(next);
                if (!block.is(net.minecraft.tags.BlockTags.LOGS) || !MatchManager.allowBreak(player, next, block)) continue;
                seen.add(next); queue.addLast(next); player.level().destroyBlock(next, true, player);
            }
        }
    }

    public static void swan(ItemStack stack, int level) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SWAN, level));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }
    public static int swanLevel(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(SWAN, 0); }
    public static float swanDamage(DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) return amount;
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || UhcProgression.match(player) == null) return amount;
        return amount * (1 + swanLevel(player.getMainHandItem()) * Math.max(0, 20 - player.getHealth()) * 0.005f);
    }
    public static boolean anvilForbidden(ItemStack stack) { String kind = UhcCrafting.kind(stack); return kind.equals("lumberjacks_axe") || kind.equals("apprentice_sword") || kind.equals("apprentice_bow") || kind.equals("bloodlust"); }

    /** Level-30 vanilla selection is confirmed; a three-level anvil charge is a local policy. */
    public static ItemStack anvil(ServerPlayer player, ItemStack item, ItemStack book) {
        if (UhcProgression.match(player) == null || item.isEmpty() || anvilForbidden(item)) return ItemStack.EMPTY;
        String kind = UhcCrafting.kind(book);
        ItemStack result = item.copyWithCount(1);
        if (kind.equals("enhancement_book") && item.has(DataComponents.ENCHANTABLE)) {
            return EnchantmentHelper.enchantItem(player.getRandom(), result, 30, player.registryAccess(), java.util.Optional.empty());
        }
        int level = swanLevel(book);
        if (level > 0 && (item.is(ItemTags.SWORDS) || item.is(Items.ENCHANTED_BOOK))) {
            int own = swanLevel(item); swan(result, own == level ? Math.min(2, level + 1) : Math.max(own, level)); return result;
        }
        return ItemStack.EMPTY;
    }

    public static boolean fusionIngredients(CraftingInput input) {
        int count = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (!stack.is(Items.DIAMOND_HELMET) && !stack.is(Items.DIAMOND_CHESTPLATE) && !stack.is(Items.DIAMOND_LEGGINGS) && !stack.is(Items.DIAMOND_BOOTS)) return false;
            count++;
        }
        return count == 5;
    }
    public static ItemStack fusion(ServerPlayer player) {
        Match match = UhcProgression.match(player);
        State state = state(match);
        int used = state.fusionUsed.getOrDefault(player.getUUID(), 0);
        if (used == 15) { state.fusionUsed.put(player.getUUID(), 0); used = 0; }
        final int mask = player.level().getGameRules().get(UhcProgression.NO_DUPLICATE_CRAFTS) ? used : 0;
        int roll = state.fusionRolls.computeIfAbsent(player.getUUID(), key -> {
            int choice = player.getRandom().nextInt(4 - Integer.bitCount(mask));
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) == 0 && choice-- == 0) return i;
            throw new IllegalStateException("Fusion pool exhausted");
        });
        ItemStack stack = new ItemStack(switch (roll) { case 0 -> Items.DIAMOND_HELMET; case 1 -> Items.DIAMOND_CHESTPLATE; case 2 -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS; });
        UhcCrafting.enchant(player, stack, Enchantments.PROTECTION, 5); return stack;
    }

    /** Weighted sampling without replacement changes only the owner's optional no-duplicates policy. */
    public static int pandoraRoll(ServerPlayer player, int[] weights) {
        State state = state(UhcProgression.match(player));
        int used = state.pandoraUsed.getOrDefault(player.getUUID(), 0);
        if (used == (1 << weights.length) - 1) used = 0;
        if (!player.level().getGameRules().get(UhcProgression.NO_DUPLICATE_CRAFTS)) used = 0;
        int total = 0;
        for (int i = 0; i < weights.length; i++) if ((used & (1 << i)) == 0) total += weights[i];
        int choice = player.getRandom().nextInt(total), offset = 0;
        for (int i = 0; i < weights.length; i++) {
            if ((used & (1 << i)) == 0) {
                if (choice < weights[i]) { state.pandoraUsed.put(player.getUUID(), used | (1 << i)); return offset + choice; }
                choice -= weights[i];
            }
            offset += weights[i];
        }
        throw new IllegalStateException("Pandora pool exhausted");
    }

    /** NOT Hypixel-confirmed: gold chance 60%-2%/HP; danger chance 2%/HP, capped at 40%. */
    public static ItemStack deepCatch(ServerPlayer player, int roll, boolean ingot) {
        State state = state(UhcProgression.match(player));
        int goldChance = Math.max(20, 60 - (int) player.getHealth() * 2);
        int used = state.goldNuggets.getOrDefault(player.getUUID(), 0);
        int reward = ingot ? 9 : 3;
        if (roll < goldChance && used + reward <= 16 * 9) {
            state.goldNuggets.put(player.getUUID(), used + reward); return new ItemStack(ingot ? Items.GOLD_INGOT : Items.GOLD_NUGGET, ingot ? 1 : 3);
        }
        if (roll >= 100 - Math.min(40, (int) player.getHealth() * 2)) {
            // NOT Hypixel-confirmed: equally weighted zombie, drowned, guardian; Speed II/Strength II.
            var type = switch (player.getRandom().nextInt(3)) { case 0 -> EntityTypes.ZOMBIE; case 1 -> EntityTypes.DROWNED; default -> EntityTypes.GUARDIAN; };
            LivingEntity mob = type.create(player.level(), EntitySpawnReason.TRIGGERED);
            if (mob != null) { mob.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0); mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200, 1)); mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1)); player.level().addFreshEntity(mob); }
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.COD);
    }

    public static InteractionResult use(ServerPlayer player, ItemStack stack) {
        String kind = UhcCrafting.kind(stack);
        if (kind.equals("masters_compass")) {
            Match match = UhcProgression.match(player); ServerPlayer nearest = null; double distance = Double.MAX_VALUE;
            for (ServerPlayer other : match.alivePlayers()) {
                if (other == player || other.level() != player.level() || other.getY() < other.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, other.blockPosition().getX(), other.blockPosition().getZ()) - 2) continue;
                double candidate = player.distanceToSqr(other); if (candidate < distance) { nearest = other; distance = candidate; }
            }
            if (nearest == null) player.sendSystemMessage(Component.literal("No surface player found."));
            else { Vec3 direction = nearest.position().subtract(player.position()).normalize(); for (int i = 1; i <= 12; i++) { Vec3 point = player.position().add(direction.scale(i * 0.5)).add(0, 1, 0); player.level().sendParticles(player, ParticleTypes.FLAME, false, false, point.x, point.y, point.z, 1, 0, 0, 0, 0); } }
            return InteractionResult.SUCCESS;
        }
        if (kind.equals("backpack")) { openBackpack(player, stack); return InteractionResult.SUCCESS; }
        if (!kind.equals("el_dorado")) return InteractionResult.PASS;
        Match match = UhcProgression.match(player); ServerLevel level = match.arena().level(); var border = level.getWorldBorder();
        // NOT Hypixel-confirmed: mirrored position, within 80% of the current border; center points use its far edge.
        double radius = Math.max(1, border.getSize() * 0.4);
        double x = Math.clamp(border.getCenterX() - (player.getX() - border.getCenterX()), border.getCenterX() - radius, border.getCenterX() + radius);
        double z = Math.clamp(border.getCenterZ() - (player.getZ() - border.getCenterZ()), border.getCenterZ() - radius, border.getCenterZ() + radius);
        if (Math.abs(x - player.getX()) + Math.abs(z - player.getZ()) < 16) x = border.getCenterX() + radius;
        BlockPos target = BlockPos.containing(x, 0, z);
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 3, true, true);
        stack.set(DataComponents.MAP_ID, map.get(DataComponents.MAP_ID));
        MapItemSavedData.addTargetDecoration(stack, target, "el_dorado", MapDecorationTypes.RED_X);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("brainage_uhc_item", "el_dorado_active"));
        state(match).treasure.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).add(target);
        player.sendSystemMessage(Component.literal("El Dorado: reach x=" + target.getX() + ", z=" + target.getZ() + "."));
        return InteractionResult.SUCCESS;
    }

    /** NOT Hypixel-confirmed: diamond pick Efficiency II, diamond sword Sharpness II, 2 apples, 8 gold. */
    private static void dropPackage(ServerLevel level, BlockPos target) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE), sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        pick.enchant(enchantments.getOrThrow(Enchantments.EFFICIENCY), 2); sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 2);
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(pick, sword, new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.GOLD_INGOT, 8))));
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("El Dorado care package"));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5, y + 16, target.getZ() + 0.5, chest));
    }

    private static void openBackpack(ServerPlayer player, ItemStack backpack) {
        SimpleContainer contents = new SimpleContainer(27) {
            @Override public void setChanged() {
                backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
            }
        };
        backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents.getItems());
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> {
            ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x3, id, inventory, contents, 3);
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot original = menu.slots.get(i);
                if (i < 27 || original.getItem() == backpack) {
                    final boolean backing = original.getItem() == backpack;
                    Slot slot = new Slot(original.container, original.getContainerSlot(), original.x, original.y) {
                        @Override public boolean mayPlace(ItemStack item) { return !backing && !UhcCrafting.kind(item).equals("backpack") && super.mayPlace(item); }
                        @Override public boolean mayPickup(net.minecraft.world.entity.player.Player user) { return !backing && super.mayPickup(user); }
                    };
                    slot.index = original.index; menu.slots.set(i, slot);
                }
            }
            return menu;
        }, Component.literal("UHC Backpack")));
    }
}
