package com.aiinterior.factory.agents;

import java.util.Set;

public interface Agent {

    AgentId id();

    String role();

    Set<AgentTool> tools();

    /** Intents this agent accepts; anything else is rejected by the runtime. */
    Set<String> intents();

    AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception;
}
