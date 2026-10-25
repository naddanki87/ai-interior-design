// Mirrors the backend records (com.aiinterior.factory.*). Only fields the UI reads are typed.

export type LengthUnit = 'MM' | 'CM' | 'M' | 'FT_IN';
export type DimensionSource = 'MEASURED' | 'DRAWING' | 'AI_ESTIMATED' | 'USER_ASSUMPTION' | 'UNKNOWN';
export type WallSide = 'SOUTH' | 'EAST' | 'NORTH' | 'WEST';
export type OpeningType = 'DOOR' | 'WINDOW' | 'OPENING';

export interface LengthInput { value: string; unit: LengthUnit | null; source: DimensionSource | null }
export interface OpeningInput {
  type: OpeningType; wall: WallSide; offset: LengthInput; width: LengthInput; height: LengthInput;
  sillHeight: LengthInput | null; label: string;
}
export interface KeepItemInput { name: string; width: LengthInput; depth: LengthInput; height: LengthInput; x: LengthInput; y: LengthInput }

export interface DesignBrief {
  projectName: string; roomName: string; roomType: string;
  length: LengthInput; width: LengthInput; height: LengthInput;
  openings: OpeningInput[]; keepItems: KeepItemInput[]; furnitureRequests: string[];
  style: string; customStyle: string | null; palette: string[]; materialPreferences: string[];
  budget: { amount: number | null; currency: string } | null;
  functionalRequirements: string[]; lightingRequirements: string[]; instructions: string;
}

export type TaskStatus = 'PENDING' | 'READY' | 'RUNNING' | 'WAITING_FOR_INPUT' | 'WAITING_FOR_APPROVAL'
  | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'SKIPPED';
export type SessionStatus = 'RUNNING' | 'WAITING_FOR_INPUT' | 'WAITING_FOR_APPROVAL' | 'COMPLETED'
  | 'COMPLETED_WITH_WARNINGS' | 'FAILED' | 'CANCELLED';
export type MessageType = 'REQUEST' | 'RESPONSE' | 'INFO' | 'WARNING' | 'ERROR' | 'QUESTION' | 'APPROVAL_REQUEST' | 'APPROVAL_DECISION';

export interface AgentMessage {
  id: string; seq: number; at: string; type: MessageType; from: string; to: string; intent: string | null;
  summary: string; payload: unknown; inReplyTo: string | null; designVersion: number;
}
export interface AgentTask {
  id: string; title: string; agent: string; status: TaskStatus; dependsOn: string[]; attempts: number;
  createdAt: string; startedAt: string | null; completedAt: string | null; durationMs: number | null;
  summary: string | null; error: string | null; designVersion: number;
}
export interface AgentState {
  agent: string; status: 'IDLE' | 'WORKING' | 'WAITING' | 'ERROR'; activeRequests: number;
  currentIntent: string | null; waitingOn: string | null; handled: number; lastActivityAt: string; lastError: string | null;
}
export interface ApprovalRequest {
  question: string; designVersion: number; selectedLayoutId: string; layoutOptions: string[]; openWarnings: string[]; requestedAt: string;
}

