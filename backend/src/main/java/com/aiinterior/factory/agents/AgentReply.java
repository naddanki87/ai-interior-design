package com.aiinterior.factory.agents;

/** What an agent sends back: a one-line human summary plus a typed payload. */
public record AgentReply(String summary, Object payload) {
}
