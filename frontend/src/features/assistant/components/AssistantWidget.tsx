import { Bot, MessageCircle, SendHorizontal, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { getErrorMessage } from '../../../api/errors';
import { cn } from '../../../utils/cn';
import { MAX_MESSAGE_CHARS, useAssistantChat, WELCOME } from '../hooks/useAssistantChat';
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

/** Floating chat button and panel for signed-in customers. */
export default function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const { entries, suggestions, sending, error, send } = useAssistantChat();
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
    if (!draft.trim() || sending) return;
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
              <h2 className="text-sm font-semibold">SwiftDrop Assistant</h2>
              <p className="text-xs text-brand-100">Tracking, fees and help</p>
            </div>
            <button
              type="button"
              aria-label="Close the assistant"
              onClick={() => setOpen(false)}
              className="rounded-full p-1.5 hover:bg-white/15"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
            <Bubble fromUser={false}>
              <MessageText text={WELCOME} />
            </Bubble>
            {entries.map((entry) => (
              <Bubble key={entry.id} fromUser={entry.role === 'user'}>
                <MessageText text={entry.content} />
              </Bubble>
            ))}
            {sending && <TypingIndicator />}
            {error != null && (
              <p role="alert" className="text-center text-xs text-red-600">
                {getErrorMessage(error)}
              </p>
            )}
            <div ref={listEnd} />
          </div>

          {suggestions.length > 0 && !sending && (
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

          <form onSubmit={submit} className="flex items-center gap-2 px-4 py-3">
            <label htmlFor="assistant-message" className="sr-only">
              Your message
            </label>
            <input
              ref={input}
              id="assistant-message"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              maxLength={MAX_MESSAGE_CHARS}
              placeholder="Ask about a parcel or a fee…"
              autoComplete="off"
              className="h-10 min-w-0 flex-1 rounded-full border border-slate-300 px-4 text-sm focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none"
            />
            <button
              type="submit"
              aria-label="Send"
              disabled={!draft.trim() || sending}
              className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50"
            >
              <SendHorizontal className="size-4" aria-hidden="true" />
            </button>
          </form>
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
