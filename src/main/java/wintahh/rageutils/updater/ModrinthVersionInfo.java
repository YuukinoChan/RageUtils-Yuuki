package wintahh.rageutils.updater;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public final class ModrinthVersionInfo {
    @SerializedName("version_number")
    private String versionNumber;

    private String changelog;
    private List<ModrinthFileInfo> files;

    private transient String currentVersion;

    public String getVersionNumber() {
        return versionNumber == null ? "" : versionNumber;
    }

    public String getChangelog() {
        return changelog == null || changelog.isBlank() ? "No changelog provided." : changelog;
    }

    public String getCurrentVersion() {
        return currentVersion == null ? "" : currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion == null ? "" : currentVersion;
    }

    public String getDownloadUrl() {
        return getPrimaryFile().getUrl();
    }

    public String getSha512() {
        return getPrimaryFile().getSha512();
    }

    public String getFileName() {
        return getPrimaryFile().getFilename();
    }

    public boolean hasUsablePrimaryFile() {
        return getPrimaryFile().isUsableJar();
    }

    public ModrinthFileInfo getPrimaryFile() {
        if (files == null || files.isEmpty()) {
            return EmptyFileHolder.EMPTY;
        }

        return files.stream()
                .filter(Objects::nonNull)
                .filter(ModrinthFileInfo::isPrimary)
                .filter(ModrinthFileInfo::isUsableJar)
                .findFirst()
                .orElseGet(() -> files.stream()
                        .filter(Objects::nonNull)
                        .filter(ModrinthFileInfo::isUsableJar)
                        .findFirst()
                        .orElse(EmptyFileHolder.EMPTY));
    }

    private static final class EmptyFileHolder {
        private static final ModrinthFileInfo EMPTY = new ModrinthFileInfo();
    }
}
