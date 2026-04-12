# DojoStay API Specification

## Overview

This document describes the implemented REST API surface for DojoStay.

- Base path: `/api`
- Transport: local network HTTP/HTTPS
- Auth model: server-side session with CSRF protection
- Response envelope: `ApiResponse<T>`

## Conventions

### Response envelope

All JSON endpoints return the shared envelope:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "..."
}
```

Error responses use the same shape:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SOME_ERROR_CODE",
    "message": "Human-readable message",
    "fields": {
      "fieldName": "Validation message"
    }
  },
  "traceId": "..."
}
```

### Authentication and CSRF

DojoStay uses Spring Security server-side sessions.

1. `GET /api/auth/csrf` to obtain CSRF cookie.
2. `POST /api/auth/login` with username/password.
3. Browser carries `JSESSIONID` and CSRF cookie on subsequent requests.
4. Mutating requests send `X-XSRF-TOKEN` automatically from the frontend Axios client.

### Pagination

Paged endpoints return Spring-style page subsets with these fields:

- `content`
- `totalElements`
- `totalPages`
- `number`
- `size`
- `first`
- `last`

### Scope behavior

- Admins have full access.
- Scoped users are filtered by organization / department / facility rules.
- Out-of-scope reads are generally surfaced as `404 Not Found` rather than `403 Forbidden`.

## Role and Permission Model

Seeded roles:

- `ADMIN`
- `STAFF`
- `STUDENT`
- `PHOTOGRAPHER`

Representative permission families:

- `users.read`, `users.write`
- `students.read`, `students.write`, `students.import`, `students.self.read`, `students.self.write`
- `training.read`, `training.write`, `bookings.read`, `bookings.write`, `credits.read`, `credits.write`
- `property.read`, `property.write`, `reservations.read`, `reservations.write`
- `community.read`, `community.write`, `moderation.review`, `notifications.read`
- `credentials.review`, `risk.read`, `risk.write`
- `ops.toggles.read`, `ops.toggles.write`, `ops.backups.read`, `exports.read`, `scopes.read`, `scopes.write`

## Auth API

| Method | Path | Auth | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/csrf` | Public | Seed CSRF cookie | No-op body, used before login and other mutations |
| POST | `/api/auth/login` | Public | Log in | JSON body includes `username`, `password` |
| POST | `/api/auth/logout` | Authenticated | Log out | Invalidates session |
| GET | `/api/auth/me` | Authenticated | Current user | Returns user id, username, fullName, primaryRole, roles, permissions |
| POST | `/api/auth/change-password` | Authenticated | Change own password | JSON body includes current and new password |
| POST | `/api/auth/unlock/{userId}` | `ROLE_ADMIN` | Admin unlock account | Resets lockout state |

## Users and Roles API

### Users

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/users` | `users.read` | List users in caller scope | Paged endpoint |
| GET | `/api/users/{id}` | `users.read` | Get one user | 404 if out of scope |
| POST | `/api/users` | `users.write` | Create user | Admin-oriented management flow |
| PUT | `/api/users/{id}` | `users.write` | Update user | Includes enable/disable behavior |
| PUT | `/api/users/{id}/roles` | `users.write` | Replace user roles | Used for role assignment |

### Role catalog

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/roles` | `users.read` | List roles | Read-only catalog |
| GET | `/api/permissions` | `users.read` | List permissions | Read-only catalog |

## Organizations and Scope API

### Organizations

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/organizations` | `orgs.read` | List visible organizations | Scope filtered |
| GET | `/api/organizations/{id}` | `orgs.read` | Get one organization | 404 if out of scope |
| POST | `/api/organizations` | `orgs.write` | Create organization | Validates unique code |
| PUT | `/api/organizations/{id}` | `orgs.write` | Update organization | Scope-aware update |

### Data scope rules

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/scopes` | `scopes.read` | List all scope rules | Admin surface |
| GET | `/api/scopes/users/{userId}` | `scopes.read` | List scope rules for one user | Returns organization / department / facility entries |
| PUT | `/api/scopes/users/{userId}` | `scopes.write` | Replace rules for one user | Body shape: `{ rules: [{ scopeType, scopeTargetId }] }` |

## Students API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/students/me` | `students.self.read` | Get own linked student profile | Student self-service |
| PUT | `/api/students/me` | `students.self.write` | Update own profile | Only allowed fields are applied server-side |
| GET | `/api/students` | `students.read` | List students | Paged, scope filtered |
| GET | `/api/students/{id}` | `students.read` | Get one student | 404 if out of scope |
| POST | `/api/students` | `students.write` | Create student | Staff/admin operation |
| PUT | `/api/students/{id}` | `students.write` | Update student | Staff/admin operation |
| POST | `/api/students/import` | `students.import` | Bulk import students from CSV | Multipart form with `organizationId` and `file` |
| GET | `/api/students/import/template` | `students.import` | Download CSV template | Returns `text/csv` |
| GET | `/api/students/import/{jobId}/errors` | `students.import` | Download full import error report | Returns `text/csv` |

