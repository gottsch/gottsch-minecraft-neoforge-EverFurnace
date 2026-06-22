/*
 * This file is part of EverFurnace.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.neoforge.everfurnace.core;

import com.mojang.logging.LogUtils;
import mod.gottsch.neoforge.everfurnace.api.EverFurnaceApi;
import mod.gottsch.neoforge.everfurnace.core.catchup.BrewingStandCatchupHandler;
import mod.gottsch.neoforge.everfurnace.core.catchup.CampfireCatchupHandler;
import mod.gottsch.neoforge.everfurnace.core.catchup.FurnaceCatchupHandler;
import mod.gottsch.neoforge.everfurnace.core.command.ModCommands;
import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.neoforge.everfurnace.core.event.FurnaceEventHandler;
import mod.gottsch.neoforge.everfurnace.core.network.ModNetwork;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(EverFurnace.MODID)
public class EverFurnace {

    public static final String MODID = "everfurnace";

    private static final Logger LOGGER = LogUtils.getLogger();

    public EverFurnace(IEventBus modEventBus, ModContainer modContainer) {

        // Register configs via the ModContainer (NeoForge 1.21.1 pattern;
        // ModLoadingContext#registerConfig was removed in NeoForge 21.0).
        modContainer.registerConfig(ModConfig.Type.COMMON, EverFurnaceConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, EverFurnaceConfig.CLIENT_SPEC);

        // Wire live config values into the API so third-party handlers (and our
        // own) can read them without importing EverFurnaceConfig directly.
        EverFurnaceApi.bindConfig(
                () -> EverFurnaceConfig.COMMON.catchupEnabled.get(),
                () -> EverFurnaceConfig.COMMON.maxCatchupTicks.get(),
                () -> EverFurnaceConfig.COMMON.minDeltaThreshold.get()
        );

        // Register built-in catch-up handlers for all vanilla cooking blocks.
        FurnaceCatchupHandler       furnaceHandler      = new FurnaceCatchupHandler();
        CampfireCatchupHandler      campfireHandler     = new CampfireCatchupHandler();
        BrewingStandCatchupHandler  brewingStandHandler = new BrewingStandCatchupHandler();

        EverFurnaceApi.registerHandler(BlockEntityType.FURNACE,       furnaceHandler);
        EverFurnaceApi.registerHandler(BlockEntityType.BLAST_FURNACE, furnaceHandler);
        EverFurnaceApi.registerHandler(BlockEntityType.SMOKER,        furnaceHandler);
        // Both CampfireBlock and SoulCampfireBlock share BlockEntityType.CAMPFIRE in 1.21.1.
        EverFurnaceApi.registerHandler(BlockEntityType.CAMPFIRE, campfireHandler);
        EverFurnaceApi.registerHandler(BlockEntityType.BREWING_STAND, brewingStandHandler);
        // Catch up modded brewing stands that subclass BrewingStandBlockEntity and
        // reuse the vanilla ticker but register their own BlockEntityType.
        EverFurnaceApi.registerFallback(be -> be instanceof BrewingStandBlockEntity, brewingStandHandler);

        // Capability default: catch up any modded furnace that subclasses
        // AbstractFurnaceBlockEntity and reuses the vanilla ticker, even though it
        // registers its own BlockEntityType.  The exact-type registrations above
        // remain the override path; this only fills the gap they leave.  (No
        // campfire fallback: CampfireBlockEntity is final and not subclassed.)
        EverFurnaceApi.registerFallback(be -> be instanceof AbstractFurnaceBlockEntity, furnaceHandler);

        // Register the network payload handler on the mod bus.
        modEventBus.addListener(ModNetwork::onRegisterPayloads);

        // Register game-event listeners (notifications, XP) on the NeoForge game bus.
        NeoForge.EVENT_BUS.register(FurnaceEventHandler.class);

        // Register admin commands (inspect / tick / simulate) on the game bus.
        NeoForge.EVENT_BUS.register(ModCommands.class);
    }
}