/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.neoforge.everfurnace.core.event;

import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.neoforge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Delivers offline-cooking notifications and pending XP to players.
 *
 * <p>Must be registered on the NeoForge <em>game</em> event bus. In
 * {@code EverFurnace}:
 * <pre>{@code
 * NeoForge.EVENT_BUS.register(FurnaceEventHandler.class);
 * }</pre>
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public class FurnaceEventHandler {

    // -------------------------------------------------------------------------
    // Feature B / F — deliver notification + XP when the player opens a furnace
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;

        AbstractFurnaceBlockEntity furnace = resolveFurnace(event.getContainer());
        if (furnace == null) return;

        deliverFurnaceRewards(player, furnace);
    }

    /**
     * Returns the {@link AbstractFurnaceBlockEntity} backing {@code menu}, or
     * {@code null} if the menu is not a vanilla furnace menu.
     */
    private static AbstractFurnaceBlockEntity resolveFurnace(AbstractContainerMenu menu) {
        if (menu instanceof FurnaceMenu fm
                && fm.getSlot(0).container instanceof AbstractFurnaceBlockEntity be) {
            return be;
        }
        if (menu instanceof BlastFurnaceMenu bfm
                && bfm.getSlot(0).container instanceof AbstractFurnaceBlockEntity be) {
            return be;
        }
        if (menu instanceof SmokerMenu sm
                && sm.getSlot(0).container instanceof AbstractFurnaceBlockEntity be) {
            return be;
        }
        return null;
    }

    /**
     * Delivers any pending notification and XP for {@code furnace} to
     * {@code player}, then clears both fields.
     */
    public static void deliverFurnaceRewards(ServerPlayer player,
                                             AbstractFurnaceBlockEntity furnace) {
        IEverFurnaceBlockEntity mixin =
                (IEverFurnaceBlockEntity)(Object) furnace;

        // --- Notification (Feature B) ---
        int pending = mixin.everfurnace$getPendingNotification();
        if (pending > 0) {
            player.sendSystemMessage(singleFurnaceMessage(
                    blockDisplayName(player.serverLevel(), furnace.getBlockPos()), pending));
            mixin.everfurnace$setPendingNotification(0);
        }

        // --- XP (Feature F) ---
        float pendingXp = mixin.everfurnace$getPendingXp();
        if (pendingXp > 0f) {
            int whole = (int) pendingXp;
            float remainder = pendingXp - whole;
            if (whole > 0) player.giveExperiencePoints(whole);
            mixin.everfurnace$setPendingXp(remainder);
        }
    }

    // -------------------------------------------------------------------------
    // Feature I — deliver notifications on login
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!EverFurnaceConfig.COMMON.notifyOnLogin.get()) return;
        if (!EverFurnaceConfig.COMMON.notifyPlayerOnCatchup.get()) return;

        ServerLevel level = player.serverLevel();
        int radius = level.getServer().getPlayerList().getSimulationDistance();

        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        int furnaceCount = 0;
        int totalCooked  = 0;
        // Remembered only for the single-furnace case, where naming the block is
        // unambiguous.  Mixed types are reported generically (see multiFurnaceMessage).
        BlockPos singlePos = null;

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk)) {
                    continue;
                }

                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;

                    IEverFurnaceBlockEntity mixin =
                            (IEverFurnaceBlockEntity)(Object) furnace;

                    int pending = mixin.everfurnace$getPendingNotification();
                    if (pending <= 0) continue;

                    furnaceCount++;
                    totalCooked += pending;
                    singlePos = furnace.getBlockPos();
                    mixin.everfurnace$setPendingNotification(0);

                    float pendingXp = mixin.everfurnace$getPendingXp();
                    if (pendingXp > 0f) {
                        int whole = (int) pendingXp;
                        float remainder = pendingXp - whole;
                        if (whole > 0) player.giveExperiencePoints(whole);
                        mixin.everfurnace$setPendingXp(remainder);
                    }
                }
            }
        }

        if (furnaceCount == 1) {
            player.sendSystemMessage(
                    singleFurnaceMessage(blockDisplayName(level, singlePos), totalCooked));
        } else if (furnaceCount > 1) {
            player.sendSystemMessage(multiFurnaceMessage(furnaceCount, totalCooked));
        }
    }

    // -------------------------------------------------------------------------
    // Message builders
    // -------------------------------------------------------------------------

    /**
     * The localized display name of the cooking block at {@code pos}, for use as a
     * message argument.
     *
     * <p>{@code Block.getName()} returns a {@code translatable} component, so a
     * smoker reads "Smoker", a blast furnace "Blast Furnace", and a modded furnace
     * picked up by the {@code AbstractFurnaceBlockEntity} fallback yields its own
     * name — no hardcoded per-block mapping needed, and each player sees it in
     * their own language.
     *
     * <p>Pending notifications are persisted in block-entity NBT, and the login
     * path resolves them by position, so the block may no longer be a furnace by
     * the time it is read (broken or replaced while offline). Fall back to a
     * generic noun rather than naming whatever now occupies the space.
     */
    private static Component blockDisplayName(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return Component.translatable("message.everfurnace.generic_furnace");
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AbstractFurnaceBlock
                ? state.getBlock().getName()
                : Component.translatable("message.everfurnace.generic_furnace");
    }

    /** The orange "[EverFurnace] " brand tag. Intentionally not translated. */
    private static MutableComponent prefix() {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500));
    }

    private static Component count(int value) {
        return Component.literal(String.valueOf(value))
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    }

    /** "[EverFurnace] Your Smoker cooked <n> item(s) while you were away." */
    private static Component singleFurnaceMessage(Component blockName, int itemCount) {
        return prefix().append(Component.translatable(
                        itemCount == 1 ? "message.everfurnace.cooked.single.one"
                                       : "message.everfurnace.cooked.single.many",
                        blockName, count(itemCount))
                .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }

    /**
     * "[EverFurnace] <n> cooking blocks cooked a combined <m> items while you were away."
     *
     * <p>Deliberately generic: a login sweep can aggregate a furnace, a smoker and a
     * blast furnace into one message, and no single block name would be correct.
     */
    private static Component multiFurnaceMessage(int furnaceCount, int totalItems) {
        return prefix().append(Component.translatable(
                        furnaceCount == 1 ? "message.everfurnace.cooked.multi.one"
                                          : "message.everfurnace.cooked.multi.many",
                        count(furnaceCount), count(totalItems))
                .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }
}