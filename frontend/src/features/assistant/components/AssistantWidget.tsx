import { Bot, Calculator, MessageCircle, SendHorizontal, ShieldCheck, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { AssistantRateLimitError } from '../../../api/assistant';
import { toApiRequestError } from '../../../api/errors';
import { useSettings } from '../../../hooks/useSettings';
import { cn } from '../../../utils/cn';
import { MAX_MESSAGE_CHARS, useAssistantChat, WELCOME } from '../hooks/useAssistantChat';
import AssistantCards from './AssistantCards';
import ChatNotice, { type ChatState } from './ChatNotice';
import FeeEstimateForm from './FeeEstimateForm';
import MessageText from './MessageText';
import TypingIndicator from './TypingIndicator';

function Bubble({ fromUser, children }: { fromUser: boolean; children: ReactNode }) {
  return (
    <div className={cn('flex', fromUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-2xl px-4 py-2.5 text-sm',
          fromUser
            ? 'rounded-br-sm bg-brand-600 text-white'
            : 'rounded-bl-sm bg-slate-100 text-slate-800',
        )}
      >
        {children}
      </div>
    </div>
  );
}

/** Whole seconds until `retryAt`, updated every second; 0 once it has passed. */
function useSecondsLeft(retryAt: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (retryAt === null || retryAt <= Date.now()) return;
    const tick = () => setNow(Date.now());
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [retryAt]);
  return retryAt === null ? 0 : Math.max(0, Math.ceil((retryAt - now) / 1000));
}

/** Floating chat button and panel for signed-in customers. */
export default function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [view, setView] = useState<'chat' | 'fee'>('chat');
  const { entries, suggestions, sending, error, retryAt, send, addReply } = useAssistantChat();
  const { settings } = useSettings();
  const secondsLeft = useSecondsLeft(retryAt);

  const lastReply = [...entries]
    .reverse()
    .find((entry) => entry.role === 'assistant' && entry.mode);
  const basicMode = lastReply?.mode === 'RULES';
  const state: ChatState =
    settings.maintenanceMode || toApiRequestError(error).code === 'MAINTENANCE_MODE'
      ? { kind: 'maintenance' }
      : secondsLeft > 0
        ? { kind: 'rate-limited', secondsLeft }
        : error != null && !(error instanceof AssistantRateLimitError)
          ? { kind: 'error', error }
          : basicMode
            ? { kind: 'basic-mode' }
            : { kind: 'ready' };
  const blocked = state.kind === 'maintenance' || state.kind === 'rate-limited';
  const listEnd = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    listEnd.current?.scrollIntoView?.({ block: 'end' });
  }, [entries, sending, open]);

  useEffect(() => {
    if (!open) return;
    input.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!draft.trim() || sending || blocked) return;
    void send(draft);
    setDraft('');
  };

  return (
    <>
      {open && (
        <section
          role="dialog"
          aria-label="SwiftDrop assistant"
          className="fixed inset-x-3 bottom-24 z-40 flex h-[min(36rem,calc(100vh-8rem))] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl sm:inset-x-auto sm:right-6 sm:w-96"
        >
          <header className="flex items-center gap-3 border-b border-slate-200 bg-brand-600 px-4 py-3 text-white">
            <span className="flex size-9 items-center justify-center rounded-full bg-white/15">
              <Bot className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                SwiftDrop Assistant
                {basicMode && (
                  <span className="rounded-full bg-white/20 px-2 py-0.5 text-[0.65rem] font-semibold tracking-wide uppercase">
                    Basic mode
                  </span>
                )}
              </h2>
              <p className="text-xs text-brand-100">Tracking, fees and help</p>
            </div>
            <button
              type="button"
              aria-label={view === 'fee' ? 'Back to chat' : 'Estimate a fee'}
              aria-pressed={view === 'fee'}
              onClick={() => setView((current) => (current === 'fee' ? 'chat' : 'fee'))}
              className={cn(
                'rounded-full p-1.5 hover:bg-white/15',
                view === 'fee' && 'bg-white/20',
              )}
            >
              <Calculator className="size-5" aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label="Close the assistant"
              onClick={() => setOpen(false)}
              className="rounded-full p-1.5 hover:bg-white/15"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </header>

          {view === 'fee' ? (
            <FeeEstimateForm
              onCancel={() => setView('chat')}
              onResult={(response) => {
                addReply(response.message, response.cards);
                setView('chat');
              }}
            />
          ) : (
            <>
              <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
                <Bubble fromUser={false}>
                  <MessageText text={WELCOME} />
                </Bubble>
                {entries.map((entry) => (
                  <div key={entry.id} className="space-y-2">
                    <Bubble fromUser={entry.role === 'user'}>
                      <MessageText text={entry.content} />
                    </Bubble>
                    {entry.cards.length > 0 && (
                      <div className="max-w-[85%]">
                        <AssistantCards cards={entry.cards} />
                      </div>
                    )}
                  </div>
                ))}
                {sending && <TypingIndicator />}
                <div ref={listEnd} />
              </div>

              <ChatNotice state={state} />

              {suggestions.length > 0 && !sending && !blocked && (
                <div className="flex flex-wrap gap-2 border-t border-slate-100 px-4 pt-3">
                  {suggestions.map((suggestion) => (
                    <button
                      key={suggestion}
                      type="button"
                      onClick={() => void send(suggestion)}
                      className="rounded-full border border-brand-200 bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700 hover:bg-brand-100"
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              )}

              <form onSubmit={submit} className="flex items-center gap-2 px-4 pt-3 pb-2">
                <label htmlFor="assistant-message" className="sr-only">
                  Your message
                </label>
                <input
                  ref={input}
                  id="assistant-message"
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  maxLength={MAX_MESSAGE_CHARS}
                  disabled={blocked}
                  placeholder="Ask about a parcel or a fee…"
                  autoComplete="off"
                  className="h-10 min-w-0 flex-1 rounded-full border border-slate-300 px-4 text-sm focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none"
                />
                <button
                  type="submit"
                  aria-label="Send"
                  disabled={!draft.trim() || sending || blocked}
                  className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50"
                >
                  <SendHorizontal className="size-4" aria-hidden="true" />
                </button>
              </form>
              <p className="flex items-center gap-1.5 px-4 pb-3 text-xs text-slate-500">
                <ShieldCheck className="size-3.5 shrink-0" aria-hidden="true" />
                Don&apos;t share personal details in the chat.
              </p>
            </>
          )}
        </section>
      )}

      <button
        type="button"
        aria-label={open ? 'Close the assistant' : 'Open the assistant'}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="fixed right-6 bottom-6 z-40 flex size-14 items-center justify-center rounded-full bg-brand-600 text-white shadow-lg transition-colors hover:bg-brand-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      >
        {open ? (
          <X className="size-6" aria-hidden="true" />
        ) : (
          <MessageCircle className="size-6" aria-hidden="true" />
        )}
      </button>
    </>
  );
}
