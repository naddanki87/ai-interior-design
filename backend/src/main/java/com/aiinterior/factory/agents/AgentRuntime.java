package com.aiinterior.factory.agents;

import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.config.FactoryProperties;
import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.MessageType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Message bus between agents. Every request is recorded on the session, handled on its own
 * virtual thread, and answered with a correlated RESPONSE (or ERROR) message.
 */
@Component
public class AgentRuntime {

    private final Map<AgentId, Agent> agents = new EnumMap<>(AgentId.class);
    private final ExecutorService executor;
    private final AiGateway ai;
    private final Duration stepDelay;
    private final Duration peerTimeout;

    public AgentRuntime(List<Agent> agentBeans, ExecutorService agentExecutor, AiGateway ai, FactoryProperties props) {
        agentBeans.forEach(a -> agents.put(a.id(), a));
        this.executor = agentExecutor;
        this.ai = ai;
        this.stepDelay = props.agents().stepDelay();
        this.peerTimeout = props.agents().peerRequestTimeout();
    }

    public Map<AgentId, Agent> agents() {
        return Map.copyOf(agents);
    }

    AiGateway ai() {
        return ai;
    }

    public CompletableFuture<AgentReply> request(DesignSession s, String from, AgentId to, String intent, String summary, Object payload) {
        Agent agent = agents.get(to);
        if (agent == null || !agent.intents().contains(intent)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(to + " does not handle " + intent));
        }
        var req = s.post(MessageType.REQUEST, from, to.name(), intent, summary, payload, null);
        return CompletableFuture.supplyAsync(() -> {
            s.agentStarted(to.name(), intent);
            String error = null;
            try {
                pause();
                AgentReply reply = agent.handle(intent, payload, new AgentContext(s, agent, this));
                pause();
                s.post(MessageType.RESPONSE, to.name(), from, intent, reply.summary(), reply.payload(), req.id());
                return reply;
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                s.post(MessageType.ERROR, to.name(), from, intent, error, null, req.id());
                throw new CompletionException(e);
            } finally {
                s.agentFinished(to.name(), error);
            }
        }, executor);
    }

    <T> T ask(DesignSession s, AgentId from, AgentId to, String intent, String summary, Object payload, Class<T> type) throws Exception {
        return type.cast(askReply(s, from, to, intent, summary, payload).payload());
    }

    AgentReply askReply(DesignSession s, AgentId from, AgentId to, String intent, String summary, Object payload) throws Exception {
        s.agentWaiting(from.name(), to.displayName());
        try {
            return request(s, from.name(), to, intent, summary, payload).get(peerTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw unwrap(e);
        } catch (TimeoutException e) {
            throw new TimeoutException(to.displayName() + " did not reply to " + intent + " within " + peerTimeout.toSeconds() + " s");
        } finally {
            s.agentResumed(from.name());
        }
    }

    static Exception unwrap(Throwable e) {
        Throwable t = e;
        while ((t instanceof ExecutionException || t instanceof CompletionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof Exception ex ? ex : new RuntimeException(t);
    }

    private void pause() {
        if (stepDelay.isZero() || stepDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(stepDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
