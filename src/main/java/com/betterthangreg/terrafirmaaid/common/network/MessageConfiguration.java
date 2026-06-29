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
import com.betterthangreg.terrafirmaaid.client.ClientHooks;
import com.betterthangreg.terrafirmaaid.client.HUDHandler;
import com.betterthangreg.terrafirmaaid.common.EventHandler;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.LoggingMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageConfiguration implements CustomPacketPayload {
    public static final Type<MessageConfiguration> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "configuration"));

    public static final StreamCodec<FriendlyByteBuf, MessageConfiguration> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageConfiguration::new
    );

    private final CompoundTag playerDamageModel;

    public MessageConfiguration(CompoundTag model) {
        this.playerDamageModel = model;
    }

    public MessageConfiguration(FriendlyByteBuf buffer) {
        this(buffer.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(playerDamageModel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageConfiguration message, final IPayloadContext context) {
        FirstAid.LOGGER.info(LoggingMarkers.NETWORK, "Received remote damage model");
        context.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            damageModel.deserializeNBT(Minecraft.getInstance().level.registryAccess(), message.playerDamageModel);
            if (damageModel.hasTutorial)
                EventHandler.TUTORIAL_DONE.add(Minecraft.getInstance().player.getName().getString());
            else
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[First Aid] " + I18n.get("terrafirmaaid.tutorial.hint", ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString())));
            HUDHandler.INSTANCE.ticker = 200;
            FirstAid.isSynced = true;
            FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
        });
    }
}
