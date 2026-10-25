package com.aiinterior.factory.session;

import com.aiinterior.factory.common.Ids;
import com.aiinterior.factory.domain.DesignBrief;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/** Live state of one design run. Held in memory only - nothing is persisted. */
public class DesignSession {

    private final String id = Ids.newId("ses");
    private final Instant createdAt;
    private final DesignBrief brief;
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong();
    private final List<AgentMessage> messages = new CopyOnWriteArrayList<>();
    private final List<AgentTask> tasks = new CopyOnWriteArrayList<>();
    private final Map<String, AgentState> agents = new ConcurrentHashMap<>();
    private final Map<String, Object> outputs = new ConcurrentHashMap<>();
    @JsonIgnore
    private final Map<String, Object> internal = new ConcurrentHashMap<>();
    private volatile SessionStatus status = SessionStatus.RUNNING;
    private volatile String statusMessage = "Starting";
    private volatile int designVersion = 1;
    private volatile ApprovalRequest pendingApproval;
    private volatile Instant finishedAt;
    @JsonIgnore
    private volatile CompletableFuture<ApprovalDecision> approvalFuture;
    @JsonIgnore
    private volatile boolean cancelRequested;
    @JsonIgnore
    private volatile Runnable onChange = () -> { };

    public DesignSession(DesignBrief brief, Clock clock) {
        this.brief = brief;
        this.clock = clock;
        this.createdAt = clock.instant();
    }

    public AgentMessage post(MessageType type, String from, String to, String intent, String summary, Object payload, String inReplyTo) {
        AgentMessage m = new AgentMessage(Ids.newId("msg"), seq.incrementAndGet(), clock.instant(), type, from, to, intent,
                summary, payload, inReplyTo, designVersion);
        messages.add(m);
        changed();
        return m;
    }

    public synchronized void putTask(AgentTask t) {
        tasks.removeIf(x -> x.id().equals(t.id()));
        tasks.add(t);
        changed();
    }

    public synchronized AgentTask updateTask(String taskId, UnaryOperator<AgentTask> fn) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id().equals(taskId)) {
                AgentTask updated = fn.apply(tasks.get(i));
                tasks.set(i, updated);
                changed();
                return updated;
            }
        }
        throw new IllegalArgumentException("Unknown task " + taskId);
    }

    public AgentTask task(String taskId) {
        return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst().orElse(null);
    }

    public synchronized void agentStarted(String agent, String intent) {
        AgentState s = agents.get(agent);
        agents.put(agent, new AgentState(agent, "WORKING", s == null ? 1 : s.activeRequests() + 1, intent, null,
                s == null ? 0 : s.handled(), clock.instant(), s == null ? null : s.lastError()));
        changed();
    }

    public synchronized void agentFinished(String agent, String error) {
        AgentState s = agents.get(agent);
        int active = s == null ? 0 : Math.max(0, s.activeRequests() - 1);
        String status = error != null ? "ERROR" : active > 0 ? "WORKING" : "IDLE";
        agents.put(agent, new AgentState(agent, status, active, active > 0 ? s.currentIntent() : null, null,
                (s == null ? 0 : s.handled()) + 1, clock.instant(), error != null ? error : s == null ? null : s.lastError()));
        changed();
    }

    public synchronized void agentWaiting(String agent, String on) {
        AgentState s = agents.get(agent);
        agents.put(agent, new AgentState(agent, "WAITING", s == null ? 0 : s.activeRequests(), s == null ? null : s.currentIntent(),
                on, s == null ? 0 : s.handled(), clock.instant(), s == null ? null : s.lastError()));
        changed();
    }

    public synchronized void agentResumed(String agent) {
        AgentState s = agents.get(agent);
        if (s != null) {
            agents.put(agent, new AgentState(agent, s.activeRequests() > 0 ? "WORKING" : "IDLE", s.activeRequests(),
                    s.currentIntent(), null, s.handled(), clock.instant(), s.lastError()));
            changed();
        }
    }

    public void status(SessionStatus s, String message) {
        this.status = s;
        this.statusMessage = message;
        if (s.finished()) {
            finishedAt = clock.instant();
        }
        changed();
    }

    public void output(String key, Object value) {
        outputs.put(key, value);
        changed();
    }

    @SuppressWarnings("unchecked")
    public <T> T output(String key, Class<T> type) {
        return (T) outputs.get(key);
    }

    /** Working data agents share that is not part of the public snapshot. */
    public void internal(String key, Object value) {
        internal.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T internal(String key, Class<T> type) {
        return (T) internal.get(key);
    }

    public CompletableFuture<ApprovalDecision> awaitApproval(ApprovalRequest request) {
        this.approvalFuture = new CompletableFuture<>();
        this.pendingApproval = request;
        changed();
        return approvalFuture;
    }

    /** @return false when no approval is pending. */
    public boolean decide(ApprovalDecision decision) {
        CompletableFuture<ApprovalDecision> f = approvalFuture;
        if (f == null || f.isDone()) {
            return false;
        }
        pendingApproval = null;
        // Leave WAITING_FOR_APPROVAL immediately so clients never see a stale gate.
        status = SessionStatus.RUNNING;
        statusMessage = "Decision received: " + decision.decision();
        f.complete(decision);
        changed();
        return true;
    }

    public void requestCancel() {
        cancelRequested = true;
        CompletableFuture<ApprovalDecision> f = approvalFuture;
        if (f != null && !f.isDone()) {
            f.complete(new ApprovalDecision(ApprovalDecision.Decision.REJECT, null, "Session cancelled"));
        }
        pendingApproval = null;
        changed();
    }

    public void nextDesignVersion() {
        designVersion++;
        changed();
    }

    public void onChange(Runnable r) {
        this.onChange = r;
    }

    private void changed() {
        onChange.run();
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public DesignBrief getBrief() {
        return brief;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public int getDesignVersion() {
        return designVersion;
    }

    public List<AgentMessage> getMessages() {
        return List.copyOf(messages);
    }

    public List<AgentTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    public Map<String, AgentState> getAgents() {
        return Map.copyOf(agents);
    }

    public Map<String, Object> getOutputs() {
        return Map.copyOf(outputs);
    }

    public ApprovalRequest getPendingApproval() {
        return pendingApproval;
    }

    @JsonIgnore
    public boolean isCancelRequested() {
        return cancelRequested;
    }
}
