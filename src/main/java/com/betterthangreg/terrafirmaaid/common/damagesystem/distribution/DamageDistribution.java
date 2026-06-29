/*
 * FirstAid
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

package com.betterthangreg.terrafirmaaid.common.damagesystem.distribution;

import com.betterthangreg.terrafirmaaid.FirstAid;
import com.betterthangreg.terrafirmaaid.FirstAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.api.event.FirstAidLivingDamageEvent;
import com.betterthangreg.terrafirmaaid.common.RegistryObjects;
import com.betterthangreg.terrafirmaaid.common.damagesystem.PlayerDamageModel;
import com.betterthangreg.terrafirmaaid.common.network.MessageUpdatePart;
import com.betterthangreg.terrafirmaaid.common.util.ArmorUtils;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.LoggingMarkers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class DamageDistribution implements IDamageDistributionAlgorithm {

    public static float handleDamageTaken(IDamageDistributionAlgorithm damageDistribution, AbstractPlayerDamageModel damageModel, float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat, boolean redistributeIfLeft) {
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- Damaging {} using {} for dmg source {}, redistribute {}, addStat {} ---", damage, damageDistribution.toString(), source.type().msgId(), redistributeIfLeft, addStat);
        }
        CompoundTag beforeCache = damageModel.serializeNBT(player.level().registryAccess());
        if (!damageDistribution.skipGlobalPotionModifiers())
            damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        //VANILLA COPY - combat tracker and exhaustion
        if (damage != 0.0F) {
            player.causeFoodExhaustion(source.getFoodExhaustion());
            player.getCombatTracker().recordDamage(source, damage);
        }

        float left = damageDistribution.distributeDamage(damage, player, source, addStat);
        if (left > 0 && redistributeIfLeft) {
            boolean hasTriedNoKill = damageDistribution == RandomDamageDistributionAlgorithm.NEAREST_NOKILL || damageDistribution == RandomDamageDistributionAlgorithm.ANY_NOKILL;
            damageDistribution = hasTriedNoKill ? RandomDamageDistributionAlgorithm.NEAREST_KILL : RandomDamageDistributionAlgorithm.getDefault();
            left = damageDistribution.distributeDamage(left, player, source, addStat);
            if (left > 0 && !hasTriedNoKill) {
                damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
                left = damageDistribution.distributeDamage(left, player, source, addStat);
            }
        }
        PlayerDamageModel before = new PlayerDamageModel();
        before.deserializeNBT(player.level().registryAccess(), beforeCache);
        if (NeoForge.EVENT_BUS.post(new FirstAidLivingDamageEvent(player, damageModel, before, source, left)).isCanceled()) {
            damageModel.deserializeNBT(player.level().registryAccess(), beforeCache); //restore prev state
            if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! Event got canceled ---");
            }
            return 0F;
        }

        if (damageModel.isDead(player))
            CommonUtils.killPlayer(damageModel, player, source);
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! {} still left ---", left);
        }
        return left;
    }

    protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart part) {
        return 0F;
    }

    protected float distributeDamageOnParts(float damage, @Nonnull AbstractPlayerDamageModel damageModel, @Nonnull EnumPlayerPart[] enumParts, @Nonnull Player player, boolean addStat) {
        ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<>(enumParts.length);
        for (EnumPlayerPart part : enumParts) {
            damageableParts.add(damageModel.getFromEnum(part));
        }
        Collections.shuffle(damageableParts);
        for (AbstractDamageablePart part : damageableParts) {
            float minHealth = minHealth(player, part);
            float dmgDone = damage - part.damage(damage, player, !player.hasEffect(RegistryObjects.MORPHINE_EFFECT), minHealth);
            PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageUpdatePart(part));
            if (addStat)
                player.awardStat(Stats.DAMAGE_TAKEN, Math.round(dmgDone * 10.0F));
            damage -= dmgDone;
            if (damage == 0)
                break;
            else if (damage < 0) {
                FirstAid.LOGGER.error(LoggingMarkers.DAMAGE_DISTRIBUTION, "Got negative damage {} left? Logic error? ", damage);
                break;
            }
        }
        return damage;
    }

    @Nonnull
    protected abstract List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList();

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        if (damage <= 0F) return 0F;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return 0F;
        if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Starting distribution of {} damage...", damage);
        }
        for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : getPartList()) {
            EquipmentSlot slot = pair.getLeft();
            EnumPlayerPart[] parts = pair.getRight();
            if (Arrays.stream(parts).map(damageModel::getFromEnum).anyMatch(part -> part.currentHealth > minHealth(player, part))) {
                final float originalDamage = damage;
                damage = ArmorUtils.applyArmor(player, player.getItemBySlot(slot), source, damage, slot);
                if (damage <= 0F)
                    return 0F;
                damage = ArmorUtils.applyEnchantmentModifiers(player, slot, source, damage);
                if (damage <= 0F)
                    return 0F;
                net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event = new net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre(player, new net.neoforged.neoforge.common.damagesource.DamageContainer(source, damage));
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                damage = event.getNewDamage();
                if (damage <= 0F) return 0F;
                final float dmgAfterReduce = damage;

                damage = distributeDamageOnParts(damage, damageModel, parts, player, addStat);
                if (damage == 0F)
                    break;
                final float absorbFactor = originalDamage / dmgAfterReduce;
                final float damageDistributed = dmgAfterReduce - damage;
                damage = originalDamage - (damageDistributed * absorbFactor);
                if (FirstAidConfig.GENERAL.debug.get()) {
                    FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Distribution round: Not done yet, going to next round. Needed to distribute {} damage (reduced to {}) to {}, but only distributed {}. New damage to be distributed is {}, based on absorb factor {}", originalDamage, dmgAfterReduce, slot, damageDistributed, damage, absorbFactor);
                }
            } else if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Skipping {}, no health > min in parts!", slot);
            }
        }
        return damage;
    }
}
