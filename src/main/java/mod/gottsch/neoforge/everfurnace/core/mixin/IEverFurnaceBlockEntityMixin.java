/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.neoforge.everfurnace.core.mixin;

import mod.gottsch.neoforge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * Created by Mark Gottschling on 12/13/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntityMixin extends IEverFurnaceBlockEntity {

    @Accessor
    int getLitTime();

    @Accessor("litTime")
    public void setLitTime(int litTime);

    @Accessor
    int getLitDuration();

    @Accessor
    int getCookingProgress();
    @Accessor
    public void setCookingProgress(int cookingProgress);

    @Accessor
    int getCookingTotalTime();
    @Accessor
    public void setCookingTotalTime(int cookingTotalTime);

    @Accessor
    NonNullList<ItemStack> getItems();

    @Accessor
    RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> getQuickCheck();

    @Invoker
    public boolean callIsLit();

    @Invoker
    public static boolean callCanBurn(RegistryAccess registryAccess, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> inventory, int maxStackSize, AbstractFurnaceBlockEntity furnace) {
        throw new AssertionError();
    }

    @Invoker
    public static boolean callBurn(RegistryAccess registryAccess, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> inventory, int maxStackSize, AbstractFurnaceBlockEntity furnace) {
        throw new AssertionError();
    }
}
