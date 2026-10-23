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

    @Test
    void testMotorOffTextDetection() {
        assertTrue(TachoModule.isMotorOffText("§cMotor ausgeschaltet"));
        assertTrue(TachoModule.isMotorOffText("Motor ausgeschaltet"));
        assertTrue(TachoModule.isMotorOffText("§4[Motor gestoppt]"));

        assertFalse(TachoModule.isMotorOffText("§eMotor startet..."));
        assertFalse(TachoModule.isMotorOffText("54 km/h | Gang: D"));
        assertFalse(TachoModule.isMotorOffText(null));
        assertFalse(TachoModule.isMotorOffText(""));
    }

    @Test
    void testMotorStartingOrRunningTextDetection() {
        assertTrue(TachoModule.isMotorStartingOrRunningText("§eMotor startet..."));
        assertTrue(TachoModule.isMotorStartingOrRunningText("54 km/h | Gang: D"));
        assertTrue(TachoModule.isMotorStartingOrRunningText("0 km/h"));

        assertFalse(TachoModule.isMotorStartingOrRunningText("§cMotor ausgeschaltet"));
        assertFalse(TachoModule.isMotorStartingOrRunningText(null));
        assertFalse(TachoModule.isMotorStartingOrRunningText(""));
    }
}
