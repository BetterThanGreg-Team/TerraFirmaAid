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

package com.betterthangreg.terrafirmaaid.common.registries;

import com.google.common.collect.ImmutableMap;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.api.debuff.IDebuff;
import com.betterthangreg.terrafirmaaid.api.debuff.IDebuffBuilder;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionTarget;
import com.betterthangreg.terrafirmaaid.api.enums.EnumDebuffSlot;
import com.betterthangreg.terrafirmaaid.common.damagesystem.debuff.SharedDebuff;
import com.betterthangreg.terrafirmaaid.common.util.LoggingMarkers;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.*;

public class TerraFirmaAidRegistryLookups {
    private static final Object LOCK = new Object();
    private static final Collection<LookupReloadListener> LISTENERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static Map<DamageType, IDamageDistributionAlgorithm> DAMAGE_DISTRIBUTIONS;
    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> DEBUFF_BUILDERS;

    public static IDamageDistributionAlgorithm getDamageDistributions(DamageType damageType) {
        return DAMAGE_DISTRIBUTIONS.get(damageType);
    }

    public static IDebuff[] getDebuffs(EnumDebuffSlot slot) {
        List<IDebuff> list = new ArrayList<>();
        for (IDebuffBuilder iDebuffBuilder : DEBUFF_BUILDERS.getOrDefault(slot, Collections.emptyList())) {
            IDebuff build = iDebuffBuilder.build();
            if (slot.playerParts.length > 1) {
                build = new SharedDebuff(build, slot);
            }
            list.add(build);
        }
        return list.toArray(new IDebuff[0]);
    }

    public static void init(RegistryAccess registryAccess, boolean isRemote) {
        if (isRemote && TerraFirmaAid.isSynced) {
            throw new IllegalStateException("Synced before registry lookups have been loaded!");
        }

        synchronized (LOCK) {
            DAMAGE_DISTRIBUTIONS = buildDamageDistributions(registryAccess);
            DEBUFF_BUILDERS = buildDebuffs(registryAccess);
            for (LookupReloadListener listener : LISTENERS) {
                listener.onLookupsReloaded();
            }
        }
        TerraFirmaAid.LOGGER.info(LoggingMarkers.REGISTRY, "Built {} TerraFirmaAid registry lookups", isRemote ? "remote" : "local");
    }

    private static Map<DamageType, IDamageDistributionAlgorithm> buildDamageDistributions(RegistryAccess registryAccess) {
        Registry<IDamageDistributionTarget> damageDistributionRegistry = registryAccess.registryOrThrow(TerraFirmaAidRegistries.Keys.DAMAGE_DISTRIBUTIONS);

        Map<DamageType, IDamageDistributionAlgorithm> staticAlgorithms = new HashMap<>();
        Map<DamageType, IDamageDistributionAlgorithm> dynamicAlgorithms = new HashMap<>();

        for (Map.Entry<ResourceKey<IDamageDistributionTarget>, IDamageDistributionTarget> entry : damageDistributionRegistry.entrySet()) {
            ResourceKey<IDamageDistributionTarget> key = entry.getKey();
            IDamageDistributionTarget distributionTarget = entry.getValue();

            IDamageDistributionAlgorithm algorithm = distributionTarget.getAlgorithm();
            List<DamageType> damageTypes = distributionTarget.buildApplyList(registryAccess.registryOrThrow(Registries.DAMAGE_TYPE));
            Map<DamageType, IDamageDistributionAlgorithm> mapToUse = distributionTarget.isDynamic() ? dynamicAlgorithms : staticAlgorithms;
            for (DamageType damageType : damageTypes) {
                IDamageDistributionAlgorithm oldVal = mapToUse.put(damageType, algorithm);
                if (oldVal != null) {
                    TerraFirmaAid.LOGGER.warn(LoggingMarkers.REGISTRY, "Damage distribution {} overwrites previously registered distribution for damage type {}", key, damageType.msgId());
                }
            }
        }
        ImmutableMap.Builder<DamageType, IDamageDistributionAlgorithm> allDamageDistributions = ImmutableMap.builder();
        allDamageDistributions.putAll(staticAlgorithms);
        for (Map.Entry<DamageType, IDamageDistributionAlgorithm> dynamicEntry : dynamicAlgorithms.entrySet()) {
            if (!staticAlgorithms.containsKey(dynamicEntry.getKey())) {
                allDamageDistributions.put(dynamicEntry);
            }
        }
        return allDamageDistributions.build();
    }

    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> buildDebuffs(RegistryAccess registryAccess) {
        Registry<IDebuffBuilder> debuffBuilderRegistry = registryAccess.registryOrThrow(TerraFirmaAidRegistries.Keys.DEBUFFS);

        EnumMap<EnumDebuffSlot, List<IDebuffBuilder>> debuffMap = new EnumMap<>(EnumDebuffSlot.class);
        for (Map.Entry<ResourceKey<IDebuffBuilder>, IDebuffBuilder> entry : debuffBuilderRegistry.entrySet()) {
            IDebuffBuilder debuffBuilder = entry.getValue();
            debuffMap.computeIfAbsent(debuffBuilder.affectedSlot(), slot -> new ArrayList<>()).add(debuffBuilder);
        }
        return debuffMap;
    }

    public static void reset() {
        DAMAGE_DISTRIBUTIONS = null;
        DEBUFF_BUILDERS = null;
        LISTENERS.clear();
    }

    public static void registerReloadListener(LookupReloadListener reloadListener) {
        Objects.requireNonNull(reloadListener);
        synchronized (LOCK) {
            LISTENERS.add(reloadListener);
            if (DAMAGE_DISTRIBUTIONS != null) {
                reloadListener.onLookupsReloaded();
            }
        }
    }
}
