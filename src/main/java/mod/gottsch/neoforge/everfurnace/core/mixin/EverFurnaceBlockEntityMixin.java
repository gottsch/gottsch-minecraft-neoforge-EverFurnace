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

import mod.gottsch.neoforge.everfurnace.api.EverFurnaceApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.level.Level;
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

        if (!EverFurnaceApi.isCatchupEnabled()) return;

        EverFurnaceBlockEntityMixin mixin = (EverFurnaceBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everfurnace$getLastGameTime();

        mixin.everfurnace$setLastGameTime(currentGameTime);

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < EverFurnaceApi.getMinDeltaThreshold()) return;

        deltaTime = Math.min(deltaTime, EverFurnaceApi.getMaxCatchupTicks());

        final long finalDelta = deltaTime;
        if (world instanceof ServerLevel serverLevel) {
            EverFurnaceApi.findHandler(blockEntity)
                    .ifPresent(handler -> handler.applyCatchup(blockEntity, finalDelta, serverLevel, pos));
        }
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