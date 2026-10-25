package com.aiinterior.factory.session;

import java.time.Instant;

/** One message on the agent bus. {@code from}/{@code to} are agent ids, "USER" or "ALL". */
public record AgentMessage(String id, long seq, Instant at, MessageType type, String from, String to, String intent,
                           String summary, Object payload, String inReplyTo, int designVersion) {
}
