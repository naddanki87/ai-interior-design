package com.aiinterior.factory.workflow;

import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.SessionService;
import com.aiinterior.factory.session.SessionStatus;
import java.util.Set;

final class WorkflowSupport {

    private WorkflowSupport() {
    }

    static DesignSession await(SessionService service, String id, SessionStatus... statuses) throws InterruptedException {
        Set<SessionStatus> wanted = Set.of(statuses);
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            DesignSession s = service.get(id);
            if (wanted.contains(s.getStatus())) {
                return s;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Session " + id + " did not reach " + wanted + ", is " + service.get(id).getStatus()
                + " - " + service.get(id).getStatusMessage());
    }
}
