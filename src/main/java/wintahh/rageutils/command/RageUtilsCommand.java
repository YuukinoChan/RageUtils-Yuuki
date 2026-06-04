package wintahh.rageutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import wintahh.rageutils.HudConfig;
import wintahh.rageutils.RageActionBar;
import wintahh.rageutils.RageUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import wintahh.rageutils.module.Module;
import wintahh.rageutils.module.ModuleRegistry;

public class RageUtilsCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Register the identical command tree under several names so /rageutils, /ru and
        // /yuuki cannot drift apart.
        for (String name : new String[]{"rageutils", "ru", "yuuki"}) {
            dispatcher.register(buildTree(name));
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildTree(String name) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> { printInfo(); return 1; })
            // clientsideblast
            .then(ClientCommandManager.literal("csb")
                .executes(ctx -> {
                    RageUtils.CLIENTSIDE_BLAST.toggle();
                    return 1;
                })
                .then(ClientCommandManager.literal("particles")
                    .executes(ctx -> {
                        HudConfig.INSTANCE.csbParticles = !HudConfig.INSTANCE.csbParticles;
                        HudConfig.save();
                        RageActionBar.show(Text.literal("[RageUtils] CSB Particles: "
                                + (HudConfig.INSTANCE.csbParticles ? "§aON" : "§cOFF")));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("sound")
                    .executes(ctx -> {
                        HudConfig.INSTANCE.csbSound = !HudConfig.INSTANCE.csbSound;
                        HudConfig.save();
                        RageActionBar.show(Text.literal("[RageUtils] CSB Sound: "
                                + (HudConfig.INSTANCE.csbSound ? "§aON" : "§cOFF")));
                        return 1;
                    })))
            // snappyinput
            .then(ClientCommandManager.literal("snap")
                .executes(ctx -> {
                    RageUtils.SNAPPY_INPUT.toggle();
                    return 1;
                }))
            // info — toggle the yaw/pitch crosshair HUD
            .then(ClientCommandManager.literal("info")
                .executes(ctx -> {
                    RageUtils.INFO_HUD.toggle();
                    return 1;
                }))
            // help — prints the mod info/help output
            .then(ClientCommandManager.literal("help")
                .executes(ctx -> { printInfo(); return 1; }))
            // hide armor
            .then(ClientCommandManager.literal("ha")
                .executes(ctx -> {
                    RageUtils.HIDE_ARMOR.toggle();
                    return 1;
                }))
            // ratehud
            .then(ClientCommandManager.literal("rh")
                .then(ClientCommandManager.literal("mine")
                    .executes(ctx -> {
                        RageUtils.RATE_HUD.toggleMining();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("reset")
                    .executes(ctx -> {
                        RageUtils.RATE_HUD.resetCounters();
                        RageUtils.RATE_HUD.resetFishingCounters();
                        RageUtils.RATE_HUD.resetBrushingCounters();
                        RageActionBar.show(Text.literal("[RageUtils] All RateHUD counters reset."));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("mine")
                        .executes(ctx -> {
                            RageUtils.RATE_HUD.resetCounters();
                            RageActionBar.show(Text.literal("[RageUtils] Mining counters reset."));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("fish")
                        .executes(ctx -> {
                            RageUtils.RATE_HUD.resetFishingCounters();
                            RageActionBar.show(Text.literal("[RageUtils] Fishing counters reset."));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("brush")
                        .executes(ctx -> {
                            RageUtils.RATE_HUD.resetBrushingCounters();
                            RageActionBar.show(Text.literal("[RageUtils] Brushing counters reset."));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("fish")
                    .executes(ctx -> {
                        RageUtils.RATE_HUD.toggleFishing();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("brush")
                    .executes(ctx -> {
                        RageUtils.RATE_HUD.toggleBrushing();
                        return 1;
                    })));    }

    private static void printInfo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(
            Text.literal("§c[RageUtils] §rHi " + mc.player.getName().getString() + "! Welcome to RageUtils! The current modules are:"),
            false
        );
        for (Module m : ModuleRegistry.getAll()) {
            mc.player.sendMessage(
                Text.literal("§c[RageUtils] §r" + m.getName() + " §7: §e" + m.getCommand()),
                false
            );
        }
    }
}
