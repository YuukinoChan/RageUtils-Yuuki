package wintahh.rageutils.updater;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class UpdateScreen extends Screen {
    private static final int BG = 0xF0141018;
    private static final int PANEL = 0xE81A1022;
    private static final int ROW = 0xAA1E1428;
    private static final int ACCENT = 0xFF7B3FBF;
    private static final int ACCENT_B = 0xFFFFD166;
    private static final int TEXT = 0xFFE9DEFF;
    private static final int MUTED = 0xFFBBAFCC;

    private final Screen parent;
    private final ModrinthVersionInfo versionInfo;
    private ButtonWidget updateButton;
    private String status = "";
    private double scroll;

    public UpdateScreen(Screen parent, ModrinthVersionInfo versionInfo) {
        super(Text.literal("RageUtils Update Available"));
        this.parent = parent;
        this.versionInfo = versionInfo;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 32;

        updateButton = ButtonWidget.builder(Text.literal("Update Now"), button -> startDownload())
                .dimensions(cx - 154, y, 148, 20)
                .build();
        addDrawableChild(updateButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Later"), button -> closeToParent())
                .dimensions(cx + 6, y, 148, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG);

        int margin = 24;
        int panelLeft = margin;
        int panelRight = width - margin;
        int panelTop = 18;
        int panelBottom = height - 46;

        context.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 2, ACCENT);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 14, ACCENT_B);
        drawVersionRows(context, panelLeft + 16, panelTop + 40);
        drawChangelog(context, panelLeft + 16, panelTop + 82, panelRight - 16, panelBottom - 28);

        if (!status.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, panelBottom - 18, MUTED);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * 18;
        clampScroll();
        return true;
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawVersionRows(DrawContext context, int x, int y) {
        int rowWidth = Math.min(width - 80, 420);
        context.fill(x, y, x + rowWidth, y + 17, ROW);
        context.fill(x, y + 22, x + rowWidth, y + 39, ROW);
        context.drawText(textRenderer, "Current version: " + versionInfo.getCurrentVersion(), x + 8, y + 5, TEXT, false);
        context.drawText(textRenderer, "Latest version: " + versionInfo.getVersionNumber(), x + 8, y + 27, ACCENT_B, false);
    }

    private void drawChangelog(DrawContext context, int left, int top, int right, int bottom) {
        context.drawText(textRenderer, "Changelog", left, top - 14, ACCENT_B, false);
        context.fill(left, top, right, bottom, 0xAA100C18);

        int textWidth = Math.max(40, right - left - 16);
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(versionInfo.getChangelog()), textWidth);
        clampScroll(lines, bottom - top);

        context.enableScissor(left, top, right, bottom);
        int y = top + 8 - (int) scroll;
        for (OrderedText line : lines) {
            if (y >= top - 10 && y <= bottom) {
                context.drawText(textRenderer, line, left + 8, y, MUTED, false);
            }
            y += 11;
        }
        context.disableScissor();
    }

    private void startDownload() {
        if (updateButton != null) {
            updateButton.active = false;
            updateButton.setMessage(Text.literal("Downloading..."));
        }
        status = "Downloading update...";

        UpdateManager.downloadUpdateAsync(versionInfo).whenComplete((result, throwable) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (throwable != null) {
                    status = "Download failed. Please try again later.";
                    resetUpdateButton();
                    return;
                }
                if (result.success()) {
                    mc.setScreen(new RestartRequiredScreen(this));
                    return;
                }
                status = result.message();
                resetUpdateButton();
            });
        });
    }

    private void resetUpdateButton() {
        if (updateButton != null) {
            updateButton.active = true;
            updateButton.setMessage(Text.literal("Update Now"));
        }
    }

    private void clampScroll() {
        int top = 100;
        int bottom = height - 74;
        int width = Math.max(40, this.width - 80);
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(versionInfo.getChangelog()), width);
        clampScroll(lines, bottom - top);
    }

    private void clampScroll(List<OrderedText> lines, int viewportHeight) {
        int contentHeight = lines.size() * 11 + 16;
        int max = Math.max(0, contentHeight - Math.max(1, viewportHeight));
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    private void closeToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
