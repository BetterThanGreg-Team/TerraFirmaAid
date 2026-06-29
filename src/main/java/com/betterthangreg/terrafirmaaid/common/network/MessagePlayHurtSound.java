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

package com.betterthangreg.terrafirmaaid.common.network;

import com.betterthangreg.terrafirmaaid.FirstAid;
import com.betterthangreg.terrafirmaaid.client.DebuffTimedSound;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

public class MessagePlayHurtSound implements CustomPacketPayload {
    public static final Type<MessagePlayHurtSound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "play_hurt_sound"));

    public static final StreamCodec<FriendlyByteBuf, MessagePlayHurtSound> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessagePlayHurtSound::new
    );

    private final SoundEvent sound;
    private final int duration;

    public MessagePlayHurtSound(FriendlyByteBuf buffer) {
        this(BuiltInRegistries.SOUND_EVENT.get(buffer.readResourceLocation()), buffer.readInt());
    }

    public MessagePlayHurtSound(SoundEvent sound, int duration) {
        this.sound = sound;
        this.duration = duration;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(Objects.requireNonNull(BuiltInRegistries.SOUND_EVENT.getKey(sound)));
        buf.writeInt(duration);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessagePlayHurtSound message, final IPayloadContext context) {
        context.enqueueWork(() -> DebuffTimedSound.playHurtSound(message.sound, message.duration));
    }
}
