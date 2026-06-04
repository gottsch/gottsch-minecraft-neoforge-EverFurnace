package mod.gottsch.neoforge.everfurnace.core.catchup;

import mod.gottsch.neoforge.everfurnace.api.CookingCatchupHandler;
import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.neoforge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import mod.gottsch.neoforge.everfurnace.core.mixin.IEverFurnaceBlockEntityMixin;
import mod.gottsch.neoforge.everfurnace.core.network.ModNetwork;
import mod.gottsch.neoforge.everfurnace.core.util.CookResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built-in {@link CookingCatchupHandler} for vanilla furnace, blast furnace, and smoker.
 *
 * <p>One instance is registered for all three block entity types in
 * {@link mod.gottsch.neoforge.everfurnace.core.EverFurnace}.  Recipe lookup is
 * performed via the furnace's own {@code quickCheck} cache (accessed through
 * {@link IEverFurnaceBlockEntityMixin#getQuickCheck()}) so no special recipe-type
 * detection is needed.
 *
 * <p>This class contains the catch-up logic that previously lived inline in
 * {@link mod.gottsch.neoforge.everfurnace.core.mixin.EverFurnaceBlockEntityMixin}.
 * Field access is performed through {@link IEverFurnaceBlockEntityMixin} accessors
 * so no Access Transformer entries are required for the furnace fields.
 *
 * @author Mark Gottschling
 */
public class FurnaceCatchupHandler implements CookingCatchupHandler {

    private static final int INPUT_SLOT  = 0;
    private static final int FUEL_SLOT   = 1;
    private static final int OUTPUT_SLOT = 2;

    @Override
    public void applyCatchup(BlockEntity blockEntity, long deltaTime, ServerLevel level, BlockPos pos) {

        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) blockEntity;
        IEverFurnaceBlockEntityMixin  ife  = (IEverFurnaceBlockEntityMixin)(Object) blockEntity;
        IEverFurnaceBlockEntity      mixin = (IEverFurnaceBlockEntity)(Object) blockEntity;

        // ── Pre-checks ───────────────────────────────────────────────────────

        if (!ife.callIsLit()) return;

        NonNullList<ItemStack> items = ife.getItems();

        ItemStack cookStack = items.get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = items.get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == furnace.getMaxStackSize()) return;

        RecipeHolder<?> recipe = ife.getQuickCheck()
                .getRecipeFor(new SingleRecipeInput(cookStack), level).orElse(null);
        if (!IEverFurnaceBlockEntityMixin.callCanBurn(level.registryAccess(), recipe,
                items, furnace.getMaxStackSize(), furnace)) return;

        ItemStack fuelStack = items.get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // ── How much virtual time can we consume? ────────────────────────────

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * ife.getLitDuration()
                + ife.getLitTime();
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * ife.getCookingTotalTime()
                + (ife.getCookingTotalTime() - ife.getCookingProgress());

        long maxApplicableTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxApplicableTime);
        if (actualAppliedTime <= 0) return;

        // ── Consume fuel ─────────────────────────────────────────────────────

        applyFuelTime(ife, furnace, fuelStack, actualAppliedTime);

        // ── Advance cooking ──────────────────────────────────────────────────

        CookResult result = applyCookTime(level, furnace, ife, recipe, cookStack, actualAppliedTime);

        // ── Notification bookkeeping ──────────────────────────────────────────

        if (result.itemsCooked() > 0 && EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) {
            mixin.everfurnace$setPendingNotification(
                    mixin.everfurnace$getPendingNotification() + result.itemsCooked());

            long currentGameTime = level.getGameTime();
            long cooldown  = EverFurnaceConfig.COMMON.notificationCooldownTicks.get();
            long lastArmed = mixin.everfurnace$getLastNotificationTime();

            if (cooldown == 0 || lastArmed == 0 || (currentGameTime - lastArmed) >= cooldown) {
                mixin.everfurnace$setLastNotificationTime(currentGameTime);
            }
        }

        // ── XP bookkeeping ───────────────────────────────────────────────────

        if (result.xpEarned() > 0f) {
            mixin.everfurnace$setPendingXp(
                    mixin.everfurnace$getPendingXp() + result.xpEarned());
        }

        // ── Particle burst ───────────────────────────────────────────────────

        if (result.itemsCooked() > 0) {
            ModNetwork.sendCatchupParticles(level, pos);
        }

        // ── Mark dirty and sync LIT block state ──────────────────────────────

        furnace.setChanged();
        if (!ife.callIsLit()) {
            BlockState state    = level.getBlockState(pos);
            BlockState newState = state.setValue(AbstractFurnaceBlock.LIT, false);
            level.setBlock(pos, newState, 3);
            furnace.setChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Consumes fuel items to cover {@code ticks} of burn time, updating
     * {@code litTime} via the accessor.
     */
    private static void applyFuelTime(IEverFurnaceBlockEntityMixin ife,
                                      AbstractFurnaceBlockEntity furnace,
                                      ItemStack fuelStack,
                                      long ticks) {
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
            long leftover               = ticksCoveredByNewItems - totalConsumed;

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
     * Advances cooking progress by {@code ticks}, calling {@code burn()} for
     * each completed item.
     *
     * @return a {@link CookResult} containing the item count and XP earned
     */
    private static CookResult applyCookTime(Level level,
                                            AbstractFurnaceBlockEntity furnace,
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

        NonNullList<ItemStack> items             = ife.getItems();
        long ticksToFinishCurrent                = cookingTotalTime - ife.getCookingProgress();

        if (ticks < ticksToFinishCurrent) {
            ife.setCookingProgress(ife.getCookingProgress() + (int) ticks);
        } else {
            ticks -= ticksToFinishCurrent;
            ife.setCookingProgress(cookingTotalTime);

            if (IEverFurnaceBlockEntityMixin.callBurn(level.registryAccess(), recipe,
                    items, furnace.getMaxStackSize(), furnace)) {
                furnace.setRecipeUsed(recipe);
                cooked++;
                xpEarned += xpPerItem;
            }
            ife.setCookingProgress(0);

            if (!cookStack.isEmpty() && cookingTotalTime > 0) {
                long additionalItems = ticks / cookingTotalTime;
                long remainder       = ticks % cookingTotalTime;

                for (long i = 0; i < additionalItems; i++) {
                    if (!IEverFurnaceBlockEntityMixin.callCanBurn(level.registryAccess(), recipe,
                            items, furnace.getMaxStackSize(), furnace)) break;
                    if (IEverFurnaceBlockEntityMixin.callBurn(level.registryAccess(), recipe,
                            items, furnace.getMaxStackSize(), furnace)) {
                        furnace.setRecipeUsed(recipe);
                        cooked++;
                        xpEarned += xpPerItem;
                    }
                }

                ife.setCookingProgress((int) remainder);
            }
        }

        return new CookResult(cooked, xpEarned);
    }
}
