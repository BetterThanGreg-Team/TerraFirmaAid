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

package com.betterthangreg.terrafirmaaid.common.damagesystem;

import com.betterthangreg.terrafirmaaid.FirstAid;
import com.betterthangreg.terrafirmaaid.FirstAidConfig;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractDamageablePart;
import com.betterthangreg.terrafirmaaid.api.damagesystem.AbstractPlayerDamageModel;
import com.betterthangreg.terrafirmaaid.api.debuff.IDebuff;
import com.betterthangreg.terrafirmaaid.api.enums.EnumDebuffSlot;
import com.betterthangreg.terrafirmaaid.api.enums.EnumPlayerPart;
import com.betterthangreg.terrafirmaaid.client.util.HealthRenderUtils;
import com.betterthangreg.terrafirmaaid.common.EventHandler;
import com.betterthangreg.terrafirmaaid.common.RegistryObjects;
import com.betterthangreg.terrafirmaaid.common.SynchedEntityDataWrapper;
import com.betterthangreg.terrafirmaaid.common.damagesystem.debuff.SharedDebuff;
import com.betterthangreg.terrafirmaaid.common.network.MessageSyncDamageModel;
import com.betterthangreg.terrafirmaaid.common.registries.FirstAidRegistryLookups;
import com.betterthangreg.terrafirmaaid.common.registries.LookupReloadListener;
import com.betterthangreg.terrafirmaaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class PlayerDamageModel extends AbstractPlayerDamageModel implements LookupReloadListener {
    private final Set<SharedDebuff> sharedDebuffs = new HashSet<>();
    private int morphineTicksLeft = 0;
    private int sleepBlockTicks = 0;
    private float prevHealthCurrent = -1F;
    private float prevScaleFactor;
    private final boolean noCritical;
    private boolean needsMorphineUpdate = false;
    private int resyncTimer = -1;

    public PlayerDamageModel() {
        super(new DamageablePart(FirstAidConfig.SERVER.maxHealthHead.get(),      FirstAidConfig.SERVER.causeDeathHead.get(),  EnumPlayerPart.HEAD),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftArm.get(),   false,                         EnumPlayerPart.LEFT_ARM),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftLeg.get(),   false,                         EnumPlayerPart.LEFT_LEG),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftFoot.get(),  false,                         EnumPlayerPart.LEFT_FOOT),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthBody.get(),      FirstAidConfig.SERVER.causeDeathBody.get(),  EnumPlayerPart.BODY),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightArm.get(),  false,                         EnumPlayerPart.RIGHT_ARM),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightLeg.get(),  false,                         EnumPlayerPart.RIGHT_LEG),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightFoot.get(), false,                         EnumPlayerPart.RIGHT_FOOT));
        noCritical = !FirstAidConfig.SERVER.causeDeathBody.get() && !FirstAidConfig.SERVER.causeDeathHead.get();
        FirstAidRegistryLookups.registerReloadListener(this);
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tagCompound = new CompoundTag();
        tagCompound.put("head", HEAD.serializeNBT(provider));
        tagCompound.put("leftArm", LEFT_ARM.serializeNBT(provider));
        tagCompound.put("leftLeg", LEFT_LEG.serializeNBT(provider));
        tagCompound.put("leftFoot", LEFT_FOOT.serializeNBT(provider));
        tagCompound.put("body", BODY.serializeNBT(provider));
        tagCompound.put("rightArm", RIGHT_ARM.serializeNBT(provider));
        tagCompound.put("rightLeg", RIGHT_LEG.serializeNBT(provider));
        tagCompound.put("rightFoot", RIGHT_FOOT.serializeNBT(provider));
        tagCompound.putBoolean("hasTutorial", hasTutorial);
        return tagCompound;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag nbt) {
        HEAD.deserializeNBT(provider, nbt.getCompound("head"));
        LEFT_ARM.deserializeNBT(provider, nbt.getCompound("leftArm"));
        LEFT_LEG.deserializeNBT(provider, nbt.getCompound("leftLeg"));
        LEFT_FOOT.deserializeNBT(provider, nbt.getCompound("leftFoot"));
        BODY.deserializeNBT(provider, nbt.getCompound("body"));
        RIGHT_ARM.deserializeNBT(provider, nbt.getCompound("rightArm"));
        RIGHT_LEG.deserializeNBT(provider, nbt.getCompound("rightLeg"));
        RIGHT_FOOT.deserializeNBT(provider, nbt.getCompound("rightFoot"));
        hasTutorial = nbt.getBoolean("hasTutorial");
        if (nbt.contains("morphineTicks")) { //legacy - we still have to read it
            morphineTicksLeft = nbt.getInt("morphineTicks");
            needsMorphineUpdate = true;
        }
        if (nbt.contains("hasTutorial")) {
            hasTutorial = nbt.getBoolean("hasTutorial");
        }
    }

    @Override
    public void onLookupsReloaded() {
        FirstAid.LOGGER.debug("Reloaded lookups");
        sharedDebuffs.clear();
        for (EnumDebuffSlot debuffSlot : EnumDebuffSlot.values()) {
            IDebuff[] debuffs = FirstAidRegistryLookups.getDebuffs(debuffSlot);
            for (EnumPlayerPart playerPart : debuffSlot.playerParts) {
                getFromEnum(playerPart).loadDebuffInfo(debuffs);
            }
            for (IDebuff debuff : debuffs) {
                if (debuff instanceof SharedDebuff sharedDebuff) {
                    sharedDebuffs.add(sharedDebuff);
                }
            }
        }
    }

    @Override
    public void tick(Level world, Player player) {
        if (isDead(player))
            return;
        world.getProfiler().push("FirstAidPlayerModel");
        if (sleepBlockTicks > 0)
            sleepBlockTicks--;
        else if (sleepBlockTicks < 0)
            throw new RuntimeException("Negative sleepBlockTicks " + sleepBlockTicks);

        float newCurrentHealth = calculateNewCurrentHealth(player);
        if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0F;
        }
        if (newCurrentHealth <= 0F) {
            FirstAid.LOGGER.error("Got {} health left, but isn't marked as dead!", newCurrentHealth);
            world.getProfiler().pop();
            return;
        }
        if (!world.isClientSide && resyncTimer != -1) {
            resyncTimer--;
            if (resyncTimer == 0) {
                resyncTimer = -1;
                PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageSyncDamageModel(player.level().registryAccess(), this, true));
            }
        }

        if (Float.isInfinite(newCurrentHealth)) {
            FirstAid.LOGGER.error("Error calculating current health: Value was infinite"); //Shouldn't happen anymore, but let's be safe
        } else {
            if (newCurrentHealth != prevHealthCurrent)
                ((SynchedEntityDataWrapper) player.entityData).set_impl(Player.DATA_HEALTH_ID, newCurrentHealth);
            prevHealthCurrent = newCurrentHealth;
        }

        if (!this.hasTutorial)
            this.hasTutorial = com.betterthangreg.terrafirmaaid.common.EventHandler.TUTORIAL_DONE.contains(player.getName().getString());

        runScaleLogic(player);

        net.minecraft.core.Holder<MobEffect> morphineEffect = RegistryObjects.MORPHINE_EFFECT;
        //morphine update
        if (this.needsMorphineUpdate) {
            player.addEffect(new MobEffectInstance(morphineEffect, this.morphineTicksLeft, 0, false, false));
        }
        MobEffectInstance morphine = player.getEffect(morphineEffect);
        if (!this.needsMorphineUpdate) {
            this.morphineTicksLeft = morphine == null ? 0 : morphine.getDuration();
        }
        this.needsMorphineUpdate = false;

        //Debuff and part ticking
        world.getProfiler().push("PartDebuffs");
        forEach(part -> part.tick(world, player, morphine == null));
        if (morphine == null && !world.isClientSide)
            sharedDebuffs.forEach(sharedDebuff -> sharedDebuff.tick(player));
        world.getProfiler().pop();
        world.getProfiler().pop();
    }

    public static int getRandMorphineDuration() { //Tweak tooltip event when changing as well
        return ((EventHandler.RAND.nextInt(5) * 20 * 15) + 20 * 210);
    }

    @Deprecated
    @Override
    public void applyMorphine() {
        morphineTicksLeft = getRandMorphineDuration();
        needsMorphineUpdate = true;
    }

    @Override
    public void applyMorphine(Player player) {
        player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, getRandMorphineDuration(), 0, false, false));
    }

    @Deprecated
    @Override
    public int getMorphineTicks() {
        return morphineTicksLeft;
    }

    @Override
    @Nonnull
    public Iterator<AbstractDamageablePart> iterator() {
        return new Iterator<AbstractDamageablePart>() {
            private byte count = 0;
            @Override
            public boolean hasNext() {
                return count < 8;
            }

            @Override
            public AbstractDamageablePart next() {
                if (count >= 8)
                    throw new NoSuchElementException();
                AbstractDamageablePart part = getFromEnum(EnumPlayerPart.VALUES[count]);
                count++;
                return part;
            }
        };
    }

    private float calculateNewCurrentHealth(Player player) {
        float currentHealth = 0;
        FirstAidConfig.Server.VanillaHealthCalculationMode mode = FirstAidConfig.SERVER.vanillaHealthCalculation.get();
        if (noCritical) mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
        switch (mode) {
            case AVERAGE_CRITICAL:
                int maxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    if (part.canCauseDeath) {
                        currentHealth += part.currentHealth;
                        maxHealth += part.getMaxHealth();
                    }
                }
                currentHealth = currentHealth / maxHealth;
                break;
            case MIN_CRITICAL:
                AbstractDamageablePart minimal = null;
                float lowest = Float.MAX_VALUE;
                for (AbstractDamageablePart part : this) {
                    if (part.canCauseDeath) {
                        float partCurrentHealth = part.currentHealth;
                        if (partCurrentHealth < lowest) {
                            minimal = part;
                            lowest = partCurrentHealth;
                        }
                    }
                }
                Objects.requireNonNull(minimal);
                currentHealth = minimal.currentHealth / minimal.getMaxHealth();
                break;
            case AVERAGE_ALL:
                for (AbstractDamageablePart part : this)
                    currentHealth += part.currentHealth;
                currentHealth = currentHealth / getCurrentMaxHealth();
                break;
            case CRITICAL_50_PERCENT_OTHER_50_PERCENT:
                float currentNormal = 0;
                int maxNormal = 0;
                float currentCritical = 0;
                int maxCritical = 0;
                for (AbstractDamageablePart part : this) {
                    if (!part.canCauseDeath) {
                        currentNormal += part.currentHealth;
                        maxNormal += part.getMaxHealth();
                    } else {
                        currentCritical += part.currentHealth;
                        maxCritical += part.getMaxHealth();
                    }
                }
                float avgNormal = currentNormal / maxNormal;
                float avgCritical = currentCritical / maxCritical;
                currentHealth = (avgCritical + avgNormal) / 2;
                break;
            default:
                throw new RuntimeException("Unknown constant " + mode);
        }
        return currentHealth * player.getMaxHealth();
    }

    @Override
    public boolean isDead(@Nullable Player player) {
        boolean bleeding = false;
        if (bleeding) {
            return true; //Technically not dead yet, but we should still return true to avoid running ticking and other logic
        }

        if (player != null && !player.isAlive())
            return true;

        if (this.noCritical) {
            boolean dead = true;
            for (AbstractDamageablePart part : this) {
                if (part.currentHealth > 0) {
                    dead = false;
                    break;
                }
            }
            return dead;
        } else {
            for (AbstractDamageablePart part : this) {
                if (part.canCauseDeath && part.currentHealth <= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Float getAbsorption() { //Float class because of DataManager
        float value = 0;
        for (AbstractDamageablePart part : this)
                value += part.getAbsorption();
        return value; //Autoboxing FTW
    }

    @Override
    public void setAbsorption(float absorption) {
        final float newAbsorption = absorption / 8F;
        forEach(damageablePart -> damageablePart.setAbsorption(newAbsorption));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getMaxRenderSize() {
        int max = 0;
        for (AbstractDamageablePart part : this) {
            int newMax;
            if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS)
                newMax = Minecraft.getInstance().font.width(HealthRenderUtils.TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth()) + 1;
            else
                newMax = (int) (((((int) (part.getMaxHealth() + part.getAbsorption() + 0.9999F)) + 1) / 2F) * 9F);
            max = Math.max(max, newMax);
        }
        return max;
    }

    @Override
    public void sleepHeal(Player player) {
        if (sleepBlockTicks > 0)
            return;
        CommonUtils.healPlayerByPercentage(FirstAidConfig.SERVER.sleepHealPercentage.get(), this, player);
        sleepBlockTicks = 20;
    }

    @Override
    public int getCurrentMaxHealth() {
        int maxHealth = 0;
        for (AbstractDamageablePart part : this) {
            maxHealth += part.getMaxHealth();
        }
        return maxHealth;
    }

    @Override
    public void revivePlayer(Player player) {
        if (FirstAidConfig.GENERAL.debug.get()) {
            CommonUtils.debugLogStacktrace("Reviving player");
        }
        player.revive();
        for (AbstractDamageablePart part : this) {
            if ((part.canCauseDeath || this.noCritical) && part.currentHealth <= 0F) {
                part.currentHealth = 1F; // Set the critical health to a non-zero value
            }
        }
        //make sure to resync the client health
        if (!player.level().isClientSide && player instanceof ServerPlayer)
            PacketDistributor.sendToPlayer((ServerPlayer) player, new MessageSyncDamageModel(player.level().registryAccess(), this, true)); //Upload changes to the client
    }

    @Override
    public void runScaleLogic(Player player) {
        if (FirstAidConfig.SERVER.scaleMaxHealth.get()) { //Attempt to calculate the max health of the body parts based on the maxHealth attribute
            player.level().getProfiler().push("healthscaling");
            float globalFactor = player.getMaxHealth() / 20F;
            if (prevScaleFactor != globalFactor) {
                if (FirstAidConfig.GENERAL.debug.get()) {
                    FirstAid.LOGGER.info("Starting health scaling factor {} -> {} (max health {})", prevScaleFactor, globalFactor, player.getMaxHealth());
                }
                player.level().getProfiler().push("distribution");
                int reduced = 0;
                int added = 0;
                float expectedNewMaxHealth = 0F;
                int newMaxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    float floatResult = ((float) part.initialMaxHealth) * globalFactor;
                    expectedNewMaxHealth += floatResult;
                    int result = (int) floatResult;
                    if (result % 2 == 1) {
                        int partMaxHealth = part.getMaxHealth();
                        if (part.currentHealth < partMaxHealth && reduced < 4) {
                            result--;
                            reduced++;
                        } else if (part.currentHealth > partMaxHealth && added < 4) {
                            result++;
                            added++;
                        } else if (reduced > added) {
                            result++;
                            added++;
                        } else {
                            result--;
                            reduced++;
                        }
                    }
                    newMaxHealth += result;
                    if (FirstAidConfig.GENERAL.debug.get()) {
                        FirstAid.LOGGER.info("Part {} max health: {} initial; {} old; {} new", part.part.name(), part.initialMaxHealth, part.getMaxHealth(), result);
                    }
                    part.setMaxHealth(result);
                }
                player.level().getProfiler().popPush("correcting");
                if (Math.abs(expectedNewMaxHealth - newMaxHealth) >= 2F) {
                    if (FirstAidConfig.GENERAL.debug.get()) {
                        FirstAid.LOGGER.info("Entering second stage - diff {}", Math.abs(expectedNewMaxHealth - newMaxHealth));
                    }
                    List<AbstractDamageablePart> prioList = new ArrayList<>();
                    for (AbstractDamageablePart part : this) {
                        prioList.add(part);
                    }
                    prioList.sort(Comparator.comparingInt(AbstractDamageablePart::getMaxHealth));
                    for (AbstractDamageablePart part : prioList) {
                        int maxHealth = part.getMaxHealth();
                        if (FirstAidConfig.GENERAL.debug.get()) {
                            FirstAid.LOGGER.info("Part {}: Second stage with total diff {}", part.part.name(), Math.abs(expectedNewMaxHealth - newMaxHealth));
                        }
                        if (expectedNewMaxHealth > newMaxHealth) {
                            part.setMaxHealth(maxHealth + 2);
                            newMaxHealth += (part.getMaxHealth() - maxHealth);
                        } else if (expectedNewMaxHealth < newMaxHealth) {
                            part.setMaxHealth(maxHealth - 2);
                            newMaxHealth -= (maxHealth - part.getMaxHealth());
                        }
                        if (Math.abs(expectedNewMaxHealth - newMaxHealth) < 2F) {
                            break;
                        }
                    }
                }
                player.level().getProfiler().pop();
            }
            prevScaleFactor = globalFactor;
            player.level().getProfiler().pop();
        }
    }

    @Override
    public void scheduleResync() {
        if (this.resyncTimer == -1) {
            this.resyncTimer = 3;
        } else {
            FirstAid.LOGGER.warn("resync already scheduled!");
        }
    }

    @Override
    public boolean hasNoCritical() {
        return this.noCritical;
    }
}