export interface Item {
  id: string; name: string; category: string; x: number; y: number; sizeX: number; sizeY: number; height: number;
  againstWall: WallSide | null; priority: string; source: string;
}
export interface ValidationIssue { severity: 'ERROR' | 'WARNING'; code: string; elementId: string | null; message: string }
export interface AlternativeReview {
  layout: { id: string; name: string; strategy: string; furniture: Item[]; unplaced: string[] };
  validation: { valid: boolean; errorCount: number; warningCount: number; issues: ValidationIssue[] };
  score: number;
}
export interface DesignProposal {
  conceptTitle: string; concept: string; featureWallIdeas: string[]; tradeOffs: string[];
  alternatives: AlternativeReview[]; recommendedId: string;
  structuralProposals: { description: string; reason: string; status: string; applied: boolean }[];
  aiProvider: string; notes: string[];
}
export interface RequirementsDoc {
  roomLabel: string; style: string; palette: string[]; requiredElements: string[]; preferredElements: string[];
  optionalEnhancements: string[]; mustNotChange: string[]; explicitConstraints: string[];
  facts: { text: string; basis: string }[]; assumptions: string[]; conflicts: string[]; summary: string; aiProvider: string; notes: string[];
}
export interface RoomSpec {
  measurements: { field: string; valueMm: number | null; source: DimensionSource; status: string; note: string | null }[];
  missing: string[]; warnings: string[]; blocking: boolean; floorAreaM2: number;
}
export interface BoqLine {
  code: string; item: string; specification: string; unit: string; quantity: number; unitRate: number;
  materialAmount: number; labour: number; installation: number; hardware: number; wastagePct: number;
  wastageAmount: number; lineTotal: number; dataSource: string; confidence: string; assumption: string | null;
}
export interface MaterialsResult {
  schedule: { surface: string; item: string; finish: string; brandModel: string; quantity: number | null; unit: string; basis: string; source: string }[];
  boq: {
    currency: string; lines: BoqLine[]; subtotal: number; transportPct: number; transport: number; contingencyPct: number;
    contingency: number; taxPct: number; tax: number; grandTotal: number; budget: number | null; budgetStatus: string;
    disclaimer: string; assumptions: string[];
  };
  aiProvider: string; notes: string[];
}
export interface LightingSchedule {
  targetLux: number; lumensRequired: number; downlightCount: number; coveMetres: number;
  fixtures: { id: string; layer: string; type: string; colourTemperature: string; lumens: number; x: number | null; y: number | null; mounting: string; note: string | null }[];
  missingInformation: string[]; notes: string[];
}
export interface RenderSet {
  floorPlanSvg: string; conceptBoardSvg: string; provider: string; mock: boolean; prompt: string; negativePrompt: string;
  cameras: string[]; disclaimer: string;
}
export interface QaReport { overall: string; checks: { id: string; category: string; status: string; message: string }[]; designVersion: number }
export interface ExportResult {
  status: string; message: string;
  files: { name: string; format: string; sizeBytes: number; sha256: string; verified: boolean; note: string | null }[];
  skp: { status: string; fileExists: boolean; sizeBytes: number; nativeHeader: boolean; boundsMatch: boolean | null;
    sketchupReport: Record<string, unknown> | null; messages: string[] };
  sketchupConfigured: boolean; sketchupLauncher: string; manualSteps: string[]; designVersion: number;
}
export interface GeometryResult {
  spec: { room: { length: number; width: number; height: number }; approvalStatus: string; furniture: Item[] };
  validation: { valid: boolean; errorCount: number; warningCount: number; issues: ValidationIssue[] };
  floorPlanSvg: string;
}

export interface SessionOutputs {
  requirements?: RequirementsDoc; room?: RoomSpec; design?: DesignProposal; selectedLayoutId?: string;
  geometry?: GeometryResult; furnitureSchedule?: { lines: { id: string; name: string; widthMm: number; depthMm: number; heightMm: number; clearanceStatus: string; priority: string; sizeSource: string; note: string }[]; issues: string[] };
  lighting?: LightingSchedule; materials?: MaterialsResult; render?: RenderSet; qa?: QaReport; export?: ExportResult; finalQa?: QaReport;
}

export interface DesignSession {
  id: string; createdAt: string; finishedAt: string | null; brief: DesignBrief; status: SessionStatus; statusMessage: string;
  designVersion: number; messages: AgentMessage[]; tasks: AgentTask[]; agents: Record<string, AgentState>;
  outputs: SessionOutputs; pendingApproval: ApprovalRequest | null;
}
export interface SessionSummary {
  id: string; projectName: string; roomName: string; roomType: string; status: SessionStatus; statusMessage: string;
  designVersion: number; createdAt: string;
}
export interface AgentInfo { id: string; name: string; role: string; tools: string[]; intents: string[] }
export interface FactoryConfig {
  aiProvider: string; aiModel: string; aiConfigurationError: string | null; huggingFaceTokenConfigured: boolean;
  imageProvider: string; sketchupConfigured: boolean; sketchupExecutable: string; exportDir: string;
  requireApprovalBeforeExport: boolean; stepDelayMs: number; roomTypes: { id: string; label: string }[];
}
