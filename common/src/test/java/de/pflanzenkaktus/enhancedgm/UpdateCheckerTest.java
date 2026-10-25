package de.pflanzenkaktus.enhancedgm;

import de.pflanzenkaktus.enhancedgm.feature.UpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {

    @Test
    void testIsNewer() {
        assertTrue(UpdateChecker.isNewer("0.3.1", "0.3.0"));
        assertTrue(UpdateChecker.isNewer("v0.3.1", "0.3.0"));
        assertTrue(UpdateChecker.isNewer("v0.4.0", "v0.3.0"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.3.0"));
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.3.0"));

        assertFalse(UpdateChecker.isNewer("0.3.0", "0.3.0"));
        assertFalse(UpdateChecker.isNewer("v0.3.0", "0.3.0"));
        assertFalse(UpdateChecker.isNewer("0.2.5", "0.3.0"));
        assertFalse(UpdateChecker.isNewer("0.2.4", "0.3.0"));
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.3.0"));
        assertFalse(UpdateChecker.isNewer(null, "0.3.0"));
    }
}
