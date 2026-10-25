package com.aiinterior.factory.session;

import java.time.Instant;
import java.util.List;

public record AgentTask(String id, String title, String agent, TaskStatus status, List<String> dependsOn, int attempts,
                        Instant createdAt, Instant startedAt, Instant completedAt, Long durationMs, String summary,
                        String error, int designVersion) {

    public AgentTask with(TaskStatus s, String summaryText, String errorText, Instant now) {
        Instant start = s == TaskStatus.RUNNING && startedAt == null ? now : startedAt;
        Instant done = s.terminal() || s == TaskStatus.WAITING_FOR_INPUT ? now : null;
        Long dur = done != null && start != null ? done.toEpochMilli() - start.toEpochMilli() : null;
        int att = s == TaskStatus.RUNNING ? attempts + 1 : attempts;
        return new AgentTask(id, title, agent, s, dependsOn, att, createdAt, start, done, dur,
                summaryText == null ? summary : summaryText, errorText, designVersion);
    }
}
