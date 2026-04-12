# DojoStay Static Delivery Acceptance & Architecture Audit

## 1. Verdict
- Overall conclusion: **Partial Pass**.
- Basis: multiple **Blocker/High** gaps against core Prompt requirements (student self-service accessibility, data-scope operability, degradation toggles enforcement, major frontend flow completeness), plus material test-quality defects that reduce static confidence.

## 2. Scope and Static Verification Boundary
- Reviewed: repository docs/config (`README.md`, `docs/*`), backend structure/controllers/services/security/migrations, frontend routes/pages/api clients, and test sources under `tests/`.
- Not reviewed/executed: runtime boot, HTTP live behavior, browser interaction, DB connectivity, Docker, external integrations, or command execution beyond static file inspection.
- Intentionally not executed: project startup, tests, Docker, and any runtime validation.
- Manual verification required for runtime-dependent claims: actual offline behavior under network loss, session persistence/csrf behavior across browsers, export artifact watermark rendering, backup/restore operations, and operational alerting behavior.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal mapped: offline-first on-prem operations/community platform with 4 roles, student/profile+import workflows, sparring/booking lifecycle, lodging availability+rate calendar, social/notification/moderation, offline auth+RBAC+data scopes, risk/credential review, encryption/masking, and ops readiness.
- Main implementation areas mapped: Spring Boot backend modules (`auth`, `users`, `students`, `training`, `property`, `community`, `credentials`, `risk`, `ops`, `scopes`), Vue SPA role-routed pages, Flyway schema `V1`-`V10`, and backend/frontend tests.
- Primary mismatch pattern: backend has substantial domain surface, but several critical Prompt flows are partial/inaccessible in-role, and frontend coverage is materially incomplete for explicit business flows.

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: **Partial Pass**.
- Rationale: startup/test docs and structure are present and mostly consistent with code (`README.md:11`, `docs/deployment-local.md:42`, `backend/pom.xml:88`, `frontend/vite.config.ts:27`).
- Evidence: `README.md:51`, `docs/deployment-local.md:83`, `tests/README.md:19`, `backend/src/main/resources/application.yml:1`.
- Manual verification note: runtime boot/health/test pass still requires manual execution.

#### 1.2 Material deviation from Prompt
- Conclusion: **Partial Pass**.
- Rationale: critical Prompt outcomes are only partially delivered (student self-service accessibility, full role-tailored UX depth, degradation controls enforcement, operational readiness breadth).
- Evidence: `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:161`, `backend/src/main/java/com/dojostay/students/StudentController.java:46`, `frontend/src/pages/StudentsPage.vue:67`, `frontend/src/pages/TrainingPage.vue:56`, `frontend/src/pages/PropertyPage.vue:26`, `backend/src/main/java/com/dojostay/ops/FeatureToggleService.java:61`.

### 2. Delivery Completeness

#### 2.1 Core explicit requirements coverage
- Conclusion: **Partial Pass**.
- Rationale: A few explicit core flows are incomplete or missing in delivered UX and/or effective role access.
- Evidence:
  - Property comparison/calendar UX missing: `frontend/src/pages/PropertyPage.vue:29`, `frontend/src/api/property.ts:34`.
  - Degradation toggles not enforced in domain flows: `backend/src/main/java/com/dojostay/ops/FeatureToggleService.java:61`, `backend/src/main/java/com/dojostay/community/CommunityService.java:95`.

#### 2.2 End-to-end deliverable vs partial/demo
- Conclusion: **Partial Pass**.
- Rationale: repository is multi-module and non-trivial, with real persistence/auth/services/tests; however, several business-critical flows remain partial from end-user perspective.
- Evidence: `README.md:14`, `backend/src/main/java/com/dojostay/DojoStayApplication.java:10`, `frontend/src/router/index.ts:19`, `tests/backend/feature/com/dojostay/e2e/FullStackJourneyIT.java:63`.

### 3. Engineering and Architecture Quality

#### 3.1 Engineering structure/module decomposition
- Conclusion: **Pass**.
- Rationale: backend module decomposition is coherent by domain/security/ops; frontend organized by pages/components/api/store.
- Evidence: `backend/src/main/java/com/dojostay`, `frontend/src`, `docs/architecture.md:197`.

