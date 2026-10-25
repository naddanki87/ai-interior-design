package com.aiinterior.factory.agents;

public class AgentPermissionException extends RuntimeException {

    public AgentPermissionException(AgentId agent, AgentTool tool) {
        super(agent.displayName() + " agent is not permitted to use " + tool);
    }
}
