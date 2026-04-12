# Project Clarification Questions

## Business Logic Questions Log

1. Frontend Application Structure

Question: What frontend delivery model should the system use for the on-prem web interface: traditional server-rendered pages, or a decoupled SPA talking to local REST APIs?
My Understanding: The prompt explicitly called for a Vue.js web interface and a Spring Boot backend over the local network, but it did not say whether the UI should be embedded in the backend or fully decoupled.
Solution: Implement a decoupled Vue 3 + TypeScript SPA with Vue Router and Pinia, backed by Spring Boot REST APIs on the LAN. The frontend uses typed Axios clients and role-aware routing, while the backend remains the authorization source of truth.

2. Authentication Mode

Question: Should the offline authentication model use JWTs or server-side sessions?
My Understanding: The prompt allowed either JWT or server sessions, but did not choose one. For an offline-first, on-prem deployment, operational simplicity and revocation mattered more than token portability.
Solution: Implement server-side sessions with CSRF protection. The SPA first fetches `/api/auth/csrf`, then logs in through `/api/auth/login`, and all subsequent requests use `JSESSIONID` plus the CSRF cookie/header pair.

3. Meaning of "Offline-First"

Question: Does offline-first mean full browser-side data sync and local persistence, or graceful degradation when the local backend is unavailable?
My Understanding: The prompt emphasized constrained local deployments and availability, but did not require a browser-resident offline database or sync engine.
Solution: Implement LAN-first graceful degradation rather than offline mutation sync. The frontend monitors browser connectivity and backend reachability through `/actuator/health`, shows an `OfflineIndicator`, and surfaces network-aware error messages while keeping the backend as the single system of record.

4. Customer vs Student Terminology

Question: Should the Customer/Student role be modeled as two separate roles, or as one end-user role with student-oriented features?
My Understanding: The prompt used both labels for the same persona, but the feature set centered on enrollment, training, housing assignment, and student profiles.
Solution: Implement a single `STUDENT` role representing the Customer/Student persona. The frontend navigation, seeded permissions, and self-service APIs all treat that role as the end-user role.

5. Student Self-Service Editing Scope

Question: Which student profile fields are self-editable, and which remain staff-managed?
My Understanding: The prompt required students to maintain a profile while also describing organization master data like school, program, class group, and housing assignment. It did not say whether all fields were student-editable.
Solution: Implement dedicated self-service endpoints (`/api/students/me`) with constrained write access. Students can update contact-oriented fields such as name, email, phone, emergency contacts, and notes, while enrollment status, school, program, class group, housing assignment, and skill-level context remain staff-managed and read-only in the self-service UI.

6. Bulk Import Failure Handling

Question: Should student bulk import fail as a single all-or-nothing operation, or complete partially and return operator-friendly diagnostics?
My Understanding: The prompt explicitly asked for downloadable templates and post-import error reports, which implies partial success handling rather than hard-stop failure at the first bad row.
Solution: Implement CSV import with a downloadable template, row-level validation, sample error previews in the response, persisted job tracking, and downloadable full error CSV reports from `/api/students/import/{jobId}/errors`.

7. Booking Lifecycle Semantics

Question: How should the sparring/training booking lifecycle map to concrete states and transitions?
My Understanding: The prompt named `Initiated`, `Confirmed`, `Canceled`, and `Refunded`, but did not define the state machine, whether confirmation was separate, or how refunds should be represented.
Solution: Implement bookings with explicit lifecycle endpoints and backend state enforcement: creation starts at `INITIATED`, confirmation moves to `CONFIRMED`, cancellation moves to `CANCELED`/`CANCELLED` depending on legacy data compatibility, and refunds move cancelled bookings to `REFUNDED`. Refunds create internal credit ledger entries only; there is no online payment integration.

8. Sparring Filter Shape

Question: Should sparring/training browsing be generic search only, or should the API and UI model the matching dimensions named in the prompt directly?
My Understanding: The prompt explicitly called out session type, level, weight class in lbs, and style, so those needed first-class support rather than a single free-text filter.
Solution: Implement training session filtering around the named dimensions with optional query params on `/api/training/sessions`, plus frontend filter controls for type, level, and style. Session entities and DTOs include `sessionType`, `level`, `weightClassLbs`, and `style`.

9. Data-Scope Enforcement Behavior

Question: When a user tries to read a record outside their organization/department/facility scope, should the API return `403 Forbidden` or hide existence with `404 Not Found`?
My Understanding: The prompt required strict data-scope isolation, and existence hiding reduces information leakage in multi-organization deployments.
Solution: Implement existence-hiding `404` behavior for out-of-scope reads. Data scope is resolved through `DataScopeService` and translated into repository predicates; non-admin users with no rules see nothing by default.

10. Cross-Organization Community Interactions

Question: Can users follow or block any account globally, or only accounts visible inside their allowed data scope?
My Understanding: The prompt asked for social features, but in an on-prem multi-organization system those actions should still respect organization boundaries unless an administrator has full visibility.
Solution: Implement community follow/block operations with scope visibility checks. Admins can interact broadly, while scoped users can only follow or block users visible under their current data scope.

11. Feature Toggle Semantics Under Degradation

Question: How should operational degradation toggles behave in domain services: hide UI only, or enforce behavior centrally in backend business logic?
My Understanding: The prompt described one-click degradation controls, which required backend enforcement instead of frontend-only hiding.
Solution: Implement centralized backend toggle checks through `FeatureGuard`. `community.enabled` gates community writes, `bookings.read-only` blocks booking mutations while preserving reads, and toggle state is managed through `/api/ops/toggles`.

12. Sensitive Data Exposure Policy

Question: Should encrypted sensitive fields always be returned in plaintext to authorized users, or masked by default with explicit opt-in for privileged readers?
My Understanding: The prompt required both encryption at rest and masking on display, but did not specify whether masking should be universal or permission-based.
Solution: Implement AES-GCM encryption at rest for student PII and return masked DTO values by default. Plaintext exposure is reserved for callers with the specific raw-view permission path; the normal UI renders masked values.

13. Export Traceability Format

Question: How should export permission enforcement and watermarking be represented in the system?
My Understanding: The prompt required explicit export permission plus a visible watermark containing username and timestamp, but did not define where that traceability lives.
Solution: Implement export requests behind `exports.read` and record watermark metadata in export job rows as `<username> <ISO-8601 timestamp>`, so every export is attributable to a specific actor and request time.

14. Ops Readiness Representation

Question: Should backups, anomaly scans, restore drills, and chaos drills be modeled as external runbooks only, or as first-class application records?
My Understanding: The prompt described operational readiness features that operators should be able to inspect from inside the deployment.
Solution: Implement ops readiness as application-managed records exposed through `/api/ops`. Feature toggles, backup status entries, export jobs, and scheduled ops job records for backup, anomaly scan, restore drill, and chaos drill are all persisted and queryable.
