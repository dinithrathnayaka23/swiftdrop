import { isAxiosError } from 'axios';
import type { ApiError } from '../types/api';

const GENERIC_MESSAGE = 'Something went wrong. Please try again.';
const NETWORK_MESSAGE = 'Unable to reach the server. Please check your connection and try again.';

interface ApiRequestErrorOptions {
  status?: number;
  code?: string;
  fieldErrors?: { field: string; message: string }[];
}

/** Normalised error thrown by every API function (real or mock). */
export class ApiRequestError extends Error {
  readonly status: number | undefined;
  readonly code: string | undefined;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(message: string, options: ApiRequestErrorOptions = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = options.status;
    this.code = options.code;
    this.fieldErrors = options.fieldErrors ?? [];
  }
}

/** Converts Axios errors (standard backend ErrorResponse body) into ApiRequestError. */
export function toApiRequestError(err: unknown): ApiRequestError {
  if (err instanceof ApiRequestError) return err;
  if (isAxiosError<ApiError>(err)) {
    if (!err.response) return new ApiRequestError(NETWORK_MESSAGE);
    const body = err.response.data;
    return new ApiRequestError(body?.message ?? GENERIC_MESSAGE, {
      status: err.response.status,
      code: body?.code,
      fieldErrors: body?.fieldErrors,
    });
  }
  return new ApiRequestError(GENERIC_MESSAGE);
}

export function getErrorMessage(err: unknown): string {
  return toApiRequestError(err).message;
}

/** Runs a request and rethrows failures as ApiRequestError. */
export async function request<T>(call: () => Promise<{ data: T }>): Promise<T> {
  try {
    return (await call()).data;
  } catch (err) {
    throw toApiRequestError(err);
  }
}
