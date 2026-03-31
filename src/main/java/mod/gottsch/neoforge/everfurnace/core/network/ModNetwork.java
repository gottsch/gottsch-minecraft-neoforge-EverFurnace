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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all network payload types for EverFurnace.
 *
 * <p>Must be subscribed to the <em>mod</em> event bus. In {@code EverFurnace}:
 * <pre>{@code
 * modEventBus.addListener(ModNetwork::onRegisterPayloads);
 * }</pre>
 *
 * <p>NeoForge 1.21.1 networking overview vs Forge 1.20.1:
 * <ul>
 *   <li>No {@code SimpleChannel} / {@code NetworkRegistry} — replaced by
 *       {@link RegisterPayloadHandlersEvent} + {@link PayloadRegistrar}.</li>
 *   <li>Packets implement {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload}
 *       and carry their own {@code TYPE} and {@code STREAM_CODEC}.</li>
 *   <li>Sending uses static methods on {@link PacketDistributor} rather than
 *       {@code CHANNEL.send(PacketDistributor.NEAR.with(...))}.</li>
 *   <li>Handlers run on the main thread by default — no {@code enqueueWork} needed.</li>
 *   <li>Client-only handling is enforced by registering with {@code playToClient}
 *       and placing the handler class in a {@code @OnlyIn(Dist.CLIENT)} class.</li>
 * </ul>
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public class ModNetwork {

    /** Protocol version — bump if the packet format changes. */
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Feature D / G / H — server → client catch-up effects packet.
        // Handler runs on the client main thread (NeoForge default).
        registrar.playToClient(
                CatchupParticlePacket.TYPE,
                CatchupParticlePacket.STREAM_CODEC,
                CatchupParticleHandler::handle
        );
    }

    // -------------------------------------------------------------------------
    // Send helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link CatchupParticlePacket} to all players within 32 blocks of
     * {@code pos} in {@code level}.
     *
     * <p>Call from the server tick after a successful catch-up pass.
     */
    public static void sendCatchupParticles(ServerLevel level, BlockPos pos) {
        PacketDistributor.sendToPlayersNear(
                level,
                null,           // excluded player (none)
                pos.getX(), pos.getY(), pos.getZ(),
                32.0,           // radius
                new CatchupParticlePacket(pos)
        );
    }
}
