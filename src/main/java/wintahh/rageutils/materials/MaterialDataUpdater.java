package wintahh.rageutils.materials;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wintahh.rageutils.RageUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class MaterialDataUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(RageUtils.MOD_ID);
    private static final String MATERIALS_URL = "https://ragemines.com/materials.html";
    private static final Pattern BIOME_NAME = Pattern.compile("name:'([^']+)'");
    private static final Pattern ITEM_LINE = Pattern.compile(
            "\\{m:'((?:\\\\'|[^'])*)',\\s*armor:([\\d.]+),\\s*pick:([\\d.]+),\\s*sword:([\\d.]+),\\s*bow:([\\d.]+),\\s*tal:([\\d.]+)[^}]*mpc:([\\d.]+)"
    );

    private static final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    private static volatile boolean syncedThisSession = false;
    private static volatile boolean pendingChatNotify = false;
    private static volatile int lastSyncedCount = 0;

    private MaterialDataUpdater() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                requestSync(true, "server join")
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                syncedThisSession = false
        );
    }

    /** Called when the materials screen opens (first time per session if not already synced). */
    public static void onMaterialsScreenOpened() {
        if (!syncedThisSession) {
            requestSync(true, "materials screen");
        }
    }

    private static void requestSync(boolean notifyPlayer, String reason) {
        if (fetchInProgress.get()) return;
        if (syncedThisSession && !notifyPlayer) return;

        fetchInProgress.set(true);
        Thread thread = new Thread(() -> {
            try {
                fetchAndApply(notifyPlayer, reason);
            } finally {
                fetchInProgress.set(false);
            }
        }, "rageutils-materials-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private static void fetchAndApply(boolean notifyPlayer, String reason) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MATERIALS_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "RageUtils/" + RageUtils.MOD_ID)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.warn("Materials sync failed ({}): HTTP {}", reason, response.statusCode());
                return;
            }
            List<MaterialData.MaterialEntry> parsed = parseBiomes(response.body());
            if (parsed.isEmpty()) {
                LOGGER.warn("Materials sync parsed zero entries ({})", reason);
                return;
            }
            MaterialData.replaceMaterials(parsed);
            syncedThisSession = true;
            lastSyncedCount = parsed.size();
            LOGGER.info("Materials synced from website ({} entries, trigger: {})", parsed.size(), reason);

            if (notifyPlayer) {
                pendingChatNotify = true;
                deliverPendingMessage();
            }
        } catch (Exception e) {
            LOGGER.warn("Materials sync failed ({}): {}", reason, e.toString());
        }
    }

    /** Load and apply materials from a user-selected local HTML file. Returns true on success. */
    public static boolean loadFromLocalFile(java.nio.file.Path path) {
        try {
            String html = java.nio.file.Files.readString(path);
            List<MaterialData.MaterialEntry> parsed = parseBiomes(html);
            if (parsed.isEmpty()) return false;
            MaterialData.replaceMaterials(parsed);
            lastSyncedCount = parsed.size();
            syncedThisSession = true;
            return true;
        } catch (Exception e) {
            LOGGER.warn("Local materials load failed: {}", e.toString());
            return false;
        }
    }

    public static int getLastSyncedCount() {
        return lastSyncedCount;
    }

    public static void deliverPendingMessage() {
        if (!pendingChatNotify) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        pendingChatNotify = false;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                        Text.literal("[RageUtils] Materials updated from website (" + lastSyncedCount + " entries)."),
                        false
                );
            }
        });
    }

    static List<MaterialData.MaterialEntry> parseBiomes(String html) {
        int start = html.indexOf("const BIOMES =");
        if (start < 0) return List.of();
        int arrayStart = html.indexOf('[', start);
        int arrayEnd = html.indexOf("];", arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) return List.of();
        String biomesJs = html.substring(arrayStart, arrayEnd + 1);

        List<MaterialData.MaterialEntry> out = new ArrayList<>();
        String currentBiome = null;
        for (String line : biomesJs.split("\n")) {
            if (line.contains("name:'")) {
                Matcher biomeMatcher = BIOME_NAME.matcher(line);
                if (biomeMatcher.find()) {
                    currentBiome = unescapeJs(biomeMatcher.group(1));
                }
            }
            Matcher itemMatcher = ITEM_LINE.matcher(line);
            if (itemMatcher.find() && currentBiome != null) {
                out.add(new MaterialData.MaterialEntry(
                        currentBiome,
                        unescapeJs(itemMatcher.group(1)),
                        parseDouble(itemMatcher.group(2)),
                        parseDouble(itemMatcher.group(3)),
                        parseDouble(itemMatcher.group(4)),
                        parseDouble(itemMatcher.group(5)),
                        parseDouble(itemMatcher.group(6)),
                        parseDouble(itemMatcher.group(7))
                ));
            }
        }
        return out;
    }

    private static String unescapeJs(String value) {
        return value.replace("\\'", "'").replace('\u2013', '-').replace('\u2014', '-');
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value);
    }
}
