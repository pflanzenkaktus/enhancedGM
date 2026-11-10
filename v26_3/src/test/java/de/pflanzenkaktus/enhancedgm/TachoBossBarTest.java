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

    @Test
    void testGearDTextDetection() {
        assertTrue(TachoModule.isGearDText("54 km/h | Gang: D"));
        assertTrue(TachoModule.isGearDText("§eGang: D §7| §f30 km/h"));
        assertTrue(TachoModule.isGearDText("Gang:D"));
        assertTrue(TachoModule.isGearDText("Gang D"));

        assertFalse(TachoModule.isGearDText("Gang: R | 12 km/h"));
        assertFalse(TachoModule.isGearDText("Gang: N"));
        assertFalse(TachoModule.isGearDText("Gang: P"));
        assertFalse(TachoModule.isGearDText("Motor ausgeschaltet"));
        assertFalse(TachoModule.isGearDText(null));
        assertFalse(TachoModule.isGearDText(""));
    }

    @Test
    void testBossBarGearTextDetection() {
        assertTrue(TachoModule.isBossBarGearText("Gang: P | 0 km/h"));
        assertTrue(TachoModule.isBossBarGearText("Gang: 1 | 20 km/h"));
        assertTrue(TachoModule.isBossBarGearText("§eGang: N"));
        assertTrue(TachoModule.isBossBarGearText("54 km/h | Gang: D"));

        assertFalse(TachoModule.isBossBarGearText("Motor ausgeschaltet"));
        assertFalse(TachoModule.isBossBarGearText("Motor startet..."));
        assertFalse(TachoModule.isBossBarGearText("Tank: 80%"));
        assertFalse(TachoModule.isBossBarGearText(null));
        assertFalse(TachoModule.isBossBarGearText(""));
    }
}
