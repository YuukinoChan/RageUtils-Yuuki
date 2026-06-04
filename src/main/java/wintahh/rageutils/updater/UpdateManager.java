package wintahh.rageutils.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wintahh.rageutils.RageUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RageUtils.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UPDATE_DIR = "rageutils/update";
    private static final String PENDING_FILE = "pending-update.json";
    private static final String UPDATE_JAR = "RageUtils-new.jar";
    private static final String DOWNLOAD_TMP = "RageUtils-new.jar.download";
    private static final String USER_AGENT = "RageUtils-Yuuki-Updater";
    private static final AtomicBoolean DOWNLOAD_IN_PROGRESS = new AtomicBoolean(false);

    private UpdateManager() {}

    public static boolean hasPendingUpdate() {
        return Files.exists(pendingUpdatePath());
    }

    public static void applyPendingUpdateAtBoot() {
        Path pendingPath = pendingUpdatePath();
        if (!Files.exists(pendingPath)) {
            return;
        }

        PendingUpdate pending;
        Path updateJar;
        try {
            pending = readPendingUpdate(pendingPath);
            updateJar = resolveUpdateFile(pending.downloadedFileName());
        } catch (Exception e) {
            LOGGER.warn("[Updater] Pending update metadata is invalid. Removing it safely: {}", e.toString());
            tryDelete(pendingPath);
            cleanupTemporaryFiles();
            return;
        }

        try {
            if (!Files.isRegularFile(updateJar)) {
                LOGGER.warn("[Updater] Pending update is missing downloaded jar: {}", updateJar);
                tryDelete(pendingPath);
                cleanupTemporaryFiles();
                return;
            }

            if (!sha512(updateJar).equalsIgnoreCase(pending.sha512())) {
                LOGGER.warn("[Updater] Pending update hash mismatch. Refusing to replace installed jar.");
                tryDelete(updateJar);
                tryDelete(pendingPath);
                cleanupTemporaryFiles();
                return;
            }

            Optional<Path> targetJar = locateInstalledJar(pending);
            if (targetJar.isEmpty()) {
                LOGGER.warn("[Updater] Could not locate installed RageUtils-Yuuki jar in mods folder.");
                return;
            }

            Path target = targetJar.get();
            if (!isInsideModsFolder(target)) {
                LOGGER.warn("[Updater] Refusing to replace jar outside mods folder: {}", target);
                return;
            }

            // This is the critical swap: it happens from Fabric preLaunch before
            // normal mod initialization, and never while the update was downloaded.
            Files.move(updateJar, target, StandardCopyOption.REPLACE_EXISTING);
            tryDelete(pendingPath);
            cleanupTemporaryFiles();
            LOGGER.info("[Updater] Applied RageUtils-Yuuki update {} to {}", pending.version(), target.getFileName());
        } catch (Exception e) {
            LOGGER.error("[Updater] Failed to apply pending update. Startup will continue normally.", e);
        }
    }

    public static CompletableFuture<DownloadResult> downloadUpdateAsync(ModrinthVersionInfo versionInfo) {
        if (!DOWNLOAD_IN_PROGRESS.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(DownloadResult.failure("An update download is already running."));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadUpdate(versionInfo);
            } finally {
                DOWNLOAD_IN_PROGRESS.set(false);
            }
        });
    }

    public static boolean attemptRelaunchAndStop(MinecraftClient client) {
        try {
            Optional<String> commandLine = ProcessHandle.current().info().commandLine();
            if (commandLine.isEmpty() || commandLine.get().isBlank()) {
                return false;
            }

            Path script = writeRelaunchScript(commandLine.get());
            startRelaunchScript(script);
            if (client != null) {
                client.scheduleStop();
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Updater] Automatic restart failed: {}", e.toString());
            return false;
        }
    }

    private static DownloadResult downloadUpdate(ModrinthVersionInfo versionInfo) {
        if (versionInfo == null || !versionInfo.hasUsablePrimaryFile()) {
            return DownloadResult.failure("The Modrinth response did not include a usable jar.");
        }

        Path updateDir = updateDir();
        Path tmp = updateDir.resolve(DOWNLOAD_TMP);
        Path finalJar = updateDir.resolve(UPDATE_JAR);

        try {
            Files.createDirectories(updateDir);
            tryDelete(tmp);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(versionInfo.getDownloadUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<Path> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                tryDelete(tmp);
                return DownloadResult.failure("Download failed with HTTP " + response.statusCode() + ".");
            }

            String actualHash = sha512(tmp);
            if (!actualHash.equalsIgnoreCase(versionInfo.getSha512())) {
                tryDelete(tmp);
                return DownloadResult.failure("Downloaded file failed SHA-512 verification.");
            }

            Files.move(tmp, finalJar, StandardCopyOption.REPLACE_EXISTING);
            writePendingUpdate(versionInfo, finalJar);
            return DownloadResult.success(finalJar);
        } catch (Exception e) {
            tryDelete(tmp);
            LOGGER.warn("[Updater] Update download failed: {}", e.toString());
            return DownloadResult.failure("Download failed. Please try again later.");
        }
    }

    private static void writePendingUpdate(ModrinthVersionInfo versionInfo, Path finalJar) throws IOException {
        PendingUpdate pending = new PendingUpdate(
                versionInfo.getVersionNumber(),
                versionInfo.getSha512(),
                finalJar.getFileName().toString(),
                findInstalledJar().map(path -> path.getFileName().toString()).orElse(""),
                System.currentTimeMillis()
        );

        Path pendingPath = pendingUpdatePath();
        Files.createDirectories(pendingPath.getParent());
        try (Writer writer = Files.newBufferedWriter(
                pendingPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            GSON.toJson(pending, writer);
        }
    }

    private static PendingUpdate readPendingUpdate(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            PendingUpdate pending = GSON.fromJson(reader, PendingUpdate.class);
            if (pending == null || pending.downloadedFileName().isBlank() || pending.sha512().isBlank()) {
                throw new IOException("Invalid pending update metadata");
            }
            return pending;
        }
    }

    private static Optional<Path> locateInstalledJar(PendingUpdate pending) {
        Path modsDir = modsDir();
        if (!pending.targetFileName().isBlank()) {
            Path target = modsDir.resolve(pending.targetFileName()).normalize();
            if (Files.isRegularFile(target) && isRageUtilsJar(target)) {
                return Optional.of(target);
            }
        }
        return findInstalledJar();
    }

    public static Optional<Path> findInstalledJar() {
        Optional<Path> fromLoader = FabricLoader.getInstance()
                .getModContainer(RageUtils.MOD_ID)
                .map(ModContainer::getOrigin)
                .stream()
                .flatMap(origin -> origin.getPaths().stream())
                .filter(UpdateManager::isJarInModsFolder)
                .findFirst();
        if (fromLoader.isPresent()) {
            return fromLoader;
        }

        Path modsDir = modsDir();
        if (!Files.isDirectory(modsDir)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            return stream
                    .filter(UpdateManager::isJarFile)
                    .filter(UpdateManager::isRageUtilsJar)
                    .sorted(Comparator.comparing(UpdateManager::jarNameRank))
                    .findFirst();
        } catch (IOException e) {
            LOGGER.warn("[Updater] Failed to scan mods folder for RageUtils jar: {}", e.toString());
            return Optional.empty();
        }
    }

    private static boolean isRageUtilsJar(Path path) {
        if (!isJarFile(path)) {
            return false;
        }

        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry("fabric.mod.json");
            if (entry == null) {
                return false;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return json.has("id") && RageUtils.MOD_ID.equals(json.get("id").getAsString());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isJarInModsFolder(Path path) {
        return isJarFile(path) && isInsideModsFolder(path);
    }

    private static boolean isJarFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static int jarNameRank(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("rageutils-yuuki")) return 0;
        if (name.contains("rageutils")) return 1;
        return 2;
    }

    private static boolean isInsideModsFolder(Path path) {
        Path normalizedMods = modsDir().toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(normalizedMods);
    }

    private static Path resolveUpdateFile(String fileName) throws IOException {
        Path updateDir = updateDir().toAbsolutePath().normalize();
        Path resolved = updateDir.resolve(fileName).normalize();
        if (!resolved.startsWith(updateDir)) {
            throw new IOException("Pending update points outside update directory");
        }
        return resolved;
    }

    private static String sha512(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void cleanupTemporaryFiles() {
        Path dir = updateDir();
        tryDelete(dir.resolve(DOWNLOAD_TMP));
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".tmp"))
                    .forEach(UpdateManager::tryDelete);
        } catch (IOException ignored) {
        }
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.debug("[Updater] Failed to delete {}: {}", path, e.toString());
        }
    }

    private static Path writeRelaunchScript(String commandLine) throws IOException {
        Files.createDirectories(updateDir());
        long pid = ProcessHandle.current().pid();
        String safeCommand = commandLine.replace("\r", " ").replace("\n", " ");
        Path script = updateDir().resolve(isWindows() ? "rageutils-relaunch.bat" : "rageutils-relaunch.sh");

        String content;
        if (isWindows()) {
            content = "@echo off\r\n"
                    + "setlocal\r\n"
                    + "set \"PID=" + pid + "\"\r\n"
                    + ":wait\r\n"
                    + "powershell -NoProfile -Command \"if (Get-Process -Id %PID% -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }\" >nul 2>nul\r\n"
                    + "if %ERRORLEVEL% EQU 0 (\r\n"
                    + "  timeout /t 1 /nobreak >nul\r\n"
                    + "  goto wait\r\n"
                    + ")\r\n"
                    + "start \"\" " + safeCommand + "\r\n"
                    + "del \"%~f0\" >nul 2>nul\r\n";
        } else {
            content = "#!/bin/sh\n"
                    + "PID=" + pid + "\n"
                    + "while kill -0 \"$PID\" 2>/dev/null; do sleep 1; done\n"
                    + safeCommand + " >/dev/null 2>&1 &\n"
                    + "rm -- \"$0\"\n";
        }

        Files.writeString(script, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        script.toFile().setExecutable(true);
        return script;
    }

    private static void startRelaunchScript(Path script) throws IOException {
        if (isWindows()) {
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", script.toAbsolutePath().toString()).start();
        } else {
            new ProcessBuilder("sh", script.toAbsolutePath().toString()).start();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Path updateDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(UPDATE_DIR);
    }

    private static Path pendingUpdatePath() {
        return updateDir().resolve(PENDING_FILE);
    }

    private static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    private record PendingUpdate(
            String version,
            String sha512,
            String downloadedFileName,
            String targetFileName,
            long createdAtEpochMillis
    ) {
        private PendingUpdate {
            version = version == null ? "" : version;
            sha512 = sha512 == null ? "" : sha512;
            downloadedFileName = downloadedFileName == null || downloadedFileName.isBlank() ? UPDATE_JAR : downloadedFileName;
            targetFileName = targetFileName == null ? "" : targetFileName;
        }
    }

    public record DownloadResult(boolean success, String message, Path file) {
        static DownloadResult success(Path file) {
            return new DownloadResult(true, "Update downloaded successfully.", file);
        }

        static DownloadResult failure(String message) {
            return new DownloadResult(false, message, null);
        }
    }
}
