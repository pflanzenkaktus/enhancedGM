package de.pflanzenkaktus.enhancedgm;

import de.pflanzenkaktus.enhancedgm.data.PlotRepository;
import de.pflanzenkaktus.enhancedgm.feature.PlotTracker;
import de.pflanzenkaktus.enhancedgm.model.OwnedPlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlotTrackerTest {

    @Test
    void parsesPlayerOwner() {
        var msg = PlotTracker.parseOwnerMessage("§7Dieses Grundstück ist im Besitz von §ePflanzenKaktus");
        assertNotNull(msg);
        assertEquals("PflanzenKaktus", msg.ownerName());
        assertFalse(msg.company());
    }

    @Test
    void parsesCompanyOwner() {
        var msg = PlotTracker.parseOwnerMessage("Diese Mietzone ist im Besitz von SuperChaos (Firma)");
        assertNotNull(msg);
        assertEquals("SuperChaos", msg.ownerName());
        assertTrue(msg.company());
    }

    @Test
    void ignoresUnrelatedMessages() {
        assertNull(PlotTracker.parseOwnerMessage("Du wurdest mit 77 km/h geblitzt."));
    }

    @Test
    void ownershipRequiresMatchingNameOrCompany() {
        var player = PlotTracker.parseOwnerMessage("Dieses Grundstück ist im Besitz von PflanzenKaktus");
        var company = PlotTracker.parseOwnerMessage("Diese Mietzone ist im Besitz von SuperChaos (Firma)");
        assertTrue(PlotTracker.isOwnedByUs(player, "pflanzenkaktus", ""));
        assertFalse(PlotTracker.isOwnedByUs(player, "Anderer", "SuperChaos"));
        assertTrue(PlotTracker.isOwnedByUs(company, "PflanzenKaktus", "SuperChaos"));
        assertFalse(PlotTracker.isOwnedByUs(company, "PflanzenKaktus", ""));
        assertFalse(PlotTracker.isOwnedByUs(company, "SuperChaos", "Andere GmbH"));
    }

    @Test
    void parsesMenuItem() {
        OwnedPlot p = PlotTracker.parseMenuItem("§akau2007",
            "kau2007 | — Typ: Grundstück | — x: -2742 y: 126 z: -609 | — Stadtgebiet: Nördlicher Gebirgszug | Klicke");
        assertNotNull(p);
        assertEquals("kau2007", p.name);
        assertEquals("Grundstück", p.type);
        assertEquals(-2742, p.x);
        assertEquals(126, p.y);
        assertEquals(-609, p.z);
        assertEquals("Nördlicher Gebirgszug", p.area);
        assertNull(PlotTracker.parseMenuItem("Zurück", "Zurück"));
    }

    @Test
    void nearestCandidateFirstAndRadiusRespected() {
        OwnedPlot a = new OwnedPlot("a", "Grundstück", 0, 64, 0, "");
        OwnedPlot b = new OwnedPlot("b", "Grundstück", 40, 64, 0, "");
        OwnedPlot far = new OwnedPlot("far", "Grundstück", 1000, 64, 0, "");
        List<OwnedPlot> c = PlotTracker.findCandidates(List.of(far, b, a), 30, 0, 100);
        assertEquals(List.of("b", "a"), c.stream().map(p -> p.name).toList());
    }

    @Test
    void deadlineIsSixtyDaysAfterVisit() {
        long now = 1_800_000_000_000L;
        OwnedPlot p = new OwnedPlot("a", "Grundstück", 0, 0, 0, "");
        assertEquals(Long.MAX_VALUE, p.daysRemaining(now));
        p.lastVisitedEpochMs = now;
        assertEquals(60, p.daysRemaining(now));
        assertEquals(10, p.daysRemaining(now + 50L * 24 * 60 * 60 * 1000));
        assertTrue(p.daysRemaining(now + 61L * 24 * 60 * 60 * 1000) <= 0);
    }

    @Test
    void mergeKeepsTimestampsAndDropsMissing(@TempDir Path dir) {
        PlotRepository repo = new PlotRepository(dir.resolve("plots.json"));
        repo.mergeScan("Me", List.of(
            new OwnedPlot("a", "Grundstück", 1, 2, 3, ""),
            new OwnedPlot("b", "Wohnung", 4, 5, 6, "")));
        OwnedPlot a = repo.getPlots("me").get(0);
        assertTrue(repo.markVisited("ME", a, 1_800_000_000_000L));

        repo.mergeScan("Me", List.of(
            new OwnedPlot("a", "Grundstück", 1, 2, 3, ""),
            new OwnedPlot("c", "Grundstück", 7, 8, 9, "")));
        List<OwnedPlot> plots = new PlotRepository(dir.resolve("plots.json")) {{ load(); }}.getPlots("me");
        assertEquals(2, plots.size());
        assertEquals(1_800_000_000_000L, plots.get(0).lastVisitedEpochMs);
        assertEquals("c", plots.get(1).name);
        assertFalse(plots.get(1).hasBeenVisited());
    }
}
