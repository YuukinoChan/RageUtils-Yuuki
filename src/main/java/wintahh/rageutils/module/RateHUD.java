package wintahh.rageutils.module;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import wintahh.rageutils.HudConfig;
import wintahh.rageutils.RageActionBar;
import wintahh.rageutils.materials.MaterialData;

import java.util.*;

public class RateHUD extends Module {

    //  ResourceType (mining)
    public static class ResourceType {
        public final String name;
        public final int color;
        public final long compressedValue;
        public final long superCompressedValue;
        public final long ultraCompressedValue;

        public ResourceType(String name, int color, long compressed, long superCompressed, long ultraCompressed) {
            this.name = name;
            this.color = color;
            this.compressedValue = compressed;
            this.superCompressedValue = superCompressed;
            this.ultraCompressedValue = ultraCompressed;
        }

        public long getValueForItemName(String displayName, int count) {
            if (ultraCompressedValue > 0 && displayName.equals("Ultra Compressed " + name))
                return ultraCompressedValue * count;
            if (superCompressedValue > 0 && displayName.equals("Super Compressed " + name))
                return superCompressedValue * count;
            if (displayName.equals("Compressed " + name))
                return compressedValue * count;
            if (displayName.equals(name))
                return count;
            return 0;
        }

        public Text getDisplayName() {
            return Text.literal(name).styled(s -> s.withBold(true).withColor(color));
        }

        public String getMaxCompressionName() {
            if (ultraCompressedValue > 0) return "UC";
            if (superCompressedValue > 0) return "SC";
            if (compressedValue == 1) return "";
            return "Comp";
        }

        public boolean isRawItem() {
            return compressedValue == 1 && superCompressedValue == 0 && ultraCompressedValue == 0;
        }

        public long getMaxCompressionValue() {
            if (ultraCompressedValue > 0) return ultraCompressedValue;
            if (superCompressedValue > 0) return superCompressedValue;
            return compressedValue;
        }
    }

    //  FishType (fishing)
    public static class FishType {
        public final String name;
        public final int color;
        public final int fishPerToken;

        public FishType(String name, int color, int fishPerToken) {
            this.name = name;
            this.color = color;
            this.fishPerToken = fishPerToken;
        }

        public double tokensForCount(long count) {
            return (double) count / fishPerToken;
        }
    }

    public static final List<FishType> FISH_TYPES = new ArrayList<>();

    static {
        FISH_TYPES.add(new FishType("Common Fish",    0x80c4ff, 64));
        FISH_TYPES.add(new FishType("Uncommon Fish",  0x4edb83, 48));
        FISH_TYPES.add(new FishType("Rare Fish",      0x4e8cdb, 32));
        FISH_TYPES.add(new FishType("Epic Fish",      0xb84edb, 16));
        FISH_TYPES.add(new FishType("Legendary Fish", 0xffa500,  8));
    }

    //  ArtifactType (brushing)

    public static class ArtifactType {
        public final String name;
        public final int color;
        /** How many of this artifact = 1 Legendary Artifact. */
        public final int perLegendary;

        public ArtifactType(String name, int color, int perLegendary) {
            this.name = name;
            this.color = color;
            this.perLegendary = perLegendary;
        }

        public double legendaryForCount(long count) {
            return (double) count / perLegendary;
        }
    }

    public static final int GOLEM_ESSENCE_LEGENDARY_VALUE = 4;

    public static final List<ArtifactType> ARTIFACT_TYPES = new ArrayList<>();

    static {
        // perLegendary = how many of this tier you trade for 1 Legendary Artifact
        ARTIFACT_TYPES.add(new ArtifactType("Common Artifact",    0x9b9b9b, 64));
        ARTIFACT_TYPES.add(new ArtifactType("Uncommon Artifact",  0x4edb83, 32));
        ARTIFACT_TYPES.add(new ArtifactType("Rare Artifact",      0x4e8cdb, 16));
        ARTIFACT_TYPES.add(new ArtifactType("Epic Artifact",      0xb84edb,  4));
        // Legendary Artifact is the target currency — 1:1
        ARTIFACT_TYPES.add(new ArtifactType("Legendary Artifact", 0xffa500,  1));
    }

    //  Anchor
    public enum Anchor {
        TOP_LEFT("top-left"),
        TOP_RIGHT("top-right"),
        BOTTOM_LEFT("bottom-left"),
        BOTTOM_RIGHT("bottom-right");

        public final String label;
        Anchor(String label) { this.label = label; }
    }

    /** Hide a section from view after this many ms with no new input (1 minute). */
    public static final long HIDE_AFTER_MS  = 60_000L;
    /** Reset (clear) session data after this many ms with no new input (10 minutes). */
    public static final long RESET_AFTER_MS = 600_000L;

    //  Auto activity selection
    // Initialized from config in registerEvents() — the RateHUD instance is constructed
    // during RageUtils class init, which happens before HudConfig.load() runs, so the
    // config value isn't available yet in the constructor.
    private boolean autoSelect = false;

    private final Map<String, Long> fishAutoBaseline = new HashMap<>();
    private boolean hasFishAutoBaseline = false;
    private boolean fishAutoScreenOpenLastTick = false;

    private final Map<String, Long> brushAutoBaseline = new HashMap<>();
    private boolean hasBrushAutoBaseline = false;
    private boolean brushAutoScreenOpenLastTick = false;

    //  Mining state
    private boolean miningEnabled = true;
    private Anchor anchor = Anchor.TOP_LEFT;

    private long mineLastActivityTime = 0;
    private boolean minePendingReset = false;

    private final Map<String, Long> previousBaseUnits = new HashMap<>();
    private final Map<String, Long> sessionMinedUnits = new HashMap<>();
    private final Map<String, Long> mineInitialUnits  = new HashMap<>();
    private final List<ResourceType> resources = new ArrayList<>();

    private long totalBlocksBroken = 0;
    private long mineStartTime = 0;
    private long mineLastBreakTime = 0;
    private long minePausedDuration = 0;
    private long minePauseStart = 0;

