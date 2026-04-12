# Delivery Acceptance and Project Architecture Audit (Static-Only)

## 1. Verdict
- Overall conclusion: **Partial Pass**.
- The backend covers most previously missing domain slices (students master data/import artifacts, property richness, booking lifecycle, community interactions, credential upload pipeline, export watermark). However, a few high-risk gaps remain in production-grade security enforcement and frontend/backend contract correctness.

## 2. Scope and Static Verification Boundary
- Reviewed statically: backend Spring code, frontend Vue/TS code, Flyway migrations, docs, and tests under `tests/`.
- Not executed: app startup, tests, Docker, browser, network fault scenarios, backups, or any external dependency.
- Runtime-only conclusions are marked as **Cannot Confirm Statistically**.
- Manual verification required for: end-to-end UX behavior, LAN offline degradation quality, upload/storage filesystem behavior in deployment, and backup/restore operability.

## 3. Repository / Requirement Mapping Summary
- Claimed delivery status is full phases 1-9 with remediation (`README.md:7`, `docs/architecture.md:101`, `docs/architecture.md:154`).
- Remediation implementation is visible in code/migration:
  - PII encryption + masking path (`backend/src/main/java/com/dojostay/students/Student.java:45`, `backend/src/main/java/com/dojostay/students/StudentService.java:452`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:13`).
  - Student import template + persisted error report (`backend/src/main/java/com/dojostay/students/StudentController.java:97`, `backend/src/main/java/com/dojostay/students/StudentService.java:333`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:27`).
  - Property amenities/images/room types/nightly rates (`backend/src/main/java/com/dojostay/property/PropertyController.java:85`, `backend/src/main/java/com/dojostay/property/PropertyController.java:117`, `backend/src/main/java/com/dojostay/property/PropertyController.java:164`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:68`).
  - Booking lifecycle and slot/matching filters (`backend/src/main/java/com/dojostay/training/BookingService.java:37`, `backend/src/main/java/com/dojostay/training/TrainingService.java:97`, `backend/src/main/java/com/dojostay/training/TrainingService.java:157`).
  - Community replies/quotes/likes/follows/mutes/blocks/restore (`backend/src/main/java/com/dojostay/community/CommunityService.java:41`, `backend/src/main/java/com/dojostay/community/ModerationService.java:150`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:118`).
  - Credential multipart upload + SHA-256 dedup + blacklist (`backend/src/main/java/com/dojostay/credentials/CredentialReviewController.java:63`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:160`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:261`).
  - Export watermark metadata (`backend/src/main/java/com/dojostay/ops/ExportJobService.java:73`, `backend/src/main/java/com/dojostay/ops/ExportJob.java:62`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:45`).

## 4. Section-by-section Review

### 1) Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: **Partial Pass**.
- Strength: docs now align better with remediation scope and module layout (`README.md:28`, `docs/architecture.md:154`, `docs/architecture.md:197`).
- Gap: production encryption requirement is described, but enforced behavior still allows plaintext fallback when key is missing (`docs/architecture.md:81`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:65`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:88`).

#### 1.2 Material deviation from prompt
- Conclusion: **Partial Pass**.
- Backend requirement fit is now mostly strong, but UI and contract issues reduce delivered functionality for key operator workflows.
- Evidence: dashboard still explicitly placeholder (`frontend/src/pages/DashboardPage.vue:14`, `frontend/src/pages/DashboardPage.vue:62`); student list API contract mismatch (`backend/src/main/java/com/dojostay/students/StudentController.java:47`, `frontend/src/api/students.ts:20`); training API contract mismatch (`backend/src/main/java/com/dojostay/training/dto/TrainingSessionResponse.java:7`, `frontend/src/api/training.ts:6`).

### 2) Delivery Completeness

#### 2.1 Core explicit requirements coverage
- Conclusion: **Partial Pass**.
- Covered well in backend: security, scopes, students import/master fields, property richness, training lifecycle/filters, community interactions, moderation restore, credential file controls, export watermark.
- Evidence: `backend/src/main/java/com/dojostay/students/Student.java:88`, `backend/src/main/java/com/dojostay/property/PropertyService.java:56`, `backend/src/main/java/com/dojostay/training/BookingService.java:43`, `backend/src/main/java/com/dojostay/community/CommunityService.java:43`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:118`, `backend/src/main/java/com/dojostay/ops/ExportJobService.java:89`.
- Remaining incomplete area is frontend reliability/completeness due to mismatched DTO assumptions and placeholder dashboard.

#### 2.2 Basic end-to-end deliverable (0->1)
- Conclusion: **Partial Pass**.
- Repo contains full stack modules and integrated test structure (`README.md:11`, `backend/pom.xml:88`, `frontend/vite.config.ts:27`).
- **Cannot Confirm Statistically** whether end-to-end journeys succeed without runtime failures because critical UI/API shape mismatches are visible in code.

### 3) Engineering and Architecture Quality

#### 3.1 Structure and decomposition
- Conclusion: **Pass**.
- Backend architecture remains modular and consistent by domain, with controller/service/repository separation and migration sequencing (`docs/architecture.md:199`, `backend/src/main/resources/db/migration/V1__baseline_auth.sql:1`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:1`).

#### 3.2 Maintainability and extensibility
- Conclusion: **Partial Pass**.
- Strength: feature additions are cohesive and documented in code comments/services.
- Gap: type contracts between frontend and backend are drifting, which undermines maintainability and release confidence (`frontend/src/api/training.ts:3`, `backend/src/main/java/com/dojostay/training/dto/TrainingSessionResponse.java:7`, `frontend/src/api/students.ts:3`, `backend/src/main/java/com/dojostay/students/StudentController.java:47`).

### 4) Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API discipline
- Conclusion: **Partial Pass**.
- Strength: centralized exception model and auditing/correlation remain solid (`backend/src/main/java/com/dojostay/common/GlobalExceptionHandler.java:26`, `backend/src/main/java/com/dojostay/common/CorrelationIdFilter.java:17`, `backend/src/main/java/com/dojostay/audit/AuditService.java:38`).
- Gap: security-sensitive encryption behavior degrades to plaintext if key missing, which is a production-hardening defect (`backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:66`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:90`, `backend/src/main/resources/application-prod.yml:17`).

#### 4.2 Product-grade vs demo-grade
- Conclusion: **Partial Pass**.
- Backend appears product-oriented; frontend still contains clear placeholder/demo indicators in the default landing page (`frontend/src/pages/DashboardPage.vue:14`, `frontend/src/pages/DashboardPage.vue:62`).

### 5) Prompt Understanding and Requirement Fit
- Conclusion: **Partial Pass**.
- The team clearly understood and implemented most backend remediation asks, but implementation fidelity is weakened by unresolved frontend/API integration correctness and weak production guardrails around encryption key enforcement.

### 6) Aesthetics / Frontend UX
- Conclusion: **Cannot Confirm Statistically**.
- Functional pages exist, the central dashboard remains explicitly placeholder text and several pages consume response shapes, making UX completion uncertain.
- **Cannot Confirm Statistically** visual polish/responsiveness in browser runtime.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker / High

1) **High — Encryption-at-rest can silently degrade to plaintext in misconfigured environments**
- Evidence: missing key logs warning then sets `keyHolder = null` (`backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:65`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:68`), write path returns raw attribute when key is null (`backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:88`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:90`), prod profile does not explicitly require/provide encryption key (`backend/src/main/resources/application-prod.yml:17`).
- Impact: confidentiality control becomes configuration-fragile; accidental plaintext persistence is possible.

