package wintahh.rageutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Owns RageUtils feature-status actionbar messages (CSB/RateHUD toggles).
 *
 * The server and other mods also write to the actionbar. To keep our ON/OFF
 * feedback visible without permanently overriding gameplay actionbar text, we:
 *  - show immediately when the actionbar is otherwise quiet, otherwise defer;
 *  - keep only the latest deferred status (stale toggle states are dropped);
 *  - replay the deferred status once the actionbar has been quiet for a while;
 *  - expire the deferred status so we never replay something long obsolete.
 */
public final class RageActionBar {

    private static final long QUIET_MS = 5000;
    private static final long REPLAY_DELAY_MS = 5000;
    private static final long EXPIRY_MS = 12000;

    private static Text pendingStatus;
    private static long pendingCreatedTime;
    private static long lastExternalTime;
    private static boolean awaitingReplay;
    private static volatile boolean suppressDetection;

    private RageActionBar() {}

    public static void show(Text status) {
        long now = System.currentTimeMillis();
        pendingStatus = status;
        pendingCreatedTime = now;
        if (now - lastExternalTime > QUIET_MS) {
            displayNow(status);
            awaitingReplay = false;
        } else {
            awaitingReplay = true;
        }
    }

    private static void displayNow(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        suppressDetection = true;
        try {
            mc.inGameHud.setOverlayMessage(text, false);
        } finally {
            suppressDetection = false;
        }
    }

    /** Immediately clears the actionbar with no fade. Use when a module is disabled. */
    public static void clearNow() {
        pendingStatus = null;
        awaitingReplay = false;
        displayNow(Text.literal(""));
    }

    /** Called from the InGameHud mixin whenever an actionbar message is set. */
    public static void onExternalActionbar(Text msg) {
        if (suppressDetection) return;
        long now = System.currentTimeMillis();
        lastExternalTime = now;
        if (pendingStatus != null && now - pendingCreatedTime < EXPIRY_MS) {
            awaitingReplay = true;
        }
    }

    public static void tick() {
        if (!awaitingReplay || pendingStatus == null) return;
        long now = System.currentTimeMillis();
        if (now - pendingCreatedTime > EXPIRY_MS) {
            pendingStatus = null;
            awaitingReplay = false;
            return;
        }
        if (now - lastExternalTime >= REPLAY_DELAY_MS) {
            displayNow(pendingStatus);
            awaitingReplay = false;
        }
    }
}
