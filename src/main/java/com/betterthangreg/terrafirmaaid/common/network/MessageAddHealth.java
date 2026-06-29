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
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.LoggingMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public class MessageAddHealth implements CustomPacketPayload {
    public static final Type<MessageAddHealth> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "add_health"));

    public static final StreamCodec<FriendlyByteBuf, MessageAddHealth> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageAddHealth::new
    );

    private final float[] table;

    public MessageAddHealth(FriendlyByteBuf buffer) {
        this.table = new float[8];
        for (int i = 0; i < 8; i++) {
            this.table[i] = buffer.readFloat();
        }
    }

    public MessageAddHealth(float[] table) {
        this.table = table;
    }

    public void encode(FriendlyByteBuf buf) {
        for (float f : table)
            buf.writeFloat(f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageAddHealth message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            LocalPlayer playerSP = Minecraft.getInstance().player;
            Optional<AbstractPlayerDamageModel> optDamageModel = CommonUtils.getOptionalDamageModel(playerSP);
            if (optDamageModel.isPresent()) {
                AbstractPlayerDamageModel damageModel = optDamageModel.get();
                for (int i = 0; i < message.table.length; i++) {
                    float f = message.table[i];
                    EnumPlayerPart part = EnumPlayerPart.VALUES[i];
                    damageModel.getFromEnum(part).heal(f, playerSP, false);
                }
            } else {
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Failed to find damage model, what?");
            }
        });
    }
}
