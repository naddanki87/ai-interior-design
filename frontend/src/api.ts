import type { AgentInfo, DesignBrief, DesignSession, FactoryConfig, SessionSummary } from './types';

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly details: string[] = []) {
    super(message);
  }
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    let details: string[] = [];
    try {
      const body = await res.json();
      message = body.detail ?? message;
      details = body.errors ?? [];
    } catch {
      /* not JSON */
    }
    throw new ApiError(message, res.status, details);
  }
  return res.json() as Promise<T>;
}

export const api = {
  config: () => call<FactoryConfig>('/api/config'),
  agents: () => call<AgentInfo[]>('/api/agents'),
  example: () => call<DesignBrief>('/api/brief/example'),
  sessions: () => call<SessionSummary[]>('/api/sessions'),
  session: (id: string) => call<DesignSession>(`/api/sessions/${id}`),
  start: (brief: DesignBrief) => call<DesignSession>('/api/sessions', { method: 'POST', body: JSON.stringify(brief) }),
  decide: (id: string, decision: 'APPROVE' | 'REQUEST_CHANGES' | 'REJECT', layoutId: string | null, comment: string) =>
    call<DesignSession>(`/api/sessions/${id}/approval`, {
      method: 'POST',
      body: JSON.stringify({ decision, layoutId, comment: comment || null }),
    }),
  cancel: (id: string) => call<DesignSession>(`/api/sessions/${id}/cancel`, { method: 'POST' }),
  verifyExport: (id: string) => call<DesignSession>(`/api/sessions/${id}/export/verify`, { method: 'POST' }),
  fileUrl: (id: string, name: string, version: number) => `/api/sessions/${id}/files/${name}?version=${version}`,
  boqUrl: (id: string) => `/api/sessions/${id}/boq.csv`,
  eventsUrl: (id: string) => `/api/sessions/${id}/events`,
};
