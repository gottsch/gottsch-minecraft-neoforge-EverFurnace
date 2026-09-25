/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.neoforge.everfurnace.core.network;

import mod.gottsch.neoforge.everfurnace.core.EverFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client when catch-up completes and at least one item was cooked.
 *
 * <p>On receipt the client handler calls {@link CatchupParticleHandler#handle}
 * which fires all three post-catch-up effects:
 * <ul>
 *   <li>Feature D — flame/smoke particle burst</li>
 *   <li>Feature G — furnace crackle sound</li>
 *   <li>Feature H — LIT block state snap</li>
 * </ul>
 * Each effect is independently gated by its own client config key.
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public record CatchupParticlePacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CatchupParticlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(EverFurnace.MODID, "catchup_particle"));

    /**
     * StreamCodec for encoding/decoding the packet.
     * NeoForge 1.21.1 play-phase packets use {@link RegistryFriendlyByteBuf}.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CatchupParticlePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    CatchupParticlePacket::pos,
                    CatchupParticlePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Packet handler registered with {@code playToClient}.
     *
     * <p>The runtime {@link FMLEnvironment#dist} check prevents
     * {@link CatchupParticleHandler} (which is {@code @OnlyIn(CLIENT)}) from
     * being classloaded on a dedicated server, where this handler is never
     * invoked but the method reference is still captured at registration time.
     */
    public static void handle(CatchupParticlePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CatchupParticleHandler.handle(packet, context);
        }
    }
}
