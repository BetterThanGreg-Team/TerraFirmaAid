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

package com.betterthangreg.terrafirmaaid.common;

import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.healing.ItemHealing;
import com.betterthangreg.terrafirmaaid.common.damagesystem.PartHealer;
import com.betterthangreg.terrafirmaaid.common.items.ItemMorphine;
import com.betterthangreg.terrafirmaaid.common.potion.TerraFirmaAidPotion;
import com.betterthangreg.terrafirmaaid.common.potion.PotionPoisonPatched;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.betterthangreg.terrafirmaaid.common.damagesystem.PlayerDamageModel;

public class RegistryObjects {
    private static final DeferredRegister<Item> ITEM_REGISTER = DeferredRegister.create(Registries.ITEM, TerraFirmaAid.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENT_REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, TerraFirmaAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, TerraFirmaAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_OVERRIDE_REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, "minecraft");
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TerraFirmaAid.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TerraFirmaAid.MODID);

    public static final DeferredHolder<Item, Item> BANDAGE;
    public static final DeferredHolder<Item, Item> PLASTER;
    public static final DeferredHolder<Item, Item> MORPHINE;

    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT;

    public static final DeferredHolder<MobEffect, MobEffect> MORPHINE_EFFECT;
    public static final DeferredHolder<MobEffect, MobEffect> POISON_PATCHED;

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB;
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerDamageModel>> DAMAGE_MODEL;

    static {
        TerraFirmaAidConfig.Server server = TerraFirmaAidConfig.SERVER;

        // ITEMS
        BANDAGE = ITEM_REGISTER.register("bandage", () -> ItemHealing.create(new Item.Properties().stacksTo(16), stack -> new PartHealer(() -> server.bandage.secondsPerHeal.get() * 20, server.bandage.totalHeals::get, stack), stack -> server.bandage.applyTime.get()));
        PLASTER = ITEM_REGISTER.register("plaster", () -> ItemHealing.create(new Item.Properties().stacksTo(16), stack -> new PartHealer(() -> server.plaster.secondsPerHeal.get() * 20, server.plaster.totalHeals::get, stack), stack -> server.plaster.applyTime.get()));
        MORPHINE = ITEM_REGISTER.register("morphine", ItemMorphine::new);

        // SOUNDS
        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "debuff.heartbeat");
        HEARTBEAT = SOUND_EVENT_REGISTER.register(soundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(soundLocation));

        // MOB EFFECTS
        MORPHINE_EFFECT = MOB_EFFECT_REGISTER.register("morphine", () -> new TerraFirmaAidPotion(MobEffectCategory.BENEFICIAL, 0xDDD));
        POISON_PATCHED = MOB_EFFECT_OVERRIDE_REGISTER.register("poison", () -> new PotionPoisonPatched(MobEffectCategory.HARMFUL, 5149489));

        CREATIVE_TAB = CREATIVE_MODE_TAB_REGISTER.register("main_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.terrafirmaaid"))
                .icon(() -> new ItemStack(BANDAGE.get()))
                .build());

        // ATTACHMENTS
        DAMAGE_MODEL = ATTACHMENT_TYPES.register("damage_model", () ->
                AttachmentType.serializable(PlayerDamageModel::new).copyOnDeath().build());
    }


    public static void registerToBus(IEventBus bus) {
        ITEM_REGISTER.register(bus);
        SOUND_EVENT_REGISTER.register(bus);
        MOB_EFFECT_REGISTER.register(bus);
        MOB_EFFECT_OVERRIDE_REGISTER.register(bus);
        CREATIVE_MODE_TAB_REGISTER.register(bus);
        ATTACHMENT_TYPES.register(bus);
    }
}
