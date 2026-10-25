package com.aiinterior.factory.export;

import java.util.List;
import java.util.Map;

public record SkpVerification(Status status, boolean fileExists, long sizeBytes, boolean nativeHeader, String sha256,
                              Boolean boundsMatch, Map<String, Object> sketchupReport, List<String> messages) {

    public enum Status { VERIFIED, FAILED, NOT_PRODUCED }
}