#### 3.2 Maintainability/extensibility
- Conclusion: **Partial Pass**.
- Rationale: generally maintainable service layering and DTO usage; but key cross-cutting controls (feature toggles/data-scope administration) are not integrated through end-to-end control paths.
- Evidence: `backend/src/main/java/com/dojostay/scopes/DataScopeService.java:73`, `backend/src/main/java/com/dojostay/ops/FeatureToggleService.java:39`, `backend/src/main/java/com/dojostay/community/CommunityService.java:95`.

### 4. Engineering Details and Professionalism

#### 4.1 Error handling/logging/validation/API design
- Conclusion: **Partial Pass**.
- Rationale: strong baseline exists (global error envelope, correlation IDs, validation annotations, audit logs, rate-limiting filter), but operational controls/alerts and some role-UX/API alignment gaps reduce professionalism.
- Evidence: `backend/src/main/java/com/dojostay/common/GlobalExceptionHandler.java:31`, `backend/src/main/java/com/dojostay/common/CorrelationIdFilter.java:31`, `backend/src/main/java/com/dojostay/common/RateLimitFilter.java:65`, `backend/src/main/java/com/dojostay/students/dto/CreateStudentRequest.java:11`.
- Manual verification note: effective alerting/metrics quality beyond exposed actuator endpoints requires runtime ops validation.

#### 4.2 Real product/service vs demo
- Conclusion: **Partial Pass**.
- Rationale: backend resembles a real service, but frontend still omits multiple explicit operational workflows and some test artifacts appear weak/stale.
- Evidence: `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:118`, `frontend/src/pages/StudentsPage.vue:67`, `tests/backend/unit/com/dojostay/remediation/SlotAlignmentTest.java:29`.

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Business goal/constraints fit
- Conclusion: **Partial Pass**.
- Rationale: implementation shows understanding of many technical slices,and enforces key business semantics: accessible student self-service role behavior, comprehensive role-tailored UX flows, and enforceable degradation-readonly controls.
- Evidence: `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:161`, `backend/src/main/java/com/dojostay/training/BookingService.java:258`, `frontend/src/pages/DashboardPage.vue:150`, `frontend/src/pages/PropertyPage.vue:29`.

### 6. Aesthetics (frontend)

#### 6.1 Visual/interaction quality fit
- Conclusion: **Partial Pass**.
- Rationale: UI has consistent spacing/states/basic feedback, but interaction depth for required workflows is limited and several pages are mostly read-only list views.
- Evidence: `frontend/src/styles/global.css`, `frontend/src/pages/StudentsPage.vue:77`, `frontend/src/pages/TrainingPage.vue:67`, `frontend/src/pages/PropertyPage.vue:34`, `frontend/src/pages/CommunityPage.vue:100`.
- Manual verification note: responsive behavior and visual polish under real browser/device conditions require manual check.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker / High

1) **Severity: Blocker**
- Title: Student role cannot perform core student profile flow
- Conclusion: **Fail**
- Evidence: `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:161`, `backend/src/main/java/com/dojostay/students/StudentController.java:46`, `backend/src/main/java/com/dojostay/students/StudentController.java:58`
- Impact: Prompt explicitly requires students/customers to maintain profile/master data; seeded permissions deny student access to student endpoints.
- Minimum actionable fix: add explicit self-profile endpoints/permissions (e.g., `students.self.read/write`) or grant constrained student access with object-level ownership checks.

2) **Severity: High**
- Title: Data-scope model is not operable for non-admin roles from product surface
- Conclusion: **Fail**
- Evidence: `backend/src/main/java/com/dojostay/scopes/DataScopeService.java:44`, `backend/src/main/java/com/dojostay/scopes/DataScopeService.java:73`, `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:376`, `backend/src/main/java/com/dojostay/training/BookingService.java:261`
- Impact: Non-admin access depends on scope rules, but no reviewed controller/API exists to manage scope rules; only sample staff gets seeded scope, risking inaccessible core flows for real users.
- Minimum actionable fix: add admin-managed scope-rule API/UI and enforce onboarding assignment for STAFF/STUDENT/PHOTOGRAPHER where required.

