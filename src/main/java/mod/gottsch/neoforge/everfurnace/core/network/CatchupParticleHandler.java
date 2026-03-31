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
package mod.gottsch.neoforge.everfurnace.core.network;

import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Random;

/**
 * Client-only handler for {@link CatchupParticlePacket}.
 *
 * <p>Isolated into its own {@code @OnlyIn(Dist.CLIENT)} class so the server
 * never attempts to load any client-only classes (Minecraft, ClientLevel, etc.).
 *
 * <p>Handles three visual/audio effects when catch-up completes and at least
 * one item was cooked:
 * <ul>
 *   <li><b>Feature D</b> — flame and smoke particle burst at the furnace position.</li>
 *   <li><b>Feature G</b> — furnace crackle sound at the furnace position.</li>
 *   <li><b>Feature H</b> — immediate LIT block state snap to ensure the client
 *       light level reflects the post-catch-up state without waiting for the
 *       next server block update.</li>
 * </ul>
 *
 * @author by Mark Gottschling on 3/31/2026
 */
@OnlyIn(Dist.CLIENT)
public class CatchupParticleHandler {

    /**
     * Entry point called from the registered payload handler on the main thread.
     * {@code IPayloadContext} is provided by NeoForge's networking system but
     * is not needed here since all three effects use client-side state only.
     */
    public static void handle(CatchupParticlePacket packet, IPayloadContext context) {
        BlockPos pos = packet.pos();
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        spawnParticles(level, pos);  // Feature D
        playSound(level, pos);       // Feature G
        snapLitState(level, pos);    // Feature H
    }

    // -------------------------------------------------------------------------
    // Feature D — particle burst
    // -------------------------------------------------------------------------

    private static void spawnParticles(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.particleBurstEnabled.get()) return;

        Random rand = new Random();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < 12; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.8;
            double oy = rand.nextDouble() * 0.6;
            double oz = (rand.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz, 0, 0.02, 0);
        }
        for (int i = 0; i < 8; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.8;
            double oy = rand.nextDouble() * 0.8;
            double oz = (rand.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.LARGE_SMOKE, cx + ox, cy + oy, cz + oz, 0, 0.03, 0);
        }
    }

    // -------------------------------------------------------------------------
    // Feature G — sound cue
    // -------------------------------------------------------------------------

    private static void playSound(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.soundCueEnabled.get()) return;

        level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                1.0f, 1.0f, false);
    }

    // -------------------------------------------------------------------------
    // Feature H — light state snap
    // -------------------------------------------------------------------------

    /**
     * Forces the client to immediately update the furnace block's LIT state so
     * the local light level reflects post-catch-up reality without waiting for
     * the server's next block-update packet.
     */
    private static void snapLitState(Level level, BlockPos pos) {
        if (!EverFurnaceConfig.CLIENT.lightFlickerEnabled.get()) return;

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(AbstractFurnaceBlock.LIT)) return;

        // Re-setting the existing state is a no-op visually but triggers a
        // client-side light engine recalculation.
        level.setBlockAndUpdate(pos, state);
    }
}
