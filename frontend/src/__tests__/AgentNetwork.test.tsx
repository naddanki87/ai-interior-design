import { fireEvent, render, screen } from '@testing-library/react';
import { AgentNetwork } from '../components/AgentNetwork';
import { MessageFeed } from '../components/MessageFeed';
import type { AgentInfo, AgentMessage, AgentState } from '../types';

const ids = ['SUPERVISOR', 'REQUIREMENTS', 'ROOM_PLANNING', 'INTERIOR_DESIGN', 'FURNITURE', 'GEOMETRY', 'LIGHTING', 'MATERIALS',
  'RENDERING', 'QUALITY_ASSURANCE', 'SKETCHUP_EXPORTER'];
const agents: AgentInfo[] = ids.map((id) => ({ id, name: id.charAt(0) + id.slice(1).toLowerCase().replaceAll('_', ' '), role: 'r', tools: [], intents: [] }));
const names = Object.fromEntries(agents.map((a) => [a.id, a.name]));

const msg = (seq: number, type: AgentMessage['type'], from: string, to: string, summary: string): AgentMessage => ({
  id: `m${seq}`, seq, at: new Date(2026, 0, 1, 10, 0, seq).toISOString(), type, from, to, intent: null, summary,
  payload: null, inReplyTo: null, designVersion: 1,
});

const messages = [
  msg(1, 'REQUEST', 'SUPERVISOR', 'INTERIOR_DESIGN', 'Propose a concept'),
  msg(2, 'REQUEST', 'INTERIOR_DESIGN', 'GEOMETRY', 'Check option A'),
  msg(3, 'RESPONSE', 'GEOMETRY', 'INTERIOR_DESIGN', 'valid, no issues'),
];

describe('AgentNetwork', () => {
  it('renders every agent with its live status and reports focus clicks', () => {
    const states: Record<string, AgentState> = {
      GEOMETRY: { agent: 'GEOMETRY', status: 'WORKING', activeRequests: 1, currentIntent: 'CHECK_LAYOUT', waitingOn: null, handled: 2, lastActivityAt: '', lastError: null },
      INTERIOR_DESIGN: { agent: 'INTERIOR_DESIGN', status: 'WAITING', activeRequests: 1, currentIntent: 'PROPOSE_DESIGN', waitingOn: 'Geometry', handled: 0, lastActivityAt: '', lastError: null },
    };
    const onFocus = vi.fn();
    render(<AgentNetwork agents={agents} states={states} messages={messages} focus={null} onFocus={onFocus} />);
    expect(screen.getAllByRole('button')).toHaveLength(12); // 11 agents + you
    expect(screen.getByRole('button', { name: /Geometry: working, CHECK_LAYOUT/ })).toBeInTheDocument();
    expect(screen.getByText('waiting on Geometry')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Geometry: working/ }));
    expect(onFocus).toHaveBeenCalledWith('GEOMETRY');
  });
});

describe('MessageFeed', () => {
  it('filters the conversation to the focused agent', () => {
    const { rerender } = render(<MessageFeed messages={messages} names={names} focus={null} />);
    expect(screen.getByText('3 messages')).toBeInTheDocument();
    rerender(<MessageFeed messages={messages} names={names} focus="GEOMETRY" />);
    expect(screen.queryByText('Propose a concept')).not.toBeInTheDocument();
    expect(screen.getByText('Check option A')).toBeInTheDocument();
  });
});