    private boolean blockWasBrokenThisTick = false;
    private int blocksBrokenThisTick = 0;
    private boolean hasInventoryBaseline = false;
    private boolean screenOpenLastTick = false;

    private String lastMinedResourceName = null;
    private ResourceType lastCollectedResource = null;

    private ResourceType displayResource = null;
    private String displayBlocksPerHour = "0";
    private String displayCompressionPrefix = "Comp";
    private double displayCompressionPerHour = 0;
    private double displayCompressionMined = 0;
    private boolean displayPaused = false;

    //  Fishing state
    private boolean fishingEnabled = true;
    private long fishLastActivityTime = 0;
    private boolean fishPendingReset = false;

    private final Map<String, Long> fishSessionCounts    = new HashMap<>();
    private final Map<String, Long> fishInitialCounts    = new HashMap<>();
    private final Map<String, Long> fishPreviousSnapshot = new HashMap<>();
    private boolean hasFishBaseline = false;
    private boolean fishScreenOpenLastTick = false;

    private long fishStartTime = 0;
    private long fishLastCatchTime = 0;
    private long fishPausedDuration = 0;
    private long fishPauseStart = 0;

    private double displayFishTokensPerHour = 0;
    private double displayFishTotalTokens = 0;
    private final Map<String, Long> displayFishCounts = new LinkedHashMap<>();
    private boolean displayFishPaused = false;

    //  Brushing state
    private boolean brushingEnabled = true;
    private long brushLastActivityTime = 0;
    private boolean brushPendingReset = false;

    private final Map<String, Long> brushSessionCounts    = new HashMap<>();
    private final Map<String, Long> brushInitialCounts    = new HashMap<>();
    private final Map<String, Long> brushPreviousSnapshot = new HashMap<>();
    private boolean hasBrushBaseline = false;
    private boolean brushScreenOpenLastTick = false;

    private long brushStartTime = 0;
    private long brushLastFindTime = 0;
    private long brushPausedDuration = 0;
    private long brushPauseStart = 0;

    private double displayBrushLegendaryPerHour = 0;
    private double displayBrushTotalLegendary = 0;
    private final Map<String, Long> displayBrushCounts = new LinkedHashMap<>();
    private boolean displayBrushPaused = false;

    //  Constructor
    public RateHUD() {
        super("RateHUD", "/yuuki rh");
        initResources();
    }

    //  Auto-select API
    public boolean isAutoSelect() { return autoSelect; }

    public void setAutoSelect(boolean v) {
        this.autoSelect = v;
        HudConfig.INSTANCE.rateHudAutoSelect = v;
        HudConfig.save();
    }

    public void toggleAutoSelect() {
        setAutoSelect(!autoSelect);
        RageActionBar.show(Text.literal("[RageUtils] RateHUD Auto: " + (autoSelect ? "§aON" : "§cOFF")));
    }

    //  Mining API
    public void onBlocksBroken(int blockCount) {
        if (blockCount <= 0) return;
        if (!miningEnabled) {
            if (!autoSelect) return;
            // Auto-enable mining with a clean baseline so pre-existing inventory isn't counted.
            miningEnabled = true;
            resetCounters();
            RageActionBar.show(Text.literal("[RageUtils] RateHUD Mining: §aON"));
        }
        // If idle timeout elapsed, reset now on first new input instead of mid-render
        if (minePendingReset) {
            minePendingReset = false;
            sessionMinedUnits.clear();
            mineInitialUnits.clear();
            totalBlocksBroken = 0;
            mineStartTime = 0;
            minePausedDuration = 0;
            minePauseStart = 0;
            mineLastBreakTime = 0;
            mineLastActivityTime = 0;
        minePendingReset = false;
            lastMinedResourceName = null;
            lastCollectedResource = null;
            hasInventoryBaseline = false;
            previousBaseUnits.clear();
            clearDisplayStats();
        }
        long now = System.currentTimeMillis();
        if (mineStartTime == 0) mineStartTime = now;
        mineLastBreakTime = now;
        mineLastActivityTime = now;
        blockWasBrokenThisTick = true;
        blocksBrokenThisTick += blockCount;
        totalBlocksBroken += blockCount;
    }

    public boolean isMiningEnabled() { return miningEnabled; }

