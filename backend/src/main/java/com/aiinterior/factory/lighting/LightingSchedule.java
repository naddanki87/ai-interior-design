package com.aiinterior.factory.lighting;

import java.util.List;

public record LightingSchedule(int targetLux, int lumensRequired, int downlightCount, double coveMetres,
                               List<Fixture> fixtures, List<String> missingInformation, List<String> notes) {

    public record Fixture(String id, String layer, String type, String colourTemperature, int lumens,
                          Double x, Double y, String mounting, String note) {
    }
}
