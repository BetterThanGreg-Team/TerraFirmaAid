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

package com.betterthangreg.terrafirmaaid.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.client.gui.FlashStateManager;
import com.betterthangreg.terrafirmaaid.common.EventHandler;
import com.betterthangreg.terrafirmaaid.common.tfc.TFCCompat;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Gui;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;

public class HealthRenderUtils {
    public static final ResourceLocation SHOW_WOUNDS_LOCATION = ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "textures/gui/show_wounds.png");
    public static final ResourceLocation GUI_ICONS_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");
    public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
    private static final Object2IntOpenHashMap<EnumPlayerPart> prevHealth = new Object2IntOpenHashMap<>();
    private static final EnumMap<EnumPlayerPart, FlashStateManager> flashStates = new EnumMap<>(EnumPlayerPart.class);

    static {
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            flashStates.put(part, new FlashStateManager());
        }
    }

    public static void drawHealthString(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        float absorption = damageablePart.getAbsorption();
        String text = TEXT_FORMAT.format(damageablePart.currentHealth) + "/" + damageablePart.getMaxHealth();
        if (absorption > 0) {
            String line2 = "+ " + TEXT_FORMAT.format(absorption);
            if (allowSecondLine) {
                guiGraphics.drawString(font, line2, xTranslation, yTranslation + 5, 0xFFFFFF);
                yTranslation -= 5;
            } else {
                text += " " + line2;
            }
        }
        guiGraphics.drawString(font, text, xTranslation, yTranslation, 0xFFFFFF);
    }

    private static void updatePrev(EnumPlayerPart part, int current, boolean playerDead) {
        if (!playerDead)
            prevHealth.put(part, current);
        else
            prevHealth.clear();
    }

    public static boolean healthChanged(AbstractDamageablePart damageablePart, boolean playerDead) {
        int current = (int) Math.ceil(damageablePart.currentHealth);
        FlashStateManager activeFlashState = Objects.requireNonNull(flashStates.get(damageablePart.part));
        if (prevHealth.containsKey(damageablePart.part)) {
            int prev = prevHealth.getInt(damageablePart.part);
            updatePrev(damageablePart.part, current, playerDead);
            if (prev != current) {
                activeFlashState.setActive(Util.getMillis());
                return true;
            }
            return false;
        }
        activeFlashState.setActive(Util.getMillis());
        updatePrev(damageablePart.part, current, playerDead);
        return true;
    }

    public static boolean drawAsString(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
        return (maxHealth + maxExtraHealth > 8 && allowSecondLine) || ((maxHealth + maxExtraHealth) > 12);
    }

    public static void drawHealth(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        // TFC-style horizontal bars when TFC is loaded and the config option is enabled
        if (TFCCompat.shouldUseTFCStyleHealthGui()) {
            drawHealthTFCStyle(guiGraphics, font, damageablePart, xTranslation, yTranslation);
            return;
        }

        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
        int current = (int) Math.ceil(damageablePart.currentHealth);
        FlashStateManager activeFlashState = Objects.requireNonNull(flashStates.get(damageablePart.part));

        if (drawAsString(damageablePart, allowSecondLine)) {
            drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
            return;
        }

        boolean hardcore = Minecraft.getInstance().level.getLevelData().isHardcore() || damageablePart.canCauseDeath;
        int absorption = (int) Math.ceil(damageablePart.getAbsorption());
        boolean highlight = activeFlashState.update(Util.getMillis());

        Minecraft mc = Minecraft.getInstance();
        PoseStack stack = guiGraphics.pose();
        int regen = -1;
        if (TerraFirmaAidConfig.SERVER.allowOtherHealingItems.get() && mc.player.hasEffect(MobEffects.REGENERATION))
            regen = (int) ((mc.gui.getGuiTicks() / 2) % 15);
        boolean low = (current + absorption) < 1.25F;

        stack.pushPose();
        stack.translate(xTranslation, yTranslation, 0);
        boolean drawSecondLine = allowSecondLine;
        if (allowSecondLine) drawSecondLine = (maxHealth + maxExtraHealth) > 4;

        if (drawSecondLine) {
            int maxHealth2 = 0;
            if (maxHealth > 4) {
                maxHealth2 = maxHealth - 4;
                maxHealth = 4;
            }

            int maxExtraHealth2 = Math.max(0, maxExtraHealth - (4 - maxHealth));
            maxExtraHealth -= maxExtraHealth2;

            int current2 = 0;
            if (current > 8) {
                current2 = current - 8;
                current = 8;
            }

            int absorption2 = absorption - maxExtraHealth * 2;
            absorption -= absorption2;

            stack.translate(0F, 5F, 0F);
            stack.pushPose();
            renderLine(stack, regen, low, hardcore, maxHealth2, maxExtraHealth2, current2, absorption2, guiGraphics, highlight);
            regen -= (maxHealth2 + maxExtraHealth);
            stack.popPose();
            stack.translate(0F, -10F, 0F);
        }
        renderLine(stack, regen, low, hardcore, maxHealth, maxExtraHealth, current, absorption, guiGraphics, highlight);

        stack.popPose();
    }

    private static void renderLine(PoseStack stack, int regen, boolean low, boolean hardcore, int maxHealth, int maxExtraHearts, int current, int absorption, GuiGraphics guiGraphics, boolean highlight) {
        stack.pushPose();
        int[] lowOffsets = new int[maxHealth + maxExtraHearts];
        if (low) {
            for (int i = 0; i < lowOffsets.length; i++) {
                lowOffsets[i] = EventHandler.RAND.nextInt(2);
            }
        }

        renderMax(regen, lowOffsets, maxHealth, hardcore, guiGraphics, highlight);
        if (maxExtraHearts > 0) { //for absorption
            if (maxHealth != 0) {
                stack.translate(2 + 9 * maxHealth, 0, 0);
            }
            renderMax(regen - maxHealth, lowOffsets, maxExtraHearts, hardcore, guiGraphics, false); //Do not highlight absorption
        }
        stack.popPose();
        stack.translate(0, 0, 1);

        renderCurrentHealth(regen, lowOffsets, current, hardcore, guiGraphics);

        if (absorption > 0) {
            int offset = maxHealth * 9 + (maxHealth == 0 ? 0 : 2);
            stack.translate(offset, 0, 0);
            renderAbsorption(regen - maxHealth, lowOffsets, absorption, hardcore, guiGraphics);
        }
    }

    public static int getMaxHearts(float value) {
        int maxCurrentHearts = (int) Math.ceil(value);
        if (maxCurrentHearts % 2 != 0)
            maxCurrentHearts++;
        return maxCurrentHearts >> 1;
    }

    private static void renderMax(int regen, int[] lowOffsets, int max, boolean hardcore, GuiGraphics guiGraphics, boolean highlight) {
        for (int i = 0; i < max; i++) {
            int x = (int) (9F * i);
            int y = (i == regen ? -2 : 0) - lowOffsets[i];
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(Gui.HeartType.CONTAINER.getSprite(hardcore, false, highlight), x, y, 9, 9);
            RenderSystem.disableBlend();
        }
    }

    private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, boolean hardcore, GuiGraphics guiGraphics) {
        boolean renderLastHalf = false;
        int render = current >> 1;
        if (current % 2 != 0) {
            renderLastHalf = true;
            render++;
        }
        Minecraft mc = Minecraft.getInstance();
        Gui.HeartType heartType = mc.player.hasEffect(MobEffects.POISON) ? Gui.HeartType.POISIONED : (mc.player.hasEffect(MobEffects.WITHER) ? Gui.HeartType.WITHERED : (mc.player.isFullyFrozen() ? Gui.HeartType.FROZEN : Gui.HeartType.NORMAL));
        for (int i = 0; i < render; i++) {
            int x = (int) (9F * i);
            int y = (i == regen ? -2 : 0) - lowOffsets[i];
            boolean renderHalf = renderLastHalf && i + 1 == render;
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(heartType.getSprite(hardcore, renderHalf, false), x, y, 9, 9);
            RenderSystem.disableBlend();
        }
    }

    private static void renderAbsorption(int regen, int[] lowOffsets, int absorption, boolean hardcore, GuiGraphics guiGraphics) {
        boolean renderLastHalf = false;
        int render = absorption >> 1;
        if (absorption % 2 != 0) {
            renderLastHalf = true;
            render++;
        }
        if (render <= 0) return;
        for (int i = 0; i < render; i++) {
            int x = (int) (9F * i);
            int y = (i == regen ? -2 : 0) - lowOffsets[i];
            boolean renderHalf = renderLastHalf && i + 1 == render;
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(Gui.HeartType.ABSORBING.getSprite(hardcore, renderHalf, false), x, y, 9, 9);
            RenderSystem.disableBlend();
        }
    }

    /** Render a TFC-style horizontal health bar for a single body part. */
    private static void drawHealthTFCStyle(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int x, int y) {
        float current = damageablePart.currentHealth;
        float max = damageablePart.getMaxHealth();
        float absorption = damageablePart.getAbsorption();
        if (max <= 0) max = 1;

        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.translate(x, y, 0);

        int barWidth = 81;
        int barHeight = 9;

        // Container background
        guiGraphics.blit(TFCCompat.TFC_HEALTH_TEXTURE, 0, 0, 0, 0, barWidth, barHeight);

        // Health fill
        float percent = Math.min(current / max, 1.0F);
        if (percent > 0) {
            guiGraphics.blit(TFCCompat.TFC_HEALTH_TEXTURE, 0, 0, 0, 9, (int) (barWidth * percent), barHeight);
        }

        // Hurt flash (when entity was recently hurt)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hurtTime > 0) {
            guiGraphics.blit(TFCCompat.TFC_HEALTH_TEXTURE, 0, 0, 0, 29, barWidth, barHeight);
        }

        // Absorption surplus
        float surplus = Math.min((current + absorption) / max - 1.0F, 1.0F);
        if (surplus > 0) {
            guiGraphics.blit(TFCCompat.TFC_HEALTH_TEXTURE, 0, 0, 90, 9, (int) (barWidth * surplus), barHeight);
        }

        // Health text
        String text = String.format("%.0f/%.0f", current * 50, max * 50);
        int textX = barWidth / 2 - font.width(text) / 2;
        int textY = 1;
        guiGraphics.drawString(font, text, textX + 1, textY + 1, 0x680000, false);
        guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, false);

        stack.popPose();
    }
}
