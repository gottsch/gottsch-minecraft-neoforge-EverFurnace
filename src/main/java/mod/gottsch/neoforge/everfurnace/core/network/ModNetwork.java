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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
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
        // .optional() — vanilla clients (without the mod) silently skip this packet
        // rather than disconnecting with an unknown-payload error.
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        // Feature D / G / H — server → client catch-up effects packet.
        // Handler runs on the client main thread (NeoForge default).
        // CatchupParticlePacket::handle performs a runtime Dist check before
        // delegating to the @OnlyIn(CLIENT) CatchupParticleHandler.
        registrar.playToClient(
                CatchupParticlePacket.TYPE,
                CatchupParticlePacket.STREAM_CODEC,
                CatchupParticlePacket::handle
        );
    }

    // -------------------------------------------------------------------------
    // Send helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link CatchupParticlePacket} to nearby players that have EverFurnace installed.
     *
     * <p>Two cases require guarding:
     * <ol>
     *   <li>{@link ConnectionType#OTHER} (vanilla / non-NeoForge) clients have no payload
     *       channel registry at all — skipped up front.</li>
     *   <li>NeoForge clients that do NOT have EverFurnace installed pass the connection-type
     *       check but still lack the channel in their per-connection {@code NetworkPayloadSetup}.
     *       {@code NetworkRegistry.checkPacket} throws {@link UnsupportedOperationException}
     *       for them even though the channel was registered as {@code .optional()}; the
     *       {@code .optional()} flag only suppresses the handshake failure, not the send
     *       check. We catch and silently ignore that case.</li>
     * </ol>
     */
    public static void sendCatchupParticles(ServerLevel level, BlockPos pos) {
        CatchupParticlePacket packet = new CatchupParticlePacket(pos);
        double radiusSq = 32.0 * 32.0;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.connection.getConnectionType().isOther()) continue;
            if (player.distanceToSqr(cx, cy, cz) > radiusSq) continue;
            try {
                PacketDistributor.sendToPlayer(player, packet);
            } catch (UnsupportedOperationException ignored) {
                // NeoForge client without EverFurnace — channel not in their payload setup
            }
        }
    }
}
