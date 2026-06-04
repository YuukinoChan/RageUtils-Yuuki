package wintahh.rageutils.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class GradientText {

    private static final int FALLBACK = 0xFF7B3FBF;
    private static final int FALLBACK_LIGHT = 0xFFC878FF;
    private static final int MUTED_LABEL = 0xFFBBAFCC;

    private GradientText() {}

    public static void drawMaterial(DrawContext ctx, TextRenderer tr, String text, int x, int y, int materialRgb) {
        int base = withAlpha(materialRgb);
        draw(ctx, tr, text, x, y, base, lighten(base, 0.45));
    }

    public static void drawMaterialRight(DrawContext ctx, TextRenderer tr, String text, int rightX, int y, int materialRgb) {
        drawMaterial(ctx, tr, text, rightX - tr.getWidth(text), y, materialRgb);
    }

    public static void drawMaterialLabel(DrawContext ctx, TextRenderer tr, String text, int x, int y, int materialRgb) {
        int base = withAlpha(materialRgb);
        draw(ctx, tr, text, x, y, lerpColor(base, MUTED_LABEL, 0.55), lighten(base, 0.25));
    }

    public static void drawMaterialValue(DrawContext ctx, TextRenderer tr, String text, int rightX, int y, int materialRgb) {
        int base = withAlpha(materialRgb);
        draw(ctx, tr, text, rightX - tr.getWidth(text), y, lighten(base, 0.2), lighten(base, 0.65));
    }

    public static void drawCentered(DrawContext ctx, TextRenderer tr, String text, int centerX, int y, int start, int end) {
        draw(ctx, tr, text, centerX - tr.getWidth(text) / 2, y, start, end);
    }

    public static void draw(DrawContext ctx, TextRenderer tr, String text, int x, int y, int start, int end) {
        if (text.isEmpty()) return;
        int cx = x;
        int len = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            double p = (double) i / len;
            ctx.drawText(tr, ch, cx, y, lerpColor(start, end, p), false);
            cx += tr.getWidth(ch);
        }
    }

    public static int withAlpha(int rgb) {
        return rgb | 0xFF000000;
    }

    public static int resolveMaterialColor(int materialRgb) {
        return materialRgb != 0 ? withAlpha(materialRgb) : FALLBACK;
    }

    public static int lerpColor(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((ar + (int) ((br - ar) * t)) << 16)
                | ((ag + (int) ((bg - ag) * t)) << 8)
                | (ab + (int) ((bb - ab) * t));
    }

    public static int lighten(int c, double amount) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000
                | ((r + (int) ((255 - r) * amount)) << 16)
                | ((g + (int) ((255 - g) * amount)) << 8)
                | (b + (int) ((255 - b) * amount));
    }
}