3) **Severity: High**
- Title: Major required frontend workflows are missing/partial
- Conclusion: **Fail**
- Evidence: `frontend/src/pages/StudentsPage.vue:67`, `frontend/src/api/students.ts:28`, `frontend/src/pages/TrainingPage.vue:56`, `frontend/src/pages/PropertyPage.vue:26`
- Impact: Prompt-required flows (inline profile edits, bulk import upload/report handling, booking lifecycle actions/conflict guidance, room-type/rate calendar comparison UX) are not fully deliverable through current UI.
- Minimum actionable fix: implement student edit/import pages, booking creation/confirm/cancel/refund UI with conflict pre-check display, and property availability/rate-calendar comparison screens.

4) **Severity: High**
- Title: Degradation feature toggles are not enforced in domain paths
- Conclusion: **Fail**
- Evidence: `backend/src/main/java/com/dojostay/ops/FeatureToggleService.java:61`, `backend/src/main/java/com/dojostay/community/CommunityService.java:95`, `backend/src/main/java/com/dojostay/training/BookingController.java:45`
- Impact: Prompt requires one-click degradation (e.g., disable community while keeping booking read-only); toggles exist as data but are not applied to request handling.
- Minimum actionable fix: add centralized toggle guard/interceptor and enforce toggle checks in community/training/property mutation endpoints.

5) **Severity: High**
- Title: Cross-organization social graph operations bypass data-scope checks
- Conclusion: **Fail**
- Evidence: `backend/src/main/java/com/dojostay/community/CommunityService.java:314`, `backend/src/main/java/com/dojostay/community/CommunityService.java:405`
- Impact: `follow`/`block` operate by raw `userId` existence without org/scope validation, weakening tenant/data-scope isolation expectations.
- Minimum actionable fix: require scope/org accessibility checks (or explicit cross-org policy) before follow/block actions and add tests for out-of-scope user IDs.

6) **Severity: High**
- Title: Operational readiness requirements only partially implemented
- Conclusion: **Fail**
- Evidence: `backend/src/main/java/com/dojostay/ops/BackupStatusService.java:15`, `backend/src/main/java/com/dojostay/DojoStayApplication.java:11`, `backend/src/main/java/com/dojostay/ops/OpsController.java:57`
- Impact: Current code records backup/export metadata but does not evidence scheduled backups with PITR drills, anomaly alerts, or chaos-drill automation requested in Prompt.
- Minimum actionable fix: add scheduler-backed backup jobs/verification records, anomaly detection+alerting services, and explicit chaos/degradation drill endpoints/workflows.

### Medium / Low

7) **Severity: Medium**
- Title: Frontend-backend contract mismatch for bookings endpoint
- Conclusion: **Fail**
- Evidence: `frontend/src/api/training.ts:44`, `backend/src/main/java/com/dojostay/training/BookingController.java:31`
- Impact: `trainingApi.listMyBookings()` calls `/api/bookings/mine`, but backend has no such route; indicates dead/incorrect client contract.
- Minimum actionable fix: add `/api/bookings/mine` endpoint (scoped to current user) or remove/replace client call.


## 6. Security Review Summary

- authentication entry points: **Pass**
  - Evidence: `backend/src/main/java/com/dojostay/auth/AuthController.java:61`, `backend/src/main/java/com/dojostay/auth/AuthService.java:70`, `backend/src/main/java/com/dojostay/auth/LockoutService.java:69`, `backend/src/main/java/com/dojostay/auth/SecurityConfig.java:67`.
  - Reasoning: session+csrf flow, password policy, lockout, blacklist checks are explicitly implemented.

- route-level authorization: **Partial Pass**
  - Evidence: `backend/src/main/java/com/dojostay/training/BookingController.java:32`, `backend/src/main/java/com/dojostay/community/CommunityController.java:34`, `backend/src/main/java/com/dojostay/ops/OpsController.java:44`.
  - Reasoning: broad `@PreAuthorize` coverage exists; some UX/API permission mismatches remain.

- object-level authorization: **Partial Pass**
  - Evidence: `backend/src/main/java/com/dojostay/users/UserService.java:158`, `backend/src/main/java/com/dojostay/students/StudentService.java:363`, `backend/src/main/java/com/dojostay/property/PropertyService.java:446`.
  - Reasoning: many object loads enforce existence-hiding scope checks; however, community follow/block paths skip org/scope checks.

