/**
 * Mirrors the Java ApiResponse / ApiError envelope so the frontend can unwrap responses
 * with strong typing. Keep this file in sync with backend/com/dojostay/common/ApiResponse.
 */

export interface ApiError {
  code: string;
  message: string;
  fields?: Record<string, string>;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  traceId: string | null;
}

export type UserRoleType = 'STUDENT' | 'PHOTOGRAPHER' | 'STAFF' | 'ADMIN';

export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  primaryRole: UserRoleType;
  roles: string[];
  permissions: string[];
}

/**
 * Subset of Spring Data's Page JSON shape. We only expose the fields the UI actually
 * renders to avoid coupling to internal pageable/sort metadata.
 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface AdminUser {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  primaryRole: UserRoleType;
  enabled: boolean;
  organizationId: number | null;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}
