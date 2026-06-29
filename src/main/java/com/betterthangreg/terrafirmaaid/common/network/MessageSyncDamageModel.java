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
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageSyncDamageModel implements CustomPacketPayload {
    public static final Type<MessageSyncDamageModel> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "sync_damage_model"));

    public static final StreamCodec<FriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageSyncDamageModel::new
    );

    private final CompoundTag playerDamageModel;
    private final boolean scaleMaxHealth;

    public MessageSyncDamageModel(FriendlyByteBuf buffer) {
        this.playerDamageModel = buffer.readNbt();
        this.scaleMaxHealth = buffer.readBoolean();
    }

    public MessageSyncDamageModel(net.minecraft.core.HolderLookup.Provider provider, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        this.playerDamageModel = damageModel.serializeNBT(provider);
        this.scaleMaxHealth = scaleMaxHealth;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(this.playerDamageModel);
        buffer.writeBoolean(scaleMaxHealth);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageSyncDamageModel message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) return;
            if (message.scaleMaxHealth)
                damageModel.runScaleLogic(mc.player);
            damageModel.deserializeNBT(mc.level.registryAccess(), message.playerDamageModel);
        });
    }
}
