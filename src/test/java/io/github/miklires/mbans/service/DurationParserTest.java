package io.github.miklires.mbans.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesCombinedDuration() {
        assertEquals(Duration.ofDays(8).plusHours(2).plusMinutes(30), DurationParser.parse("1w1d2h30m").orElseThrow());
    }

    @Test
    void rejectsMalformedAndOverflowingValues() {
        assertTrue(DurationParser.parse("7days").isEmpty());
        assertTrue(DurationParser.parse("0m").isEmpty());
        assertTrue(DurationParser.parse("999999999999999999999y").isEmpty());
    }

    @Test
    void formatsStableUnits() {
        assertEquals("1d 2h 3m", DurationParser.format(Duration.ofDays(1).plusHours(2).plusMinutes(3)));
        assertEquals("permanent", DurationParser.format(null));
    }
}
