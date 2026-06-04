package wintahh.rageutils.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import wintahh.rageutils.HudConfig;
import wintahh.rageutils.RageUtils;
import wintahh.rageutils.module.RateHUD;
import wintahh.rageutils.ui.GradientText;

import java.util.List;

public class HudCustomizeScreen extends Screen {

    // Purple theme
    private static final int BG_OVERLAY  = 0xAA0D0B14;
    private static final int ACCENT      = 0xFF7B3FBF;
    private static final int ACCENT2     = 0xFFC878FF;
    private static final int HOVER       = 0x337B3FBF;
    private static final int TEXT_GRAY   = 0xFFBBAFCC;
    private static final int PANEL_BG    = 0xEE110E1A;

    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private int hoveredPinIndex = -1;

    private ScaleSlider scaleSlider;

    public HudCustomizeScreen() {
        super(Text.literal("Customize HUD"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int by = height - 30;

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 50, by, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Position"), b -> {
            HudConfig.INSTANCE.hudX = 4;
            HudConfig.INSTANCE.hudY = 4;
            HudConfig.save();
        }).dimensions(cx - 170, by, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Scale"), b -> {
            HudConfig.INSTANCE.hudScale = 1.0f;
            if (scaleSlider != null) scaleSlider.syncFromConfig();
            HudConfig.save();
        }).dimensions(cx + 60, by, 110, 20).build());

        scaleSlider = new ScaleSlider(cx - 80, by - 28, 160, 18);
        addDrawableChild(scaleSlider);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG_OVERLAY);

        int hx = HudConfig.INSTANCE.hudX;
        int hy = HudConfig.INSTANCE.hudY;
        int hw = Math.max(HudConfig.INSTANCE.hudWidth, 60);
        int hh = Math.max(HudConfig.INSTANCE.hudHeight, 16);

        ctx.fill(hx - 2, hy - 2, hx + hw + 2, hy + hh + 2, ACCENT);
        ctx.fill(hx - 1, hy - 1, hx + hw + 1, hy + hh + 1, 0x88C878FF);

        super.render(ctx, mouseX, mouseY, delta);

        String tip = "Drag the widget  \u2502  Slider to resize  \u2502  \u2715 to unpin";
        GradientText.drawCentered(ctx, textRenderer, tip, width / 2, 8, ACCENT, ACCENT2);

        renderPinnedPanel(ctx, mouseX, mouseY);
    }

    private void renderPinnedPanel(DrawContext ctx, int mouseX, int mouseY) {
        List<String> pinned = HudConfig.INSTANCE.pinnedMaterials;
        if (pinned.isEmpty()) return;

        int px = width - 190;
        int py = 28;
        int pw = 180;
        int ph = pinned.size() * 14 + 26;

        ctx.fill(px, py, px + pw, py + ph, PANEL_BG);
        ctx.fill(px, py, px + pw, py + 1, ACCENT);
        ctx.fill(px, py, px + 3, py + ph, ACCENT);
        GradientText.draw(ctx, textRenderer, "Pinned Materials", px + 6, py + 7, ACCENT, ACCENT2);

        hoveredPinIndex = -1;
        for (int i = 0; i < pinned.size(); i++) {
            int ry = py + 22 + i * 14;
            boolean hov = mouseX >= px && mouseX < px + pw && mouseY >= ry && mouseY < ry + 13;
            if (hov) { hoveredPinIndex = i; ctx.fill(px, ry, px + pw, ry + 13, HOVER); }
            int matColor = getMaterialColor(pinned.get(i));
            GradientText.drawMaterial(ctx, textRenderer, pinned.get(i), px + 6, ry + 2, matColor);
            ctx.drawText(textRenderer, "\u2715", px + pw - 14, ry + 2,
                    hov ? 0xFFFF4466 : 0xFF663355, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean focused) {
        double mouseX = click.x(), mouseY = click.y();
        int hx = HudConfig.INSTANCE.hudX, hy = HudConfig.INSTANCE.hudY;
        int hw = Math.max(HudConfig.INSTANCE.hudWidth, 60);
        int hh = Math.max(HudConfig.INSTANCE.hudHeight, 16);

        if (hoveredPinIndex >= 0 && click.button() == 0) {
            List<String> pinned = HudConfig.INSTANCE.pinnedMaterials;
            if (hoveredPinIndex < pinned.size()) { pinned.remove(hoveredPinIndex); HudConfig.save(); return true; }
        }
        if (mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh && click.button() == 0) {
            dragging = true;
            dragOffsetX = (int) mouseX - hx;
            dragOffsetY = (int) mouseY - hy;
            return true;
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            int nx = (int) click.x() - dragOffsetX;
            int ny = (int) click.y() - dragOffsetY;
            nx = Math.max(0, Math.min(nx, width  - Math.max(HudConfig.INSTANCE.hudWidth, 60)));
            ny = Math.max(0, Math.min(ny, height - Math.max(HudConfig.INSTANCE.hudHeight, 16)));
            HudConfig.INSTANCE.hudX = nx;
            HudConfig.INSTANCE.hudY = ny;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        super.removed();
        HudConfig.save();  
    }

    private int getMaterialColor(String name) {
        RateHUD.ResourceType res = RageUtils.RATE_HUD.findResource(name);
        return res != null ? res.color : 0x7B3FBF;
    }

    // Scale slider
    private static class ScaleSlider extends SliderWidget {
        private static final float MIN = 0.5f, MAX = 3.0f;

        ScaleSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), toValue(HudConfig.INSTANCE.hudScale));
            updateMessage();
        }

        private static double toValue(float scale) { return (scale - MIN) / (MAX - MIN); }

        void syncFromConfig() {
            this.value = toValue(HudConfig.INSTANCE.hudScale);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("Scale: %.1fx", fromValue(value))));
        }

        @Override
        protected void applyValue() { HudConfig.INSTANCE.hudScale = fromValue(value); }

        private float fromValue(double v) { return MIN + (float)(v * (MAX - MIN)); }
    }
}
