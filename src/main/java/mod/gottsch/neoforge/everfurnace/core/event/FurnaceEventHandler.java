package mod.gottsch.neoforge.everfurnace.core.event;

import mod.gottsch.neoforge.everfurnace.core.config.EverFurnaceConfig;
import mod.gottsch.neoforge.everfurnace.core.furnace.IEverFurnaceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
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
            player.sendSystemMessage(singleFurnaceMessage(pending));
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
            player.sendSystemMessage(singleFurnaceMessage(totalCooked));
        } else if (furnaceCount > 1) {
            player.sendSystemMessage(multiFurnaceMessage(furnaceCount, totalCooked));
        }
    }

    // -------------------------------------------------------------------------
    // Message builders
    // -------------------------------------------------------------------------

    /** "[EverFurnace] Your furnace cooked <n> item(s) while you were away." */
    private static Component singleFurnaceMessage(int itemCount) {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500))
                .append(Component.literal("Your furnace cooked ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(String.valueOf(itemCount))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(itemCount == 1 ? " item" : " items")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(" while you were away.")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }

    /** "[EverFurnace] <n> furnaces cooked a combined <m> items while you were away." */
    private static Component multiFurnaceMessage(int furnaceCount, int totalItems) {
        return Component.literal("[EverFurnace] ")
                .withStyle(style -> style.withColor(0xFFA500))
                .append(Component.literal(String.valueOf(furnaceCount))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" furnaces cooked a combined ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(String.valueOf(totalItems))
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" items while you were away.")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));
    }
}