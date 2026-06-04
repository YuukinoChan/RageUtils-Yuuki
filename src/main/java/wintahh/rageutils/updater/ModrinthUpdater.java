package wintahh.rageutils.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wintahh.rageutils.RageUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModrinthUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(RageUtils.MOD_ID);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String PROJECT_ID = "rageutils-yuuki";
    private static final String USER_AGENT = "RageUtils-Yuuki-Updater";
    private static final String MODRINTH_URL = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(new UpdaterThreadFactory());

    private ModrinthUpdater() {}

    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ModrinthUpdater::checkForUpdatesAsync);
    }

    public static void checkForUpdatesAsync(MinecraftClient client) {
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        if (UpdateManager.hasPendingUpdate()) {
            client.execute(() -> client.setScreen(new RestartRequiredScreen(
                    client.currentScreen,
                    "Update downloaded successfully.",
                    "Restart required to apply update."
            )));
            return;
        }

        CompletableFuture.runAsync(() -> checkForUpdates(client), EXECUTOR);
    }

    private static void checkForUpdates(MinecraftClient client) {
        try {
            LOGGER.info("[Updater] Checking Modrinth for updates...");

            String currentVersion = FabricLoader.getInstance()
                    .getModContainer(RageUtils.MOD_ID)
                    .orElseThrow()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString();
            String minecraftVersion = FabricLoader.getInstance().getRawGameVersion();

            Optional<ModrinthVersionInfo> latest = fetchLatestVersion(minecraftVersion);
            if (latest.isEmpty()) {
                return;
            }

            ModrinthVersionInfo info = latest.get();
            info.setCurrentVersion(currentVersion);

            LOGGER.info("[Updater] Current version: {}", currentVersion);
            LOGGER.info("[Updater] Latest version: {}", info.getVersionNumber());

            if (!VersionComparator.isNewer(info.getVersionNumber(), currentVersion)) {
                return;
            }

            client.execute(() -> {
                if (client.currentScreen instanceof UpdateScreen
                        || client.currentScreen instanceof RestartRequiredScreen) {
                    return;
                }
                client.setScreen(new UpdateScreen(client.currentScreen, info));
            });
        } catch (Exception e) {
            // Offline or blocked networking should never surface to the player.
            LOGGER.debug("[Updater] Update check failed: {}", e.toString());
        }
    }

    private static Optional<ModrinthVersionInfo> fetchLatestVersion(String minecraftVersion) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildVersionUrl(minecraftVersion)))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            LOGGER.warn("[Updater] Modrinth API returned HTTP {}", response.statusCode());
            return Optional.empty();
        }

        ModrinthVersionInfo[] versions = GSON.fromJson(response.body(), ModrinthVersionInfo[].class);
        if (versions == null || versions.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(versions)
                .filter(ModrinthVersionInfo::hasUsablePrimaryFile)
                .findFirst();
    }

    private static String buildVersionUrl(String minecraftVersion) {
        String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        String gameVersions = URLEncoder.encode("[\"" + minecraftVersion + "\"]", StandardCharsets.UTF_8);
        return MODRINTH_URL + "?loaders=" + loaders + "&game_versions=" + gameVersions;
    }

    private static final class UpdaterThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "rageutils-modrinth-updater");
            thread.setDaemon(true);
            return thread;
        }
    }
}
