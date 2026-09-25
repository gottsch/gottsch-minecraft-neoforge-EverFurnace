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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds offline catch-up to vanilla campfires (and soul campfires).
 *
 * <p>The vanilla {@code cookTick} ticker only runs for chunks that are actually
 * ticking — i.e. within a player's simulation distance — and only while the
 * campfire is lit (otherwise {@code cooldownTick} is registered instead). So
 * proximity gating and the "is lit" check are both implicit in hooking this
 * method: while no player is near, the ticker doesn't run and the real-world
 * gap accumulates in {@link #everfurnace$lastGameTime}; when a player brings the
 * chunk back into range the first tick applies the catch-up.
 *
 * <p>Rather than re-implement the recipe assemble / drop / slot-clear that the
 * vanilla body performs on completion, this inject simply advances each slot's
 * progress (capped at its total). A slot pushed to its total is completed by the
 * vanilla body's own {@code ++}/threshold check that runs immediately after this
 * inject returns. Each slot holds a single item and is not restocked, so output
 * is inherently bounded to one item per slot.
 *
 * @author by Mark Gottschling on 2026
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {

    @Unique private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";
    @Unique private static final String NBT_VERSION_TAG    = "everfurnace_version";
    @Unique private static final int    CURRENT_NBT_VERSION = 1;

    @Unique private long everfurnace$lastGameTime;

    // -------------------------------------------------------------------------
    // NBT save / load
    // -------------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void everfurnace$onSave(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt (NBT_VERSION_TAG,    CURRENT_NBT_VERSION);
        tag.putLong(LAST_GAME_TIME_TAG, this.everfurnace$lastGameTime);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void everfurnace$onLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.everfurnace$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    // -------------------------------------------------------------------------
    // Tick injection
    // -------------------------------------------------------------------------

    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void everfurnace$onCookTick(Level world, BlockPos pos, BlockState state,
                                               CampfireBlockEntity blockEntity, CallbackInfo ci) {

        if (!EverFurnaceApi.isCatchupEnabled()) return;

        CampfireBlockEntityMixin mixin = (CampfireBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getGameTime();
        long localLastGameTime = mixin.everfurnace$lastGameTime;

        mixin.everfurnace$lastGameTime = currentGameTime;

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
}
