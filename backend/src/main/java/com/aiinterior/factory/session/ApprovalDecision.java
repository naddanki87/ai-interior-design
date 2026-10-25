package com.aiinterior.factory.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecision(@NotNull Decision decision, String layoutId, @Size(max = 2000) String comment) {

    public enum Decision { APPROVE, REQUEST_CHANGES, REJECT }
}
