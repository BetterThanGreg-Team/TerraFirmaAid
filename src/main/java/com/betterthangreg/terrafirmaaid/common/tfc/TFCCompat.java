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
package com.betterthangreg.terrafirmaaid.common.tfc;

import com.betterthangreg.terrafirmaaid.TerraFirmaAid;
import com.betterthangreg.terrafirmaaid.TerraFirmaAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Runtime TFC (TerraFirmaCraft) compatibility layer.
 * Detects TFC at runtime and adapts the health system accordingly.
 * Uses only reflection-free ModList checks to avoid compile-time dependency.
 */
public class TFCCompat {

    private static final String TFC_MOD_ID = "tfc";
    private static Boolean tfcLoaded;

    /** TFC's health bar overlay texture. */
    public static final ResourceLocation TFC_HEALTH_TEXTURE = ResourceLocation.fromNamespaceAndPath(TFC_MOD_ID, "textures/gui/icons/overlay.png");

    /**
     * @return true if TFC is loaded at runtime
     */
    public static boolean isTFCLoaded() {
        if (tfcLoaded == null) {
            tfcLoaded = ModList.get() != null && ModList.get().isLoaded(TFC_MOD_ID);
            if (tfcLoaded) {
                TerraFirmaAid.LOGGER.info("TerraFirmaCraft detected! Integrating health systems.");
            }
        }
        return tfcLoaded;
    }

    /**
     * @return true if TFC is loaded and the player wants TFC-style health GUI
     */
    public static boolean shouldUseTFCStyleHealthGui() {
        return isTFCLoaded() && TerraFirmaAidConfig.CLIENT.useTFCHealthGui.get();
    }

    /**
     * TFC uses a different HP scale than vanilla.
     * Vanilla: 20 max health = 10 hearts
     * TFC: 20 max health but nutrition-scaling can increase it
     * We keep our limb health system but scale around TFC's effective max health.
     *
     * @return true if we should adapt to TFC health scaling
     */
    public static boolean shouldUseTFCHP() {
        return isTFCLoaded();
    }

    /**
     * Get the TFC-scaled max health for a player.
     * TFC modifies maxHealth attribute based on nutrition.
     * When TFC is present, we use their effective max health as our baseline.
     */
    public static float getTFCMaxHealth(Player player) {
        if (!isTFCLoaded() || player == null) return 20.0F;
        // When TFC is loaded, the player's maxHealth attribute reflects TFC's nutrition-based scaling
        return player.getMaxHealth();
    }

    /**
     * Check if TFC's natural regeneration should be overridden by TerraFirmaAid's config.
     */
    public static boolean shouldOverrideTFCRegen() {
        return isTFCLoaded() && TerraFirmaAidConfig.SERVER.overrideTFCNaturalRegen.get();
    }

    /**
     * Gets the scalar to convert TFC health points to TerraFirmaAid internal health.
     * TFC's max health is typically 20 (scaled by nutrition), 
     * and our limb system sums up the total of all limbs.
     * 
     * When TFC is loaded, we scale limb health proportionally to TFC's effective max.
     */
    public static float getTFCHealthScale(Player player) {
        if (!isTFCLoaded() || player == null) return 1.0F;
        float tfcMax = getTFCMaxHealth(player);
        if (tfcMax <= 0) return 1.0F;
        // TFC's effective max vs vanilla 20
        return tfcMax / 20.0F;
    }

    /**
     * Convert a config value (in half-hearts, i.e., "2 = 1 heart") to 
     * TFC health points when TFC is loaded.
     * This is used in config descriptions and tooltips.
     */
    public static String describeConfigInTFCHP(String vanillaDescription) {
        if (!isTFCLoaded()) return vanillaDescription;
        return vanillaDescription + " (When TFC is present: health values scale with TFC's nutrition-based HP)";
    }
}
