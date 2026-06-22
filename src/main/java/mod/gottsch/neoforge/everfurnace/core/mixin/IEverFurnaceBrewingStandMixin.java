/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor / invoker interface for the private brewing state and helpers on
 * {@link BrewingStandBlockEntity}, used by {@code BrewingStandCatchupHandler}.
 *
 * <p>The catch-up handler re-uses vanilla's own {@code isBrewable} / {@code doBrew}
 * via {@link Invoker} so all brewing rules, ingredient remainders, and the brew
 * level-event are handled correctly rather than re-implemented.
 *
 * @author Mark Gottschling
 */
@Mixin(BrewingStandBlockEntity.class)
public interface IEverFurnaceBrewingStandMixin {

    @Accessor
    int getBrewTime();
    @Accessor
    void setBrewTime(int brewTime);

    @Accessor
    int getFuel();
    @Accessor
    void setFuel(int fuel);

    @Accessor
    Item getIngredient();
    @Accessor
    void setIngredient(Item ingredient);

    /** The private 5-slot inventory (0–2 bottles, 3 ingredient, 4 blaze-powder fuel). */
    @Accessor("items")
    NonNullList<ItemStack> getBrewingItems();

    @Invoker("isBrewable")
    static boolean callIsBrewable(PotionBrewing potionBrewing, NonNullList<ItemStack> items) {
        throw new AssertionError();
    }

    @Invoker("doBrew")
    static void callDoBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        throw new AssertionError();
    }
}
