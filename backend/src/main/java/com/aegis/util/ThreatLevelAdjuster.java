package com.aegis.util;

/** Post-process LLM threat scores for consistency with category and source. */
public final class ThreatLevelAdjuster {

    private ThreatLevelAdjuster() {}

    public static int adjust(String category, String sourceType, int raw) {
        int t = raw;
        if (t < 1) t = 1;
        if (t > 10) t = 10;
        if ("LEGAL".equals(category) && t < 5) {
            t = 5;
        }
        if ("PARTNERSHIP".equals(category) && t < 4) {
            t = 4;
        }
        if ("EDGAR".equals(sourceType) && t < 5) {
            t = 5;
        }
        return t;
    }
}
