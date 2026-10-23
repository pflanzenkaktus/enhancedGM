package de.pflanzenkaktus.enhancedgm;

import de.pflanzenkaktus.enhancedgm.client.module.TachoModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TachoBossBarTest {

    @Test
    void testVehicleBossBarTextDetection() {
        assertTrue(TachoModule.isVehicleBossBarText("§e§l54 km/h"));
        assertTrue(TachoModule.isVehicleBossBarText("Gang: 3 | 72 km/h"));
        assertTrue(TachoModule.isVehicleBossBarText("§6Hoehe: 120m | km/h: 180"));
        assertTrue(TachoModule.isVehicleBossBarText("Höhe: 85m"));
        assertTrue(TachoModule.isVehicleBossBarText("§cMotor ausgeschaltet"));
        assertTrue(TachoModule.isVehicleBossBarText("Motor startet..."));
        assertTrue(TachoModule.isVehicleBossBarText("Tank: 45%"));
        assertTrue(TachoModule.isVehicleBossBarText("Geschwindigkeit: 30 km/h"));

        // Non-vehicle bossbars
        assertFalse(TachoModule.isVehicleBossBarText("Ender Dragon"));
        assertFalse(TachoModule.isVehicleBossBarText("Wither"));
        assertFalse(TachoModule.isVehicleBossBarText("Event: Schatzsuche"));
        assertFalse(TachoModule.isVehicleBossBarText(null));
        assertFalse(TachoModule.isVehicleBossBarText("   "));
    }
}
