package com.aiinterior.factory.session;

import java.time.Instant;

/** status: IDLE, WORKING, WAITING (on another agent) or ERROR. */
public record AgentState(String agent, String status, int activeRequests, String currentIntent, String waitingOn,
                         int handled, Instant lastActivityAt, String lastError) {
}
