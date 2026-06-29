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
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPartHealer;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.api.healing.ItemHealing;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.LoggingMarkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageApplyHealingItem implements CustomPacketPayload {
    public static final Type<MessageApplyHealingItem> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "apply_healing_item"));

    public static final StreamCodec<FriendlyByteBuf, MessageApplyHealingItem> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageApplyHealingItem::new
    );

    private final EnumPlayerPart part;
    private final InteractionHand hand;

    public MessageApplyHealingItem(FriendlyByteBuf buffer) {
        this.part = EnumPlayerPart.VALUES[buffer.readByte()];
        this.hand = buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) {
        this.part = part;
        this.hand = hand;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(part.ordinal());
        buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageApplyHealingItem message, final IPayloadContext context) {
        final ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            ItemStack stack = player.getItemInHand(message.hand);
            AbstractPartHealer healer = null;
            if (stack.getItem() instanceof ItemHealing itemHealing) {
                healer = itemHealing.createNewHealer(stack);
            }
            if (healer == null) {
                FirstAid.LOGGER.warn(LoggingMarkers.NETWORK, "Player {} has invalid item in hand {} while it should be an healing item", player.getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
                player.sendSystemMessage(Component.literal("Unable to apply healing item!"));
                return;
            }
            stack.shrink(1);
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
            damageablePart.activeHealer = healer;
        });
    }
}
