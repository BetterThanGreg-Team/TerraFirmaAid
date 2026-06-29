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

package com.betterthangreg.terrafirmaaid.common.registries;

import com.mojang.serialization.Codec;
import com.betterthangreg.terrafirmaaid.api.debuff.IDebuffBuilder;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionAlgorithm;
import com.betterthangreg.terrafirmaaid.api.distribution.IDamageDistributionTarget;
import net.minecraft.util.ExtraCodecs;

import java.util.function.Function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.MapLike;
import java.util.stream.Stream;

public class FirstAidBaseCodecs {
    public static final Codec<IDebuffBuilder> DEBUFF_BUILDERS_DIRECT_CODEC = Codec.lazyInitialized(() -> FirstAidRegistries.DEBUFF_BUILDERS.byNameCodec())
            .dispatch(IDebuffBuilder::codec, codec -> toMapCodec(codec));
    public static final Codec<IDamageDistributionAlgorithm> DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC = Codec.lazyInitialized(() -> FirstAidRegistries.DAMAGE_DISTRIBUTION_ALGORITHMS.byNameCodec())
            .dispatch(IDamageDistributionAlgorithm::codec, codec -> toMapCodec(codec));
    public static final Codec<IDamageDistributionTarget> DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC = Codec.lazyInitialized(() -> FirstAidRegistries.DAMAGE_DISTRIBUTION_TARGETS.byNameCodec())
            .dispatch(IDamageDistributionTarget::codec, codec -> toMapCodec(codec));

    private static <T> MapCodec<T> toMapCodec(Codec<T> codec) {
        return new MapCodec<T>() {
            @Override
            public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
                return codec.decode(ops, ops.createMap(input.entries())).map(com.mojang.datafixers.util.Pair::getFirst);
            }

            @Override
            public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
                DataResult<S> encoded = codec.encode(input, ops, ops.empty());
                if (encoded.result().isPresent()) {
                    S result = encoded.result().get();
                    if (ops.getMap(result).result().isPresent()) {
                        MapLike<S> mapLike = ops.getMap(result).result().get();
                        mapLike.entries().forEach(entry -> prefix.add(entry.getFirst(), entry.getSecond()));
                        return prefix;
                    }
                }
                return prefix.withErrorsFrom(encoded);
            }

            @Override
            public <S> Stream<S> keys(DynamicOps<S> ops) {
                return Stream.empty();
            }
        };
    }
}
