const TONE: Record<string, string> = {
  SUCCEEDED: 'ok', COMPLETED: 'ok', PASS: 'ok', VERIFIED: 'ok', SKP_VERIFIED: 'ok', IDLE: 'muted',
  RUNNING: 'live', WORKING: 'live', READY: 'live',
  WAITING_FOR_APPROVAL: 'attn', WAITING_FOR_INPUT: 'attn', WAITING: 'attn', WARN: 'attn', PASS_WITH_WARNINGS: 'attn',
  COMPLETED_WITH_WARNINGS: 'attn', NOT_PRODUCED: 'attn', SKP_NOT_PRODUCED: 'attn',
  FAILED: 'bad', ERROR: 'bad', FAIL: 'bad', SKP_VERIFICATION_FAILED: 'bad',
  PENDING: 'muted', CANCELLED: 'muted', SKIPPED: 'muted',
};

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const tone = TONE[status] ?? 'muted';
  return (
    <span className={`pill pill-${tone}`} title={status}>
      <span className="pill-dot" aria-hidden />
      {label ?? status.replaceAll('_', ' ').toLowerCase()}
    </span>
  );
}
