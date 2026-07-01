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

package com.betterthangreg.terrafirmaaid.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.client.util.HealthRenderUtils;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.gui.Gui;

public class TerraFirmaAidIngameGui {

    private static int lastProcessedActualHealth = -1;

    // Copy of ForgeGui#renderHealth, modified to fit being called from an event listener and to support different textures for different parts of the texture
    public static void renderHealth(Gui gui, int width, int height, GuiGraphics guiGraphics) {
        PoseStack stack = guiGraphics.pose();

        // Firstaid: No pre event, we get called from this
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getProfiler().push("health");
        // Firstaid: calculate criticalDamage
        AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel(minecraft.player).orElse(null);
        int criticalHalfHearts = 0;
        if (damageModel != null) {
            float minCriticalHealth = Float.MAX_VALUE;
            for (AbstractDamageablePart part : damageModel) {
                if (part.canCauseDeath) {
                    minCriticalHealth = Math.min(minCriticalHealth, part.currentHealth);
                }
            }
            if (minCriticalHealth != Float.MAX_VALUE) {
                criticalHalfHearts = Mth.ceil(minCriticalHealth);
            }
        }
        RenderSystem.enableBlend();

        Player player = (Player) minecraft.getCameraEntity();
        int health = Mth.ceil(player.getHealth());
        boolean highlight = gui.healthBlinkTime > (long) gui.tickCount && (gui.healthBlinkTime - (long) gui.tickCount) / 3L % 2L == 1L;

        if (lastProcessedActualHealth == -1) {
            lastProcessedActualHealth = health;
            gui.lastHealth = health;
            gui.displayHealth = health;
        }

        if (health < lastProcessedActualHealth)
        {
            gui.lastHealthTime = Util.getMillis();
            gui.healthBlinkTime = (long)(gui.tickCount + 20);
        }
        else if (health > lastProcessedActualHealth)
        {
            gui.lastHealthTime = Util.getMillis();
            gui.healthBlinkTime = (long)(gui.tickCount + 10);
        }
        lastProcessedActualHealth = health;

        if (Util.getMillis() - gui.lastHealthTime > 1000L)
        {
            gui.lastHealth = health;
            gui.displayHealth = health;
            gui.lastHealthTime = Util.getMillis();
        }

        gui.lastHealth = health;
        int healthLast = gui.displayHealth;

        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), Math.max(healthLast, health));
        int absorb = Mth.ceil(player.getAbsorptionAmount());

        int healthRows = Mth.ceil((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        gui.random.setSeed((long)(gui.tickCount * 312871));

        int left = width / 2 - 91;
        int top = height - gui.leftHeight;
        gui.leftHeight += (healthRows * rowHeight);
        if (rowHeight != 10) gui.leftHeight += 10 - rowHeight;

        int regen = -1;
        if (player.hasEffect(MobEffects.REGENERATION))
        {
            regen = gui.tickCount % Mth.ceil(healthMax + 5.0F);
        }

        final int BACKGROUND = (highlight ? 25 : 16);
        int MARGIN = 16;
        if (player.hasEffect(MobEffects.POISON))      MARGIN += 36;
        else if (player.hasEffect(MobEffects.WITHER)) MARGIN += 72;
        float absorbRemaining = absorb;
        boolean hardcore = player.level().getLevelData().isHardcore();
        Gui.HeartType heartType = player.hasEffect(MobEffects.POISON) ? Gui.HeartType.POISIONED : (player.hasEffect(MobEffects.WITHER) ? Gui.HeartType.WITHERED : (player.isFullyFrozen() ? Gui.HeartType.FROZEN : Gui.HeartType.NORMAL));

        for (int i = Mth.ceil((healthMax + absorb) / 2.0F) - 1; i >= 0; --i)
        {
            boolean thisHalfCritical = (i * 2) + 1 == criticalHalfHearts;
            int row = Mth.ceil((float)(i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            if (health <= 4) y += gui.random.nextInt(2);
            if (i == regen) y -= 2;

            boolean containerBlinking = highlight;
            boolean heartHardcore = hardcore || (i * 2 < criticalHalfHearts);
            guiGraphics.blitSprite(Gui.HeartType.CONTAINER.getSprite(heartHardcore, false, containerBlinking), x, y, 9, 9);

            if (highlight)
            {
                if (thisHalfCritical) {
                    stack.pushPose();
                    stack.translate(0.0F, 0.0F, 1000.0F);
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, false, true), x, y, 9, 9);
                    stack.popPose();
                } else if (i * 2 + 1 < healthLast) {
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, false, true), x, y, 9, 9);
                } else if (i * 2 + 1 == healthLast) {
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, false, true), x, y, 9, 9);
                }
            }

            if (absorbRemaining > 0.0F)
            {
                boolean halfAbsorption = (absorbRemaining == 1.0F) || (absorbRemaining == absorb && absorb % 2.0F == 1.0F);
                guiGraphics.blitSprite(Gui.HeartType.ABSORBING.getSprite(heartHardcore, halfAbsorption, false), x, y, 9, 9);
                absorbRemaining -= (halfAbsorption ? 1.0F : 2.0F);
            }
            else
            {
                if (thisHalfCritical) {
                    stack.pushPose();
                    stack.translate(0.0F, 0.0F, 10.0F);
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, false, true), x, y, 9, 9);
                    stack.popPose();
                } else if (i * 2 + 1 < health) {
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, false, false), x, y, 9, 9);
                } else if (i * 2 + 1 == health) {
                    guiGraphics.blitSprite(heartType.getSprite(heartHardcore, true, false), x, y, 9, 9);
                }
            }
        }

        RenderSystem.disableBlend();
        minecraft.getProfiler().pop();
    }
}
