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

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
public abstract class EverFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {

    @Unique
    private static final int INPUT_SLOT = 0;
    @Unique
    private static final int FUEL_SLOT = 1;
    @Unique
    private static final int OUTPUT_SLOT = 2;
    @Unique
    private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";

    @Unique
    private long everfurnace$lastGameTime;

    protected EverFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putLong(LAST_GAME_TIME_TAG, this.everfurnace$lastGameTime);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.everfurnace$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    @Inject(method = "serverTick", at = @At("HEAD")) // target more specifically somewhere closer to the actual calculations?
    private static void onTick(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        // cast block entity as a mixin block entity
        EverFurnaceBlockEntity blockEntityMixin = (EverFurnaceBlockEntity) (Object) furnace;
        IEverFurnaceBlockEntity everFurnaceBlockEntity = (IEverFurnaceBlockEntity) ((Object) furnace);

        // record last world time
        long localLastGameTime = blockEntityMixin.getEverfurnace$lastGameTime();
        blockEntityMixin.setEverfurnace$lastGameTime(level.getGameTime());
        if (!everFurnaceBlockEntity.callIsLit()) {
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = furnace.getLevel().getGameTime() - localLastGameTime;

        // exit if not enough time has passed
        if (deltaTime < 20) {
            return;
        }

        /*
         * //////////////////////
         * validations
         * //////////////////////
         */
        ItemStack cookStack = everFurnaceBlockEntity.getItems().get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        // get the output stack
        ItemStack outputStack = everFurnaceBlockEntity.getItems().get(OUTPUT_SLOT);
        // return if it is already maxed out
        if (!outputStack.isEmpty() && outputStack.getCount() == furnace.getMaxStackSize()) return;

        // test if can accept recipe output
        SingleRecipeInput singleRecipeInput = new SingleRecipeInput(cookStack);
        RecipeHolder recipeholder = (RecipeHolder)everFurnaceBlockEntity.getQuickCheck().getRecipeFor(new SingleRecipeInput(cookStack), level).orElse(null);
        if (!IEverFurnaceBlockEntity.callCanBurn(level.registryAccess(), recipeholder, singleRecipeInput, everFurnaceBlockEntity.getItems(), furnace.getMaxStackSize())) return;
        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = everFurnaceBlockEntity.getItems().get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // have to calculate fuel time as it is no longer calculated during readNbt() as in 1.21.1
        if (everFurnaceBlockEntity.getLitDuration() == 0) {
            everFurnaceBlockEntity.setLitDuration(everFurnaceBlockEntity.callGetBurnDuration(level.fuelValues(), fuelStack));
        }

       long totalBurnTimeRemaining = (long) (fuelStack.getCount() - 1) * everFurnaceBlockEntity.getLitDuration() + everFurnaceBlockEntity.getLitTime();

        // calculate totalCookTimeRemaining
        long totalCookTimeRemaining = (long) (cookStack.getCount() -1) * everFurnaceBlockEntity.getCookingTotalTime() + (everFurnaceBlockEntity.getCookingTotalTime() - everFurnaceBlockEntity.getCookingProgress());

        // determine the max amount of time that can be used before one or both input run out.
        long maxInputTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);

        /*
         * determine  the actual max time that can be applied to processing. ie if elapsed time is < maxInputTime,
         * then only the elapse time can be used.
         */
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        if (actualAppliedTime < everFurnaceBlockEntity.getLitDuration()) {
            // reduce burn time
            everFurnaceBlockEntity.setLitTime(everFurnaceBlockEntity.getLitTime() - (int) actualAppliedTime);
            if (everFurnaceBlockEntity.getLitTime() <= 0) {
                // reduce the size of the fuel stack
                fuelStack.shrink(1);
                if (fuelStack.isEmpty()) {
                    everFurnaceBlockEntity.setLitTime(0);
                    everFurnaceBlockEntity.getItems().set(1, fuelStack.getItem().getCraftingRemainder());
                } else {
                    everFurnaceBlockEntity.setLitTime(everFurnaceBlockEntity.getLitTime() + everFurnaceBlockEntity.getLitDuration());
                }
            }
        } else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / everFurnaceBlockEntity.getLitDuration()));
            long remainder = actualAppliedTime % everFurnaceBlockEntity.getLitDuration();
            // reduced stack by quotient
            fuelStack.shrink(quotient);
            // reduce litTime by remainder
            everFurnaceBlockEntity.setLitTime(everFurnaceBlockEntity.getLitTime() - (int) remainder);
            if (everFurnaceBlockEntity.getLitTime() <= 0) {
                // reduce the size of the fuel stack
                fuelStack.shrink(1);
            }
            if (fuelStack.isEmpty()) {
                everFurnaceBlockEntity.setLitTime(0);
                everFurnaceBlockEntity.getItems().set(1, fuelStack.getItem().getCraftingRemainder());
            } else {
                everFurnaceBlockEntity.setLitTime(everFurnaceBlockEntity.getLitTime()
                        - (int) everFurnaceBlockEntity.getLitDuration());

            }
        }

        if (actualAppliedTime < everFurnaceBlockEntity.getCookingTotalTime()) {
            // increment cook progress time
            everFurnaceBlockEntity.setCookingProgress(everFurnaceBlockEntity.getCookingProgress()
                + (int) actualAppliedTime);

            if (everFurnaceBlockEntity.getCookingProgress() >= everFurnaceBlockEntity.getCookingTotalTime()) {
                if (IEverFurnaceBlockEntity.callBurn(level.registryAccess(), recipeholder, singleRecipeInput, everFurnaceBlockEntity.getItems(), furnace.getMaxStackSize())) {
                    furnace.setRecipeUsed(recipeholder);
                }
                if (cookStack.isEmpty()) {
                    everFurnaceBlockEntity.setCookingProgress(0);
                    everFurnaceBlockEntity.setCookingTotalTime(0);
                } else {
                    everFurnaceBlockEntity.setCookingProgress(everFurnaceBlockEntity.getCookingProgress()
                        - everFurnaceBlockEntity.getCookingTotalTime());
                }
            }
        }
        // actual applied time is greated that cook time total,
        // there, need to apply a factor of
        else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / everFurnaceBlockEntity.getCookingTotalTime()));
            long remainder = actualAppliedTime % everFurnaceBlockEntity.getCookingTotalTime();
            // reduced stack by quotient
            boolean isSuccessful = false;
            for (int iterations = 0; iterations < quotient; iterations++) {
                isSuccessful |= IEverFurnaceBlockEntity.callBurn(level.registryAccess(), recipeholder, singleRecipeInput, everFurnaceBlockEntity.getItems(), furnace.getMaxStackSize());
            }
            // update last recipe
            if (isSuccessful) furnace.setRecipeUsed(recipeholder);

            // increment cook time
            everFurnaceBlockEntity.setCookingProgress(everFurnaceBlockEntity.getCookingProgress()
             + (int) remainder);

            if (everFurnaceBlockEntity.getCookingProgress() >= everFurnaceBlockEntity.getCookingTotalTime()) {
                if (IEverFurnaceBlockEntity.callBurn(level.registryAccess(), recipeholder, singleRecipeInput, everFurnaceBlockEntity.getItems(), furnace.getMaxStackSize())) {
                    furnace.setRecipeUsed(recipeholder);
                }
                if (cookStack.isEmpty()) {
                    everFurnaceBlockEntity.setCookingProgress(0);
                    everFurnaceBlockEntity.setCookingTotalTime(0);
                } else {
                    everFurnaceBlockEntity.setCookingProgress(everFurnaceBlockEntity.getCookingProgress()
                        - everFurnaceBlockEntity.getCookingTotalTime());

                }
            }
        }

        if(!everFurnaceBlockEntity.callIsLit()) {
            state = state.setValue(AbstractFurnaceBlock.LIT, everFurnaceBlockEntity.callIsLit());
            level.setBlock(pos, state, 3);
            AbstractFurnaceBlockEntity.setChanged(level, pos, state);
        }
    }

    @Unique
    public long getEverfurnace$lastGameTime() {
        return everfurnace$lastGameTime;
    }

    @Unique
    public void setEverfurnace$lastGameTime(long everfurnace$lastGameTime) {
        this.everfurnace$lastGameTime = everfurnace$lastGameTime;
    }
}
