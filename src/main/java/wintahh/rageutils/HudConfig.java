package wintahh.rageutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wintahh.rageutils.materials.MaterialData;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Singleton that stores all persistent HUD configuration.
 * Saved to/loaded from <configDir>/rageutils.json automatically.
 */
public class HudConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RageUtils.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "rageutils.json";

    public static final HudConfig INSTANCE = new HudConfig();

    public int hudX = 4;
    public int hudY = 4;
    public int hudWidth = 0;
    public int hudHeight = 0;
    public float hudScale = 1.0f;

    public final List<String> pinnedMaterials = new ArrayList<>();

    public int materialsWorldHeads = 1;
    /**
     * Active gear filters. Replaces the old single-value materialsGearFilter.
     * Defaults to ALL. Never empty — enforced in MaterialsScreen toggle logic.
     */
    public EnumSet<MaterialData.GearFilter> materialsGearFilters =
            EnumSet.of(MaterialData.GearFilter.ALL);
    public MaterialData.TotalMode materialsTotalMode = MaterialData.TotalMode.GEAR_AND_COHESIVE;

    public boolean hideArmor = false;
    public boolean csbParticles = true;
    public boolean csbSound = true;
    public boolean rateHudAutoSelect = true;

    private HudConfig() {}

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;
            HudConfig cfg = INSTANCE;

            if (json.has("hudX"))         cfg.hudX         = json.get("hudX").getAsInt();
            if (json.has("hudY"))         cfg.hudY         = json.get("hudY").getAsInt();
            if (json.has("hudScale"))     cfg.hudScale     = json.get("hudScale").getAsFloat();
            if (json.has("hideArmor"))    cfg.hideArmor    = json.get("hideArmor").getAsBoolean();
            if (json.has("csbParticles")) cfg.csbParticles = json.get("csbParticles").getAsBoolean();
            if (json.has("csbSound"))     cfg.csbSound     = json.get("csbSound").getAsBoolean();
            if (json.has("rateHudAutoSelect"))
                cfg.rateHudAutoSelect = json.get("rateHudAutoSelect").getAsBoolean();
            if (json.has("materialsWorldHeads"))
                cfg.materialsWorldHeads = json.get("materialsWorldHeads").getAsInt();

            // Load multi-filter set (new format: array of names)
            if (json.has("materialsGearFilters") && json.get("materialsGearFilters").isJsonArray()) {
                EnumSet<MaterialData.GearFilter> loaded = EnumSet.noneOf(MaterialData.GearFilter.class);
                for (JsonElement el : json.getAsJsonArray("materialsGearFilters")) {
                    try {
                        loaded.add(MaterialData.GearFilter.valueOf(el.getAsString()));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!loaded.isEmpty()) cfg.materialsGearFilters = loaded;
            } else if (json.has("materialsGearFilter")) {
                // Backward compat: old single-value string field
                try {
                    MaterialData.GearFilter single = MaterialData.GearFilter.valueOf(
                            json.get("materialsGearFilter").getAsString());
                    cfg.materialsGearFilters = EnumSet.of(single);
                } catch (IllegalArgumentException ignored) {}
            }

            if (json.has("materialsTotalMode")) {
                try {
                    cfg.materialsTotalMode = MaterialData.TotalMode.valueOf(
                            json.get("materialsTotalMode").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }

            cfg.pinnedMaterials.clear();
            if (json.has("pinnedMaterials")) {
                for (JsonElement el : json.getAsJsonArray("pinnedMaterials")) {
                    String name = el.getAsString();
                    if (!name.isBlank()) cfg.pinnedMaterials.add(name);
                }
            }

            LOGGER.info("[RageUtils] Config loaded ({} pinned materials)", cfg.pinnedMaterials.size());
        } catch (Exception e) {
            LOGGER.warn("[RageUtils] Failed to load config: {}", e.toString());
        }
    }

    public static void save() {
        HudConfig cfg = INSTANCE;
        JsonObject json = new JsonObject();
        json.addProperty("hudX",               cfg.hudX);
        json.addProperty("hudY",               cfg.hudY);
        json.addProperty("hudScale",           cfg.hudScale);
        json.addProperty("hideArmor",          cfg.hideArmor);
        json.addProperty("csbParticles",       cfg.csbParticles);
        json.addProperty("csbSound",           cfg.csbSound);
        json.addProperty("rateHudAutoSelect",  cfg.rateHudAutoSelect);
        json.addProperty("materialsWorldHeads",cfg.materialsWorldHeads);
        json.addProperty("materialsTotalMode", cfg.materialsTotalMode.name());

        JsonArray filtersArr = new JsonArray();
        for (MaterialData.GearFilter f : cfg.materialsGearFilters) filtersArr.add(f.name());
        json.add("materialsGearFilters", filtersArr);

        JsonArray pins = new JsonArray();
        for (String mat : cfg.pinnedMaterials) pins.add(mat);
        json.add("pinnedMaterials", pins);

        Path path = configPath();
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            LOGGER.warn("[RageUtils] Failed to save config: {}", e.toString());
        }
    }
}
