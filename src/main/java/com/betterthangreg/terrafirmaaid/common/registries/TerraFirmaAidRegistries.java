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

import com.mojang.serialization.Codec;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.api.debuff.IDebuffBuilder;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionTarget;
import com.betterthangreg.terrafirmaaid.common.apiimpl.StaticDamageDistributionTarget;
import com.betterthangreg.terrafirmaaid.common.apiimpl.TagDamageDistributionTarget;
import com.betterthangreg.terrafirmaaid.common.damagesystem.debuff.builder.ConstantDebuffBuilder;
import com.betterthangreg.terrafirmaaid.common.damagesystem.debuff.builder.OnHitDebuffBuilder;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.EqualDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class TerraFirmaAidRegistries {

    public static class Keys {
        public static final ResourceKey<Registry<Codec<? extends IDamageDistributionAlgorithm>>> DAMAGE_DISTRIBUTION_ALGORITHMS = key("damage_distribution_algorithms");
        public static final ResourceKey<Registry<Codec<? extends IDamageDistributionTarget>>> DAMAGE_DISTRIBUTION_TARGETS = key("damage_distribution_targets");
        public static final ResourceKey<Registry<IDamageDistributionTarget>> DAMAGE_DISTRIBUTIONS = key("damage_distributions");

        public static final ResourceKey<Registry<Codec<? extends IDebuffBuilder>>> DEBUFF_BUILDERS = key("debuff_builders");
        public static final ResourceKey<Registry<IDebuffBuilder>> DEBUFFS = key("debuffs");

        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, name));
        }
    }

    // --- DEFERRED REGISTERS ---
    static final DeferredRegister<Codec<? extends IDamageDistributionAlgorithm>> DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS = 
        DeferredRegister.create(Keys.DAMAGE_DISTRIBUTION_ALGORITHMS, TerraFirmaAid.MODID);
        
    static final DeferredRegister<Codec<? extends IDamageDistributionTarget>> DEFERRED_DAMAGE_DISTRIBUTION_TARGETS = 
        DeferredRegister.create(Keys.DAMAGE_DISTRIBUTION_TARGETS, TerraFirmaAid.MODID);
        
    static final DeferredRegister<Codec<? extends IDebuffBuilder>> DEFERRED_DEBUFF_BUILDERS = 
        DeferredRegister.create(Keys.DEBUFF_BUILDERS, TerraFirmaAid.MODID);

    // --- REGISTRY DEFINITIONS ---
    public static final Registry<Codec<? extends IDamageDistributionAlgorithm>> DAMAGE_DISTRIBUTION_ALGORITHMS = 
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.makeRegistry(builder -> {});
        
    public static final Registry<Codec<? extends IDamageDistributionTarget>> DAMAGE_DISTRIBUTION_TARGETS = 
        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.makeRegistry(builder -> {});
        
    public static final Registry<Codec<? extends IDebuffBuilder>> DEBUFF_BUILDERS = 
        DEFERRED_DEBUFF_BUILDERS.makeRegistry(builder -> {});

    static {
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("direct", () -> DirectDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("equal", () -> EqualDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("random", () -> RandomDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("standard", () -> StandardDamageDistributionAlgorithm.CODEC);

        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register("static", () -> StaticDamageDistributionTarget.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register("tag", () -> TagDamageDistributionTarget.CODEC);

        DEFERRED_DEBUFF_BUILDERS.register("constant", () -> ConstantDebuffBuilder.CODEC);
        DEFERRED_DEBUFF_BUILDERS.register("on_hit", () -> OnHitDebuffBuilder.CODEC);
    }

    // --- DATA DRIVEN REGISTRIES
    public static void createDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(Keys.DAMAGE_DISTRIBUTIONS, TerraFirmaAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC, TerraFirmaAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC);
        event.dataPackRegistry(Keys.DEBUFFS, TerraFirmaAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC, TerraFirmaAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC);
    }

    public static void setup(IEventBus modEventBus) {
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register(modEventBus);
        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register(modEventBus);
        DEFERRED_DEBUFF_BUILDERS.register(modEventBus);
        modEventBus.addListener(TerraFirmaAidRegistries::createDataPackRegistries);
    }
}
