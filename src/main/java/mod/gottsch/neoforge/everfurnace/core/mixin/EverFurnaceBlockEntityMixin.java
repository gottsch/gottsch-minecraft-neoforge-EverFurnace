/*
 * This file is part of EverFurnace.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
 *
 * EverFurnace is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverFurnace is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverFurnace.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.neoforge.everfurnace.core.mixin;

import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.neoforge.everfurnace.core.network.ModNetwork;
import mod.gottsch.neoforge.everfurnace.core.util.CookResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by Mark Gottschling on 12/13/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class EverFurnaceBlockEntityMixin extends BaseContainerBlockEntity
        implements IEverFurnaceBlockEntityMixin, WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    @Unique private static final int INPUT_SLOT  = 0;
    @Unique private static final int FUEL_SLOT   = 1;
    @Unique private static final int OUTPUT_SLOT = 2;

    // NBT tag names
    @Unique private static final String LAST_GAME_TIME_TAG          = "everfurnace_lastGameTime";
    @Unique private static final String PENDING_NOTIFICATION_TAG    = "everfurnace_pendingNotification";
    @Unique private static final String LAST_NOTIFICATION_TIME_TAG  = "everfurnace_lastNotificationTime";
    @Unique private static final String PENDING_XP_TAG              = "everfurnace_pendingXp";

    // NBT versioning
    @Unique private static final int    CURRENT_NBT_VERSION         = 2;
    @Unique private static final String NBT_VERSION_TAG             = "everfurnace_version";

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    @Unique private long  everfurnace$lastGameTime;
    @Unique private int   everfurnace$pendingNotification;
    @Unique private long  everfurnace$lastNotificationTime;
    @Unique private float everfurnace$pendingXp;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected EverFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // -------------------------------------------------------------------------
    // NBT save / load
    // -------------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt  (NBT_VERSION_TAG,            CURRENT_NBT_VERSION);
        tag.putLong (LAST_GAME_TIME_TAG,         this.everfurnace$lastGameTime);
        tag.putInt  (PENDING_NOTIFICATION_TAG,   this.everfurnace$pendingNotification);
        tag.putLong (LAST_NOTIFICATION_TIME_TAG, this.everfurnace$lastNotificationTime);
        tag.putFloat(PENDING_XP_TAG,             this.everfurnace$pendingXp);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.everfurnace$lastGameTime         = tag.getLong (LAST_GAME_TIME_TAG);
        this.everfurnace$pendingNotification  = tag.getInt  (PENDING_NOTIFICATION_TAG);
        this.everfurnace$lastNotificationTime = tag.getLong (LAST_NOTIFICATION_TIME_TAG);
        this.everfurnace$pendingXp            = tag.getFloat(PENDING_XP_TAG);
    }

    // -------------------------------------------------------------------------
    // Tick injection
    // -------------------------------------------------------------------------

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onTick(Level world, BlockPos pos, BlockState state,
                               AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceConfig.COMMON.catchupEnabled.get()) return;

        EverFurnaceBlockEntityMixin mixin = (EverFurnaceBlockEntityMixin)(Object) blockEntity;
        IEverFurnaceBlockEntityMixin ife  = (IEverFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everfurnace$getLastGameTime();

        mixin.everfurnace$setLastGameTime(currentGameTime);

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceConfig.COMMON.minDeltaThreshold.get()) return;

        if (!ife.callIsLit()) return;

        long maxCatchup = EverFurnaceConfig.COMMON.maxCatchupTicks.get();
        deltaTime = Math.min(deltaTime, maxCatchup);

        // ------------------------------------------------------------------
        // Guard checks
        // ------------------------------------------------------------------

        ItemStack cookStack = ife.getItems().get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = ife.getItems().get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxStackSize()) return;

        RecipeHolder<?> recipe = (RecipeHolder<?>) ife.getQuickCheck()
                .getRecipeFor(new SingleRecipeInput(cookStack), world).orElse(null);
        if (!IEverFurnaceBlockEntityMixin.callCanBurn(world.registryAccess(), recipe,
                ife.getItems(), blockEntity.getMaxStackSize(), blockEntity)) return;

        ItemStack fuelStack = ife.getItems().get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ------------------------------------------------------------------
        // Time budget
        // ------------------------------------------------------------------

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * ife.getLitDuration()
                + ife.getLitTime();
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * ife.getCookingTotalTime()
                + (ife.getCookingTotalTime() - ife.getCookingProgress());

        long maxApplicableTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxApplicableTime);

        if (actualAppliedTime <= 0) return;

        // ------------------------------------------------------------------
        // Apply fuel consumption
        // ------------------------------------------------------------------

        applyFuelTime(ife, fuelStack, actualAppliedTime);

        // ------------------------------------------------------------------
        // Apply cooking progress
        // ------------------------------------------------------------------

        CookResult result = applyCookTime(world, blockEntity, ife, recipe, cookStack, actualAppliedTime);

        // ------------------------------------------------------------------
        // Notification bookkeeping
        // ------------------------------------------------------------------

        if (result.itemsCooked() > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            mixin.everfurnace$pendingNotification += result.itemsCooked();

            long cooldown  = EverFurnaceConfig.COMMON.notificationCooldownTicks.get();
            long lastArmed = mixin.everfurnace$getLastNotificationTime();

            if (cooldown == 0 || lastArmed == 0 || (currentGameTime - lastArmed) >= cooldown) {
                mixin.everfurnace$setLastNotificationTime(currentGameTime);
            }
        }

        // ------------------------------------------------------------------
        // XP accumulation
        // ------------------------------------------------------------------

        if (result.xpEarned() > 0f) {
            mixin.everfurnace$pendingXp += result.xpEarned();
        }

        // ------------------------------------------------------------------
        // Particle / sound / light packet  (server → client)
        // ------------------------------------------------------------------

        if (result.itemsCooked() > 0 && world instanceof ServerLevel serverLevel) {
            ModNetwork.sendCatchupParticles(serverLevel, pos);
        }

        // ------------------------------------------------------------------
        // Mark dirty; update LIT state if furnace burned out
        // ------------------------------------------------------------------

        AbstractFurnaceBlockEntity.setChanged(world, pos, state);

        if (!ife.callIsLit()) {
            BlockState newState = state.setValue(AbstractFurnaceBlock.LIT, false);
            world.setBlock(pos, newState, 3);
            AbstractFurnaceBlockEntity.setChanged(world, pos, newState);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Consume fuel from the fuel stack to cover {@code ticks} elapsed time.
     * All furnace field access goes through {@link IEverFurnaceBlockEntityMixin} accessors.
     */
    @Unique
    private static void applyFuelTime(IEverFurnaceBlockEntityMixin ife,
                                      ItemStack fuelStack, long ticks) {
        long totalConsumed = ticks;
        int  litDuration   = ife.getLitDuration();

        if (totalConsumed <= ife.getLitTime()) {
            ife.setLitTime(ife.getLitTime() - (int) totalConsumed);
        } else {
            totalConsumed -= ife.getLitTime();
            ife.setLitTime(0);

            int wholeItemsNeeded = (int) Math.ceil((double) totalConsumed / litDuration);
            wholeItemsNeeded = Math.min(wholeItemsNeeded, fuelStack.getCount());

            long ticksCoveredByNewItems = (long) wholeItemsNeeded * litDuration;
            long leftover = ticksCoveredByNewItems - totalConsumed;

            fuelStack.shrink(wholeItemsNeeded);

            if (fuelStack.isEmpty()) {
                ife.setLitTime(0);
                ife.getItems().set(FUEL_SLOT, fuelStack.getCraftingRemainingItem());
            } else {
                ife.setLitTime((int) leftover);
            }
        }
    }

    /**
     * Advance cooking progress by {@code ticks}, calling {@code burn()} for each
     * completed item. Returns a {@link CookResult} with the cooked count and XP earned.
     */
    @Unique
    private static CookResult applyCookTime(Level world,
                                            AbstractFurnaceBlockEntity blockEntity,
                                            IEverFurnaceBlockEntityMixin ife,
                                            RecipeHolder<?> recipe,
                                            ItemStack cookStack,
                                            long ticks) {
        int cookingTotalTime = ife.getCookingTotalTime();
        if (cookingTotalTime <= 0) return new CookResult(0, 0f);

        float xpPerItem = (recipe != null && recipe.value() instanceof AbstractCookingRecipe cookingRecipe)
                ? cookingRecipe.getExperience()
                : 0f;

        int   cooked   = 0;
        float xpEarned = 0f;

        long ticksToFinishCurrent = cookingTotalTime - ife.getCookingProgress();

        if (ticks < ticksToFinishCurrent) {
            ife.setCookingProgress(ife.getCookingProgress() + (int) ticks);
        } else {
            ticks -= ticksToFinishCurrent;
            ife.setCookingProgress(cookingTotalTime);

            if (IEverFurnaceBlockEntityMixin.callBurn(world.registryAccess(), recipe,
                    ife.getItems(), blockEntity.getMaxStackSize(), blockEntity)) {
                blockEntity.setRecipeUsed(recipe);
                cooked++;
                xpEarned += xpPerItem;
            }
            ife.setCookingProgress(0);

            if (!cookStack.isEmpty() && cookingTotalTime > 0) {
                long additionalItems = ticks / cookingTotalTime;
                long remainder       = ticks % cookingTotalTime;

                for (long i = 0; i < additionalItems; i++) {
                    if (!IEverFurnaceBlockEntityMixin.callCanBurn(world.registryAccess(), recipe,
                            ife.getItems(), blockEntity.getMaxStackSize(), blockEntity)) {
                        break;
                    }
                    if (IEverFurnaceBlockEntityMixin.callBurn(world.registryAccess(), recipe,
                            ife.getItems(), blockEntity.getMaxStackSize(), blockEntity)) {
                        blockEntity.setRecipeUsed(recipe);
                        cooked++;
                        xpEarned += xpPerItem;
                    }
                }

                ife.setCookingProgress((int) remainder);
            }
        }

        return new CookResult(cooked, xpEarned);
    }

    // -------------------------------------------------------------------------
    // ModFurnaceBlockEntityInterface  (implements contract)
    // -------------------------------------------------------------------------

    @Unique public long  everfurnace$getLastGameTime()               { return this.everfurnace$lastGameTime; }
    @Unique public void  everfurnace$setLastGameTime(long t)         { this.everfurnace$lastGameTime = t; }

    @Unique public int   everfurnace$getPendingNotification()        { return this.everfurnace$pendingNotification; }
    @Unique public void  everfurnace$setPendingNotification(int n)   { this.everfurnace$pendingNotification = n; }

    @Unique public long  everfurnace$getLastNotificationTime()       { return this.everfurnace$lastNotificationTime; }
    @Unique public void  everfurnace$setLastNotificationTime(long t) { this.everfurnace$lastNotificationTime = t; }

    @Unique public float everfurnace$getPendingXp()                  { return this.everfurnace$pendingXp; }
    @Unique public void  everfurnace$setPendingXp(float xp)          { this.everfurnace$pendingXp = xp; }
}