    public void toggleMining() {
        miningEnabled = !miningEnabled;
        resetCounters();
        RageActionBar.show(Text.literal("[RageUtils] RateHUD Mining: " + (miningEnabled ? "§aON" : "§cOFF")));
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                    Text.literal("[RageUtils] RateHUD anchor: §e" + anchor.label),
                    true);
        }
    }

    public void resetCounters() {
        long now = System.currentTimeMillis();
        sessionMinedUnits.clear();
        mineInitialUnits.clear();
        totalBlocksBroken = 0;
        mineStartTime = miningEnabled ? now : 0;
        minePausedDuration = 0;
        minePauseStart = 0;
        mineLastBreakTime = now;
        blockWasBrokenThisTick = false;
        blocksBrokenThisTick = 0;
        hasInventoryBaseline = false;
        lastMinedResourceName = null;
        lastCollectedResource = null;
        screenOpenLastTick = false;
        previousBaseUnits.clear();
        clearDisplayStats();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Map<String, Long> snap = getBaseUnitSnapshot(mc);
            previousBaseUnits.putAll(snap);
            mineInitialUnits.putAll(snap);
            hasInventoryBaseline = true;
        }
    }

    //  Fishing API
    public boolean isFishingEnabled() { return fishingEnabled; }

    public void toggleFishing() {
        fishingEnabled = !fishingEnabled;
        resetFishingCounters();
        RageActionBar.show(Text.literal("[RageUtils] RateHUD Fishing: " + (fishingEnabled ? "§aON" : "§cOFF")));
    }

    public void resetFishingCounters() {
        fishSessionCounts.clear();
        fishInitialCounts.clear();
        fishPreviousSnapshot.clear();
        fishScreenOpenLastTick = false;
        fishStartTime = 0;
        fishLastCatchTime = 0;
        fishPausedDuration = 0;
        fishPauseStart = 0;
        fishLastActivityTime = 0;
        fishPendingReset = false;
        hasFishBaseline = false;
        clearFishDisplayStats();
        seedFishBaselineFromInventory();
    }

    //  Brushing API
    public boolean isBrushingEnabled() { return brushingEnabled; }

    public void toggleBrushing() {
        brushingEnabled = !brushingEnabled;
        resetBrushingCounters();
        RageActionBar.show(Text.literal("[RageUtils] RateHUD Brushing: " + (brushingEnabled ? "§aON" : "§cOFF")));
    }

    public void resetBrushingCounters() {
        brushSessionCounts.clear();
        brushInitialCounts.clear();
        brushPreviousSnapshot.clear();
        brushScreenOpenLastTick = false;
        brushStartTime = 0;
        brushLastFindTime = 0;
        brushPausedDuration = 0;
        brushPauseStart = 0;
        brushLastActivityTime = 0;
        brushPendingReset = false;
        hasBrushBaseline = false;
        clearBrushDisplayStats();
        seedBrushBaselineFromInventory();
    }

    //  Shared inventory helpers
    public long getInventoryBaseUnits(ResourceType resource) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return 0;
        return getBaseUnitSnapshot(mc).getOrDefault(resource.name, 0L);
    }

    public double getLiveCompressedCount(ResourceType resource) {
        long units = getInventoryBaseUnits(resource);
        long divisor = resource.getMaxCompressionValue();
        return divisor > 0 ? (double) units / divisor : 0;
    }

    public double getLiveCompressedCount(String materialName) {
        ResourceType resource = findResource(materialName);
        return resource != null ? getLiveCompressedCount(resource) : 0;
    }

    public ResourceType findResource(String materialName) {
        if (materialName == null) return null;
        String resourceName = MaterialData.resolveResourceName(materialName);
        for (ResourceType resource : resources) {
            if (resource.name.equalsIgnoreCase(resourceName)) return resource;
        }
        String resolved = MaterialData.find(materialName)
                .map(MaterialData.MaterialEntry::name)
                .orElse(materialName);
        for (ResourceType resource : resources) {
            if (resource.name.equalsIgnoreCase(resolved)) return resource;
        }
        return null;
    }

    //  Event registration
    public void registerEvents() {
        // HudConfig.load() has run by now (called before registerEvents in onInitializeClient).
        this.autoSelect = HudConfig.INSTANCE.rateHudAutoSelect;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickMining(client);
            tickFishing(client);
            tickBrushing(client);
        });
    }

    //  Mining tick
    private void tickMining(MinecraftClient client) {
        if (!miningEnabled || client.player == null || client.world == null) {
            clearDisplayStats();
            return;
        }

        // Auto-reset session data after 10 min of inactivity (but keep enabled)
        // Only flag for reset here; actual reset happens on next player input (onBlocksBroken).
        if (!minePendingReset && mineLastActivityTime > 0 && mineStartTime > 0) {
            long idleMs = System.currentTimeMillis() - mineLastActivityTime;
            if (idleMs > RESET_AFTER_MS) {
                minePendingReset = true;
            }
        }

        if (client.currentScreen != null) {
            screenOpenLastTick = true;
            return;
        }
        if (screenOpenLastTick) {
            previousBaseUnits.clear();
            previousBaseUnits.putAll(getBaseUnitSnapshot(client));
            screenOpenLastTick = false;
            hasInventoryBaseline = true;
            blockWasBrokenThisTick = false;
            blocksBrokenThisTick = 0;
            return;
        }

        Map<String, Long> current = getBaseUnitSnapshot(client);
        if (!hasInventoryBaseline) {
            previousBaseUnits.clear();
            previousBaseUnits.putAll(current);
            mineInitialUnits.clear();
            mineInitialUnits.putAll(current);
            hasInventoryBaseline = true;
            blockWasBrokenThisTick = false;
            blocksBrokenThisTick = 0;
            return;
        }

        processDelta(current);
        previousBaseUnits.clear();
        previousBaseUnits.putAll(current);
        blockWasBrokenThisTick = false;
        blocksBrokenThisTick = 0;

        ResourceType top = getTopResource();
        if (top == null || mineStartTime == 0) {
            clearDisplayStats();
            return;
        }

        long now = System.currentTimeMillis();
        boolean isPaused = (now - mineLastBreakTime) > 3000;
        if (isPaused) {
            if (minePauseStart == 0) minePauseStart = mineLastBreakTime + 3000;
        } else {
            if (minePauseStart > 0) {
                minePausedDuration += now - minePauseStart;
                minePauseStart = 0;
            }
        }

        long activeDuration = now - mineStartTime - minePausedDuration - (isPaused ? now - minePauseStart : 0);
        double elapsedHours = activeDuration / 3600000.0;

        double blocksPerHour = elapsedHours > 0 ? totalBlocksBroken / elapsedHours : 0;
        String blocksPerHourStr = blocksPerHour >= 100_000
                ? String.format(Locale.ROOT, "%.0fk", blocksPerHour / 1000)
                : String.format(Locale.ROOT, "%.0f", blocksPerHour);

        long sessionUnits = sessionMinedUnits.getOrDefault(top.name, 0L);
        long initialUnits = mineInitialUnits.getOrDefault(top.name, 0L);
        double sessionMined = top.getMaxCompressionValue() > 0
                ? (double) sessionUnits / top.getMaxCompressionValue() : 0;
        double totalMined = top.getMaxCompressionValue() > 0
                ? (double) (initialUnits + sessionUnits) / top.getMaxCompressionValue() : 0;
        double sessionPerHour = (isPaused || elapsedHours <= 0) ? 0 : sessionMined / elapsedHours;

        displayResource = top;
        displayBlocksPerHour = blocksPerHourStr;
        displayCompressionPrefix = top.getMaxCompressionName();
        displayCompressionPerHour = sessionPerHour;
        displayCompressionMined = totalMined;
        displayPaused = isPaused;
    }


    //  Fishing tick

    private void tickFishing(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clearFishDisplayStats();
            hasFishAutoBaseline = false;
            return;
        }
        if (!fishingEnabled) {
            clearFishDisplayStats();
            if (autoSelect) detectFishAutoEnable(client);
            else hasFishAutoBaseline = false;
            return;
        }

        if (client.currentScreen != null) {
            fishScreenOpenLastTick = true;
            return;
        }

        // Auto-reset session data after 10 min of inactivity
        // Only flag for reset here; actual reset happens on next player input (onFishCaught).
        if (!fishPendingReset && fishLastActivityTime > 0 && fishStartTime > 0) {
            long idleMs = System.currentTimeMillis() - fishLastActivityTime;
            if (idleMs > RESET_AFTER_MS) {
                fishPendingReset = true;
            }
        }

        if (fishScreenOpenLastTick) {
            Map<String, Long> snap = getFishSnapshot(client);
            fishPreviousSnapshot.clear();
            fishPreviousSnapshot.putAll(snap);
            if (fishInitialCounts.isEmpty()) {
                fishInitialCounts.putAll(snap);
                startFishingDisplayIfInventoryHasFish(snap);
            }
            fishScreenOpenLastTick = false;
            hasFishBaseline = true;
            return;
        }

        Map<String, Long> current = getFishSnapshot(client);
        if (!hasFishBaseline) {
            fishPreviousSnapshot.clear();
            fishPreviousSnapshot.putAll(current);
            fishInitialCounts.clear();
            fishInitialCounts.putAll(current);
            hasFishBaseline = true;
            // If player already has fish, start the timer so totals display immediately
            startFishingDisplayIfInventoryHasFish(current);
            return;
        }

        boolean caughtSomething = false;
        for (FishType ft : FISH_TYPES) {
            long prev = fishPreviousSnapshot.getOrDefault(ft.name, 0L);
            long cur  = current.getOrDefault(ft.name, 0L);
            long delta = cur - prev;
            if (delta > 0) {
                fishSessionCounts.merge(ft.name, delta, Long::sum);
                caughtSomething = true;
            }
        }

        fishPreviousSnapshot.clear();
        fishPreviousSnapshot.putAll(current);

        if (caughtSomething) {
            long now = System.currentTimeMillis();
            // If idle timeout elapsed, reset now on first new catch instead of mid-render
            if (fishPendingReset) {
                fishPendingReset = false;
                fishSessionCounts.clear();
                fishInitialCounts.clear();
                fishStartTime = 0;
                fishLastCatchTime = 0;
                fishPausedDuration = 0;
                fishPauseStart = 0;
                fishLastActivityTime = 0;
                hasFishBaseline = false;
                fishPreviousSnapshot.clear();
                clearFishDisplayStats();
            }
            if (fishStartTime == 0) fishStartTime = now;
            fishLastCatchTime = now;
            fishLastActivityTime = now;
        }

        if (fishStartTime == 0) {
            clearFishDisplayStats();
            return;
        }

        long now = System.currentTimeMillis();
        boolean isPaused = fishLastCatchTime > 0 && (now - fishLastCatchTime) > 10_000;

        if (isPaused) {
            if (fishPauseStart == 0) fishPauseStart = fishLastCatchTime + 10_000;
        } else {
            if (fishPauseStart > 0) {
                fishPausedDuration += now - fishPauseStart;
                fishPauseStart = 0;
            }
        }

        long activeDuration = now - fishStartTime - fishPausedDuration - (isPaused ? now - fishPauseStart : 0);
        double elapsedHours = activeDuration / 3600000.0;

        refreshFishDisplay(isPaused, elapsedHours);
    }


    //  Fishing auto-detection (runs while fishing is disabled and autoSelect is on)

    private void detectFishAutoEnable(MinecraftClient client) {
        if (client.currentScreen != null) {
            fishAutoScreenOpenLastTick = true;
            return;
        }
        if (fishAutoScreenOpenLastTick) {
            fishAutoBaseline.clear();
            fishAutoBaseline.putAll(getFishSnapshot(client));
            fishAutoScreenOpenLastTick = false;
            hasFishAutoBaseline = true;
            return;
        }

        Map<String, Long> current = getFishSnapshot(client);
        if (!hasFishAutoBaseline) {
            fishAutoBaseline.clear();
            fishAutoBaseline.putAll(current);
            hasFishAutoBaseline = true;
            return;
        }

        boolean gained = false;
        for (FishType ft : FISH_TYPES) {
            long prev = fishAutoBaseline.getOrDefault(ft.name, 0L);
            long cur = current.getOrDefault(ft.name, 0L);
            if (cur - prev > 0) { gained = true; break; }
        }
        fishAutoBaseline.clear();
        fishAutoBaseline.putAll(current);

        if (gained) {
            // Enable with a clean baseline so the triggering gain is not counted as session.
            fishingEnabled = true;
            resetFishingCounters();
            RageActionBar.show(Text.literal("[RageUtils] RateHUD Fishing: §aON"));
        }
    }

    //  Fish snapshot

    private Map<String, Long> getFishSnapshot(MinecraftClient mc) {
        Map<String, Long> snapshot = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            addFishStackToSnapshot(snapshot, mc.player.getInventory().getStack(i));
        }
        if (mc.player.currentScreenHandler != null) {
            addFishStackToSnapshot(snapshot, mc.player.currentScreenHandler.getCursorStack());
        }
        return snapshot;
    }

    private void addFishStackToSnapshot(Map<String, Long> snapshot, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = stack.getName().getString();
        for (FishType ft : FISH_TYPES) {
            if (ft.name.equals(name)) {
                snapshot.merge(ft.name, (long) stack.getCount(), Long::sum);
                break;
            }
        }
    }

    private void seedFishBaselineFromInventory() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        Map<String, Long> snap = getFishSnapshot(mc);
        fishPreviousSnapshot.clear();
        fishPreviousSnapshot.putAll(snap);
        fishInitialCounts.clear();
        fishInitialCounts.putAll(snap);
        hasFishBaseline = true;
        startFishingDisplayIfInventoryHasFish(snap);
    }

    private void startFishingDisplayIfInventoryHasFish(Map<String, Long> snapshot) {
        boolean hasAnyFish = snapshot.values().stream().anyMatch(v -> v > 0);
        if (!hasAnyFish) return;

        if (fishStartTime == 0) {
            fishStartTime = System.currentTimeMillis();
            fishLastCatchTime = fishStartTime;
            fishLastActivityTime = fishStartTime;
        }
        refreshFishDisplay(false, 0);
    }

    private void refreshFishDisplay(boolean isPaused, double elapsedHours) {
        double sessionTokens = 0;
        double liveTotalTokens = 0;
        Map<String, Long> liveCounts = new LinkedHashMap<>();
        for (FishType ft : FISH_TYPES) {
            long sessionCount = fishSessionCounts.getOrDefault(ft.name, 0L);
            long initialCount = fishInitialCounts.getOrDefault(ft.name, 0L);
            long displayCount = initialCount + sessionCount;
            liveCounts.put(ft.name, displayCount);
            sessionTokens += ft.tokensForCount(sessionCount);
            liveTotalTokens += ft.tokensForCount(displayCount);
        }

        displayFishTokensPerHour = (isPaused || elapsedHours <= 0) ? 0 : sessionTokens / elapsedHours;
        displayFishTotalTokens = liveTotalTokens;
        displayFishCounts.clear();
        displayFishCounts.putAll(liveCounts);
        displayFishPaused = isPaused;
    }


    //  Brushing tick

    private void tickBrushing(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clearBrushDisplayStats();
            hasBrushAutoBaseline = false;
            return;
        }
        if (!brushingEnabled) {
            clearBrushDisplayStats();
            if (autoSelect) detectBrushAutoEnable(client);
            else hasBrushAutoBaseline = false;
            return;
        }

        if (client.currentScreen != null) {
            brushScreenOpenLastTick = true;
            return;
        }

        // Auto-reset session data after 10 min of inactivity
        // Only flag for reset here; actual reset happens on next player input (onArtifactFound).
        if (!brushPendingReset && brushLastActivityTime > 0 && brushStartTime > 0) {
            long idleMs = System.currentTimeMillis() - brushLastActivityTime;
            if (idleMs > RESET_AFTER_MS) {
                brushPendingReset = true;
            }
        }

        if (brushScreenOpenLastTick) {
            Map<String, Long> snap = getBrushSnapshot(client);
            brushPreviousSnapshot.clear();
            brushPreviousSnapshot.putAll(snap);
            if (brushInitialCounts.isEmpty()) {
                brushInitialCounts.putAll(snap);
                startBrushingDisplayIfInventoryHasArtifacts(snap);
            }
            brushScreenOpenLastTick = false;
            hasBrushBaseline = true;
            return;
        }

        Map<String, Long> current = getBrushSnapshot(client);
        if (!hasBrushBaseline) {
            brushPreviousSnapshot.clear();
            brushPreviousSnapshot.putAll(current);
            brushInitialCounts.clear();
            brushInitialCounts.putAll(current);
            hasBrushBaseline = true;
            // If player already has artifacts, start the timer so totals display immediately
            startBrushingDisplayIfInventoryHasArtifacts(current);
            return;
        }

        boolean foundSomething = false;
        for (ArtifactType at : ARTIFACT_TYPES) {
            long prev  = brushPreviousSnapshot.getOrDefault(at.name, 0L);
            long cur   = current.getOrDefault(at.name, 0L);
            long delta = cur - prev;
            if (delta > 0) {
                brushSessionCounts.merge(at.name, delta, Long::sum);
                foundSomething = true;
            }
        }
        // Also track Golem Essence separately
        long prevGolem  = brushPreviousSnapshot.getOrDefault("Golem Essence", 0L);
        long curGolem   = current.getOrDefault("Golem Essence", 0L);
        long deltaGolem = curGolem - prevGolem;
        if (deltaGolem > 0) {
            brushSessionCounts.merge("Golem Essence", deltaGolem, Long::sum);
            foundSomething = true;
        }

        brushPreviousSnapshot.clear();
        brushPreviousSnapshot.putAll(current);

        if (foundSomething) {
            long now = System.currentTimeMillis();
            // If idle timeout elapsed, reset now on first new find instead of mid-render
            if (brushPendingReset) {
                brushPendingReset = false;
                brushSessionCounts.clear();
                brushInitialCounts.clear();
                brushStartTime = 0;
                brushLastFindTime = 0;
                brushPausedDuration = 0;
                brushPauseStart = 0;
                brushLastActivityTime = 0;
                hasBrushBaseline = false;
                brushPreviousSnapshot.clear();
                clearBrushDisplayStats();
            }
            if (brushStartTime == 0) brushStartTime = now;
            brushLastFindTime = now;
            brushLastActivityTime = now;
        }

        if (brushStartTime == 0) {
            clearBrushDisplayStats();
            return;
        }

        long now = System.currentTimeMillis();
        boolean isPaused = brushLastFindTime > 0 && (now - brushLastFindTime) > 10_000;

        if (isPaused) {
            if (brushPauseStart == 0) brushPauseStart = brushLastFindTime + 10_000;
        } else {
            if (brushPauseStart > 0) {
                brushPausedDuration += now - brushPauseStart;
                brushPauseStart = 0;
            }
        }

        long activeDuration = now - brushStartTime - brushPausedDuration - (isPaused ? now - brushPauseStart : 0);
        double elapsedHours = activeDuration / 3600000.0;

        refreshBrushDisplay(isPaused, elapsedHours);
    }


    //  Brushing auto-detection (runs while brushing is disabled and autoSelect is on)

    private void detectBrushAutoEnable(MinecraftClient client) {
        if (client.currentScreen != null) {
            brushAutoScreenOpenLastTick = true;
            return;
        }
        if (brushAutoScreenOpenLastTick) {
            brushAutoBaseline.clear();
            brushAutoBaseline.putAll(getBrushSnapshot(client));
            brushAutoScreenOpenLastTick = false;
            hasBrushAutoBaseline = true;
            return;
        }

        Map<String, Long> current = getBrushSnapshot(client);
        if (!hasBrushAutoBaseline) {
            brushAutoBaseline.clear();
            brushAutoBaseline.putAll(current);
            hasBrushAutoBaseline = true;
            return;
        }

        boolean gained = false;
        for (ArtifactType at : ARTIFACT_TYPES) {
            long prev = brushAutoBaseline.getOrDefault(at.name, 0L);
            long cur = current.getOrDefault(at.name, 0L);
            if (cur - prev > 0) { gained = true; break; }
        }
        if (!gained) {
            long prevGolem = brushAutoBaseline.getOrDefault("Golem Essence", 0L);
            long curGolem = current.getOrDefault("Golem Essence", 0L);
            if (curGolem - prevGolem > 0) gained = true;
        }
        brushAutoBaseline.clear();
        brushAutoBaseline.putAll(current);

        if (gained) {
            // Enable with a clean baseline so the triggering gain is not counted as session.
            brushingEnabled = true;
            resetBrushingCounters();
            RageActionBar.show(Text.literal("[RageUtils] RateHUD Brushing: §aON"));
        }
    }

    //  Brush snapshot

    private Map<String, Long> getBrushSnapshot(MinecraftClient mc) {
        Map<String, Long> snapshot = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            addBrushStackToSnapshot(snapshot, mc.player.getInventory().getStack(i));
        }
        if (mc.player.currentScreenHandler != null) {
            addBrushStackToSnapshot(snapshot, mc.player.currentScreenHandler.getCursorStack());
        }
        return snapshot;
    }

    private void addBrushStackToSnapshot(Map<String, Long> snapshot, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = stack.getName().getString();
        for (ArtifactType at : ARTIFACT_TYPES) {
            if (at.name.equals(name)) {
                snapshot.merge(at.name, (long) stack.getCount(), Long::sum);
                return;
            }
        }
        if ("Golem Essence".equals(name)) {
            snapshot.merge("Golem Essence", (long) stack.getCount(), Long::sum);
        }
    }

    private void seedBrushBaselineFromInventory() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        Map<String, Long> snap = getBrushSnapshot(mc);
        brushPreviousSnapshot.clear();
        brushPreviousSnapshot.putAll(snap);
        brushInitialCounts.clear();
        brushInitialCounts.putAll(snap);
        hasBrushBaseline = true;
        startBrushingDisplayIfInventoryHasArtifacts(snap);
    }

    private void startBrushingDisplayIfInventoryHasArtifacts(Map<String, Long> snapshot) {
        boolean hasAnyArtifact = snapshot.values().stream().anyMatch(v -> v > 0);
        if (!hasAnyArtifact) return;

        if (brushStartTime == 0) {
            brushStartTime = System.currentTimeMillis();
            brushLastFindTime = brushStartTime;
            brushLastActivityTime = brushStartTime;
        }
        refreshBrushDisplay(false, 0);
    }

    private void refreshBrushDisplay(boolean isPaused, double elapsedHours) {
        double sessionLegendary = 0;
        double liveTotalLegendary = 0;
        Map<String, Long> liveCounts = new LinkedHashMap<>();
        for (ArtifactType at : ARTIFACT_TYPES) {
            long sessionCount = brushSessionCounts.getOrDefault(at.name, 0L);
            long initialCount = brushInitialCounts.getOrDefault(at.name, 0L);
            long displayCount = initialCount + sessionCount;
            liveCounts.put(at.name, displayCount);
            sessionLegendary += at.legendaryForCount(sessionCount);
            liveTotalLegendary += at.legendaryForCount(displayCount);
        }
        long sessionGolem = brushSessionCounts.getOrDefault("Golem Essence", 0L);
        long initialGolem = brushInitialCounts.getOrDefault("Golem Essence", 0L);
        long displayGolem = initialGolem + sessionGolem;
        liveCounts.put("Golem Essence", displayGolem);
        sessionLegendary += sessionGolem * GOLEM_ESSENCE_LEGENDARY_VALUE;
        liveTotalLegendary += displayGolem * GOLEM_ESSENCE_LEGENDARY_VALUE;

        displayBrushLegendaryPerHour = (isPaused || elapsedHours <= 0) ? 0 : sessionLegendary / elapsedHours;
        displayBrushTotalLegendary = liveTotalLegendary;
        displayBrushCounts.clear();
        displayBrushCounts.putAll(liveCounts);
        displayBrushPaused = isPaused;
    }


    //  Mining snapshot / delta

    private Map<String, Long> getBaseUnitSnapshot(MinecraftClient mc) {
        Map<String, Long> snapshot = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            addStackToSnapshot(snapshot, mc.player.getInventory().getStack(i));
        }
        if (mc.player.currentScreenHandler != null) {
            addStackToSnapshot(snapshot, mc.player.currentScreenHandler.getCursorStack());
        }
        return snapshot;
    }

    private void addStackToSnapshot(Map<String, Long> snapshot, ItemStack stack) {
        if (stack.isEmpty()) return;
        String displayName = stack.getName().getString();
        // Resolve alias (e.g. "Neptune Key" → "Ocean Key")
        String resolvedName = ITEM_NAME_ALIASES.getOrDefault(displayName, displayName);
        for (ResourceType resource : resources) {
            long value = resource.getValueForItemName(resolvedName, stack.getCount());
            if (value > 0) {
                snapshot.merge(resource.name, value, Long::sum);
                break;
            }
        }
    }

    private void processDelta(Map<String, Long> current) {
        for (ResourceType resource : resources) {
            long currentUnits = current.getOrDefault(resource.name, 0L);
            long previousUnits = previousBaseUnits.getOrDefault(resource.name, 0L);
            long delta = currentUnits - previousUnits;
            if (delta > 0) {
                // Normal blocks require a block-break event this tick.
                // Slow-mine blocks (e.g. Melon, Pink) drop items on a different tick
                // from the break event, so we allow their deltas through unconditionally.
                if (!blockWasBrokenThisTick && !SLOW_BLOCK_NAMES.contains(resource.name)) continue;
                if (lastMinedResourceName == null || !lastMinedResourceName.equals(resource.name)) {
                    resetCountersForTrackedResource(resource);
                } else {
                    trackResource(resource);
                }
                sessionMinedUnits.merge(resource.name, delta, Long::sum);
            }
        }
    }

    private ResourceType getTopResource() {
        if (lastCollectedResource != null) return lastCollectedResource;
        return sessionMinedUnits.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> findResource(e.getKey()))
                .orElse(null);
    }

    private void trackResource(ResourceType resource) {
        lastMinedResourceName = resource.name;
        lastCollectedResource = resource;
    }

    private void resetCountersForTrackedResource(ResourceType resource) {
        long now = System.currentTimeMillis();
        sessionMinedUnits.clear();
        totalBlocksBroken = blocksBrokenThisTick;
        mineStartTime = now;
        minePausedDuration = 0;
        minePauseStart = 0;
        mineLastBreakTime = now;
        trackResource(resource);
        clearDisplayStats();
    }


    //  Getters — mining

    public ResourceType getTopResourcePublic()      { return displayResource; }
    public List<ResourceType> getResources()        { return resources; }
    public long getStartTime()                      { return mineStartTime; }
    public boolean isPaused()                       { return displayPaused; }
    public String getDisplayBlocksPerHour()         { return displayBlocksPerHour; }
    public String getDisplayCompressionPrefix()     { return displayCompressionPrefix; }
    public double getDisplayCompressionPerHour()    { return displayCompressionPerHour; }
    public double getDisplayCompressionMined()      { return displayCompressionMined; }
    public long getMineLastActivityTime()           { return mineLastActivityTime; }


    //  Getters — fishing

    public long getFishStartTime()                  { return fishStartTime; }
    public boolean isFishingPaused()                { return displayFishPaused; }
    public double getDisplayFishTokensPerHour()     { return displayFishTokensPerHour; }
    public double getDisplayFishTotalTokens()       { return displayFishTotalTokens; }
    public Map<String, Long> getDisplayFishCounts() { return Collections.unmodifiableMap(displayFishCounts); }
    public long getFishLastActivityTime()           { return fishLastActivityTime; }


    //  Getters — brushing

    public long getBrushStartTime()                       { return brushStartTime; }
    public boolean isBrushingPaused()                     { return displayBrushPaused; }
    public double getDisplayBrushLegendaryPerHour()       { return displayBrushLegendaryPerHour; }
    public double getDisplayBrushTotalLegendary()         { return displayBrushTotalLegendary; }
    public Map<String, Long> getDisplayBrushCounts()      { return Collections.unmodifiableMap(displayBrushCounts); }
    public long getBrushLastActivityTime()                { return brushLastActivityTime; }


    //  Clear helpers

    private void clearDisplayStats() {
        displayResource = null;
        displayBlocksPerHour = "0";
        displayCompressionPrefix = "Comp";
        displayCompressionPerHour = 0;
        displayCompressionMined = 0;
        displayPaused = false;
    }

    private void clearFishDisplayStats() {
        displayFishTokensPerHour = 0;
        displayFishTotalTokens = 0;
        displayFishCounts.clear();
        displayFishPaused = false;
    }

    private void clearBrushDisplayStats() {
        displayBrushLegendaryPerHour = 0;
        displayBrushTotalLegendary = 0;
        displayBrushCounts.clear();
        displayBrushPaused = false;
    }


    //  Resource definitions

    private void initResources() {
        r("Oak Wood",       0x9e512c, 64,  0,     0);
        r("Loam Soil",      0x4b3d38, 64,  0,     0);
        r("Peat",           0x5f3a1f, 64,  0,     0);
        r("Dirt",           0x8d6d49, 96,  0,     0);
        r("Ryegrass",       0xc57d59, 64,  0,     0);
        r("Lemongrass",     0xa1b45b, 64,  0,     0);
        r("Oak Leaves",     0x6b8835, 64,  0,     0);
        r("Silvergrass",    0x4b794b, 64,  4096,  0);
        r("Stone",          0x7e7e7e, 64,  4096,  0);
        r("Moss",           0x69992f, 64,  4096,  0);
        r("Coal",           0x443f3f, 64,  4096,  0);
        r("Iron",           0xd5d5d5, 64,  4096,  0);
        r("Dripstone",      0x966b3e, 64,  4096,  0);
        r("Gold",           0xf6e94d, 64,  4096,  0);
        r("Emerald",        0x00d639, 64,  4096,  0);
        r("Diamond",        0x32e8fc, 64,  4096,  0);
        r("Amethyst",       0x985bc4, 64,  0,     0);
        r("Cherry Wood",    0x70424f, 64,  4096,  0);
        r("Rose Stone",     0xe87582, 64,  4096,  0);
        r("Cherry Leaves",  0xd98a99, 64,  4096,  0);
        r("Bloom Clay",     0x9e4d4d, 64,  4096,  0);
        r("Cherry Sand",    0xeda3a8, 64,  4096,  0);
        r("Spirit Clay",    0x9c5b75, 64,  4096,  0);
        r("Pink Petal",     0xfc949e, 64,  4096,  0);
        r("Snow",             0xf4f4f4, 128, 16384, 0);
        r("Snowpack",         0xeaeaea, 128, 16384, 0);
        r("Ice Wool",         0x39add6, 128, 16384, 0);
        r("Icestone",         0x6ea8d3, 128, 16384, 0);
        r("Cryoblood",        0x495b89, 128, 16384, 0);
        r("Blue Ice",         0x73a5ca, 128, 16384, 0);
        r("Glacierstone",     0x2b2e8d, 128, 16384, 0);
        r("Frostgene",        0x2e6d8a, 128, 16384, 0);
        r("Crimson Stem",     0x891e1e, 128, 16384, 0);
        r("Magma",            0xfc6900, 128, 16384, 0);
        r("Firewart",         0x9f1212, 128, 16384, 0);
        r("Ashrock",          0x6d2a2a, 128, 16384, 0);
        r("Infernal Quartz",  0xe3c7b3, 128, 16384, 0);
        r("Ancient Debris",   0x39291f, 128, 16384, 0);
        r("Hellplate",        0x8f4747, 128, 16384, 0);
        r("Bloodplate",       0xbe382a, 128, 16384, 0);
        r("Mycelium",         0xe5ded3, 64,  4096,  0);
        r("Aether Wood",      0xe3d0a1, 64,  4096,  262144);
        r("Celestone",        0xf1eee9, 64,  4096,  262144);
        r("Luminium",         0xcdcdcf, 64,  4096,  262144);
        r("Aether Tile",      0xf6f6f6, 64,  4096,  262144);
        r("Aurum",            0xe3b64b, 64,  4096,  262144);
        r("Solar Fiber",      0xefd556, 64,  4096,  262144);
        r("Solaris",          0xd6a240, 64,  4096,  262144);
        r("Faded",            0x8c7c6d, 64,  4096,  262144);
        r("Dusk",             0x7b7b7b, 64,  4096,  262144);
        r("Void",             0x0d1c24, 64,  4096,  262144);
        r("Blackstone",       0x29232a, 64,  4096,  262144);
        r("Obsidian",         0x432a6d, 64,  4096,  262144);
        r("Crying Obsidian",  0x31076d, 64,  4096,  262144);
        r("Purpur",           0xa57aa5, 64,  4096,  262144);
        r("Dark Matter",      0x6c2f96, 64,  4096,  262144);
        r("Void Sensor",      0x0d1c24, 64,  4096,  262144);
        r("Tidewood",         0x0d7276, 64,  4096,  262144);
        r("Azure",            0x3c56fc, 64,  4096,  262144);
        r("Seastone",         0x62c5b0, 64,  4096,  262144);
        r("Deepstone",        0x274d4a, 64,  4096,  262144);
        r("Aqualith",         0x68cee4, 64,  4096,  262144);
        r("Sandstone",  0xd6c68c, 128, 16384, 2097152);
        r("Straw",      0xc7a027, 128, 16384, 2097152);
        r("Amber",      0xdd6001, 128, 16384, 2097152);
        r("Rust",       0xa6491f, 128, 16384, 2097152);
        r("Relic",      0x6a1d0c, 128, 16384, 2097152);
        r("Flareon",    0xd6683b, 128, 16384, 2097152);
        r("Poppy",                     0xff3333, 1, 0, 0);
        r("Warden Heart",              0x3af0c0, 1, 0, 0);
        r("Kunai",                     0x8888aa, 1, 0, 0);
        r("Bamboo",                    0x77cc44, 1, 0, 0);
        r("Lovers Tear",               0xff88cc, 1, 0, 0);
        r("Frost Powder",              0xaaddff, 1, 0, 0);
        r("Icefur",                    0x88ccee, 1, 0, 0);
        r("Icedrake Egg",              0x55aadd, 1, 0, 0);
        r("Fire Core",                 0xff6600, 1, 0, 0);
        r("Fire Powder",               0xff4400, 1, 0, 0);
        r("Aether Residue",            0xccffaa, 1, 0, 0);
        r("Luminium Core",             0xddddff, 1, 0, 0);
        r("Abyss Core",                0x550077, 1, 0, 0);
        r("Ocean Token",               0x00aaff, 1, 0, 0);
        r("Lobster",                   0xff6644, 1, 0, 0);
        r("Ocean Key",                 0x0077cc, 1, 0, 0);
        r("Token (Tidewood path)",     0x0d7276, 1, 0, 0);
        r("Token (Azure path)",        0x3c56fc, 1, 0, 0);
        r("Token (Seastone path)",     0x62c5b0, 1, 0, 0);
        r("Token (Deepstone path)",    0x274d4a, 1, 0, 0);
        r("Token (Aqualith path)",     0x68cee4, 1, 0, 0);
        r("Leg. Artifact - Rust",      0xa6491f, 1, 0, 0);
        r("Leg. Artifact - Relic",     0x6a1d0c, 1, 0, 0);
        r("Leg. Artifact - Flareon",   0xd6683b, 1, 0, 0);
        r("Leg. Artifact - Total",     0xffcc44, 1, 0, 0);
        r("Worm Teeth",                0xddbb88, 1, 0, 0);
        r("Sting",                     0xaacc44, 1, 0, 0);
        r("Golem Essence",             0x888888, 1, 0, 0);
        r("Dog Core",                  0xcc8844, 1, 0, 0);
        // Slow-mine non-blast blocks
        r("Melon",          0x55cc22, 64, 4096, 262144);
        r("Pink",           0xff88cc, 64, 4096, 262144);
        r("Brown Mushroom", 0xaa7744, 64, 4096, 262144);
        r("Red Mushroom",   0xee3333, 64, 4096, 262144);
        r("Magic Dirt",     0x66dd88, 64, 4096, 262144);
        r("Magic Wart",     0x33cc99, 64, 4096, 262144);
    }

    private void r(String name, int color, long c, long s, long u) {
        resources.add(new ResourceType(name, color, c, s, u));
    }

    // Slow-mine non-blast blocks — tracked like normal mining but shown with a distinct overlay colour
    public static final java.util.Set<String> SLOW_BLOCK_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
        "Melon", "Pink", "Brown Mushroom", "Red Mushroom", "Magic Dirt", "Magic Wart"
    ));

    /**
     * Maps alternate in-game item names to the ResourceType name they should count toward.
     * e.g. "Neptune Key" drops count toward the "Ocean Key" resource.
     */
    public static final Map<String, String> ITEM_NAME_ALIASES = new HashMap<>();

    static {
        ITEM_NAME_ALIASES.put("Neptune Key", "Ocean Key");
    }

    public boolean isSlowBlock(ResourceType res) {
        return res != null && SLOW_BLOCK_NAMES.contains(res.name);
    }
}
