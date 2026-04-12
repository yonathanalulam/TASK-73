import axios, { type AxiosError, type AxiosInstance } from 'axios';
import type { ApiError, ApiResponse } from '@/types/api';

/**
 * Centralized Axios instance.
 *
 * Responsibilities:
 *  - send credentials (cookies) so server-side sessions work
 *  - reflect the XSRF cookie back as X-XSRF-TOKEN header (Spring CSRF cookie repo)
 *  - unwrap the {success, data, error} envelope so callers get plain T
 *  - normalize errors into a typed ApiError that toasts/forms can render
 */
export const http: AxiosInstance = axios.create({
  baseURL: '/',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

export class ApiException extends Error {
  readonly status: number;
  readonly apiError: ApiError;
  readonly traceId: string | null;

  constructor(status: number, apiError: ApiError, traceId: string | null) {
    super(apiError.message);
    this.status = status;
    this.apiError = apiError;
    this.traceId = traceId;
  }
}

function unwrap<T>(envelope: ApiResponse<T>): T {
  if (envelope.success) {
    // data may be null for 200 OK with no body (envelope.empty()).
    return envelope.data as T;
  }
  throw new ApiException(
    200,
    envelope.error ?? { code: 'UNKNOWN', message: 'Unknown error' },
    envelope.traceId,
  );
}

http.interceptors.response.use(
  (response) => {
    if (response.data && typeof response.data === 'object' && 'success' in response.data) {
      // Replace the response body with the unwrapped payload.
      // Callers receive `T` directly via response.data.
      response.data = unwrap(response.data as ApiResponse<unknown>);
    }
    return response;
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status ?? 0;
    const envelope = error.response?.data;
    const apiErr: ApiError = envelope?.error ?? {
      code: status === 0 ? 'NETWORK_ERROR' : 'HTTP_' + status,
      message:
        status === 0
          ? 'Cannot reach the server. Check your local network connection.'
          : error.message,
    };
    return Promise.reject(new ApiException(status, apiErr, envelope?.traceId ?? null));
  },
);

/**
 * Fetches a fresh CSRF cookie from the backend. Should be called once before the first
 * mutating request (e.g. login).
 */
export async function ensureCsrf(): Promise<void> {
  await http.get('/api/auth/csrf');
}