Student domain highlights:

- supports school, program, classGroup, housingAssignment
- uses masked DTO values for sensitive fields by default
- supports import job tracking and error report download

## Training API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/training/classes` | `training.read` | List training classes | |
| POST | `/api/training/classes` | `training.write` | Create training class | |
| GET | `/api/training/classes/{id}/sessions` | `training.read` | List sessions for one class | |
| GET | `/api/training/sessions` | `training.read` | Filter sessions | Optional query params: `sessionType`, `level`, `weightClassLbs`, `style`, `from`, `to` |
| POST | `/api/training/sessions` | `training.write` | Create session | Enforces 30-minute slot alignment |
| DELETE | `/api/training/sessions/{id}` | `training.write` | Cancel session | |

Session design includes:

- `VENUE` and `ONLINE` session types
- sparring filters by level, weight class, and style
- capacity and booked-seat tracking

## Bookings API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/bookings/mine` | `bookings.read` | List bookings for current user | Uses linked student record |
| GET | `/api/bookings` | `bookings.read` | List bookings by filter | Query params: `sessionId` or `studentId`; returns empty list if neither provided |
| POST | `/api/bookings` | `bookings.write` | Create booking | JSON body includes `trainingSessionId`, `studentId`, `notes` |
| POST | `/api/bookings/{id}/confirm` | `bookings.write` | Confirm initiated booking | `INITIATED -> CONFIRMED` |
| DELETE | `/api/bookings/{id}` | `bookings.write` | Cancel booking | Releases seat for active bookings |
| POST | `/api/bookings/{id}/refund` | `bookings.write` | Refund booking as internal credit | Body includes `creditAmount` and optional `notes` |

Booking rules:

- booking writes are blocked when `bookings.read-only` is active
- overlapping active bookings are rejected as conflicts
- refunds are internal credit only

## Credits API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/credits/students/{studentId}/balance` | `credits.read` | Get credit balance | |
| GET | `/api/credits/students/{studentId}/history` | `credits.read` | Get credit history | |
| POST | `/api/credits/adjust` | `credits.write` | Post credit adjustment | Used by staff flows and refunds |

## Property and Lodging API

### Property catalog and availability

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/property` | `property.read` | List properties | |
| POST | `/api/property` | `property.write` | Create property | |
| GET | `/api/property/{id}/availability` | `property.read` | Check availability by date range | Query params: `startsOn`, `endsOn` |

### Reservations

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/property/reservations` | `reservations.write` | Create reservation | |
| DELETE | `/api/property/reservations/{id}` | `reservations.write` | Cancel reservation | |

### Amenities

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/property/{id}/amenities` | `property.read` | List amenities for property | |
| PUT | `/api/property/{id}/amenities` | `property.write` | Upsert amenity | One amenity per request |
| DELETE | `/api/property/{id}/amenities/{code}` | `property.write` | Remove amenity | Uses amenity code |

### Images

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/property/{id}/images` | `property.read` | List images for property | |
| POST | `/api/property/{id}/images` | `property.write` | Upload property image | Multipart form with `file`, optional `caption`, optional `displayOrder` |
| DELETE | `/api/property/images/{imageId}` | `property.write` | Delete property image | |

### Room types and nightly rates

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/property/{id}/room-types` | `property.read` | List room types | |
| POST | `/api/property/{id}/room-types` | `property.write` | Create room type | |
| PUT | `/api/property/room-types/{roomTypeId}` | `property.write` | Update room type | |
| GET | `/api/property/room-types/{roomTypeId}/rates` | `property.read` | List nightly rates in range | Query params: `from`, `to` |
| PUT | `/api/property/room-types/{roomTypeId}/rates` | `property.write` | Upsert nightly rate | JSON body carries rate/date payload |

Property domain highlights:

- property descriptions and policies are first-class fields
- availability, room types, images, amenities, and nightly rates are separate surfaces

## Community API

### Posts and comments

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/community/posts` | `community.read` | List posts visible to caller | Scope filtered |
| POST | `/api/community/posts` | `community.write` | Create post | Blocked when `community.enabled` is off |
| GET | `/api/community/posts/{postId}/comments` | `community.read` | List comments for post | |
| POST | `/api/community/comments` | `community.write` | Create comment | Supports replies and quote references |

### Likes

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/community/posts/{postId}/like` | `community.write` | Like post | |
| DELETE | `/api/community/posts/{postId}/like` | `community.write` | Unlike post | |
| POST | `/api/community/comments/{commentId}/like` | `community.write` | Like comment | |
| DELETE | `/api/community/comments/{commentId}/like` | `community.write` | Unlike comment | |

### Follow graph

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/community/users/{userId}/follow` | `community.write` | Follow user | Scope visibility enforced |
| DELETE | `/api/community/users/{userId}/follow` | `community.write` | Unfollow user | |
| GET | `/api/community/me/following` | `community.read` | List users current user follows | |
| GET | `/api/community/me/followers` | `community.read` | List followers of current user | |

