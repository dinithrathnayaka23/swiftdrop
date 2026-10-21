/** Mirrors com.swiftdrop.common.exception.ErrorResponse. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path?: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
}
