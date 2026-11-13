// Feature: assistant (Member 5). The AI delivery assistant for signed-in customers.
// Backend: com.swiftdrop.assistant (docs/assistant.md).
import type { ParcelStatusCode, TrackingResult } from '../../types/tracking';

export type AssistantRole = 'user' | 'assistant';

export interface AssistantMessage {
  role: AssistantRole;
  content: string;
}

/** Body of POST /api/v1/assistant/chat. The conversation is kept in the browser only. */
export interface AssistantChatRequest {
  /** Oldest first; the last one is the new question. */
  messages: AssistantMessage[];
}

export interface FeeLine {
  label: string;
  amount: number;
}

export interface FeeCard {
  totalFee: number;
  currency: string;
  lines: FeeLine[];
}

export interface ParcelSummary {
  trackingNumber: string;
  status: ParcelStatusCode;
  /** The status in plain words, e.g. "Out for delivery" */
  statusLabel: string;
  fromCity: string;
  toCity: string;
  lastUpdatedAt: string;
}

/** Facts shown under a reply, built by the server from tool results. */
export type AssistantCard =
  | { type: 'TRACKING'; tracking: TrackingResult }
  | { type: 'FEE'; fee: FeeCard }
  | { type: 'PARCELS'; parcels: ParcelSummary[] };

/** AI: answered by the language model. RULES: basic mode (no model, or it failed). */
export type AssistantMode = 'AI' | 'RULES';

/** Response of POST /api/v1/assistant/chat. */
export interface AssistantChatResponse {
  /** Plain text: never rendered as HTML or markdown. */
  reply: string;
  cards: AssistantCard[];
  /** Follow-up questions shown as quick-reply chips. */
  suggestions: string[];
  mode: AssistantMode;
}

/** Body of POST /api/v1/assistant/fee-estimate (the chat's fee form; no AI). */
export interface AssistantFeeRequest {
  pickupDistrict: string;
  pickupCity: string;
  dropoffDistrict: string;
  dropoffCity: string;
  weightKg: number;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
}

/** Response of POST /api/v1/assistant/fee-estimate: one FEE card, or none when prices aren't available. */
export interface AssistantFeeResponse {
  cards: AssistantCard[];
  message: string;
}

export interface AssistantApi {
  chat(request: AssistantChatRequest): Promise<AssistantChatResponse>;
  estimateFee(request: AssistantFeeRequest): Promise<AssistantFeeResponse>;
}