2) **High — Students page contract mismatch (paged backend vs array frontend, field-name mismatch)**
- Evidence: backend returns `Page<StudentResponse>` (`backend/src/main/java/com/dojostay/students/StudentController.java:47`), frontend expects `Student[]` (`frontend/src/api/students.ts:20`), UI binds `status` field while backend DTO uses `enrollmentStatus` (`frontend/src/api/students.ts:15`, `frontend/src/pages/StudentsPage.vue:77`, `backend/src/main/java/com/dojostay/students/dto/StudentResponse.java:25`).
- Impact: likely broken list rendering and status display.

3) **High — Training page contract mismatch (frontend expects fields not present in backend DTO)**
- Evidence: frontend expects `title`, `description`, `locationName` (`frontend/src/api/training.ts:6`, `frontend/src/api/training.ts:7`, `frontend/src/api/training.ts:12`), backend returns `trainingClassId`, `location`, `notes` and no `title/description/locationName` (`backend/src/main/java/com/dojostay/training/dto/TrainingSessionResponse.java:9`, `backend/src/main/java/com/dojostay/training/dto/TrainingSessionResponse.java:13`, `backend/src/main/java/com/dojostay/training/dto/TrainingSessionResponse.java:18`), page checks `ACTIVE` status though backend status enum is `SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED` (`frontend/src/pages/TrainingPage.vue:45`, `backend/src/main/java/com/dojostay/training/TrainingSession.java:24`).
- Impact: likely inconsistent or empty/incorrect UI content.

