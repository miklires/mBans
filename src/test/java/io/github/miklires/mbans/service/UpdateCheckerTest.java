package io.github.miklires.mbans.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test void comparesNumericVersions() {
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.9") > 0);
        assertTrue(UpdateChecker.compare("v2.0.0", "1.99.0") > 0);
        assertEquals(0, UpdateChecker.compare("1.0", "1.0.0"));
        assertTrue(UpdateChecker.compare("1.0.0-beta.1", "1.0.0") < 0);
        assertTrue(UpdateChecker.compare("1.0.0-beta.2", "1.0.0-beta.1") > 0);
    }
}
