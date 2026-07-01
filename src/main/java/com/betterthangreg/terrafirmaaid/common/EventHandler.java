/*
 * TerraFirmaAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.betterthangreg.terrafirmaaid.common;

import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.common.tfc.TFCCompat;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.DamageDistribution;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.HealthDistribution;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.common.network.MessageConfiguration;
import com.betterthangreg.terrafirmaaid.common.network.MessageSyncDamageModel;
import com.betterthangreg.terrafirmaaid.common.registries.TerraFirmaAidRegistryLookups;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.PlayerSizeHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class EventHandler {
    public static final Random RAND = new Random();

    public static final Map<Player, Pair<Entity, HitResult>> hitList = new WeakHashMap<>();
    public static final Set<String> TUTORIAL_DONE = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOWEST) //so all other can modify their damage first, and we apply after that
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !CommonUtils.hasDamageModel(entity))
            return;
        float amountToDamage = event.getAmount();
        Player player = (Player) entity;
        DamageSource source = event.getSource();

        float originalDamage = amountToDamage;
        if (player.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (amountToDamage <= player.lastHurt) {
                event.setCanceled(true);
                return;
            }
            amountToDamage -= player.lastHurt;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return;

        if (amountToDamage == Float.MAX_VALUE || Float.isNaN(amountToDamage) || amountToDamage == Float.POSITIVE_INFINITY) {
            damageModel.forEach(damageablePart -> damageablePart.currentHealth = 0F);
            if (player instanceof ServerPlayer)
                PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageSyncDamageModel(player.level().registryAccess(), damageModel, false));
            event.setCanceled(true);
            CommonUtils.killPlayer(damageModel, player, source);
            return;
        }

        boolean addStat = amountToDamage < 3.4028235E37F;
        IDamageDistributionAlgorithm damageDistribution = TerraFirmaAidRegistryLookups.getDamageDistributions(source.type());

        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Pair<Entity, HitResult> rayTraceResult = hitList.remove(player);
            if (rayTraceResult != null) {
                Entity entityProjectile = rayTraceResult.getLeft();
                EquipmentSlot slot = PlayerSizeHelper.getSlotTypeForProjectileHit(entityProjectile, player);
                if (slot != null) {
                    List<EnumPlayerPart> possibleParts = CommonUtils.getPartListForSlot(slot);
                    damageDistribution = new StandardDamageDistributionAlgorithm(Collections.singletonMap(slot, possibleParts), false, true);
                }
            }
        }
        if (damageDistribution == null) {
            // No given distribution found, and no projectile distribution either. Let's check if we can tell by the source where we should apply the damage, otherwise fall back to random
            damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source);
            if (damageDistribution == null) {
                damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
            }
        }

        float left = DamageDistribution.handleDamageTaken(damageDistribution, damageModel, amountToDamage, player, source, addStat, true);

        if (amountToDamage > left) {
            player.level().broadcastDamageEvent(player, source);
            player.invulnerableTime = 20;
            player.lastHurt = originalDamage;
        }

        event.setCanceled(true);

        hitList.remove(player);
    }

    @SubscribeEvent(priority =  EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        HitResult result = event.getRayTraceResult();
        if (result.getType() != HitResult.Type.ENTITY)
            return;

        Entity entity = ((EntityHitResult) result).getEntity();
        if (!entity.level().isClientSide && entity instanceof Player) {
            hitList.put((Player) entity, Pair.of(event.getEntity(), event.getRayTraceResult()));
        }
    }

    @SubscribeEvent
    public static void onEntityConstructing(net.neoforged.neoforge.event.entity.EntityEvent.EntityConstructing event) {
        Entity entity = event.getEntity();
        if (CommonUtils.hasDamageModel(entity)) {
            Player player = (Player) entity;
            //replace the data manager with our wrapper to grab absorption
            player.entityData = new SynchedEntityDataWrapper(player, player.entityData);
        }
    }

    @SubscribeEvent
    public static void tickPlayers(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!event.getEntity().getAbilities().invulnerable) {
            Player player = event.getEntity();
            if (!player.isAlive()) return;
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            damageModel.tick(player.level(), player);
            hitList.remove(player); //Damage should be done in the same tick as the hit was noted, otherwise we got a false-positive
        }
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (ModList.get().isLoaded("morpheus")) return;
        for (Player player : event.getLevel().players()) {
            if (player.isSleepingLongEnough()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.sleepHeal(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation tableName = event.getName();
        int bandage, plaster, morphine;
        NumberProvider bandageMax  = UniformGenerator.between(1, 3);
        NumberProvider plasterMax  = UniformGenerator.between(1, 5);
        NumberProvider morphineMax = UniformGenerator.between(1, 2);
        NumberProvider poolRolls   = ConstantValue.exactly(1.0F);
        if (tableName.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
            bandage = 8;
            plaster = 16;
            morphine = 4;
            morphineMax = ConstantValue.exactly(1);
        } else if (tableName.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR) || tableName.equals(BuiltInLootTables.STRONGHOLD_CROSSING) || tableName.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
            bandage = 20;
            plaster = 24;
            morphine = 8;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
            bandage = 4;
            plaster = 20;
            morphine = 2;
            plasterMax = UniformGenerator.between(3, 8);
        } else if (tableName.equals(BuiltInLootTables.IGLOO_CHEST)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            bandageMax = UniformGenerator.between(1, 2);
            plasterMax = UniformGenerator.between(1, 3);
            morphineMax = ConstantValue.exactly(1);
            poolRolls = UniformGenerator.between(0, 1);
        } else {
            return;
        }
        LootPool.Builder builder = LootPool.lootPool().name("terrafirmaaid_main").setRolls(poolRolls);
        builder.add(LootItem.lootTableItem(RegistryObjects.BANDAGE::get)
                    .apply(SetItemCountFunction.setCount(bandageMax))
                    .setWeight(bandage)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.PLASTER::get)
                    .apply(SetItemCountFunction.setCount(plasterMax))
                    .setWeight(plaster)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.MORPHINE::get)
                    .apply(SetItemCountFunction.setCount(morphineMax))
                    .setWeight(morphine)
                    .setQuality(0));
        event.getTable().addPool(builder.build());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isDeadOrDying() || !CommonUtils.hasDamageModel(entity))
            return;
        event.setCanceled(true);
        if (entity.level().isClientSide || !TerraFirmaAidConfig.SERVER.allowOtherHealingItems.get())
            return;

        // TFC compatibility: if TFC is loaded and override is disabled, don't block TFC regen
        // We still cancel the event but only distribute our own healing for non-TFC sources
        float amount = event.getAmount();

        // Check if this is natural regen (from FoodData)
        boolean isNaturalRegen = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(
                stackTraceElement -> stackTraceElement.getClassName().equals(FoodData.class.getName()));

        if (isNaturalRegen) {
            if (TFCCompat.shouldOverrideTFCRegen()) {
                // We override TFC regen - apply our multiplier
                if (TerraFirmaAidConfig.SERVER.allowNaturalRegeneration.get())
                    amount *= (float) (double) TerraFirmaAidConfig.SERVER.naturalRegenMultiplier.get();
                else
                    return; // TFC regen blocked entirely
            } else if (TFCCompat.isTFCLoaded()) {
                // TFC is loaded and we're not overriding - let TFC handle natural regen
                return;
            } else {
                // Vanilla - apply our multiplier if enabled
                if (TerraFirmaAidConfig.SERVER.allowNaturalRegeneration.get())
                    amount *= (float) (double) TerraFirmaAidConfig.SERVER.naturalRegenMultiplier.get();
                else
                    return; // Vanilla regen blocked
            }
        } else {
            // External healing (potions, etc.)
            amount *= (float) (double) TerraFirmaAidConfig.SERVER.otherRegenMultiplier.get();
        }

        if (TerraFirmaAidConfig.GENERAL.debug.get()) {
            CommonUtils.debugLogStacktrace("External healing: : " + amount);
        }
        HealthDistribution.distributeHealth(amount, (Player) entity, true);
    }


    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            TerraFirmaAid.LOGGER.debug("Sending damage model to {}", event.getEntity().getName());
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(event.getEntity());
            if (damageModel == null) return;
            if (damageModel.hasTutorial)
                com.betterthangreg.terrafirmaaid.common.EventHandler.TUTORIAL_DONE.add(event.getEntity().getName().getString());
            ServerPlayer playerMP = (ServerPlayer) event.getEntity();
            PacketDistributor.sendToPlayer(playerMP, new MessageConfiguration(damageModel.serializeNBT(playerMP.registryAccess())));
        }
    }

    @SubscribeEvent(priority =  EventPriority.LOW)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        hitList.remove(event.getEntity());
    }

    public static void onWorldLoad(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        if (!world.isClientSide() && world instanceof Level) {
            // TFC compatibility: if TFC is loaded and override is disabled, don't touch the gamerule
            if (!TFCCompat.isTFCLoaded() || TFCCompat.shouldOverrideTFCRegen()) {
                ((Level) world).getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                        .set(TerraFirmaAidConfig.SERVER.allowNaturalRegeneration.get(), ((Level) world).getServer());
            }
        }
    }


    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageSyncDamageModel(player.level().registryAccess(), damageModel, true));
        }
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            TerraFirmaAidRegistryLookups.init(event.getRegistryAccess(), event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED);
        }
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        TerraFirmaAid.LOGGER.debug("Cleaning up");
        com.betterthangreg.terrafirmaaid.common.EventHandler.TUTORIAL_DONE.clear();
        EventHandler.hitList.clear();
        TerraFirmaAidRegistryLookups.reset();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        DebugDamageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!event.isEndConquered() && !player.level().isClientSide && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            damageModel.runScaleLogic(player);
            damageModel.forEach(damageablePart -> damageablePart.heal(damageablePart.getMaxHealth(), player, false));
            damageModel.scheduleResync();
        }
    }
}
