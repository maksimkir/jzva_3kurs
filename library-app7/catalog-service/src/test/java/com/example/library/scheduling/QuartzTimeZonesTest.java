package com.example.library.scheduling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzTimeZonesTest {

    @Test
    void resolve_knownId_returnsThatZone() {
        assertThat(QuartzTimeZones.resolve("Europe/Kyiv").getID()).isEqualTo("Europe/Kyiv");
        assertThat(QuartzTimeZones.resolve("UTC").getID()).isEqualTo("UTC");
    }

    @Test
    void resolve_unknownId_fallsBackToGmt() {
        assertThat(QuartzTimeZones.resolve("Europe/Kyev").getID()).isEqualTo("GMT");
        assertThat(QuartzTimeZones.resolve("Mars/Olympus").getID()).isEqualTo("GMT");
    }

    @Test
    void resolve_explicitGmt_isGmtWithoutWarning() {
        assertThat(QuartzTimeZones.resolve("GMT").getID()).isEqualTo("GMT");
    }
}
