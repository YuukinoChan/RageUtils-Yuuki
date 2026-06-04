package wintahh.rageutils.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import wintahh.rageutils.HudConfig;
import wintahh.rageutils.materials.MaterialData;
import wintahh.rageutils.materials.MaterialDataUpdater;
import wintahh.rageutils.materials.MaterialData.GearFilter;
import wintahh.rageutils.materials.MaterialData.MaterialEntry;
import wintahh.rageutils.materials.MaterialData.TotalMode;
import net.minecraft.client.gui.Click;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class MaterialsScreen extends Screen {
    private static final String MATERIALS_URL = "https://ragemines.com/materials.html";
    private static final int ROW_HEIGHT = 42;
    private static final int HEADER_HEIGHT = 116;
    private static final int FOOTER_HEIGHT = 18;
    private static final int BG_MAIN    = 0xE0141018;
    private static final int BG_HEADER  = 0xF01A1022;
    private static final int BG_ROW_A   = 0xAA1E1428;
    private static final int BG_ROW_B   = 0xAA161020;
    private static final int ACCENT_LINE = 0xFF7B3FBF;

    private TextFieldWidget searchBox;
    private TextFieldWidget headAmountBox;
    // Multi-select filter set. Never empty; always contains at least GearFilter.ALL OR one-or-more specific filters.
    private EnumSet<GearFilter> gearFilters = EnumSet.of(GearFilter.ALL);
    private TotalMode totalMode = TotalMode.GEAR_AND_COHESIVE;
    private String biomeFilter = "All";
    private String query = "";
    private int worldHeads = 1;
    private double scroll;

    public MaterialsScreen() {
        super(Text.literal("RageMines Materials"));
        worldHeads  = HudConfig.INSTANCE.materialsWorldHeads;
        gearFilters = EnumSet.copyOf(HudConfig.INSTANCE.materialsGearFilters);
        totalMode   = HudConfig.INSTANCE.materialsTotalMode;
    }

    @Override
    protected void init() {
        MaterialDataUpdater.onMaterialsScreenOpened();
        clearChildren();
        int left = 18;
        int searchWidth = Math.min(220, width - 36);

        searchBox = new TextFieldWidget(textRenderer, left, 30, searchWidth, 20, Text.literal("Search materials"));
        searchBox.setPlaceholder(Text.literal("Search materials"));
        searchBox.setChangedListener(value -> {
            query = value.toLowerCase(Locale.ROOT).trim();
            scroll = 0;
        });
        addDrawableChild(searchBox);

        // ── Gear filter buttons (multi-toggle) ────────────────────────────
        int x = left;
        int y = 56;
        for (GearFilter filter : GearFilter.values()) {
            int buttonWidth = filter == GearFilter.ALL ? 42 : 58;
            boolean selected = gearFilters.contains(filter);
            // Prefix selected buttons with "§e" (yellow) so the player can see what's active
            String label = (selected ? "§e" : "") + filter.label;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                if (filter == GearFilter.ALL) {
                    // ALL resets to only ALL
                    gearFilters.clear();
                    gearFilters.add(GearFilter.ALL);
                } else {
                    // Remove ALL when toggling a specific filter
                    gearFilters.remove(GearFilter.ALL);
                    if (gearFilters.contains(filter)) {
                        // Deselect — fall back to ALL if nothing left
                        gearFilters.remove(filter);
                        if (gearFilters.isEmpty()) gearFilters.add(GearFilter.ALL);
                    } else {
                        gearFilters.add(filter);
                    }
                }
                syncHudConfig();
                scroll = 0;
                rebuildControls();
            }).dimensions(x, y, buttonWidth, 20).build());
            x += buttonWidth + 4;
        }

        x = left;
        y = 82;
        addBiomeButton("All", x, y, 42);
        x += 46;
        for (String biome : MaterialData.biomes()) {
            int buttonWidth = Math.max(50, textRenderer.getWidth(biome) + 18);
            if (x + buttonWidth > width - 18) break;
            addBiomeButton(biome, x, y, buttonWidth);
            x += buttonWidth + 4;
        }

        int right = width - 18;
        addDrawableChild(ButtonWidget.builder(Text.literal("Website"), button ->
            Util.getOperatingSystem().open(MATERIALS_URL)
        ).dimensions(right - 74, 30, 74, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Load file"), button ->
            openLoadFileDialog()
        ).dimensions(right - 74 - 4 - 74, 30, 74, 20).build());

        int headX = right - 276;
        int headY = 56;
        for (int count : new int[]{0, 1, 2, 4, 8}) {
            addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(count)), button -> {
                worldHeads = count;
                syncHudConfig();
                if (headAmountBox != null) headAmountBox.setText(String.valueOf(worldHeads));
                rebuildControls();
            }).dimensions(headX, headY, 30, 20).build());
            headX += 34;
        }

        headAmountBox = new TextFieldWidget(textRenderer, headX + 4, headY, 64, 20, Text.literal("Custom amount"));
        headAmountBox.setMaxLength(5);
        headAmountBox.setTextPredicate(value -> value.isEmpty() || value.matches("\\d+"));
        headAmountBox.setText(String.valueOf(worldHeads));
        headAmountBox.setPlaceholder(Text.literal("Custom"));
        headAmountBox.setChangedListener(value -> {
            if (value.isEmpty()) return;
            try {
                worldHeads = Math.max(0, Integer.parseInt(value));
                syncHudConfig();
            } catch (NumberFormatException ignored) {
                worldHeads = 0;
                syncHudConfig();
            }
        });
        addDrawableChild(headAmountBox);

        int modeX = right - 276;
        int modeY = 82;
        for (TotalMode mode : TotalMode.values()) {
            int buttonWidth = mode == TotalMode.GEAR_AND_COHESIVE ? 84 : 74;
            addDrawableChild(ButtonWidget.builder(Text.literal(mode.buttonLabel), button -> {
                totalMode = mode;
                syncHudConfig();
                rebuildControls();
            }).dimensions(modeX, modeY, buttonWidth, 20).build());
            modeX += buttonWidth + 4;
        }
    }

    private void addBiomeButton(String biome, int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(Text.literal(biome), button -> {
            biomeFilter = biome;
            scroll = 0;
            rebuildControls();
        }).dimensions(x, y, width, 20).build());
    }

    private void rebuildControls() {
        String value = searchBox == null ? "" : searchBox.getText();
        init();
        searchBox.setText(value);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG_MAIN);
        context.fill(0, 0, width, HEADER_HEIGHT - 8, BG_HEADER);
        context.fill(0, HEADER_HEIGHT - 9, width, HEADER_HEIGHT - 8, ACCENT_LINE);

        super.render(context, mouseX, mouseY, delta);

        drawGradientText(context, title.getString(), 18, 12, 0xFFFF6B5F, 0xFFFFD166);
        drawGradientText(context, "Cohesive: " + worldHeads, width - 276, 42, 0xFFFFD166, 0xFFFF8A5C);

        List<MaterialEntry> rows = filteredRows();
        clampScroll(rows.size());
        renderRows(context, rows);

        String footer = rows.size() + " materials shown  |  Mouse wheel scroll  |  Esc closes";
        drawText(context, footer, 18, height - 14, 0xFF888888);
    }

    private void renderRows(DrawContext context, List<MaterialEntry> rows) {
        int top    = HEADER_HEIGHT;
        int bottom = height - FOOTER_HEIGHT;
        int first  = Math.max(0, (int) (scroll / ROW_HEIGHT));
        int y      = top - (int) (scroll % ROW_HEIGHT);

        for (int i = first; i < rows.size() && y < bottom; i++) {
            MaterialEntry entry = rows.get(i);
            if (y + ROW_HEIGHT > top) renderRow(context, entry, y, i % 2 == 0);
            y += ROW_HEIGHT;
        }

        if (rows.isEmpty()) {
            String empty = "No materials match your filters.";
            context.drawText(textRenderer, empty, (width - textRenderer.getWidth(empty)) / 2, top + 32, 0xFFBBAFCC, false);
        }
    }

    private void renderRow(DrawContext context, MaterialEntry entry, int y, boolean alternate) {
        int left  = 18;
        int right = width - 18;
        int fill  = alternate ? BG_ROW_A : BG_ROW_B;
        context.fill(left, y, right, y + ROW_HEIGHT - 4, fill);
        context.fill(left, y, left + 3, y + ROW_HEIGHT - 4, biomeColor(entry.biome()));

        drawGradientText(context, entry.name(), left + 10, y + 6, biomeGradientStart(entry.biome()), biomeGradientEnd(entry.biome()));
        drawText(context, entry.biome(), left + 10, y + 22, 0xFF9955CC);

        int statsX = Math.max(175, width / 3);

        // ── Stat display: ALL → full grid; 1 specific → centred; multi → only selected slots ──
        if (gearFilters.contains(GearFilter.ALL)) {
            drawStat(context, "Armor",    entry.armor(),    statsX,       y + 6);
            drawStat(context, "Pick",     entry.pick(),     statsX + 84,  y + 6);
            drawStat(context, "Sword",    entry.sword(),    statsX + 168, y + 6);
            drawStat(context, "Bow",      entry.bow(),      statsX,       y + 22);
            drawStat(context, "Talisman", entry.talisman(), statsX + 84,  y + 22);
        } else if (gearFilters.size() == 1) {
            GearFilter only = gearFilters.iterator().next();
            drawStat(context, only.label, entry.valueFor(only), statsX, y + 14);
        } else {
            // Multi-select: render only the chosen slots in their fixed positions
            if (gearFilters.contains(GearFilter.ARMOR))    drawStat(context, "Armor",    entry.armor(),    statsX,       y + 6);
            if (gearFilters.contains(GearFilter.PICK))     drawStat(context, "Pick",     entry.pick(),     statsX + 84,  y + 6);
            if (gearFilters.contains(GearFilter.SWORD))    drawStat(context, "Sword",    entry.sword(),    statsX + 168, y + 6);
            if (gearFilters.contains(GearFilter.BOW))      drawStat(context, "Bow",      entry.bow(),      statsX,       y + 22);
            if (gearFilters.contains(GearFilter.TALISMAN)) drawStat(context, "Talisman", entry.talisman(), statsX + 84,  y + 22);
        }

        double total      = MaterialData.computeDisplayTotal(entry, gearFilters, totalMode, worldHeads);
        double headsTotal = entry.materialsPerHead() * worldHeads;
        String totals = "Total " + format(total);
        if (totalMode == TotalMode.GEAR_AND_COHESIVE && entry.materialsPerHead() > 0 && worldHeads > 0) {
            totals += "  |  heads " + format(headsTotal);
        }
        totals += "  |  " + stackString(total);
        drawText(context, totals, right - textRenderer.getWidth(totals) - 8, y + 14, 0xFFFFD166);

        boolean pinned  = HudConfig.INSTANCE.pinnedMaterials.contains(entry.name());
        String pinIcon  = pinned ? "\u2605" : "\u2606";
        int    pinColor = pinned ? 0xFFFFD166 : 0xFF665555;
        drawText(context, pinIcon, right - 14, y + 6, pinColor);
    }

    private void drawStat(DrawContext context, String label, double value, int x, int y) {
        drawText(context, label + ": " + format(value), x, y, value > 0 ? 0xFFDDDDDD : 0xFF666666);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color, boolean shadow) {
        context.drawText(textRenderer, text, x, y, color, shadow);
    }

    private void drawGradientText(DrawContext context, String text, int x, int y, int startColor, int endColor) {
        int cursor = x;
        int visibleChars = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            String character = String.valueOf(text.charAt(i));
            double progress = (double) i / visibleChars;
            context.drawText(textRenderer, character, cursor, y, lerpColor(startColor, endColor, progress), false);
            cursor += textRenderer.getWidth(character);
        }
    }

    private List<MaterialEntry> filteredRows() {
        List<MaterialEntry> rows = new ArrayList<>();
        for (MaterialEntry entry : MaterialData.materials()) {
            if (!biomeFilter.equals("All") && !entry.biome().equals(biomeFilter)) continue;
            if (!query.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            rows.add(entry);
        }
        return rows;
    }

    private void clampScroll(int rowCount) {
        int viewport  = Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT);
        int maxScroll = Math.max(0, rowCount * ROW_HEIGHT - viewport);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * 24;
        clampScroll(filteredRows().size());
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean focused) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0) {
            int top    = HEADER_HEIGHT;
            int bottom = height - FOOTER_HEIGHT;
            List<MaterialEntry> rows = filteredRows();
            int first = Math.max(0, (int)(scroll / ROW_HEIGHT));
            int y     = top - (int)(scroll % ROW_HEIGHT);
            int right = width - 18;
            for (int i = first; i < rows.size() && y < bottom; i++) {
                if (mouseX >= right - 16 && mouseX <= right && mouseY >= y + 4 && mouseY <= y + 16) {
                    String name = rows.get(i).name();
                    java.util.List<String> pins = wintahh.rageutils.HudConfig.INSTANCE.pinnedMaterials;
                    if (pins.contains(name)) pins.remove(name); else pins.add(name);
                    HudConfig.save();
                    return true;
                }
                y += ROW_HEIGHT;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256 && client != null) {
            client.setScreen(null);
            return true;
        }
        return super.keyPressed(input);
    }

    private static int biomeColor(String biome) {
        return switch (biome) {
            case "Overworld" -> 0xFF5CA856;
            case "Cave"      -> 0xFF888888;
            case "Sakura"    -> 0xFFE88CA5;
            case "Iceworld"  -> 0xFF74BFE8;
            case "Inferno"   -> 0xFFE05A33;
            case "Aether"    -> 0xFFE3CE83;
            case "Oblivion"  -> 0xFF7D5AC8;
            case "Ocean"     -> 0xFF36AFC4;
            case "Desert"    -> 0xFFD2A44C;
            default          -> 0xFF9955CC;
        };
    }

    private static int biomeGradientStart(String biome) {
        return switch (biome) {
            case "Overworld" -> 0xFF2F7D32;
            case "Cave"      -> 0xFF7A7F86;
            case "Sakura"    -> 0xFFC13D72;
            case "Iceworld"  -> 0xFF287EA8;
            case "Inferno"   -> 0xFFB51F16;
            case "Aether"    -> 0xFFC89523;
            case "Oblivion"  -> 0xFF4A247F;
            case "Ocean"     -> 0xFF087C91;
            case "Desert"    -> 0xFF9B6517;
            default          -> 0xFF8C3029;
        };
    }

    private static int biomeGradientEnd(String biome) {
        return switch (biome) {
            case "Overworld" -> 0xFF9CCC4D;
            case "Cave"      -> 0xFFB8B1A1;
            case "Sakura"    -> 0xFFFF8AB3;
            case "Iceworld"  -> 0xFF7DE3FF;
            case "Inferno"   -> 0xFFFF7A1A;
            case "Aether"    -> 0xFFFFE08A;
            case "Oblivion"  -> 0xFFB06CFF;
            case "Ocean"     -> 0xFF45D6C4;
            case "Desert"    -> 0xFFFFC857;
            default          -> 0xFFFF8A5C;
        };
    }

    private static int lerpColor(int startColor, int endColor, double progress) {
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8)  & 0xFF;
        int startB =  startColor        & 0xFF;
        int endA   = (endColor   >> 24) & 0xFF;
        int endR   = (endColor   >> 16) & 0xFF;
        int endG   = (endColor   >> 8)  & 0xFF;
        int endB   =  endColor          & 0xFF;
        return (lerp(startA, endA, progress) << 24)
             | (lerp(startR, endR, progress) << 16)
             | (lerp(startG, endG, progress) << 8)
             |  lerp(startB, endB, progress);
    }

    private static int lighten(int color, double amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;
        return (a << 24)
             | (lerp(r, 255, amount) << 16)
             | (lerp(g, 255, amount) << 8)
             |  lerp(b, 255, amount);
    }

    private static int lerp(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    private static String format(double value) {
        if (value == (long) value) return String.format(Locale.ROOT, "%,d", (long) value);
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    private static String stackString(double value) {
        int    stacks    = (int) (value / 64);
        double remainder = value - stacks * 64;
        if (stacks <= 0) return format(remainder);
        if (remainder == 0) return stacks + " stack" + (stacks == 1 ? "" : "s");
        return stacks + " stack" + (stacks == 1 ? "" : "s") + " + " + format(remainder);
    }

    private void syncHudConfig() {
        HudConfig.INSTANCE.materialsWorldHeads  = worldHeads;
        HudConfig.INSTANCE.materialsGearFilters = EnumSet.copyOf(gearFilters);
        HudConfig.INSTANCE.materialsTotalMode   = totalMode;
        HudConfig.save();
    }

    private void openLoadFileDialog() {
        Thread t = new Thread(() -> {
            String chosen = null;
            try {
                java.awt.FileDialog dialog = new java.awt.FileDialog(
                        (java.awt.Frame) null, "Select materials.html", java.awt.FileDialog.LOAD);
                dialog.setFile("*.html");
                dialog.setFilenameFilter((dir, fileName) -> {
                    String lower = fileName.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".html") || lower.endsWith(".htm");
                });
                dialog.setVisible(true);
                String dir  = dialog.getDirectory();
                String file = dialog.getFile();
                if (dir != null && file != null) chosen = dir + file;
            } catch (Throwable err) {
                chosen = null;
            }

            final String selected = chosen;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.execute(() -> {
                if (selected == null) return;
                boolean ok = MaterialDataUpdater.loadFromLocalFile(java.nio.file.Path.of(selected));
                if (mc.player != null) {
                    if (ok) {
                        mc.player.sendMessage(Text.literal(
                                "[RageUtils] Materials loaded from file ("
                                        + MaterialDataUpdater.getLastSyncedCount() + " entries)."), false);
                    } else {
                        mc.player.sendMessage(Text.literal(
                                "§c[RageUtils] Failed to load materials from that file."), false);
                    }
                }
                if (ok && mc.currentScreen == this) rebuildControls();
            });
        }, "rageutils-materials-file");
        t.setDaemon(true);
        t.start();
    }
}
