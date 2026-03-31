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

import mod.gottsch.neoforge.everfurnace.core.EverFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
}
