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

package com.betterthangreg.terrafirmaaid.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.client.gui.FlashStateManager;
import com.betterthangreg.terrafirmaaid.client.gui.GuiHealthScreen;
import com.betterthangreg.terrafirmaaid.client.util.HealthRenderUtils;
import com.betterthangreg.terrafirmaaid.client.util.PlayerModelRenderer;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class HUDHandler implements ResourceManagerReloadListener {
    public static final HUDHandler INSTANCE = new HUDHandler();
    private static final int FADE_TIME = 30;
    private final Map<EnumPlayerPart, String> TRANSLATION_MAP = new EnumMap<>(EnumPlayerPart.class);
    private final FlashStateManager flashStateManager = new FlashStateManager();
    private int maxLength;
    public int ticker = -1;

    @Override
    public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
        buildTranslationTable();
    }

    private synchronized void buildTranslationTable() {
        TerraFirmaAid.LOGGER.debug("Building GUI translation table");
        TRANSLATION_MAP.clear();
        maxLength = 0;
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.get("terrafirmaaid.gui." + part.toString().toLowerCase(Locale.ENGLISH));
            maxLength = Math.max(maxLength, Minecraft.getInstance().font.width(translated));
            TRANSLATION_MAP.put(part, translated);
        }
    }

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (TerraFirmaAidConfig.CLIENT.overlayMode.get() == TerraFirmaAidConfig.Client.OverlayMode.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) return;
        mc.getProfiler().push("TerraFirmaAidOverlay");
        doRenderOverlay(guiGraphics, mc, deltaTracker.getGameTimeDeltaTicks());
        mc.getProfiler().pop();
        mc.getProfiler().pop();
    }

    private void doRenderOverlay(GuiGraphics guiGraphics, Minecraft mc, float partialTicks) {
        mc.getProfiler().push("prepare");

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) return;
        if (!TerraFirmaAid.isSynced) //Wait until we receive the remote model
            return;

        if (TRANSLATION_MAP.isEmpty()) buildTranslationTable(); //just to make sure

        int visibleTicks = TerraFirmaAidConfig.CLIENT.visibleDurationTicks.get();
        if (visibleTicks != -1) visibleTicks += FADE_TIME;
        boolean playerDead = damageModel.isDead(mc.player);
        for (AbstractDamageablePart damageablePart : damageModel) {
            if (HealthRenderUtils.healthChanged(damageablePart, playerDead)) { //Always call healthChanged, it affects the GUI as well
                if (visibleTicks != -1)
                    ticker = Math.max(ticker, visibleTicks);
                if (TerraFirmaAidConfig.CLIENT.flash.get()) {
                    flashStateManager.setActive(Util.getMillis());
                }
            }
        }

        TerraFirmaAidConfig.Client.OverlayMode overlayMode = TerraFirmaAidConfig.CLIENT.overlayMode.get();
        if (overlayMode == TerraFirmaAidConfig.Client.OverlayMode.OFF || (GuiHealthScreen.isOpen && !overlayMode.isPlayerModel()) || !com.betterthangreg.terrafirmaaid.client.ClientHooks.shouldDrawSurvivalElements())
            return;

        if (visibleTicks != -1 && ticker < 0)
            return;

        RenderSystem.setShaderTexture(0, HealthRenderUtils.GUI_ICONS_LOCATION);
        int xOffset = TerraFirmaAidConfig.CLIENT.xOffset.get();
        int yOffset = TerraFirmaAidConfig.CLIENT.yOffset.get();
        boolean playerModel = overlayMode.isPlayerModel();
        switch (TerraFirmaAidConfig.CLIENT.pos.get()) {
            case TOP_LEFT:
                if (playerModel)
                    xOffset += 1;
                break;
            case TOP_RIGHT:
                xOffset = mc.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + (maxLength));
                break;
            case BOTTOM_LEFT:
                if (playerModel)
                    xOffset += 1;
                yOffset = mc.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
                break;
            case BOTTOM_RIGHT:
                xOffset = mc.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + (maxLength));
                yOffset = mc.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 62 : 80);
                break;
            default:
                throw new RuntimeException("Invalid config option for position: " + TerraFirmaAidConfig.CLIENT.pos.get());
        }

        if (mc.screen instanceof ChatScreen && TerraFirmaAidConfig.CLIENT.pos.get() == TerraFirmaAidConfig.Client.Position.BOTTOM_LEFT)
            return;
        if (mc.getDebugOverlay().showDebugScreen() && TerraFirmaAidConfig.CLIENT.pos.get() == TerraFirmaAidConfig.Client.Position.TOP_LEFT)
            return;

        boolean enableAlphaBlend = visibleTicks != -1 && ticker < FADE_TIME;
        int alpha = enableAlphaBlend ? Mth.clamp((int)((FADE_TIME - ticker) * 255.0F / (float) FADE_TIME), TerraFirmaAidConfig.CLIENT.alpha.get(), 250) : TerraFirmaAidConfig.CLIENT.alpha.get();

        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.translate(xOffset, yOffset, 0F);
        if (enableAlphaBlend) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }
        mc.getProfiler().popPush("render");
        if (overlayMode.isPlayerModel()) {
            boolean fourColors = overlayMode == TerraFirmaAidConfig.Client.OverlayMode.PLAYER_MODEL_4_COLORS;
            PlayerModelRenderer.renderPlayerHealth(stack, damageModel, fourColors, guiGraphics, flashStateManager.update(Util.getMillis()), alpha, partialTicks);
        } else {
            int xTranslation = maxLength;
            for (AbstractDamageablePart part : damageModel) {
                guiGraphics.drawString(mc.font, TRANSLATION_MAP.get(part.part), 0, 0, 0xFFFFFF - (alpha << 24 & -0xFFFFFF));
                if (TerraFirmaAidConfig.CLIENT.overlayMode.get() == TerraFirmaAidConfig.Client.OverlayMode.NUMBERS) {
                    HealthRenderUtils.drawHealthString(guiGraphics, mc.font, part, xTranslation, 0, false);
                } else {
                    HealthRenderUtils.drawHealth(guiGraphics, mc.font, part, xTranslation, 0, false);
                }
                stack.translate(0, 10F, 0F);
            }
        }
        if (enableAlphaBlend)
            RenderSystem.disableBlend();
        stack.popPose();
    }
}
