/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.neoforge.everfurnace.core.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers EverFurnace admin commands on the NeoForge game event bus.
 *
 * <p>Must be registered in {@code EverFurnace} constructor:
 * <pre>{@code
 * NeoForge.EVENT_BUS.register(ModCommands.class);
 * }</pre>
 *
 * @author Mark Gottschling on 4/28/2026
 */
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        EverFurnaceCommand.register(event.getDispatcher());
    }
}
