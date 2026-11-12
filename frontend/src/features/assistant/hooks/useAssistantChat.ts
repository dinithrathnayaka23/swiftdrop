import { useCallback, useRef, useState } from 'react';
import { assistantApi } from '../../../api/assistant';
import type { AssistantCard, AssistantMessage, AssistantMode } from '../types';

/** The backend uses the last 10; sending more only costs bandwidth. */
export const HISTORY_LIMIT = 10;
export const MAX_MESSAGE_CHARS = 1000;

export const WELCOME =
  "Hi! I'm the SwiftDrop assistant. I can track your parcels, estimate delivery fees and answer questions about our service.";
export const STARTER_SUGGESTIONS = [
  'Where are my parcels?',
  'How much to send 2 kg?',
  'What can I send?',
];

export interface ChatEntry extends AssistantMessage {
  id: number;
  cards: AssistantCard[];
  mode?: AssistantMode;
}

/** The conversation lives in this component only; it's gone when the page reloads. */
export function useAssistantChat() {
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>(STARTER_SUGGESTIONS);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const nextId = useRef(1);

  const send = useCallback(
    async (text: string) => {
      const content = text.trim().slice(0, MAX_MESSAGE_CHARS);
      if (!content || sending) return;

      const question: ChatEntry = { id: nextId.current++, role: 'user', content, cards: [] };
      const history = [...entries, question]
        .slice(-HISTORY_LIMIT)
        .map(({ role, content: messageText }) => ({ role, content: messageText }));

      setEntries((current) => [...current, question]);
      setSuggestions([]);
      setError(null);
      setSending(true);
      try {
        const response = await assistantApi.chat({ messages: history });
        setEntries((current) => [
          ...current,
          {
            id: nextId.current++,
            role: 'assistant',
            content: response.reply,
            cards: response.cards,
            mode: response.mode,
          },
        ]);
        setSuggestions(response.suggestions);
      } catch (err) {
        setError(err);
      } finally {
        setSending(false);
      }
    },
    [entries, sending],
  );

  return { entries, suggestions, sending, error, send };
}
