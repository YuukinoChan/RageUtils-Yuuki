package wintahh.rageutils.updater;

import java.util.Locale;

public final class ModrinthFileInfo {
    private String url;
    private String filename;
    private boolean primary;
    private ModrinthHashes hashes;

    public String getUrl() {
        return url == null ? "" : url;
    }

    public String getFilename() {
        return filename == null ? "" : filename;
    }

    public boolean isPrimary() {
        return primary;
    }

    public ModrinthHashes getHashes() {
        return hashes;
    }

    public String getSha512() {
        return hashes == null ? "" : hashes.getSha512();
    }

    public boolean isUsableJar() {
        return !getUrl().isBlank()
                && !getSha512().isBlank()
                && getFilename().toLowerCase(Locale.ROOT).endsWith(".jar");
    }
}
