package com.aiinterior.factory.agents;

import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.MessageType;

/** The only window an agent has onto the outside world; every capability is permission-checked. */
public final class AgentContext {

    private final DesignSession session;
    private final Agent self;
    private final AgentRuntime runtime;

    AgentContext(DesignSession session, Agent self, AgentRuntime runtime) {
        this.session = session;
        this.self = self;
        this.runtime = runtime;
    }

    public DesignSession session() {
        return session;
    }

    /** Sends a request to another agent and waits (bounded) for its reply payload. */
    public <T> T ask(AgentId to, String intent, String summary, Object payload, Class<T> replyType) throws Exception {
        require(AgentTool.MESSAGING);
        return runtime.ask(session, self.id(), to, intent, summary, payload, replyType);
    }

    /** Like {@link #ask} but returns the whole reply (summary + payload). */
    public AgentReply askReply(AgentId to, String intent, String summary, Object payload) throws Exception {
        require(AgentTool.MESSAGING);
        return runtime.askReply(session, self.id(), to, intent, summary, payload);
    }

    public void say(MessageType type, String to, String text) {
        session.post(type, self.id().name(), to, null, text, null, null);
    }

    public void tell(String text) {
        say(MessageType.INFO, "ALL", text);
    }

    public void warn(String text) {
        say(MessageType.WARNING, "ALL", text);
    }

    public AiGateway ai() {
        require(AgentTool.AI_TEXT);
        return runtime.ai();
    }

    public void require(AgentTool tool) {
        if (!self.tools().contains(tool)) {
            throw new AgentPermissionException(self.id(), tool);
        }
    }
}
