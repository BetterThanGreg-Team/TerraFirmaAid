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

import com.mojang.blaze3d.platform.InputConstants;
import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.client.gui.GuiHealthScreen;
import com.betterthangreg.terrafirmaaid.client.util.EventCalendar;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public class ClientHooks {
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H), "TerraFirmaAid");

    public static void setup(IEventBus modEventBus) {
        TerraFirmaAid.LOGGER.debug("Loading ClientHooks");
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        modEventBus.addListener(ClientHooks::registerKeybindEvent);
        modEventBus.addListener(ClientHooks::registerOverlayEvent);
        modEventBus.addListener(ClientHooks::registerReloadListenerEvent);
        EventCalendar.checkDate();
    }

    public static void showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) return;
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen(GuiHealthScreen.INSTANCE);
    }

    public static void registerKeybindEvent(RegisterKeyMappingsEvent event) {
        event.register(ClientHooks.SHOW_WOUNDS);
    }

    public static void registerOverlayEvent(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath(TerraFirmaAid.MODID, "hud"), HUDHandler.INSTANCE::render);
    }

    public static void registerReloadListenerEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HUDHandler.INSTANCE);
    }
    public static boolean shouldDrawSurvivalElements() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameMode != null && mc.gameMode.canHurtPlayer() && mc.getCameraEntity() instanceof net.minecraft.world.entity.player.Player;
    }
}
