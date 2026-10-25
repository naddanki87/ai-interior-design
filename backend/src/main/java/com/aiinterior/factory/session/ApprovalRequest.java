package com.aiinterior.factory.session;

import java.time.Instant;
import java.util.List;

public record ApprovalRequest(String question, int designVersion, String selectedLayoutId, List<String> layoutOptions,
                              List<String> openWarnings, Instant requestedAt) {
}
