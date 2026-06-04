package wintahh.rageutils.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import wintahh.rageutils.HudConfig;
import wintahh.rageutils.RageUtils;
import wintahh.rageutils.materials.MaterialData;
import wintahh.rageutils.module.RateHUD;
import wintahh.rageutils.ui.GradientText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RateHudOverlay {

    private static final int BG        = 0xE0141018;
    private static final int BG_HEADER = 0xF01A1022;
    private static final int ACCENT    = 0xFF7B3FBF;
    private static final int ACCENT2   = 0xFFC878FF;
    private static final int DIVIDER   = 0x88491A7A;
    private static final int TEXT_DONE   = 0xFFAA88FF;
    private static final int TEXT_PAUSED = 0xFF776688;
    // Fishing colours
    private static final int FISH_ACCENT  = 0xFF1fa8c8;
    private static final int FISH_ACCENT2 = 0xFF63deff;
    private static final int PAD = 6;
    private static final int ROW = 9;
    private static final int LINE_SPACING = 2;
    private static final int SECTION_GAP = 3;
    private static final int VALUE_COL_MIN = 88;

    public static void register() {
        HudRenderCallback.EVENT.register(RateHudOverlay::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (mc.currentScreen instanceof wintahh.rageutils.screen.HudCustomizeScreen) return;

        RateHUD hud = RageUtils.RATE_HUD;
        boolean mining   = hud.isMiningEnabled();
        boolean fishing  = hud.isFishingEnabled();
        boolean brushing = hud.isBrushingEnabled();
        List<String> pinned = HudConfig.INSTANCE.pinnedMaterials;

        long now = System.currentTimeMillis();
        // Hide each section if no activity for 2.5 minutes (but keep session data for 15 min)
        if (mining  && hud.getMineLastActivityTime()  > 0 && (now - hud.getMineLastActivityTime())  > RateHUD.HIDE_AFTER_MS) mining  = false;
        if (fishing && hud.getFishLastActivityTime()  > 0 && (now - hud.getFishLastActivityTime())  > RateHUD.HIDE_AFTER_MS) fishing = false;
        if (brushing && hud.getBrushLastActivityTime() > 0 && (now - hud.getBrushLastActivityTime()) > RateHUD.HIDE_AFTER_MS) brushing = false;

        if (!mining && !fishing && !brushing && pinned.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        float scale = Math.max(0.5f, HudConfig.INSTANCE.hudScale);

        // Build content sections
        List<String[]> mineLines    = buildMineLines(hud, mining);
        List<String[]> fishLines    = buildFishLines(hud, fishing);
        List<String[]> brushLines   = buildBrushLines(hud, brushing);
        List<String[]> pinnedRows   = buildPinnedLines(pinned, hud);

        boolean showMining   = mining   && hud.getTopResourcePublic() != null && hud.getStartTime() != 0 && !mineLines.isEmpty();
        boolean showFishing  = fishing  && hud.getFishStartTime() != 0 && !fishLines.isEmpty();
        boolean showBrushing = brushing && hud.getBrushStartTime() != 0 && !brushLines.isEmpty();

        if (!showMining && !showFishing && !showBrushing && pinnedRows.isEmpty()) return;

        int rawW   = computeWidth(tr, mineLines, fishLines, brushLines, pinnedRows);
        int totalH = computeHeight(showMining, mineLines.size(), showFishing, fishLines.size(),
                                   showBrushing, brushLines.size(), pinnedRows.size(), mining);
        if (totalH <= 0) return;

        HudConfig.INSTANCE.hudWidth  = (int) (rawW * scale);
        HudConfig.INSTANCE.hudHeight = (int) (totalH * scale);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(HudConfig.INSTANCE.hudX, HudConfig.INSTANCE.hudY);
        ctx.getMatrices().scale(scale, scale);

        drawRoundedFill(ctx, 0, 0, rawW, totalH, BG);
        ctx.fill(0, 0, 3, totalH, ACCENT);
        ctx.fill(3, 0, 4, totalH, 0x44C878FF);

        int valueCol = rawW - PAD;
        int cy = PAD;

        // Mining section
        if (showMining) {
            int headerBgH = PAD + ROW + SECTION_GAP + 1;
            drawRoundedFill(ctx, 0, 0, rawW, headerBgH, BG_HEADER);

            RateHUD.ResourceType top = hud.getTopResourcePublic();
            int matColor = top.color;

            GradientText.drawMaterial(ctx, tr, top.name, PAD + 4, cy, matColor);
            if (hud.isPaused()) {
                int pausedColor = GradientText.lerpColor(GradientText.resolveMaterialColor(matColor), TEXT_PAUSED, 0.65);
                GradientText.draw(ctx, tr, "PAUSED", valueCol - tr.getWidth("PAUSED"), cy, pausedColor, TEXT_PAUSED);
            }

            int dividerY = cy + ROW + SECTION_GAP;
            ctx.fill(0, dividerY, rawW, dividerY + 1, DIVIDER);
            cy = dividerY + 1 + SECTION_GAP;

            for (int i = 0; i < mineLines.size(); i++) {
                String[] pair = mineLines.get(i);
                GradientText.drawMaterialLabel(ctx, tr, pair[0], PAD + 4, cy, matColor);
                GradientText.drawMaterialValue(ctx, tr, pair[1], valueCol, cy, matColor);
                cy += ROW;
                if (i < mineLines.size() - 1) cy += LINE_SPACING;
            }
            if (!mineLines.isEmpty()) cy += PAD;
        }

        // Fishing section
        if (showFishing) {
            // Divider if mining was drawn above
            if (cy > PAD) {
                ctx.fill(0, cy, rawW, cy + 1, DIVIDER);
                cy += 1 + SECTION_GAP;
            }

            GradientText.draw(ctx, tr, "Fishing", PAD + 4, cy, FISH_ACCENT, FISH_ACCENT2);
            if (hud.isFishingPaused()) {
                int pausedColor = GradientText.lerpColor(0x1fa8c8, TEXT_PAUSED, 0.65);
                GradientText.draw(ctx, tr, "PAUSED", valueCol - tr.getWidth("PAUSED"), cy, pausedColor, TEXT_PAUSED);
            }
            cy += ROW + LINE_SPACING;

            ctx.fill(0, cy, rawW, cy + 1, DIVIDER);
            cy += 1 + SECTION_GAP;

            for (int i = 0; i < fishLines.size(); i++) {
                String[] row = fishLines.get(i);
                int rowColor = row.length > 2 ? Integer.parseInt(row[2], 16) : 0x1fa8c8;
                GradientText.drawMaterialLabel(ctx, tr, row[0], PAD + 4, cy, rowColor);
                GradientText.drawMaterialValue(ctx, tr, row[1], valueCol, cy, rowColor);
                cy += ROW;
                if (i < fishLines.size() - 1) cy += LINE_SPACING;
            }
            cy += PAD;
        }

        // Brushing section
        if (showBrushing) {
            if (cy > PAD) {
                ctx.fill(0, cy, rawW, cy + 1, DIVIDER);
                cy += 1 + SECTION_GAP;
            }

            GradientText.draw(ctx, tr, "Brushing", PAD + 4, cy, 0xFFB833, 0xFFDC78);
            if (hud.isBrushingPaused()) {
                int pausedColor = GradientText.lerpColor(0xFFB833, TEXT_PAUSED, 0.65);
                GradientText.draw(ctx, tr, "PAUSED", valueCol - tr.getWidth("PAUSED"), cy, pausedColor, TEXT_PAUSED);
            }
            cy += ROW + LINE_SPACING;

            ctx.fill(0, cy, rawW, cy + 1, DIVIDER);
            cy += 1 + SECTION_GAP;

            for (int i = 0; i < brushLines.size(); i++) {
                String[] row = brushLines.get(i);
                int rowColor = row.length > 2 ? Integer.parseInt(row[2], 16) : 0xFFB833;
                GradientText.drawMaterialLabel(ctx, tr, row[0], PAD + 4, cy, rowColor);
                GradientText.drawMaterialValue(ctx, tr, row[1], valueCol, cy, rowColor);
                cy += ROW;
                if (i < brushLines.size() - 1) cy += LINE_SPACING;
            }
            cy += PAD;
        }

        // Pinned section
        if (!pinnedRows.isEmpty()) {
            if (cy > PAD) {
                ctx.fill(0, cy, rawW, cy + 1, DIVIDER);
                cy += 1 + SECTION_GAP;
            }
            if (!showMining && !showFishing && !showBrushing) {
                GradientText.draw(ctx, tr, "Pinned", PAD + 4, cy, ACCENT, ACCENT2);
                cy += ROW + LINE_SPACING;
            }
            for (int i = 0; i < pinnedRows.size(); i++) {
                String[] row = pinnedRows.get(i);
                int matColor = Integer.parseInt(row[2], 16);
                GradientText.drawMaterial(ctx, tr, row[0], PAD + 4, cy, matColor);
                if ("DONE".equals(row[1])) {
                    GradientText.draw(ctx, tr, row[1], valueCol - tr.getWidth(row[1]), cy, TEXT_DONE, ACCENT2);
                } else {
                    GradientText.drawMaterialValue(ctx, tr, row[1], valueCol, cy, matColor);
                }
                cy += ROW;
                if (i < pinnedRows.size() - 1) cy += LINE_SPACING;
            }
            cy += PAD;
        }

        ctx.getMatrices().popMatrix();
    }

    private static List<String[]> buildMineLines(RateHUD hud, boolean mining) {
        List<String[]> out = new ArrayList<>();
        if (!mining) return out;
        if (hud.getTopResourcePublic() == null || hud.getStartTime() == 0) return out;

        String prefix = hud.getDisplayCompressionPrefix();
        out.add(new String[]{"Blocks/h", hud.getDisplayBlocksPerHour()});
        out.add(new String[]{prefix + "/h", String.format("%.2f", hud.getDisplayCompressionPerHour())});
        out.add(new String[]{prefix + " mined", String.format("%.2f", hud.getDisplayCompressionMined())});
        return out;
    }

    private static final int[] RARITY_COLORS = { 0xDDDDDD, 0x55CC55, 0x4488EE, 0xAA44EE, 0xFF8800 };

    private static List<String[]> buildFishLines(RateHUD hud, boolean fishing) {
        List<String[]> out = new ArrayList<>();
        if (!fishing || hud.getFishStartTime() == 0) return out;

        String teal = "1fa8c8";
        out.add(new String[]{"Tokens/h",   String.format("%.2f", hud.getDisplayFishTokensPerHour()), teal});
        out.add(new String[]{"Total tkns", String.format("%.2f", hud.getDisplayFishTotalTokens()),   teal});

        Map<String, Long> counts = hud.getDisplayFishCounts();
        int ri = 0;
        for (RateHUD.FishType ft : RateHUD.FISH_TYPES) {
            long c = counts.getOrDefault(ft.name, 0L);
            String label = ft.name.replace(" Fish", "");
            String hex = String.format("%06X", RARITY_COLORS[Math.min(ri, RARITY_COLORS.length - 1)]);
            out.add(new String[]{label, String.valueOf(c), hex});
            ri++;
        }
        return out;
    }

    private static List<String[]> buildBrushLines(RateHUD hud, boolean brushing) {
        List<String[]> out = new ArrayList<>();
        if (!brushing || hud.getBrushStartTime() == 0) return out;

        String gold = "FFB833";
        out.add(new String[]{"Leg/h",      String.format("%.2f", hud.getDisplayBrushLegendaryPerHour()), gold});
        out.add(new String[]{"Total leg",  String.format("%.2f", hud.getDisplayBrushTotalLegendary()),   gold});

        Map<String, Long> counts = hud.getDisplayBrushCounts();
        int ri = 0;
        for (RateHUD.ArtifactType at : RateHUD.ARTIFACT_TYPES) {
            long c = counts.getOrDefault(at.name, 0L);
            String label = at.name.replace(" Artifact", "");
            String hex = String.format("%06X", RARITY_COLORS[Math.min(ri, RARITY_COLORS.length - 1)]);
            out.add(new String[]{label, String.valueOf(c), hex});
            ri++;
        }
        long golem = counts.getOrDefault("Golem Essence", 0L);
        out.add(new String[]{"Golem Ess", String.valueOf(golem), "CC8844"});
        return out;
    }

    private static List<String[]> buildPinnedLines(List<String> pinned, RateHUD hud) {
        List<String[]> out = new ArrayList<>();
        for (String matName : pinned) {
            RateHUD.ResourceType res = hud.findResource(matName);
            String colorHex = res != null ? String.format("%06X", res.color & 0xFFFFFF) : "7B3FBF";

            if (res == null) {
                out.add(new String[]{matName, "?", colorHex});
                continue;
            }

            if (res.isRawItem()) {
                long rawCount = hud.getInventoryBaseUnits(res);
                double needed = MaterialData.computeDisplayTotal(matName);
                String value;
                if (needed > 0) {
                    value = rawCount >= (long) Math.ceil(needed)
                            ? "DONE"
                            : rawCount + " / " + (long) Math.ceil(needed);
                } else {
                    value = String.valueOf(rawCount);
                }
                out.add(new String[]{matName, value, colorHex});
                continue;
            }

            if (res.getMaxCompressionValue() <= 0) {
                out.add(new String[]{matName, "?", colorHex});
                continue;
            }

            String tier   = res.getMaxCompressionName();
            double needed = MaterialData.computeDisplayTotal(matName);
            double mined  = hud.getLiveCompressedCount(res);

            String value;
            if (needed > 0) {
                value = mined + 1e-6 >= needed
                        ? "DONE"
                        : String.format("%.2f / %.0f %s", mined, Math.ceil(needed), tier);
            } else {
                value = String.format("%.2f %s", mined, tier);
            }
            out.add(new String[]{matName, value, colorHex});
        }
        return out;
    }

    private static int computeHeight(boolean mining, int mineStatLines,
                                     boolean fishing, int fishStatLines,
                                     boolean brushing, int brushStatLines,
                                     int pinnedLines, boolean miningEnabled) {
        int h = 0;

        if (mining) {
            h = PAD + ROW + SECTION_GAP + 1 + SECTION_GAP;
            if (mineStatLines > 0) {
                h += mineStatLines * ROW + (mineStatLines - 1) * LINE_SPACING + PAD;
            }
        }

        if (fishing) {
            if (h > 0) h += 1 + SECTION_GAP;
            else h = PAD;
            h += ROW + LINE_SPACING + 1 + SECTION_GAP;
            if (fishStatLines > 0) {
                h += fishStatLines * ROW + (fishStatLines - 1) * LINE_SPACING + PAD;
            }
        }

        if (brushing) {
            if (h > 0) h += 1 + SECTION_GAP;
            else h = PAD;
            h += ROW + LINE_SPACING + 1 + SECTION_GAP;
            if (brushStatLines > 0) {
                h += brushStatLines * ROW + (brushStatLines - 1) * LINE_SPACING + PAD;
            }
        }

        if (pinnedLines > 0) {
            if (h > 0) {
                h += 1 + SECTION_GAP;
            } else {
                h = PAD;
            }
            if (!mining && !fishing && !brushing) {
                h += ROW + LINE_SPACING;
            }
            h += pinnedLines * ROW + (pinnedLines - 1) * LINE_SPACING + PAD;
        }
        return h;
    }

    private static int computeWidth(TextRenderer tr, List<String[]> mineLines,
                                    List<String[]> fishLines, List<String[]> brushLines,
                                    List<String[]> pinnedRows) {
        int max = 148;
        for (String[] p : mineLines) {
            max = Math.max(max, PAD + 4 + tr.getWidth(p[0]) + VALUE_COL_MIN + tr.getWidth(p[1]) + PAD);
        }
        for (String[] p : fishLines) {
            max = Math.max(max, PAD + 4 + tr.getWidth(p[0]) + VALUE_COL_MIN + tr.getWidth(p[1]) + PAD);
        }
        for (String[] p : brushLines) {
            max = Math.max(max, PAD + 4 + tr.getWidth(p[0]) + VALUE_COL_MIN + tr.getWidth(p[1]) + PAD);
        }
        for (String[] p : pinnedRows) {
            max = Math.max(max, PAD + 4 + tr.getWidth(p[0]) + VALUE_COL_MIN + tr.getWidth(p[1]) + PAD);
        }
        return max;
    }

    private static void drawRoundedFill(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
        ctx.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x11000000);
    }
}
