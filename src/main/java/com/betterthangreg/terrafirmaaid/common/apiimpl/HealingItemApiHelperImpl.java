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

package com.betterthangreg.terrafirmaaid.common.apiimpl;

import com.betterthangreg.terrafirmaaid.api.healing.HealingItemApiHelper;
import com.betterthangreg.terrafirmaaid.api.healing.ItemHealing;
import com.betterthangreg.terrafirmaaid.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nonnull;

public class HealingItemApiHelperImpl extends HealingItemApiHelper {
    private static final HealingItemApiHelperImpl INSTANCE = new HealingItemApiHelperImpl();

    public static void init() {
        HealingItemApiHelper.setImpl(INSTANCE);
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> onItemRightClick(ItemHealing itemHealing, Level world, Player player, InteractionHand hand) {
        if (world.isClientSide) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHooks.showGuiApplyHealth(hand);
            }
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }
}
