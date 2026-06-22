package mod.gottsch.neoforge.everfurnace.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author by Mark Gottschling on 3/31/2026
 */
public class EverFurnaceConfig {

    public static final Common COMMON;
    public static final Client CLIENT;

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final Pair<Common, ModConfigSpec> commonPair =
                new ModConfigSpec.Builder().configure(Common::new);
        COMMON      = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        final Pair<Client, ModConfigSpec> clientPair =
                new ModConfigSpec.Builder().configure(Client::new);
        CLIENT      = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    // =========================================================================
    // Common config  (everfurnace-common.toml)
    // =========================================================================

    public static class Common {

        public final ModConfigSpec.BooleanValue catchupEnabled;
        public final ModConfigSpec.LongValue    maxCatchupTicks;
        public final ModConfigSpec.IntValue     minDeltaThreshold;

        public final ModConfigSpec.BooleanValue brewingStandCatchupEnabled;

        public final ModConfigSpec.BooleanValue notifyPlayerOnCatchup;
        public final ModConfigSpec.LongValue    notificationCooldownTicks;
        public final ModConfigSpec.BooleanValue notifyOnLogin;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("EverFurnace common settings — apply on both server and singleplayer.")
                    .push("catchup");

            catchupEnabled = builder
                    .comment("Master toggle for the catch-up mechanic.")
                    .define("catchupEnabled", true);

            maxCatchupTicks = builder
                    .comment("Maximum ticks of catch-up simulated per load event (1 in-game day = 24 000).",
                            "Range: 1 – 192 000")
                    .defineInRange("maxCatchupTicks", 24_000L, 1L, 192_000L);

            minDeltaThreshold = builder
                    .comment("Minimum tick gap before catch-up fires.",
                            "Below this the furnace is considered actively ticking.",
                            "Range: 1 – 72 000")
                    .defineInRange("minDeltaThreshold", 20, 1, 72_000);

            brewingStandCatchupEnabled = builder
                    .comment("Per-block toggle for brewing stand catch-up.",
                            "Brewing potions offline (especially with automated, hopper-fed stands)",
                            "has a different balance profile than smelting, so it can be disabled",
                            "independently while leaving furnace/campfire catch-up on.",
                            "Requires the master 'catchupEnabled' to also be true.",
                            "Default: true")
                    .define("brewingStandCatchupEnabled", true);

            builder.pop().push("notifications");

            notifyPlayerOnCatchup = builder
                    .comment("Send a chat message when items are cooked offline.")
                    .define("notifyPlayerOnCatchup", true);

            notificationCooldownTicks = builder
                    .comment("Minimum ticks between notification arms for a single furnace.",
                            "Items cooked during the cooldown are still counted — they are batched",
                            "into the existing pending notification rather than dropped.",
                            "Set to 0 to disable the cooldown (notify on every catch-up pass).",
                            "Range: 0 – 72 000  |  Default: 200 (10 seconds)")
                    .defineInRange("notificationCooldownTicks", 200L, 0L, 72_000L);

            notifyOnLogin = builder
                    .comment("Deliver pending furnace notifications when the player logs in,",
                            "in addition to when they open a furnace.",
                            "Recommended on multiplayer servers where catch-up may fire before",
                            "the owning player has connected.",
                            "Default: true")
                    .define("notifyOnLogin", true);

            builder.pop();
        }
    }

    // =========================================================================
    // Client config  (everfurnace-client.toml)
    // =========================================================================

    public static class Client {

        /** Spawn a flame/smoke particle burst when catch-up completes (Feature D). */
        public final ModConfigSpec.BooleanValue particleBurstEnabled;

        /** Play a furnace crackle sound when catch-up completes (Feature G). */
        public final ModConfigSpec.BooleanValue soundCueEnabled;

        /**
         * Immediately sync the furnace LIT state on the client after catch-up,
         * so the light level updates without waiting for the next server block
         * update (Feature H).
         */
        public final ModConfigSpec.BooleanValue lightFlickerEnabled;

        Client(ModConfigSpec.Builder builder) {
            builder.comment("EverFurnace client settings — ignored by dedicated servers.")
                    .push("visuals");

            particleBurstEnabled = builder
                    .comment("Spawn a flame/smoke particle burst when catch-up completes",
                            "and at least one item was cooked.")
                    .define("particleBurstEnabled", true);

            soundCueEnabled = builder
                    .comment("Play a furnace crackle sound when catch-up completes",
                            "and at least one item was cooked.")
                    .define("soundCueEnabled", true);

            lightFlickerEnabled = builder
                    .comment("Immediately sync the furnace LIT block state on the client",
                            "when catch-up completes, so the light level updates without",
                            "waiting for the next server block update.")
                    .define("lightFlickerEnabled", true);

            builder.pop();
        }
    }
}
