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
import com.betterthangreg.terrafirmaaid.common.EventHandler;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageClientRequest implements CustomPacketPayload {
    public static final Type<MessageClientRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "client_request"));

    public static final StreamCodec<FriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageClientRequest::new
    );

    private final TypeEnum type;

    public MessageClientRequest(FriendlyByteBuf buffer) {
        this.type = TypeEnum.TYPES[buffer.readByte()];
    }

    public MessageClientRequest(TypeEnum type) {
        this.type = type;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum TypeEnum {
        TUTORIAL_COMPLETE, REQUEST_REFRESH;

        private static final TypeEnum[] TYPES = values();
    }

    public static void handle(final MessageClientRequest message, final IPayloadContext context) {
        final ServerPlayer player = (ServerPlayer) context.player();
        if (message.type == TypeEnum.TUTORIAL_COMPLETE) {
            EventHandler.TUTORIAL_DONE.add(player.getName().getString());
            context.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.hasTutorial = true;
            });
        } else if (message.type == TypeEnum.REQUEST_REFRESH) {
            context.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                PacketDistributor.sendToPlayer(player, new MessageSyncDamageModel(player.level().registryAccess(), damageModel, true));
            });
        }
    }
}
