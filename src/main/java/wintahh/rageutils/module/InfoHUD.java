package wintahh.rageutils.module;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import wintahh.rageutils.RageActionBar;

public class InfoHUD extends Module {

    public InfoHUD() {
        super("InfoHUD", "/yuuki info");
    }

    private boolean wasEnabled = false;

    public void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!isEnabled()) {
                if (wasEnabled) {
                    RageActionBar.clearNow();
                    wasEnabled = false;
                }
                return;
            }
            wasEnabled = true;

            float yaw   = client.player.getYaw();
            float pitch = client.player.getPitch();

            // Normalize yaw to [-180, 180]
            yaw = ((yaw % 360) + 360) % 360;
            if (yaw > 180) yaw -= 360;

            client.player.sendMessage(
                Text.literal(String.format("§7Yaw: §f%.1f  §7Pitch: §f%.1f", yaw, pitch)),
                true
            );
        });
    }
}
