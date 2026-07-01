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
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageUpdatePart implements CustomPacketPayload {
    public static final Type<MessageUpdatePart> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "update_part"));
    
    public static final StreamCodec<FriendlyByteBuf, MessageUpdatePart> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> msg.encode(buf),
        MessageUpdatePart::new
    );

    private final byte id;
    private final int maxHealth;
    private final float absorption;
    private final float currentHealth;

    public MessageUpdatePart(FriendlyByteBuf buf) {
        this.id = buf.readByte();
        this.maxHealth = buf.readInt();
        this.absorption = buf.readFloat();
        this.currentHealth = buf.readFloat();
        validate();
    }

    public MessageUpdatePart(AbstractDamageablePart part) {
        this.id = (byte) part.part.ordinal();
        this.maxHealth = part.getMaxHealth();
        this.absorption = part.getAbsorption();
        this.currentHealth = part.currentHealth;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(id);
        buf.writeInt(maxHealth);
        buf.writeFloat(absorption);
        buf.writeFloat(currentHealth);
        validate();
    }

    private void validate() {
        if (currentHealth < 0)
            throw new RuntimeException("Negative currentHealth!");
        if (absorption < 0)
            throw new RuntimeException("Negative absorption!");
        if (maxHealth < 0)
            throw new RuntimeException("Negative maxHealth!");
        if (EnumPlayerPart.VALUES[id].ordinal() != this.id)
            throw new RuntimeException("Wrong player mapping!");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MessageUpdatePart message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(EnumPlayerPart.VALUES[message.id]);
            damageablePart.setMaxHealth(message.maxHealth);
            damageablePart.setAbsorption(message.absorption);
            damageablePart.currentHealth = message.currentHealth;
        });
    }
}
