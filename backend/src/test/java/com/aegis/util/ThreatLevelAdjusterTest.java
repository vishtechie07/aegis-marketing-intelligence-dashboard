package com.aegis.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatLevelAdjusterTest {

    @Test
    void clampsRawRange() {
        assertThat(ThreatLevelAdjuster.adjust("OTHER", "", 0)).isEqualTo(1);
        assertThat(ThreatLevelAdjuster.adjust("OTHER", "", 99)).isEqualTo(10);
    }

    @Test
    void bumpsLegalFloor() {
        assertThat(ThreatLevelAdjuster.adjust("LEGAL", "", 2)).isEqualTo(5);
    }

    @Test
    void bumpsEdgarFloor() {
        assertThat(ThreatLevelAdjuster.adjust("OTHER", "EDGAR", 3)).isEqualTo(5);
    }
}
