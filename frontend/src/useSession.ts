import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { DesignSession } from './types';

/**
 * Live session state: Server-Sent Events tell us when something changed and we refetch the
 * snapshot (throttled). If SSE fails we fall back to polling every 2 s.
 */
export function useSession(id: string | null) {
  const [session, setSession] = useState<DesignSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState<'sse' | 'polling' | 'off'>('off');
  const pending = useRef(false);
  const timer = useRef<number | null>(null);

  const refresh = useCallback(async () => {
    if (!id) return;
    try {
      const s = await api.session(id);
      setSession(s);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [id]);

  const schedule = useCallback(() => {
    if (pending.current) return;
    pending.current = true;
    timer.current = window.setTimeout(() => {
      pending.current = false;
      void refresh();
    }, 150);
  }, [refresh]);

  useEffect(() => {
    setSession(null);
    if (!id) return;
    void refresh();
    let poll: number | null = null;
    let es: EventSource | null = null;
    const startPolling = () => {
      setLive('polling');
      if (poll == null) poll = window.setInterval(() => void refresh(), 2000);
    };
    if (typeof EventSource !== 'undefined') {
      es = new EventSource(api.eventsUrl(id));
      es.addEventListener('hello', () => setLive('sse'));
      es.addEventListener('changed', schedule);
      es.onerror = () => {
        es?.close();
        startPolling();
      };
    } else {
      startPolling();
    }
    return () => {
      es?.close();
      if (poll != null) window.clearInterval(poll);
      if (timer.current != null) window.clearTimeout(timer.current);
      pending.current = false;
    };
  }, [id, refresh, schedule]);

  return { session, error, live, refresh };
}
