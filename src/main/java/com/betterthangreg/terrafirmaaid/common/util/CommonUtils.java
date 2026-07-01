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

package com.betterthangreg.terrafirmaaid.common.util;

import com.google.common.primitives.Ints;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.common.SynchedEntityDataWrapper;
import com.betterthangreg.terrafirmaaid.common.damagesystem.distribution.HealthDistribution;
import com.betterthangreg.terrafirmaaid.common.network.MessageSyncDamageModel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.betterthangreg.terrafirmaaid.common.RegistryObjects;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CommonUtils {
    @Nonnull
    public static final EquipmentSlot[] ARMOR_SLOTS;
    @Nonnull
    private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS;

    static {
        ARMOR_SLOTS = new EquipmentSlot[4];
        ARMOR_SLOTS[3] = EquipmentSlot.HEAD;
        ARMOR_SLOTS[2] = EquipmentSlot.CHEST;
        ARMOR_SLOTS[1] = EquipmentSlot.LEGS;
        ARMOR_SLOTS[0] = EquipmentSlot.FEET;
        SLOT_TO_PARTS = new EnumMap<>(EquipmentSlot.class);
        SLOT_TO_PARTS.put(EquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD));
        SLOT_TO_PARTS.put(EquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY));
        SLOT_TO_PARTS.put(EquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG));
        SLOT_TO_PARTS.put(EquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT));
    }

    public static List<EnumPlayerPart> getPartListForSlot(EquipmentSlot slot) {
        return new ArrayList<>(SLOT_TO_PARTS.get(slot));
    }

    public static EnumPlayerPart[] getPartArrayForSlot(EquipmentSlot slot) {
        return getPartListForSlot(slot).toArray(new EnumPlayerPart[0]);
    }

    public static void killPlayer(@Nonnull AbstractPlayerDamageModel damageModel, @Nonnull Player player, @Nullable DamageSource source) {
        if (player.level().isClientSide) {
            try {
                throw new RuntimeException("Tried to kill the player on the client!");
            } catch (RuntimeException e) {
                TerraFirmaAid.LOGGER.warn("Tried to kill the player on the client! This should only happen on the server! Ignoring...", e);
            }
        }
        SynchedEntityDataWrapper wrapper = (SynchedEntityDataWrapper) player.entityData;
        if (source != null && TerraFirmaAidConfig.SERVER.allowOtherHealingItems.get()) {
            boolean protection;
            wrapper.toggleTracking(false);
            try {
                //totem protected the player - make sure he actually isn't dead
                protection = player.checkTotemDeathProtection(source);
            } finally {
                wrapper.toggleTracking(true);
            }
            if (protection) {
                for (AbstractDamageablePart part : damageModel) {
                    if (part.canCauseDeath)
                        part.currentHealth = Math.max(part.currentHealth, 1F);
                }
                if (player instanceof ServerPlayer)
                    PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageSyncDamageModel(player.level().registryAccess(), damageModel, false));
                return;
            }
        }
        wrapper.set_impl(Player.DATA_HEALTH_ID, 0F);
    }

    public static boolean isValidArmorSlot(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Nonnull
    public static String getActiveModidSafe() {
        ModContainer activeModContainer = ModLoadingContext.get().getActiveContainer();
        return activeModContainer == null ? "UNKNOWN-NULL" : activeModContainer.getModId();
    }

    public static void healPlayerByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        int healValue = Ints.checkedCast(Math.round(damageModel.getCurrentMaxHealth() * percentage));
        HealthDistribution.manageHealth(healValue, damageModel, player, true, false);
    }

    public static void debugLogStacktrace(String name) {
        if (!TerraFirmaAidConfig.GENERAL.debug.get()) return;
        try {
            throw new RuntimeException("DEBUG:" + name);
        } catch (RuntimeException e) {
            TerraFirmaAid.LOGGER.info("DEBUG: " + name, e);
        }
    }

    @Nullable
    public static AbstractPlayerDamageModel getDamageModel(Player player) {
        Optional<AbstractPlayerDamageModel> optionalDamageModel = getOptionalDamageModel(player);
        try {
            return optionalDamageModel.orElseThrow(() -> new IllegalArgumentException("Player " + player.getName().getContents() + " is missing a damage model!"));
        } catch (IllegalArgumentException e) {
            // This is a band-aid solution, as bug reports about this keep coming up and these are really hard to debug bugs
            // I don't have the time to correctly debug this, so it seems like there is no other way right now
            if (TerraFirmaAidConfig.GENERAL.debug.get()) {
                TerraFirmaAid.LOGGER.fatal("Mandatory damage model missing!", e);
                throw e;
            } else {
                TerraFirmaAid.LOGGER.error("Missing a damage model, skipping further processing!", e);
                return null;
            }
        }
    }

    @Nonnull
    public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(Player player) {
        if (!hasDamageModel(player)) {
            return Optional.empty();
        }
        return Optional.of(player.getData(RegistryObjects.DAMAGE_MODEL.get()));
    }

    public static boolean hasDamageModel(Entity entity) {
        return entity instanceof Player && !(entity instanceof FakePlayer);
    }


}
