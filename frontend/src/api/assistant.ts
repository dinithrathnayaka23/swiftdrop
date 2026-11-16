import { isAxiosError } from 'axios';
import type {
  AssistantApi,
  AssistantChatResponse,
  AssistantFeeResponse,
} from '../features/assistant/types';
import { apiClient } from './client';
import { ApiRequestError, toApiRequestError } from './errors';
import { parseRetryAfter } from './tracking';

/** 429 from the assistant: too many questions from this customer for now. */
export class AssistantRateLimitError extends ApiRequestError {
  /** Whole seconds to wait before asking again (from the Retry-After header). */
  readonly retryAfterSeconds: number;

  constructor(message: string, retryAfterSeconds: number) {
    super(message, { status: 429, code: 'TOO_MANY_REQUESTS' });
    this.name = 'AssistantRateLimitError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  try {
    const { data } = await apiClient.post<T>(path, body);
    return data;
  } catch (err) {
    // Read Retry-After here: the shared error conversion doesn't keep response headers.
    if (isAxiosError(err) && err.response?.status === 429) {
      throw new AssistantRateLimitError(
        toApiRequestError(err).message,
        parseRetryAfter(err.response.headers['retry-after']),
      );
    }
    throw toApiRequestError(err);
  }
}

/**
 * The customer chat assistant, always from the backend: VITE_USE_MOCK_API doesn't apply here. Without
 * an AI key the backend answers in basic mode (mode RULES).
 */
export const assistantApi: AssistantApi = {
  chat: (request) => post<AssistantChatResponse>('/assistant/chat', request),
  estimateFee: (request) => post<AssistantFeeResponse>('/assistant/fee-estimate', request),
};