### Mute and block

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/community/posts/{postId}/mute` | `community.write` | Mute thread | |
| DELETE | `/api/community/posts/{postId}/mute` | `community.write` | Unmute thread | |
| POST | `/api/community/users/{userId}/block` | `community.write` | Block user | Scope visibility enforced |
| DELETE | `/api/community/users/{userId}/block` | `community.write` | Unblock user | |

Community highlights:

- mentions trigger notifications
- muted threads suppress future notifications
- blocked users are filtered from feed/notification behavior

## Notifications API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/notifications` | `notifications.read` | List current user's notifications | |
| GET | `/api/notifications/unread-count` | `notifications.read` | Get unread count | Returns `{ unread: number }` |
| POST | `/api/notifications/{id}/read` | `notifications.read` | Mark notification read | |

## Moderation API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/moderation/reports` | `moderation.review` | List open reports | Staff/admin moderation queue |
| POST | `/api/moderation/reports` | `community.write` | File moderation report | End-user report flow |
| POST | `/api/moderation/reports/{id}/resolve` | `moderation.review` | Resolve report | Body includes resolution and hide-target decision |
| POST | `/api/moderation/restore` | `moderation.review` | Restore hidden content | Query params: `targetType`, `targetId` |

## Credentials API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/credentials/reviews` | `credentials.review` | List credential reviews | Scope-aware reviewer view |
| POST | `/api/credentials/reviews` | `credentials.review` | Submit review without file | Legacy JSON surface |
| POST | `/api/credentials/reviews/upload` | `credentials.review` | Submit review with evidence file | Multipart parts: `file`, `studentId`, `discipline`, `requestedRank`, optional `currentRank`, optional `evidence` |
| POST | `/api/credentials/reviews/{id}/decide` | `credentials.review` | Approve or reject review | Body includes decision and reviewer notes |
| POST | `/api/credentials/reviews/{id}/blacklist` | `credentials.review` | Blacklist user linked to review | Separate from review decision |

Credential design highlights:

- file size/type validation
- SHA-256 duplicate detection
- local file storage under configured upload root

## Risk API

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/risk/flags` | `risk.read` | List risk flags | |
| POST | `/api/risk/flags` | `risk.write` | Raise risk flag | |
| POST | `/api/risk/flags/{id}/clear` | `risk.write` | Clear risk flag | |
| GET | `/api/risk/incidents` | `risk.read` | List incidents | |
| POST | `/api/risk/incidents` | `risk.write` | Log incident | |

## Ops API

### Feature toggles

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/ops/toggles` | `ops.toggles.read` | List feature toggles | |
| POST | `/api/ops/toggles` | `ops.toggles.write` | Create or update toggle | Used for degradation controls |

### Backups and exports

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/ops/backups` | `ops.backups.read` | List backup status rows | |
| POST | `/api/ops/backups` | `ops.backups.read` | Record backup status row | Records backup outcomes into the system |
| GET | `/api/ops/exports` | `exports.read` | List caller export jobs | |
| POST | `/api/ops/exports` | `exports.read` | Request export job | Export rows include watermark metadata |

### Ops job records

| Method | Path | Permission | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/ops/jobs` | `ops.backups.read` | List recent ops job records | Includes backup, anomaly, restore, chaos jobs |
| GET | `/api/ops/jobs/{kind}` | `ops.backups.read` | List recent jobs by kind | `kind` is an `OpsJobRecord.JobKind` enum |
| POST | `/api/ops/jobs/trigger` | `ops.toggles.write` | Trigger manual ops job | Body includes `jobKind` |

Seeded toggle codes:

- `community.enabled`
- `bookings.read-only`
- `property.read-only`

## Actuator Surface

| Method | Path | Auth | Purpose | Notes |
| --- | --- | --- | --- | --- |
| GET | `/actuator/health` | Public | Health check | Used by frontend reachability checks |
| GET | `/actuator/info` | Public | Deployment info | Public allowlist in security config |
| GET | `/actuator/**` | `ROLE_ADMIN` | Internal actuator endpoints | All other actuator routes are admin protected |

## Notes for Frontend Integrators

1. Always call `GET /api/auth/csrf` before the first mutating request in a new browser session.
2. Expect `404` for some out-of-scope reads even when a record exists.
3. Treat `FEATURE_DISABLED` as an operational state, not a generic server failure.
4. For uploads, use `multipart/form-data` and let the browser set the request boundary.
5. For paged endpoints, consume only the documented page subset fields rather than Spring internals.
