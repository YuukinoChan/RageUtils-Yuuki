package wintahh.rageutils.updater;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class RestartRequiredScreen extends Screen {
    private static final int BG = 0xF0141018;
    private static final int PANEL = 0xE81A1022;
    private static final int ACCENT = 0xFF7B3FBF;
    private static final int TEXT = 0xFFE9DEFF;
    private static final int MUTED = 0xFFBBAFCC;

    private final Screen parent;
    private final String lineOne;
    private String lineTwo;

    public RestartRequiredScreen(Screen parent) {
        this(parent, "Update downloaded successfully.", "Restart required to apply update.");
    }

    public RestartRequiredScreen(Screen parent, String lineOne, String lineTwo) {
        super(Text.literal("Restart Required"));
        this.parent = parent;
        this.lineOne = lineOne;
        this.lineTwo = lineTwo;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = Math.min(height - 54, height / 2 + 42);

        addDrawableChild(ButtonWidget.builder(Text.literal("Restart Now"), button -> {
            button.active = false;
            lineTwo = "Restarting...";
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!UpdateManager.attemptRelaunchAndStop(mc)) {
                lineTwo = "Please restart manually.";
                button.active = true;
            }
        }).dimensions(cx - 154, y, 148, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Restart Later"), button -> closeToParent())
                .dimensions(cx + 6, y, 148, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG);

        int panelWidth = Math.min(420, width - 36);
        int panelHeight = 132;
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - panelHeight) / 2 - 10);
        context.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        context.fill(left, top, left + panelWidth, top + 2, ACCENT);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 18, 0xFFFFD166);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(lineOne), width / 2, top + 48, TEXT);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(lineTwo), width / 2, top + 66, MUTED);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void closeToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
