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

package com.betterthangreg.terrafirmaaid;

import com.betterthangreg.terrafirmaaid.client.ClientHooks;
import com.betterthangreg.terrafirmaaid.common.EventHandler;
import com.betterthangreg.terrafirmaaid.common.RegistryObjects;
import com.betterthangreg.terrafirmaaid.common.apiimpl.HealingItemApiHelperImpl;
import com.betterthangreg.terrafirmaaid.common.network.*;
import com.betterthangreg.terrafirmaaid.common.registries.TerraFirmaAidRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TerraFirmaAid.MODID)
public class TerraFirmaAid {
    public static final String MODID = "terrafirmaaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static boolean isSynced = false;

    public TerraFirmaAid(IEventBus bus, ModContainer container) {
        NeoForge.EVENT_BUS.register(EventHandler.class);
        bus.addListener(this::init);
        bus.addListener(this::registerCreativeTab);
        bus.addListener(this::registerPayloads);
        
        RegistryObjects.registerToBus(bus);
        TerraFirmaAidRegistries.setup(bus);

        container.registerConfig(ModConfig.Type.SERVER, TerraFirmaAidConfig.serverSpec);
        container.registerConfig(ModConfig.Type.COMMON, TerraFirmaAidConfig.generalSpec);
        container.registerConfig(ModConfig.Type.CLIENT, TerraFirmaAidConfig.clientSpec);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHooks.setup(bus);
        }
        
        //Setup API
        HealingItemApiHelperImpl.init();
    }

    private void registerCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(RegistryObjects.CREATIVE_TAB.getKey())) {
            event.accept(RegistryObjects.BANDAGE.get());
            event.accept(RegistryObjects.PLASTER.get());
            event.accept(RegistryObjects.MORPHINE.get());
        }
    }

    public void init(FMLCommonSetupEvent event) {
        LOGGER.info("{} starting...", MODID);
        if (TerraFirmaAidConfig.GENERAL.debug.get()) {
            LOGGER.warn("DEBUG MODE ENABLED");
            LOGGER.warn("TerraFirmaAid may be slower than usual and will produce much noisier logs if debug mode is enabled");
            LOGGER.warn("Disable debug in terrafirmaaid config");
        }
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Server -> Client payloads
        registrar.playToClient(
            MessageUpdatePart.TYPE,
            MessageUpdatePart.STREAM_CODEC,
            MessageUpdatePart::handle
        );
        registrar.playToClient(
            MessageApplyAbsorption.TYPE,
            MessageApplyAbsorption.STREAM_CODEC,
            MessageApplyAbsorption::handle
        );
        registrar.playToClient(
            MessageAddHealth.TYPE,
            MessageAddHealth.STREAM_CODEC,
            MessageAddHealth::handle
        );
        registrar.playToClient(
            MessagePlayHurtSound.TYPE,
            MessagePlayHurtSound.STREAM_CODEC,
            MessagePlayHurtSound::handle
        );
        registrar.playToClient(
            MessageConfiguration.TYPE,
            MessageConfiguration.STREAM_CODEC,
            MessageConfiguration::handle
        );
        registrar.playToClient(
            MessageSyncDamageModel.TYPE,
            MessageSyncDamageModel.STREAM_CODEC,
            MessageSyncDamageModel::handle
        );

        // Client -> Server payloads
        registrar.playToServer(
            MessageApplyHealingItem.TYPE,
            MessageApplyHealingItem.STREAM_CODEC,
            MessageApplyHealingItem::handle
        );
        registrar.playToServer(
            MessageClientRequest.TYPE,
            MessageClientRequest.STREAM_CODEC,
            MessageClientRequest::handle
        );
    }
}
