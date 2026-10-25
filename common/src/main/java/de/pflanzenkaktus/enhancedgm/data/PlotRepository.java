package de.pflanzenkaktus.enhancedgm.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local-only store (config/gm-addon/plots.json) of the player's plots, separated per Minecraft account.
 * Never uploaded anywhere.
 */
public class PlotRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Store {
        Map<String, List<OwnedPlot>> accounts = new LinkedHashMap<>();
    }

    private final Path file;
    private Store store = new Store();

    public PlotRepository(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Store loaded = GSON.fromJson(reader, Store.class);
            if (loaded != null && loaded.accounts != null) {
                store = loaded;
            }
        } catch (Exception e) {
            System.err.println("[GM-Addon] Failed to read plots.json: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
        } catch (Exception e) {
            System.err.println("[GM-Addon] Failed to save plots.json: " + e.getMessage());
        }
    }

    private static String key(String account) {
        return account == null ? "" : account.toLowerCase(Locale.ROOT);
    }

    public synchronized List<OwnedPlot> getPlots(String account) {
        List<OwnedPlot> list = store.accounts.get(key(account));
        return list == null ? List.of() : new ArrayList<>(list);
    }

    /**
     * Merges a fresh scan: known plots (same name + coordinates) keep their visit timestamp, new plots are added
     * with an unknown visit time and plots that are no longer listed are removed.
     */
    public synchronized void mergeScan(String account, List<OwnedPlot> scanned) {
        List<OwnedPlot> old = store.accounts.getOrDefault(key(account), new ArrayList<>());
        List<OwnedPlot> merged = new ArrayList<>();
        for (OwnedPlot fresh : scanned) {
            for (OwnedPlot known : old) {
                if (known.sameAs(fresh)) {
                    fresh.lastVisitedEpochMs = known.lastVisitedEpochMs;
                    break;
                }
            }
            merged.add(fresh);
        }
        store.accounts.put(key(account), merged);
        save();
    }

    /**
     * Records a verified visit. Returns true if the stored timestamp changed noticeably (&gt; 1 minute).
     */
    public synchronized boolean markVisited(String account, OwnedPlot plot, long nowEpochMs) {
        List<OwnedPlot> list = store.accounts.get(key(account));
        if (list == null) {
            return false;
        }
        for (OwnedPlot p : list) {
            if (p.sameAs(plot)) {
                boolean changed = nowEpochMs - p.lastVisitedEpochMs > 60_000L;
                if (changed) {
                    p.lastVisitedEpochMs = nowEpochMs;
                    save();
                }
                return changed;
            }
        }
        return false;
    }

    /**
     * Directly updates a plot's last visited timestamp (useful for manual testing or corrections).
     */
    public synchronized boolean setLastVisited(String account, String plotName, long visitedEpochMs) {
        List<OwnedPlot> list = store.accounts.get(key(account));
        if (list == null) {
            return false;
        }
        for (OwnedPlot p : list) {
            if (p.name.equalsIgnoreCase(plotName)) {
                p.lastVisitedEpochMs = visitedEpochMs;
                save();
                return true;
            }
        }
        return false;
    }
}
