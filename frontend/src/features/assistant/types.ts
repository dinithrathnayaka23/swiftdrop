// Feature: assistant (Member 5). The customer-side AI delivery assistant (chat widget).
// New folder: Member 1 is asked to approve it and to mount the widget in the public and customer
// layouts. Until then the widget is shown from Member 5's own pages (landing, public tracking).

export type AssistantRole = 'user' | 'assistant';

export interface AssistantMessage {
  role: AssistantRole;
  content: string;
}

/** Body of POST /api/v1/public/assistant/chat. The conversation is kept in the browser only. */
export interface AssistantChatRequest {
  /** Oldest first; the last one is the new question. */
  messages: AssistantMessage[];
}

/** Response of POST /api/v1/public/assistant/chat. */
export interface AssistantChatResponse {
  reply: string;
  /** Tracking numbers mentioned in the reply, shown as links to /track/{number}. */
  trackingNumbers: string[];
  /** Follow-up questions shown as quick-reply chips. */
  suggestions: string[];
}

export interface AssistantApi {
  chat(request: AssistantChatRequest): Promise<AssistantChatResponse>;
}