- function-level authorization: **Partial Pass**
  - Evidence: `backend/src/main/java/com/dojostay/auth/SecurityConfig.java:81`, `backend/src/main/java/com/dojostay/roles/RoleController.java:33`.
  - Reasoning: method-level guards are common; business-level enforcement not uniformly aligned with Prompt semantics (degradation toggles absent).

- tenant / user data isolation: **Partial Pass**
  - Evidence: `backend/src/main/java/com/dojostay/scopes/DataScopeSpec.java:67`, `backend/src/main/java/com/dojostay/scopes/DataScopeService.java:98`, `backend/src/main/java/com/dojostay/community/CommunityService.java:314`.
  - Reasoning: design is deny-by-default and scope-based, but practical operability gaps and cross-user follow/block checks weaken isolation guarantees.

- admin / internal / debug protection: **Pass**
  - Evidence: `backend/src/main/java/com/dojostay/auth/SecurityConfig.java:88`, `backend/src/main/java/com/dojostay/auth/SecurityConfig.java:89`.
  - Reasoning: `/actuator/**` and `/api/admin/**` are role-gated; health/info allowlisted.

## 7. Tests and Logging Review

- Unit tests: **Partial Pass**
  - Evidence: `tests/backend/unit/com/dojostay/auth/PasswordPolicyValidatorTest.java:20`, `tests/backend/unit/com/dojostay/auth/LockoutServiceTest.java:68`, `tests/backend/unit/com/dojostay/remediation/SlotAlignmentTest.java:21`.
  - Reasoning: key units exist, but some are low-signal (slot alignment) and do not validate production behavior adequately.

- API / integration tests: **Partial Pass**
  - Evidence: `tests/backend/api/com/dojostay/auth/AuthControllerIT.java:78`, `tests/backend/api/com/dojostay/users/UserControllerIT.java:109`, `tests/backend/feature/com/dojostay/e2e/FullStackJourneyIT.java:159`.
  - Reasoning: substantial service-level/MockMvc coverage exists, but many feature tests use admin context and miss realistic 401/403/object-scope-negative paths for key modules.

- Logging categories / observability: **Partial Pass**
  - Evidence: `backend/src/main/resources/application.yml:71`, `backend/src/main/java/com/dojostay/common/CorrelationIdFilter.java:39`, `backend/src/main/java/com/dojostay/audit/AuditService.java:26`.
  - Reasoning: correlation IDs + audit events + actuator metrics are present; full operational observability (alerts/drills) is not statically evidenced.