4) **High — Dashboard remains explicitly placeholder, conflicting with claimed full phase delivery**
- Evidence: comments and user text still identify phase-1 placeholder behavior (`frontend/src/pages/DashboardPage.vue:14`, `frontend/src/pages/DashboardPage.vue:62`).
- Impact: core role-specific UX remains unfinished at main entry route.

### Medium

5) **Medium — Notification unread-count API/client shape mismatch**
- Evidence: backend returns object `{ unread: number }` (`backend/src/main/java/com/dojostay/community/NotificationController.java:33`), frontend client types it as raw number (`frontend/src/api/notifications.ts:21`).
- Impact: latent runtime bug when unread count is consumed.

6) **Medium — Data scope model includes department/facility levels but most services enforce organization-only filtering**
- Evidence: multi-level scope model exists (`backend/src/main/java/com/dojostay/scopes/DataScopeService.java:100`, `backend/src/main/java/com/dojostay/scopes/DataScopeSpec.java:37`), core services predominantly use org-only spec (`backend/src/main/java/com/dojostay/users/UserService.java:67`, `backend/src/main/java/com/dojostay/students/StudentService.java:85`).
- Impact: requirement intent for finer-grained scope may be only partially realized.

7) **Medium — Remediation test set appears partially stale against current service signatures**
- Evidence: `ExportJobService.request` requires `CreateExportJobRequest` (`backend/src/main/java/com/dojostay/ops/ExportJobService.java:58`), but remediation test calls two-arg string overload not present (`tests/backend/feature/com/dojostay/remediation/ExportWatermarkIT.java:49`).
- Impact: static confidence in test suite is reduced; test compilation/execution risk is elevated.

