package wintahh.rageutils.updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VersionComparator {
    private VersionComparator() {}

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    public static int compare(String left, String right) {
        ParsedVersion a = ParsedVersion.parse(left);
        ParsedVersion b = ParsedVersion.parse(right);

        int main = compareMain(a.mainParts, b.mainParts);
        if (main != 0) {
            return main;
        }

        return comparePrerelease(a.prereleaseParts, b.prereleaseParts);
    }

    private static int compareMain(List<BigInteger> left, List<BigInteger> right) {
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            BigInteger a = i < left.size() ? left.get(i) : BigInteger.ZERO;
            BigInteger b = i < right.size() ? right.get(i) : BigInteger.ZERO;
            int cmp = a.compareTo(b);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int comparePrerelease(List<String> left, List<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 0;
        if (left.isEmpty()) return 1;
        if (right.isEmpty()) return -1;

        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            if (i >= left.size()) return -1;
            if (i >= right.size()) return 1;

            String a = left.get(i);
            String b = right.get(i);
            boolean aNumber = a.matches("\\d+");
            boolean bNumber = b.matches("\\d+");

            int cmp;
            if (aNumber && bNumber) {
                cmp = new BigInteger(a).compareTo(new BigInteger(b));
            } else if (aNumber) {
                cmp = -1;
            } else if (bNumber) {
                cmp = 1;
            } else {
                cmp = a.compareTo(b);
            }

            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private record ParsedVersion(List<BigInteger> mainParts, List<String> prereleaseParts) {
        private static ParsedVersion parse(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("v")) {
                normalized = normalized.substring(1);
            }

            int buildIndex = normalized.indexOf('+');
            if (buildIndex >= 0) {
                normalized = normalized.substring(0, buildIndex);
            }

            String main = normalized;
            String prerelease = "";
            int prereleaseIndex = normalized.indexOf('-');
            if (prereleaseIndex >= 0) {
                main = normalized.substring(0, prereleaseIndex);
                prerelease = normalized.substring(prereleaseIndex + 1);
            }

            List<BigInteger> mainParts = new ArrayList<>();
            for (String part : main.split("\\.")) {
                if (part.matches("\\d+")) {
                    mainParts.add(new BigInteger(part));
                } else {
                    mainParts.add(BigInteger.ZERO);
                }
            }

            List<String> prereleaseParts = new ArrayList<>();
            if (!prerelease.isBlank()) {
                for (String part : prerelease.split("\\.")) {
                    if (!part.isBlank()) {
                        prereleaseParts.add(part);
                    }
                }
            }

            return new ParsedVersion(mainParts, prereleaseParts);
        }
    }
}
