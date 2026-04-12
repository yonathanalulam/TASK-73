# DojoStay Design Plan

## Overview

DojoStay was designed as an on-premise operations and community platform for martial arts organizations that need four things in one system:

- offline-capable local-network authentication and role-aware navigation
- student and staff operational workflows
- training, sparring, booking, and internal-credit flows
- lodging-style property availability plus community and moderation features

The implementation follows a decoupled architecture:

- `backend/`: Spring Boot 3.x, Java 21, JPA, Flyway, MySQL
- `frontend/`: Vue 3, TypeScript, Vite, Pinia, Vue Router
- `tests/`: backend unit/api/feature tests and frontend unit tests

## Design Principles

1. On-prem first
All core behavior runs on the local network with no external SaaS dependency.

2. Backend-enforced security
The backend owns authentication, authorization, data-scope checks, audit logging, and feature-toggle enforcement. The frontend mirrors permissions only for navigation and UX hints.

3. Small, explicit modules
The backend is split by domain rather than by technical layer alone: `auth`, `users`, `students`, `training`, `property`, `community`, `credentials`, `risk`, `ops`, and `scopes`.

4. Clear role experience
The frontend shell and dashboard adapt to `STUDENT`, `PHOTOGRAPHER`, `STAFF`, and `ADMIN` so each role lands on a focused operational surface.

5. Auditability by default
Privileged actions, moderation, unlocks, blacklist operations, booking changes, and ops changes all write audit records with actor, target, timestamp, and correlation metadata.

## Delivery Plan Followed

The development plan was executed in phases, each adding a coherent slice of the final system:

1. Foundation
- Spring Boot app bootstrap
- Vue SPA shell
- shared API envelope and exception handling
- authentication, lockout, password policy, and audit log foundation

2. Authorization and scope control
- three-layer role/permission/resource model
- data-scope rules by organization, department, and facility area
- deny-by-default access behavior for non-admin users

3. Student and organization operations
- organization CRUD
- student master data
- bulk student import, template download, and error reporting
- student self-service profile route and API

4. Property and lodging
- property catalog and details
- availability search by date window
- amenities, images, room types, and nightly rate calendar support
- reservation creation and cancellation

5. Training, sparring, and bookings
- training classes and sessions
- 30-minute slot alignment
- session type (`VENUE` / `ONLINE`)
- sparring filters (`level`, `weightClassLbs`, `style`)
- booking lifecycle and credit-backed refunds

6. Community and engagement
- posts and threaded comments
- replies and quotes
- likes, follows, thread mute, and user block
- notifications with unread counts
- moderation queue and restore flow

7. Credentials and risk
- credential review queue
- evidence upload with SHA-256 deduplication
- approve, reject, and blacklist workflows
- risk flags and incident logging

8. Ops and resilience
- export jobs with watermark metadata
- feature toggles
- backup status records
- scheduled ops job records for backup, anomaly scan, restore drill, and chaos drill

9. Remediation and hardening
- student self-service route/API completion
- scope management API and client surface
- booking contract alignment
- feature-guard enforcement for community and bookings
- additional remediation-focused tests

## System Architecture

## Frontend

The frontend was designed as a role-aware SPA using a single authenticated shell.

Core elements:

- `AppShell.vue` for the application frame
- `AppSidebar.vue` for role-based primary navigation
- `DashboardPage.vue` for role-specific landing cards and summary widgets
- typed API clients in `frontend/src/api`
- `auth` Pinia store for current-user state and permission checks
- `OfflineIndicator.vue` plus `useNetworkStatus.ts` for LAN reachability awareness

Primary routed pages:

- `LoginPage.vue`
- `DashboardPage.vue`
- `MyProfilePage.vue`
- `StudentsPage.vue`
- `TrainingPage.vue`
- `PropertyPage.vue`
- `CommunityPage.vue`
- `NotificationsPage.vue`
- `ModerationPage.vue`
- `AdminUsersPage.vue`

UX plan by role:

- `STUDENT`: own profile, training browse, bookings, community, notifications
- `PHOTOGRAPHER`: training visibility, community, notifications
- `STAFF`: students, moderation, operational dashboards, scoped user visibility
- `ADMIN`: full access, user administration, organizations, ops controls, global visibility

## Backend

The backend was designed as a REST API with domain-aligned services and DTO boundaries.

Key modules:

