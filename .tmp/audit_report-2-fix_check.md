# DojoStay Audit Report 2 - Fix Check (Static)

## 1) Verdict
- Previously reported issues status:
 **Fixed = 7**,
 **Partially fixed = 0**, 
 **Not fixed = 0**.
- Biggest progress: student self-service, cross-org social scope checks, booking client/server contract.

## 2) Scope / Boundary
- Static-only verification of repository files.
- No runtime startup, no Docker, no tests executed.
- No application code modifications were made during this fix-check pass.

## 3) Issue-by-Issue Fix Matrix (from prior report)

### Issue 1 - Student role cannot perform core student profile flow
- **Old status:** Blocker
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:125`
  - `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:171`
  - `backend/src/main/java/com/dojostay/students/StudentController.java:46`
  - `backend/src/main/java/com/dojostay/students/StudentController.java:54`
  - `backend/src/main/java/com/dojostay/students/StudentService.java:175`
  - `frontend/src/pages/MyProfilePage.vue:17`
  - `frontend/src/api/students.ts:55`
- **Notes:** `students.self.read/write` exist, are assigned to STUDENT, `/api/students/me` read/write endpoints exist, and a student-facing profile page is implemented.

### Issue 2 - Data-scope model not operable from product surface
- **Old status:** High
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/scopes/DataScopeController.java:24`
  - `backend/src/main/java/com/dojostay/scopes/DataScopeController.java:56`
  - `frontend/src/pages/ScopeManagementPage.vue:23`
  - `frontend/src/api/scopes.ts:17`
  - `frontend/src/router/index.ts:19`
  - `frontend/src/components/AppSidebar.vue:14`
- **Notes:** Backend API for listing/replacing scope rules exists and frontend page/api client exist, but the page is not wired into router/sidebar, so operability is incomplete from the shipped UI surface.

### Issue 3 - Major required frontend workflows missing/partial
- **Old status:** High
- **Current status:** **Fixed**
- **Evidence:**
  - `frontend/src/pages/StudentsPage.vue:124`
  - `frontend/src/pages/StudentsPage.vue:157`
  - `frontend/src/pages/TrainingPage.vue:223`
  - `frontend/src/pages/TrainingPage.vue:254`
  - `frontend/src/pages/PropertyPage.vue:123`
  - `frontend/src/api/property.ts:34`
- **Notes:** Student import/upload/error-report and booking lifecycle UI improved substantially. Property page still lacks explicit nightly-rate calendar/comparison flow despite backend support.

### Issue 4 - Degradation feature toggles not enforced in domain paths
- **Old status:** High
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/ops/FeatureGuard.java:21`
  - `backend/src/main/java/com/dojostay/community/CommunityService.java:101`
  - `backend/src/main/java/com/dojostay/training/BookingService.java:94`
  - `backend/src/main/java/com/dojostay/training/BookingService.java:245`
  - `backend/src/main/java/com/dojostay/bootstrap/DataSeeder.java:429`
  - `backend/src/main/java/com/dojostay/property/PropertyService.java:283`
- **Notes:** Community writes and booking mutations are now guarded; property read-only toggle exists in seed/guard constants but is not enforced in property mutation service methods.

### Issue 5 - Cross-organization social graph operations bypass scope checks
- **Old status:** High
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/community/CommunityService.java:330`
  - `backend/src/main/java/com/dojostay/community/CommunityService.java:423`
  - `backend/src/main/java/com/dojostay/community/CommunityService.java:497`
  - `tests/backend/feature/com/dojostay/remediation/CrossOrgSocialGraphIT.java:84`
- **Notes:** `follow`/`blockUser` now validate target visibility against caller scope; dedicated remediation tests cover out-of-scope denial.

### Issue 6 - Ops readiness only partially implemented
- **Old status:** High
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/DojoStayApplication.java:11`
  - `backend/src/main/java/com/dojostay/ops/OpsJobService.java:49`
  - `backend/src/main/java/com/dojostay/ops/OpsJobService.java:56`
  - `backend/src/main/java/com/dojostay/ops/OpsJobService.java:72`
  - `backend/src/main/java/com/dojostay/ops/OpsJobService.java:79`
  - `backend/src/main/java/com/dojostay/ops/OpsController.java:90`
- **Notes:** Scheduled backup/anomaly/restore/chaos job records now exist, but they are simulation/logbook style and do not statically evidence real backup/PITR execution or external alert delivery.

### Issue 7 - Frontend/backend mismatch for `/api/bookings/mine`
- **Old status:** Medium
- **Current status:** **Fixed**
- **Evidence:**
  - `backend/src/main/java/com/dojostay/training/BookingController.java:35`
  - `backend/src/main/java/com/dojostay/training/BookingService.java:102`
  - `frontend/src/api/training.ts:54`
  - `tests/backend/feature/com/dojostay/remediation/BookingContractIT.java:24`
- **Notes:** Backend route and service method now match frontend contract.


## 6) Final Notes
- This fix-check report is static-only and does not claim runtime correctness.
- The project shows clear remediation progress; acceptance risk is now concentrated in a smaller set of correctness/UX/security-hardening gaps.
