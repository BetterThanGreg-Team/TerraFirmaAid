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

package com.betterthangreg.terrafirmaaid.common.network;

import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageApplyAbsorption implements CustomPacketPayload {
    public static final Type<MessageApplyAbsorption> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "apply_absorption"));

    public static final StreamCodec<FriendlyByteBuf, MessageApplyAbsorption> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageApplyAbsorption::new
    );

    private final float amount;

    public MessageApplyAbsorption(FriendlyByteBuf buffer) {
        amount = buffer.readFloat();
    }

    public MessageApplyAbsorption(float amount) {
        this.amount = amount;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(amount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageApplyAbsorption message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            damageModel.setAbsorption(message.amount);
        });
    }
}
