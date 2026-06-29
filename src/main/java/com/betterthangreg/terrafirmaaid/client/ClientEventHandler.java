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

package com.betterthangreg.terrafirmaaid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.betterthangreg.terrafirmaaid.FirstAid;
import com.betterthangreg.terrafirmaaid.FirstAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPartHealer;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.healing.ItemHealing;
import com.betterthangreg.terrafirmaaid.client.gui.FirstaidIngameGui;
import com.betterthangreg.terrafirmaaid.client.gui.GuiHealthScreen;
import com.betterthangreg.terrafirmaaid.client.tutorial.GuiTutorial;
import com.betterthangreg.terrafirmaaid.client.util.EventCalendar;
import com.betterthangreg.terrafirmaaid.client.util.PlayerModelRenderer;
import com.betterthangreg.terrafirmaaid.common.AABBAlignedBoundingBox;
import com.betterthangreg.terrafirmaaid.common.RegistryObjects;
import com.betterthangreg.terrafirmaaid.common.util.ArmorUtils;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import com.betterthangreg.terrafirmaaid.common.util.PlayerSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ClientEventHandler {
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");
    private static int id;

    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null || mc.isPaused()) return;
        if (EventCalendar.isGuiFun()) {
            GuiHealthScreen.BED_ITEMSTACK.setDamageValue(id);
            if (mc.level != null && mc.level.getGameTime() % 3 == 0) id++;
            if (id > 15) id = 0;
            GuiHealthScreen.tickFun();
            PlayerModelRenderer.tickFun();
        }
        if (HUDHandler.INSTANCE.ticker >= 0)
            HUDHandler.INSTANCE.ticker--;
    }

    @SubscribeEvent
    public static void onKeyPress(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) return;
            if (!damageModel.hasTutorial) {
                damageModel.hasTutorial = true;
                com.betterthangreg.terrafirmaaid.common.EventHandler.TUTORIAL_DONE.add(mc.player.getName().getString());
                Minecraft.getInstance().setScreen(new GuiTutorial());
            }
            else {
                mc.setScreen(new GuiHealthScreen(damageModel));
            }
        }
    }

    @SubscribeEvent
    public static void preRender(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            FirstAidConfig.Client.VanillaHealthbarMode vanillaHealthBarMode = FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
            if (vanillaHealthBarMode != FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
                event.setCanceled(true);
                if (com.betterthangreg.terrafirmaaid.client.ClientHooks.shouldDrawSurvivalElements() && vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.HIGHLIGHT_CRITICAL_PATH && FirstAidConfig.SERVER.vanillaHealthCalculation.get() == FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
                    FirstaidIngameGui.renderHealth(Minecraft.getInstance().gui, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight(), event.getGuiGraphics());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingRender(RenderLivingEvent.Post<Player, PlayerModel<Player>> event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (renderDispatcher.shouldRenderHitBoxes()) {
                PoseStack poseStack = event.getPoseStack();
                poseStack.pushPose();
                //See PlayerRenderer.getRenderOffset
                if (entity.isCrouching()) {
                    poseStack.translate(0D, 0.125D, 0D);
                }
                AABB aabb = entity.getBoundingBox();


                Collection<AABBAlignedBoundingBox> allBoxes = PlayerSizeHelper.getBoxes(entity).values();
                float r = 0.25F;
                float g = 1.0F;
                float b = 1.0F;

                for (AABBAlignedBoundingBox box : allBoxes) {
                    AABB bbox = box.createAABB(aabb);
                    LevelRenderer.renderLineBox(poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()), bbox.inflate(0.02D).move(-entity.getX(), -entity.getY(), -entity.getZ()), r, g, b, 1.0F);
                    r += 0.25F;
                    g += 0.5F;
                    b += 0.1F;

                    r %= 1.0F;
                    g %= 1.0F;
                    b %= 1.0F;
                }
                poseStack.popPose();
            }
        }
    }

    private static Component makeArmorMsg(double value) {
        return Component.translatable("terrafirmaaid.specificarmor", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static Component makeToughnessMsg(double value) {
        return Component.translatable("terrafirmaaid.specifictoughness", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static <T> void replaceOrAppend(List<T> list, T search, T replace) {
        int index = list.indexOf(search);
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() == FirstAidConfig.Client.TooltipMode.REPLACE && index >= 0) {
            list.set(index, replace);
        } else {
            list.add(replace);
        }
    }


    @SubscribeEvent
    public static void tooltipItems(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (item == RegistryObjects.MORPHINE.get()) {
            event.getToolTip().add(Component.translatable("terrafirmaaid.tooltip.morphine", "3:30-4:30"));
            return;
        }
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() != FirstAidConfig.Client.TooltipMode.NONE) {
            if (item instanceof ArmorItem armor) {
                List<Component> tooltip = event.getToolTip();

                double normalArmor = ArmorUtils.getArmor(stack, armor.getEquipmentSlot());
                double totalArmor = ArmorUtils.applyArmorModifier(armor.getEquipmentSlot(), normalArmor);
                if (totalArmor > 0D) {
                    Component original = Component.translatable("attribute.modifier.plus.0", FORMAT.format(normalArmor), Component.translatable("attribute.name.generic.armor")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeArmorMsg(totalArmor));
                }

                double normalToughness = ArmorUtils.getArmorToughness(stack, armor.getEquipmentSlot());
                double totalToughness = ArmorUtils.applyToughnessModifier(armor.getEquipmentSlot(), normalToughness);
                if (totalToughness > 0D) {
                    Component original = Component.translatable("attribute.modifier.plus.0", FORMAT.format(normalToughness), Component.translatable("attribute.name.generic.armor_toughness")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeToughnessMsg(totalToughness));
                }
            }
        }
        if (item instanceof PotionItem) {
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            Iterable<MobEffectInstance> list = potionContents != null ? potionContents.getAllEffects() : null;
            if (list != null && list.iterator().hasNext()) {
                for (MobEffectInstance potionEffect : list) {
                    if (potionEffect.getEffect().equals(MobEffects.DAMAGE_RESISTANCE)) {
                        MobEffect potion = potionEffect.getEffect().value();
                        potion.createModifiers(potionEffect.getAmplifier(), (holder, realModifier) -> {
                            double d1;

                            if (realModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && realModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                                d1 = realModifier.amount();
                            } else {
                                d1 = realModifier.amount() * 100.0D;
                            }

                            Component raw = (Component.translatable("attribute.modifier.plus." + realModifier.operation().id(), net.minecraft.world.item.component.ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(holder.value().getDescriptionId()))).withStyle(ChatFormatting.BLUE);

                            List<Component> toolTip = event.getToolTip();
                            int index = toolTip.indexOf(raw);
                            if (index != -1) {
                                Component replacement = (Component.translatable("attribute.modifier.plus." + realModifier.operation().id(), net.minecraft.world.item.component.ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1 * ((float) FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get() / 20F)), Component.translatable(holder.value().getDescriptionId()))).withStyle(ChatFormatting.BLUE);
                                toolTip.set(index, replacement);
                            }
                        });
                    }
                }

            }
        }

        if (stack.getItem() instanceof ItemHealing itemHealing) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null && event.getEntity() != null) {
                event.getToolTip().add(Component.translatable("terrafirmaaid.tooltip.healer", healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt(), 20.0F)));
            }
        }
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
    }
}
