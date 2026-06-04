package wintahh.rageutils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import wintahh.rageutils.command.RageUtilsCommand;
import wintahh.rageutils.hud.RateHudOverlay;
import wintahh.rageutils.materials.MaterialDataUpdater;
import wintahh.rageutils.module.*;
import wintahh.rageutils.updater.ModrinthUpdater;

public class RageUtils implements ClientModInitializer {

    public static final String MOD_ID = "rageutils";

    public static final ClientSideBlast CLIENTSIDE_BLAST = new ClientSideBlast();
    public static final RateHUD         RATE_HUD          = new RateHUD();
    public static final InfoHUD         INFO_HUD          = new InfoHUD();
    public static final HideArmor       HIDE_ARMOR        = new HideArmor();
    public static final SnappyInput     SNAPPY_INPUT      = new SnappyInput();

    @Override
    public void onInitializeClient() {
        HudConfig.load();

        RageUtilsCommand.register();
        KeyBinds.register();
        RateHudOverlay.register();
        MaterialDataUpdater.register();
        ModrinthUpdater.register();

        ModuleRegistry.register(CLIENTSIDE_BLAST);

        ModuleRegistry.register(RATE_HUD);
        RATE_HUD.registerEvents();

        ModuleRegistry.register(INFO_HUD);
        INFO_HUD.registerEvents();

        ModuleRegistry.register(HIDE_ARMOR);
        ModuleRegistry.register(SNAPPY_INPUT);

        ClientTickEvents.END_CLIENT_TICK.register(client -> MaterialDataUpdater.deliverPendingMessage());
        ClientTickEvents.END_CLIENT_TICK.register(client -> RageActionBar.tick());

        // Enable core features by default
        RATE_HUD.toggleMining();
        CLIENTSIDE_BLAST.toggle();
        SNAPPY_INPUT.enable();

        // Restore persisted module states from config
        if (HudConfig.INSTANCE.hideArmor) HIDE_ARMOR.enable();
    }
}
