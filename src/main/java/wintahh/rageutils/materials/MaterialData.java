package wintahh.rageutils.materials;

import wintahh.rageutils.HudConfig;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MaterialData {

    public record MaterialEntry(
            String biome,
            String name,
            double armor,
            double pick,
            double sword,
            double bow,
            double talisman,
            double materialsPerHead
    ) {
        public double gearTotal() {
            return armor + pick + sword + bow + talisman;
        }

        public double valueFor(GearFilter filter) {
            return switch (filter) {
                case ALL -> gearTotal();
                case ARMOR -> armor;
                case PICK -> pick;
                case SWORD -> sword;
                case BOW -> bow;
                case TALISMAN -> talisman;
            };
        }

        public double valueFor(Set<GearFilter> filters) {
            if (filters.contains(GearFilter.ALL)) return gearTotal();
            double sum = 0;
            for (GearFilter f : filters) sum += valueFor(f);
            return sum;
        }
    }

    public enum GearFilter {
        ALL("All"),
        ARMOR("Armor"),
        PICK("Pick"),
        SWORD("Sword"),
        BOW("Bow"),
        TALISMAN("Talisman");

        public final String label;

        GearFilter(String label) {
            this.label = label;
        }
    }

    public enum TotalMode {
        GEAR_AND_COHESIVE("Gear + Cohesive", "All Total"),
        GEAR_ONLY("No Cohesive", "Gear Only"),
        COHESIVE_ONLY("Just Cohesive", "Cohesive");

        public final String label;
        public final String buttonLabel;

        TotalMode(String label, String buttonLabel) {
            this.label = label;
            this.buttonLabel = buttonLabel;
        }
    }

    private static final Map<String, String> NAME_ALIASES = Map.of(
            "Rust", "Desert Rust",
            "Relic", "Desert Relic",
            "Flareon", "Desert Flareon"
    );

    private static final Map<String, String> RESOURCE_NAME_ALIASES = Map.of(
            "Desert Rust", "Rust",
            "Desert Relic", "Relic",
            "Desert Flareon", "Flareon"
    );

    public static String resolveResourceName(String materialName) {
        if (materialName == null) return null;
        return RESOURCE_NAME_ALIASES.getOrDefault(materialName, materialName);
    }

    private static volatile List<MaterialEntry> materials = defaultMaterials();

    private MaterialData() {}

    public static List<MaterialEntry> materials() {
        return materials;
    }

    public static void replaceMaterials(List<MaterialEntry> next) {
        if (next != null && !next.isEmpty()) {
            materials = List.copyOf(next);
        }
    }

    public static Optional<MaterialEntry> find(String name) {
        if (name == null) return Optional.empty();
        String resolved = NAME_ALIASES.getOrDefault(name, name);
        for (MaterialEntry entry : materials) {
            if (entry.name().equalsIgnoreCase(resolved)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    // ── Single-filter overloads (legacy / HUD compat) ──────────────────────

    public static double computeDisplayTotal(String materialName, GearFilter gearFilter, TotalMode totalMode, int worldHeads) {
        return find(materialName)
                .map(entry -> computeDisplayTotal(entry, gearFilter, totalMode, worldHeads))
                .orElse(0.0);
    }

    public static double computeDisplayTotal(MaterialEntry entry, GearFilter gearFilter, TotalMode totalMode, int worldHeads) {
        return computeDisplayTotal(entry, EnumSet.of(gearFilter), totalMode, worldHeads);
    }

    // ── Multi-filter overloads ─────────────────────────────────────────────

    public static double computeDisplayTotal(String materialName, Set<GearFilter> gearFilters, TotalMode totalMode, int worldHeads) {
        return find(materialName)
                .map(entry -> computeDisplayTotal(entry, gearFilters, totalMode, worldHeads))
                .orElse(0.0);
    }

    public static double computeDisplayTotal(MaterialEntry entry, Set<GearFilter> gearFilters, TotalMode totalMode, int worldHeads) {
        double gearTotal = entry.valueFor(gearFilters);
        double headsTotal = entry.materialsPerHead() * worldHeads;
        return switch (totalMode) {
            case GEAR_AND_COHESIVE -> gearTotal + headsTotal;
            case GEAR_ONLY -> gearTotal;
            case COHESIVE_ONLY -> headsTotal;
        };
    }

    // ── Convenience shortcut using HudConfig ──────────────────────────────

    public static double computeDisplayTotal(String materialName) {
        HudConfig cfg = HudConfig.INSTANCE;
        return computeDisplayTotal(
                materialName,
                cfg.materialsGearFilters,
                cfg.materialsTotalMode,
                cfg.materialsWorldHeads
        );
    }

    public static List<String> biomes() {
        List<String> out = new ArrayList<>();
        String last = null;
        for (MaterialEntry entry : materials) {
            if (!entry.biome().equals(last)) {
                out.add(entry.biome());
                last = entry.biome();
            }
        }
        return out;
    }

    private static List<MaterialEntry> defaultMaterials() {
        return List.of(
                m("Overworld", "Oak Wood", 64, 12, 32, 32, 32, 64),
                m("Overworld", "Loam Soil", 20, 6, 6, 5, 5, 64),
                m("Overworld", "Peat", 40, 12, 12, 10, 10, 64),
                m("Overworld", "Dirt", 144, 42, 42, 36, 42, 52),
                m("Overworld", "Ryegrass", 192, 56, 56, 48, 48, 64),
                m("Overworld", "Lemongrass", 192, 56, 56, 48, 48, 64),
                m("Overworld", "Oak Leaves", 192, 56, 56, 48, 48, 64),
                m("Overworld", "Silvergrass", 384, 160, 160, 64, 64, 64),
                m("Overworld", "Poppy", 8, 0, 2, 0, 0, 4),
                m("Cave", "Stone", 12, 3.5, 3.5, 3, 3, 8),
                m("Cave", "Moss", 20, 6, 7, 6, 3, 3),
                m("Cave", "Coal", 20, 6, 6, 5, 5, 8),
                m("Cave", "Iron", 24, 7, 7, 6, 6, 8),
                m("Cave", "Dripstone", 52, 18, 18, 9, 13, 9),
                m("Cave", "Gold", 24, 8, 8, 6, 6, 7),
                m("Cave", "Emerald", 28, 9, 9, 7, 7, 7),
                m("Cave", "Diamond", 28, 9, 9, 7, 7, 7),
                m("Cave", "Amethyst", 0, 0, 0, 0, 0, 64),
                m("Cave", "Warden Heart", 8, 0, 2, 0, 0, 4),
                m("Sakura", "Cherry Wood", 40, 14, 12, 10, 10, 12),
                m("Sakura", "Rose Stone", 68, 24, 24, 17, 17, 19),
                m("Sakura", "Cherry Leaves", 52, 18, 18, 13, 13, 15),
                m("Sakura", "Bloom Clay", 84, 30, 30, 21, 21, 23),
                m("Sakura", "Cherry Sand", 136, 48, 48, 34, 34, 36),
                m("Sakura", "Spirit Clay", 52, 18, 18, 13, 13, 15),
                m("Sakura", "Pink Petal", 56, 18, 18, 14, 14, 16),
                m("Sakura", "Kunai", 0, 0, 0, 0, 0, 96),
                m("Sakura", "Bamboo", 0, 0, 0, 0, 0, 4),
                m("Sakura", "Lovers Tear", 4, 0, 1, 0, 0, 1),
                m("Iceworld", "Snow", 52, 18, 18, 13, 13, 15),
                m("Iceworld", "Snowpack", 92, 32, 32, 23, 23, 25),
                m("Iceworld", "Ice Wool", 52, 18, 18, 13, 13, 15),
                m("Iceworld", "Icestone", 100, 36, 36, 25, 25, 27),
                m("Iceworld", "Cryoblood", 52, 18, 18, 13, 13, 15),
                m("Iceworld", "Blue Ice", 196, 72, 72, 49, 49, 52),
                m("Iceworld", "Glacierstone", 68, 24, 24, 17, 17, 19),
                m("Iceworld", "Frostgene", 68, 24, 24, 17, 17, 19),
                m("Iceworld", "Frost Powder", 0, 0, 0, 0, 0, 128),
                m("Iceworld", "Icefur", 0, 0, 0, 0, 0, 8),
                m("Iceworld", "Icedrake Egg", 12, 0, 3, 0, 0, 3),
                m("Inferno", "Crimson Stem", 108, 39, 39, 27, 27, 29),
                m("Inferno", "Magma", 108, 39, 39, 27, 27, 29),
                m("Inferno", "Firewart", 68, 24, 24, 17, 19, 19),
                m("Inferno", "Ashrock", 140, 51, 51, 33, 37, 37),
                m("Inferno", "Infernal Quartz", 204, 75, 75, 51, 51, 53),
                m("Inferno", "Ancient Debris", 512, 192, 192, 64, 64, 64),
                m("Inferno", "Hellplate", 140, 51, 51, 35, 35, 39),
                m("Inferno", "Bloodplate", 512, 192, 192, 64, 64, 64),
                m("Inferno", "Fire Powder", 0, 0, 0, 0, 0, 192),
                m("Inferno", "Fire Core", 12, 0, 3, 0, 0, 3),
                m("Aether", "Aether Wood", 12, 4, 4, 3, 3, 4),
                m("Aether", "Celestone", 44, 15, 15, 11, 11, 16),
                m("Aether", "Luminium", 44, 15, 15, 11, 11, 16),
                m("Aether", "Mycelium", 136, 48, 48, 34, 36, 40),
                m("Aether", "Aether Tile", 44, 15, 15, 11, 11, 16),
                m("Aether", "Aurum", 44, 15, 15, 11, 11, 16),
                m("Aether", "Solar Fiber", 20, 6, 6, 5, 5, 8),
                m("Aether", "Solaris", 52, 18, 18, 13, 13, 20),
                m("Aether", "Luminium Core", 0, 0, 0, 0, 0, 128),
                m("Aether", "Aether Residue", 12, 0, 3, 0, 0, 2),
                m("Oblivion", "Faded", 124, 45, 45, 31, 31, 37),
                m("Oblivion", "Dusk", 44, 15, 15, 11, 11, 16),
                m("Oblivion", "Void", 44, 15, 15, 11, 11, 16),
                m("Oblivion", "Blackstone", 132, 48, 48, 33, 33, 39),
                m("Oblivion", "Obsidian", 108, 39, 39, 27, 27, 31),
                m("Oblivion", "Crying Obsidian", 116, 42, 42, 29, 29, 33),
                m("Oblivion", "Purpur", 156, 57, 57, 39, 39, 45),
                m("Oblivion", "Dark Matter", 164, 60, 60, 41, 41, 51),
                m("Oblivion", "Void Sensor", 0, 0, 0, 0, 0, 3),
                m("Oblivion", "Abyss Core", 16, 0, 4, 0, 0, 6),
                m("Ocean", "Tidewood", 76, 27, 27, 19, 19, 23),
                m("Ocean", "Azure", 76, 27, 27, 19, 19, 23),
                m("Ocean", "Seastone", 244, 90, 90, 61, 61, 64),
                m("Ocean", "Deepstone", 252, 93, 93, 63, 63, 64),
                m("Ocean", "Aqualith", 260, 96, 96, 64, 64, 64),
                m("Ocean", "Ocean Token", 736, 0, 268.5, 183, 183, 197),
                m("Ocean", "Lobster", 0, 0, 0, 0, 0, 12),
                m("Ocean", "Ocean Key", 16, 0, 0, 0, 0, 6),
                m("Ocean", "Token (Tidewood path)", 76, 0, 27, 19, 19, 0),
                m("Ocean", "Token (Azure path)", 152, 0, 54, 38, 38, 0),
                m("Ocean", "Token (Seastone path)", 122, 0, 45, 30.5, 30.5, 0),
                m("Ocean", "Token (Deepstone path)", 126, 0, 46.5, 31.5, 31.5, 0),
                m("Ocean", "Token (Aqualith path)", 260, 0, 96, 64, 64, 0),
                m("Desert", "Sandstone", 92, 33, 33, 23, 23, 25),
                m("Desert", "Straw", 36, 9, 9, 7, 6, 9),
                m("Desert", "Amber", 100, 36, 36, 25, 25, 27),
                m("Desert", "Desert Rust", 20, 6, 6, 5, 5, 6),
                m("Desert", "Desert Relic", 68, 24, 24, 17, 17, 19),
                m("Desert", "Desert Flareon", 76, 27, 27, 19, 19, 21),
                m("Desert", "Leg. Artifact - Rust", 280, 84, 84, 70, 70, 84),
                m("Desert", "Leg. Artifact - Relic", 136, 48, 48, 34, 34, 38),
                m("Desert", "Leg. Artifact - Flareon", 228, 81, 81, 57, 57, 63),
                m("Desert", "Leg. Artifact - Total", 644, 213, 213, 161, 161, 185),
                m("Desert", "Worm Teeth", 0, 0, 0, 0, 0, 128),
                m("Desert", "Sting", 0, 0, 0, 0, 0, 128),
                m("Desert", "Golem Essence", 0, 0, 0, 0, 0, 4),
                m("Desert", "Dog Core", 16, 0, 4, 0, 0, 6)
        );
    }

    private static MaterialEntry m(String biome, String name, double armor, double pick, double sword, double bow, double talisman, double materialsPerHead) {
        return new MaterialEntry(biome, name, armor, pick, sword, bow, talisman, materialsPerHead);
    }
}
