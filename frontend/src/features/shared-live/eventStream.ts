import { API_BASE_URL } from '../../utils/env';

/**
 * Reads a server-sent events stream with fetch() and a ReadableStream instead of EventSource, so a
 * request can carry an `Authorization: Bearer` header (the admin stream needs one) and its error
 * status (404, 429, 503) can be read. No npm package needed.
 */

/** One event from the stream. Comment lines (`:connected`, `:ping`) are not events. */
export interface StreamMessage {
  /** The `event:` field, "message" when the server sent none. */
  event: string;
  /** The `data:` lines, joined with newlines. */
  data: string;
}

/** The server refused to open the stream (or the response wasn't a stream). */
export class StreamHttpError extends Error {
  readonly status: number;
  /** `code` of the standard error body, e.g. TOO_MANY_REQUESTS or MAINTENANCE_MODE. */
  readonly code: string | undefined;
  /** Seconds from a Retry-After header, when there was one. */
  readonly retryAfterSeconds: number | undefined;

  constructor(status: number, code?: string, message?: string, retryAfterSeconds?: number) {
    super(message ?? `The live stream could not be opened (${status}).`);
    this.name = 'StreamHttpError';
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * Splits text chunks into events, following the event stream format: fields end at any line
 * break, an empty line ends an event, `:` starts a comment. `id:` and `retry:` aren't used here.
 */
export function createEventStreamParser(onMessage: (message: StreamMessage) => void) {
  let buffer = '';
  let event = '';
  let data: string[] = [];

  function line(text: string) {
    if (text === '') {
      if (data.length > 0) onMessage({ event: event || 'message', data: data.join('\n') });
      event = '';
      data = [];
      return;
    }
    if (text.startsWith(':')) return;
    const colon = text.indexOf(':');
    const field = colon === -1 ? text : text.slice(0, colon);
    let value = colon === -1 ? '' : text.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }

  return (chunk: string) => {
    buffer += chunk;
    const lines = buffer.split(/\r\n|\r|\n/);
    // The last piece may be a line that hasn't finished arriving.
    buffer = lines.pop() ?? '';
    lines.forEach(line);
  };
}

export interface OpenStreamOptions {
  /** Ends the stream; the returned promise then rejects with an AbortError. */
  signal: AbortSignal;
  /** Sent as `Authorization: Bearer …` (admin streams). */
  token?: string | null;
  /** Called once the server has accepted the stream (status 200). */
  onOpen?: () => void;
  onMessage: (message: StreamMessage) => void;
}

function retryAfter(header: string | null): number | undefined {
  if (!header || !/^\d+$/.test(header.trim())) return undefined;
  return Math.max(1, Number(header.trim()));
}

/**
 * Opens `path` (relative to the API base, e.g. `/public/tracking/SD1/stream`) and reads it until
 * the server ends it. Resolves when the server closes the stream normally (reconnect then).
 * Rejects with {@link StreamHttpError} when the server refuses it, or with the fetch error when
 * the network fails or the stream is aborted.
 */
export async function openEventStream(path: string, options: OpenStreamOptions): Promise<void> {
  const headers: Record<string, string> = {
    // JSON too, so a refusal comes back as the standard error body.
    Accept: 'text/event-stream, application/json',
  };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers,
    signal: options.signal,
    cache: 'no-store',
    credentials: 'same-origin',
  });

  const contentType = response.headers.get('Content-Type') ?? '';
  if (!response.ok || !contentType.startsWith('text/event-stream') || !response.body) {
    let code: string | undefined;
    let message: string | undefined;
    try {
      const body = (await response.json()) as { code?: string; message?: string };
      code = body.code;
      message = body.message;
    } catch {
      // Not the standard error body: keep the status only.
    }
    throw new StreamHttpError(
      response.status,
      code,
      message,
      retryAfter(response.headers.get('Retry-After')),
    );
  }

  options.onOpen?.();
  const parse = createEventStreamParser(options.onMessage);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) return;
    // stream: true keeps a multi-byte character that is split across chunks intact.
    parse(decoder.decode(value, { stream: true }));
  }
}
