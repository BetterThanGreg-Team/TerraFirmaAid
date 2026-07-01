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
package com.betterthangreg.terrafirmaaid.client.tutorial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.client.ClientHooks;
import com.betterthangreg.terrafirmaaid.client.gui.GuiHealthScreen;
import com.betterthangreg.terrafirmaaid.client.util.HealthRenderUtils;
import com.betterthangreg.terrafirmaaid.common.damagesystem.PlayerDamageModel;
import com.betterthangreg.terrafirmaaid.common.network.MessageClientRequest;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class GuiTutorial extends Screen {
    private final GuiHealthScreen parent;
    private final AbstractPlayerDamageModel demoModel;
    private int guiTop;
    private final TutorialAction action;

    @SuppressWarnings("deprecation") // we still need this method
    public GuiTutorial() {
        super(Component.translatable("terrafirmaaid.gui.tutorial"));
        this.demoModel = new PlayerDamageModel();
        this.parent = new GuiHealthScreen(demoModel);
        this.action = new TutorialAction(this);

        this.action.addTextWrapper("terrafirmaaid.tutorial.welcome");
        this.action.addTextWrapper("terrafirmaaid.tutorial.line1");
        this.action.addTextWrapper("terrafirmaaid.tutorial.line2");
        this.action.addActionCallable(guiTutorial -> guiTutorial.demoModel.LEFT_FOOT.damage(4F, null, false));
        this.action.addTextWrapper("terrafirmaaid.tutorial.line3");
        //We need the deprecated version
        this.action.addActionCallable(guiTutorial -> guiTutorial.demoModel.applyMorphine());
        this.action.addTextWrapper("terrafirmaaid.tutorial.line4");
        this.action.addTextWrapper("terrafirmaaid.tutorial.line5");
        this.action.addActionCallable(guiTutorial -> guiTutorial.demoModel.LEFT_FOOT.heal(3F, null, false));
        if (TerraFirmaAidConfig.SERVER.sleepHealPercentage.get() != 0D)
            this.action.addTextWrapper("terrafirmaaid.tutorial.sleephint");
        this.action.addTextWrapper("terrafirmaaid.tutorial.line6");
        this.action.addActionCallable(guiTutorial -> guiTutorial.demoModel.HEAD.damage(16F, null, false));
        this.action.addTextWrapper("terrafirmaaid.tutorial.line7");
        this.action.addTextWrapper("terrafirmaaid.tutorial.line8", ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString());
        this.action.addTextWrapper("terrafirmaaid.tutorial.end");

        this.action.next();
    }

    @Override
    public void init() {
        parent.init(minecraft, this.width, this.height);
        guiTop = parent.guiTop - 30;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (action.hasNext()) GuiTutorial.this.action.next();
            else {
                PacketDistributor.sendToServer(new MessageClientRequest(MessageClientRequest.TypeEnum.TUTORIAL_COMPLETE));
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
                if (damageModel == null) return;
                minecraft.setScreen(new GuiHealthScreen(damageModel));
            }
        }).bounds(parent.guiLeft + GuiHealthScreen.xSize - 34, guiTop + 4, 32, 20).build());
        for (AbstractWidget button : parent.getButtons()) {
            if (button == parent.cancelButton) {
                addRenderableWidget(Button.builder(button.getMessage(), ignored -> {
                    PacketDistributor.sendToServer(new MessageClientRequest(MessageClientRequest.TypeEnum.TUTORIAL_COMPLETE));
                    minecraft.setScreen(null);
                }).bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight()).build());
                continue;
            }
            addRenderableWidget(button);
        }
        parent.getButtons().clear();
    }

    public void drawOffsetString(GuiGraphics guiGraphics, String s, int yOffset) {
        guiGraphics.drawString(minecraft.font, s, parent.guiLeft + 30, guiTop + yOffset, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        parent.render(guiGraphics, mouseX, mouseY, partialTicks);
        stack.popPose();
        guiGraphics.blit(HealthRenderUtils.SHOW_WOUNDS_LOCATION, parent.guiLeft, guiTop, 0, 139, GuiHealthScreen.xSize, 28);
        stack.pushPose();
        this.action.draw(guiGraphics);
        stack.popPose();
        guiGraphics.drawCenteredString(minecraft.font, I18n.get("terrafirmaaid.tutorial.notice"), parent.guiLeft + (GuiHealthScreen.xSize / 2), parent.guiTop + 128, 0xFFFFFF);
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void onClose() {
        GuiHealthScreen.isOpen = false;
    }
}
