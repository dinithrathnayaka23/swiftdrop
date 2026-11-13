import { Clock, Info, Wrench } from 'lucide-react';
import { getErrorMessage } from '../../../api/errors';
import { cn } from '../../../utils/cn';

export type ChatState =
  | { kind: 'maintenance' }
  | { kind: 'rate-limited'; secondsLeft: number }
  | { kind: 'basic-mode' }
  | { kind: 'error'; error: unknown }
  | { kind: 'ready' };

/** One line above the message box explaining why the assistant is limited, if it is. */
export default function ChatNotice({ state }: { state: ChatState }) {
  if (state.kind === 'ready') return null;

  const {
    icon: Icon,
    tone,
    text,
  } = {
    maintenance: {
      icon: Wrench,
      tone: 'border-amber-200 bg-amber-50 text-amber-800',
      text: 'The assistant is unavailable during maintenance. Please try again later.',
    },
    'rate-limited': {
      icon: Clock,
      tone: 'border-amber-200 bg-amber-50 text-amber-800',
      text:
        state.kind === 'rate-limited'
          ? `You've asked a lot of questions. You can ask again in ${state.secondsLeft} s.`
          : '',
    },
    'basic-mode': {
      icon: Info,
      tone: 'border-sky-200 bg-sky-50 text-sky-800',
      text: 'Basic mode: the AI is busy, so answers are simpler. Tracking, your parcels and the fee form still work.',
    },
    error: {
      icon: Info,
      tone: 'border-red-200 bg-red-50 text-red-700',
      text: state.kind === 'error' ? getErrorMessage(state.error) : '',
    },
  }[state.kind];

  return (
    <p
      role={state.kind === 'error' ? 'alert' : 'status'}
      className={cn('mx-4 mt-3 flex gap-2 rounded-lg border px-3 py-2 text-xs', tone)}
    >
      <Icon className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
      {text}
    </p>
  );
}