## 6. Security Review Summary
- **Authentication entry points:** **Pass**. Session + CSRF + lockout + blacklist handling are present (`backend/src/main/java/com/dojostay/auth/SecurityConfig.java:67`, `backend/src/main/java/com/dojostay/auth/AuthService.java:95`).
- **Route-level authorization:** **Pass** (static). Broad `@PreAuthorize` coverage across modules (`backend/src/main/java/com/dojostay/students/StudentController.java:46`, `backend/src/main/java/com/dojostay/community/CommunityController.java:34`, `backend/src/main/java/com/dojostay/ops/OpsController.java:72`).
- **Object/data-scope authorization:** **Partial Pass**. Existence-hiding reads are common and org scope leakage in organizations appears remediated (`backend/src/main/java/com/dojostay/organizations/OrganizationController.java:58`, `backend/src/main/java/com/dojostay/organizations/OrganizationController.java:73`).
- **Sensitive-data controls:** **Partial Fail**. Encryption/masking implemented, but plaintext fallback on missing key remains (`backend/src/main/java/com/dojostay/students/Student.java:46`, `backend/src/main/java/com/dojostay/students/StudentService.java:457`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:90`).
- **Internal/admin endpoints:** **Pass** (static). Actuator/admin paths restricted (`backend/src/main/java/com/dojostay/auth/SecurityConfig.java:88`, `backend/src/main/java/com/dojostay/auth/SecurityConfig.java:89`).

## 7. Tests and Logging Review
- **Tests layout:** good externalized structure with backend unit/api/feature and frontend Vitest (`backend/pom.xml:88`, `tests/README.md:6`, `frontend/vite.config.ts:30`).
- **Backend feature tests:** substantial remediation coverage exists (`tests/backend/feature/com/dojostay/remediation/ScopeLeakageIT.java:29`, `tests/backend/feature/com/dojostay/remediation/BookingLifecycleIT.java:42`, `tests/backend/feature/com/dojostay/remediation/CommunityFeaturesIT.java:45`, `tests/backend/feature/com/dojostay/remediation/PropertyRichnessIT.java:40`).
- **Logging/observability:** correlation id pattern and actuator metrics are configured (`backend/src/main/resources/application.yml:49`, `backend/src/main/resources/application.yml:77`).
- **Cannot Confirm Statistically:** actual passing status of the full test suite; static mismatch indicates at least one probable compile/runtime test issue (`tests/backend/feature/com/dojostay/remediation/ExportWatermarkIT.java:49`, `backend/src/main/java/com/dojostay/ops/ExportJobService.java:58`).

## 8. Mandatory Static Test Coverage Audit

### 8.1 Coverage map

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Scope leakage / existence-hiding | `tests/backend/feature/com/dojostay/remediation/ScopeLeakageIT.java:29` | Out-of-scope availability throws `NotFoundException` (`ScopeLeakageIT.java:75`) | basically covered | Focused on property path | Add cross-module scope tests (students/training/community object reads) |
| Booking lifecycle + refund | `tests/backend/feature/com/dojostay/remediation/BookingLifecycleIT.java:42` | INITIATED->CONFIRMED->CANCELED->REFUNDED + invalid transitions (`BookingLifecycleIT.java:103`) | basically covered | No controller-level contract test for `/confirm` and `/refund` | Add API tests for endpoint-level auth/validation |
| Property richness | `tests/backend/feature/com/dojostay/remediation/PropertyRichnessIT.java:40` | amenities/room types/nightly rates create/update behavior | basically covered | No multipart image upload test | Add feature test for image mime/size validation + persistence |
| Community interaction set | `tests/backend/feature/com/dojostay/remediation/CommunityFeaturesIT.java:45` | replies, likes, blocks, restore behaviors | basically covered | Limited mention/mute notification assertions | Add tests for mention parsing and mute/block notification suppression |
| Student import template | `tests/backend/feature/com/dojostay/remediation/StudentImportTemplateIT.java:23` | expected template headers | partially covered | No static test for `/import/{jobId}/errors` download path | Add controller test for persisted error report retrieval + scope checks |
| Export watermark | `tests/backend/feature/com/dojostay/remediation/ExportWatermarkIT.java:26` | watermark shape assertion | insufficient confidence | Test appears stale vs service signature | Align test with `CreateExportJobRequest` API and rerun |
| Credentials upload hardening | none found for multipart upload path | n/a | missing | No test coverage for file mime/size/SHA-256 duplicate/blacklist flow | Add dedicated feature/API tests for `/api/credentials/reviews/upload` + blacklist |
| PII encryption + masking | none found for converter/masked DTO policy | n/a | missing | No explicit tests for encrypted-at-rest write behavior or `students.view-raw` behavior | Add unit tests for converter + API tests for masked vs raw responses |
| Frontend role pages contract correctness | only `tests/frontend/unit/auth.store.spec.ts:1` | auth store behavior only | insufficient | No tests for students/training/community page API contracts | Add Vitest component/integration tests with mocked API envelopes |

### 8.2 Coverage judgment
- Overall static test coverage judgment: **Partial Pass**.
- Backend domain behavior has meaningful coverage, but security-critical and integration-critical paths (PII crypto guarantees, credential upload controls, frontend API contract correctness) are under-tested.

## 9. Final Notes
- This audit is static-only; no runtime success claims are made.
- The codebase has materially progressed versus earlier baseline expectations, but acceptance is blocked by production-safety and integration-correctness gaps.
- Recommended fix order:
  1. Fail-fast encryption behavior for non-dev profiles (remove plaintext fallback in production).
  2. Reconcile frontend API typings/pages with backend DTO shapes (students, training, notifications).
  3. Add/repair tests for encryption policy, credential multipart controls, and updated remediation paths.