- Sensitive-data leakage risk in logs / responses: **Partial Pass (Suspected Risk)**
  - Evidence: `backend/src/main/java/com/dojostay/students/StudentService.java:458`, `backend/src/main/java/com/dojostay/common/GlobalExceptionHandler.java:79`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:214`.
  - Reasoning: masking and generic error envelopes reduce risk; some audit summaries include detailed identifiers/fingerprints and should be reviewed under least-disclosure policy.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests: present under `tests/backend/unit` and `tests/frontend/unit`.
- API/integration tests: present under `tests/backend/api` and `tests/backend/feature`.
- Frameworks: JUnit5/Spring Boot Test/MockMvc/Vitest (`backend/pom.xml:69`, `frontend/package.json:10`).
- Test entry points documented: `tests/README.md:25`, `docs/deployment-local.md:83`.
- Evidence: `backend/pom.xml:88`, `frontend/vite.config.ts:27`, `tests/README.md:21`.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth login/lockout baseline | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java:85` | 5 failures then 423 `ACCOUNT_LOCKED` at `tests/backend/api/com/dojostay/auth/AuthControllerIT.java:142` | basically covered | No blacklist-path API assertion | Add `ACCOUNT_BLACKLISTED` login test via blacklisted user fixture |
| Password policy enforcement | `tests/backend/unit/com/dojostay/auth/PasswordPolicyValidatorTest.java:20` | strong password accepted `:52`, weak variants rejected | sufficient | No controller-level weak-password response validation | Add API test for `/api/auth/change-password` weak path |
| Data-scope behavior | `tests/backend/feature/com/dojostay/users/UserScopeFilteringIT.java:127` | org-scoped visibility assertions `:140-148` | basically covered | Coverage concentrated on users/property; limited cross-module 404-for-out-of-scope APIs | Add API-level scope tests for training/community/risk endpoints |
| Student import template/report | `tests/backend/feature/com/dojostay/remediation/StudentImportTemplateIT.java:43` | header assertions `:46-54` | insufficient | No HTTP-level upload+error-report download security/ownership checks | Add MockMvc tests for `/api/students/import` and `/import/{jobId}/errors` incl out-of-scope 404 |
| Booking lifecycle/conflict/refund credit | `tests/backend/feature/com/dojostay/remediation/BookingLifecycleIT.java:103`, `tests/backend/feature/com/dojostay/training/BookingConflictIT.java:99` | status transitions/asserted business codes | basically covered | Lacks route-level auth/403 and student-scope realism | Add controller tests for booking endpoints with STUDENT/STAFF forbidden/allowed permutations |
| Community interactions/moderation | `tests/backend/feature/com/dojostay/remediation/CommunityFeaturesIT.java:80`, `tests/backend/feature/com/dojostay/community/ModerationFlowIT.java:62` | threaded reply/like/block/restore assertions | basically covered | Tests mostly run as admin-like user; limited out-of-scope/403 checks | Add non-admin scoped user tests for report/restore/follow/block boundaries |
| Property availability/rates | `tests/backend/feature/com/dojostay/property/AvailabilityIT.java:88`, `tests/backend/feature/com/dojostay/remediation/PropertyRichnessIT.java:132` | overlap and nightly rate upsert assertions | basically covered | No frontend flow tests for calendar comparison UI | Add frontend component tests for property rate/calendar rendering |
| Ops toggles/backups/exports | `tests/backend/feature/com/dojostay/ops/OpsReadinessIT.java:58`, `tests/backend/feature/com/dojostay/remediation/ExportWatermarkIT.java:50` | toggle upsert and watermark text checks | insufficient | No enforcement tests that toggles actually gate features | Add integration tests verifying toggles disable community writes / booking mutability |
| Slot alignment rule | `tests/backend/unit/com/dojostay/remediation/SlotAlignmentTest.java:21` | only arithmetic on literals; no call into service | missing | Does not validate production method behavior | Add tests through `TrainingService.createSession` with aligned/misaligned timestamps |

### 8.3 Security Coverage Audit
- authentication: **Basically covered**
  - Evidence: `tests/backend/api/com/dojostay/auth/AuthControllerIT.java:79`, `tests/backend/api/com/dojostay/auth/AuthControllerIT.java:116`.
- route authorization: **Partially covered**
  - Evidence: `tests/backend/api/com/dojostay/users/UserControllerIT.java:116`, `tests/backend/api/com/dojostay/students/StudentControllerIT.java:102`.
  - Gap: sparse 403 checks for community/risk/ops/property/training controller routes.
- object-level authorization: **Partially covered**
  - Evidence: `tests/backend/feature/com/dojostay/remediation/ScopeLeakageIT.java:75`.
  - Gap: broad object-scope negative tests across all modules are limited.
- tenant / data isolation: **Partially covered**
  - Evidence: `tests/backend/feature/com/dojostay/users/UserScopeFilteringIT.java:127`.
  - Gap: no explicit tests for cross-org follow/block or student role scope boundaries.
- admin / internal protection: **Cannot Confirm Statistically**
  - Evidence: no direct MockMvc tests found for `/actuator/**` admin-only path.
  - Gap: severe defects here could remain undetected despite current test pass.

### 8.4 Final Coverage Judgment
- **Partial Pass**
- Covered major risks: basic auth lockout path, core booking/property/community service behaviors, some scope filtering.
- Uncovered/high-risk gaps: route/authz breadth, object-level/tenant isolation edge cases, toggle enforcement, and weak tests that do not assert production logic deeply enough; severe defects could still remain while current tests pass.

## 9. Final Notes
- This audit is static-only; no runtime success claim is made.
- Key blockers are requirement-fit and operability gaps rather than codebase size/structure.
- Highest-value next actions are: (1) unblock student/self-service access model, (2) wire scope administration + toggle enforcement, (3) complete missing UI workflows, (4) strengthen security/authz negative-path tests.
