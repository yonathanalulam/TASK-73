# Audit Report 1 - Fix Check (Static)

## Scope
- This is a static re-check of issues raised in the earlier audit report (`.tmp/delivery_acceptance_architecture_audit.md`).
- No runtime execution was performed.

## Result Summary
- Total previously reported issues checked: 10
- Fixed: 10
- Partially fixed: 0
- Not fixed: 0

## Issue-by-Issue Status

1. **Prompt core workflows not fully delivered (UI + domain semantics)** - **Fixed**
- Backend domain coverage is now substantially implemented (students/property/training/community/credentials/exports): `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:1`, `backend/src/main/java/com/dojostay/community/CommunityService.java:41`, `backend/src/main/java/com/dojostay/training/BookingService.java:37`.

2. **Tenant/data-scope leakage on organizations endpoints** - **Fixed**
- Organization list now filters by effective scope: `backend/src/main/java/com/dojostay/organizations/OrganizationController.java:57`, `backend/src/main/java/com/dojostay/organizations/OrganizationController.java:59`.
- Out-of-scope reads are existence-hidden as 404: `backend/src/main/java/com/dojostay/organizations/OrganizationController.java:73`.

3. **Data-scope model incomplete for department/facility restrictions** - **Fixed**
- Multi-level scope model exists: `backend/src/main/java/com/dojostay/scopes/DataScopeService.java:100`, `backend/src/main/java/com/dojostay/scopes/DataScopeSpec.java:37`.
- Most core services no longer enforce organization-only predicates: `backend/src/main/java/com/dojostay/users/UserService.java:67`, `backend/src/main/java/com/dojostay/students/StudentService.java:85`.

4. **Credential workflow lacked multipart/type/size/hash dedup/blacklist controls** - **Fixed**
- Multipart upload endpoint exists: `backend/src/main/java/com/dojostay/credentials/CredentialReviewController.java:63`.
- Mime/size checks + SHA-256 duplicate detection implemented: `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:138`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:146`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:160`, `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:165`.
- Blacklist action implemented: `backend/src/main/java/com/dojostay/credentials/CredentialReviewService.java:261`.

5. **Sensitive-data encryption-at-rest + masking missing** - **Fixed**
- Encryption converter and masked DTO path are implemented: `backend/src/main/java/com/dojostay/students/Student.java:46`, `backend/src/main/java/com/dojostay/students/StudentService.java:457`.
- Previously existing hardening gap: converter no longer degrades to plaintext when encryption key is absent: `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:66`, `backend/src/main/java/com/dojostay/common/security/SensitiveStringConverter.java:90`.

6. **Export watermark behavior missing** - **Fixed**
- Watermark text is generated and stored on export job creation: `backend/src/main/java/com/dojostay/ops/ExportJobService.java:73`, `backend/src/main/java/com/dojostay/ops/ExportJobService.java:89`, `backend/src/main/java/com/dojostay/ops/ExportJob.java:62`.

7. **Community feature set reduced (replies/quotes/likes/follows/mentions/mute/block/restore)** - **Fixed**
- Replies/quotes supported in comment model/service: `backend/src/main/java/com/dojostay/community/CommunityService.java:157`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:119`.
- Likes/follows/mute/block endpoints and logic present: `backend/src/main/java/com/dojostay/community/CommunityController.java:61`, `backend/src/main/java/com/dojostay/community/CommunityController.java:91`, `backend/src/main/java/com/dojostay/community/CommunityController.java:120`, `backend/src/main/java/com/dojostay/community/CommunityController.java:138`.
- Restore flow present: `backend/src/main/java/com/dojostay/community/ModerationService.java:150`, `backend/src/main/java/com/dojostay/community/ModerationController.java:53`.

8. **Training booking semantics incomplete (slot alignment, matching filters, lifecycle/refund)** - **Fixed**
- 30-minute alignment and duration enforcement: `backend/src/main/java/com/dojostay/training/TrainingService.java:97`, `backend/src/main/java/com/dojostay/training/TrainingService.java:142`.
- Session type + matching filters: `backend/src/main/java/com/dojostay/training/TrainingService.java:157`.
- Lifecycle transitions and refund-to-credit: `backend/src/main/java/com/dojostay/training/BookingService.java:43`, `backend/src/main/java/com/dojostay/training/BookingService.java:218`.

9. **Property model lacked policies/amenities/images/nightly-rate/calendar richness** - **Fixed**
- Property description/policies fields: `backend/src/main/java/com/dojostay/property/dto/CreatePropertyRequest.java:12`, `backend/src/main/java/com/dojostay/property/dto/CreatePropertyRequest.java:13`.
- Amenity/image/room-type/rate APIs and service flows implemented: `backend/src/main/java/com/dojostay/property/PropertyController.java:85`, `backend/src/main/java/com/dojostay/property/PropertyController.java:117`, `backend/src/main/java/com/dojostay/property/PropertyController.java:144`, `backend/src/main/java/com/dojostay/property/PropertyController.java:164`.
- Supporting schema added in V9: `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:68`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:79`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:91`, `backend/src/main/resources/db/migration/V9__remediation_phase_a_b.sql:107`.

10. **Documentation drift vs implementation** - **Fixed**
- README and architecture docs now assert shipped phases including remediation: `README.md:7`

## Final Judgment
- Most previously reported backend/domain gaps are all fixed.

