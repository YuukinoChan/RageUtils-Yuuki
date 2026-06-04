package wintahh.rageutils.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import wintahh.rageutils.RageActionBar;

public abstract class Module {
    private boolean enabled = false;
    private final String name;
    private final String command;

    public Module(String name, String command) {
        this.name = name;
        this.command = command;
    }

    public void toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            RageActionBar.show(
                Text.literal("[RageUtils] " + name + ": " + (enabled ? "§aON" : "§cOFF"))
            );
        }
    }

    protected void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void enable() { this.enabled = true; }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getCommand() { return command; }
}