- `auth`: login/logout, lockout, password policy, current-user model, security config
- `users` and `roles`: user lifecycle, role catalog, permission catalog, role assignment
- `scopes`: data-scope rules, effective scope resolution, repository specifications
- `students`: master data, self-service profile, bulk import jobs and reports
- `training`: classes, sessions, bookings, conflict detection, credit ledger
- `property`: properties, reservations, amenities, images, room types, nightly rates
- `community`: posts, comments, likes, follows, blocks, mutes, mentions, notifications
- `credentials`: review queue, file-backed submissions, duplicate detection, blacklist path
- `risk`: risk flags and incident tracking
- `ops`: feature toggles, backup status, export jobs, scheduled ops job records
- `common`: API envelope, global exception handler, correlation IDs, rate limiting

## Security Design

### Authentication

The design chose server-side sessions over JWTs because the deployment is local-network only and benefits from simpler revocation and CSRF control.

Implemented behaviors:

- `GET /api/auth/csrf` seeds the CSRF cookie
- `POST /api/auth/login` authenticates and writes the security context to the session
- `POST /api/auth/logout` invalidates the session
- `GET /api/auth/me` returns the current authenticated principal
- password changes stay inside the local system

### Authorization

Authorization follows three layers:

1. role to permission
2. permission to endpoint through `@PreAuthorize`
3. user to data scope through `DataScopeService` and `DataScopeSpec`

Design rules:

- admins get full scope
- scoped users only see records inside granted organization/department/facility axes
- out-of-scope reads return `404`, not `403`, to avoid existence leaks

### Data protection

Sensitive student fields are encrypted at rest. DTOs default to masked display values. Additional protections include:

- BCrypt password hashing
- 12-character complexity policy
- 5 failed attempts -> 15 minute lockout
- admin unlock path
- CSRF protection
- rate limiting filter
- SQL-injection-safe JPA access patterns

## Domain Design

### Students and imports

Student records carry enrollment and organization master data including school, program, class group, and housing assignment. The design split editing responsibility:

- staff can manage full student records
- students use `/api/students/me` for constrained self-service updates

Imports were designed as operator workflows rather than raw uploads:

- downloadable template
- row-level validation
- persisted import job
- sample errors in response
- downloadable full error report

### Training and bookings

Training supports both general sessions and sparring-style matching. Sessions include:

- 30-minute grid-aligned start/end times
- online vs venue session types
- optional level, weight class, and style metadata

Booking design decisions:

- conflict detection happens before creation
- lifecycle is explicit, not free-form
- refunds are internal credits only
- booking mutations respect degradation toggles

### Property and lodging

Property was designed as a lodging-style inventory surface rather than just a building list. The data model includes:

- property basics and policies
- amenities
- images
- room types
- nightly rates by date
- availability queries by date range
- reservations

This allows the frontend to present both summary cards and richer property detail screens.

### Community and moderation

Community was designed as operational engagement tied to the same identity and scope system as the rest of the app. Features include:

- posts and comments
- replies and quotes
- likes
- follows
- @mentions
- mute thread
- block user
- notification delivery and unread count
- moderation reports, resolution, and restore

Community write operations are backend-gated by feature toggles, and social graph actions respect data-scope visibility.

### Credentials and risk control

Credential review is fully offline. The design supports:

- JSON submission and multipart file-backed submission
- type and size validation
- SHA-256 deduplication for uploaded evidence
- reviewer decision workflow
- blacklist path for high-risk identities
- separate risk-flag and incident logging APIs

### Operations and resilience

Operational readiness is represented inside the product itself through:

- feature toggles
- backup status entries
- export job requests with watermark metadata
- scheduled ops job records for backup, anomaly, restore drill, and chaos drill

This keeps operational state inspectable from the same deployment without requiring external services.

## Data and Persistence Plan

Persistence uses Flyway-managed schema evolution. Major tables are grouped around the same domain boundaries as the backend modules:

- users, roles, permissions, login attempts, lock states
- organizations and scope rules
- students and bulk import jobs
- properties, reservations, room types, nightly rates, images, amenities
- training classes, sessions, bookings, credit transactions
- posts, comments, likes, follows, blocks, notifications, moderation reports
- credential reviews, risk flags, incidents
- feature toggles, backup statuses, export jobs, ops job records, audit log

## Testing Plan

Testing followed the same layered design:

- unit tests for focused business logic and helpers
- API tests for controller/auth behavior
- feature tests for multi-entity domain flows
- frontend unit tests for client/store behavior

The tests directory is intentionally outside the app source trees so audit and delivery review can inspect it independently.

## Summary

The design plan was to build DojoStay as a local-network operational platform with strict backend security, domain-focused services, a role-aware Vue SPA, and enough breadth to cover identity, student operations, training, lodging-style availability, community engagement, risk handling, and operational controls in one deployment. The current codebase reflects that plan directly.
