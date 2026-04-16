# Test Coverage Audit

Audit mode: static inspection only (no test run, no build, no runtime execution).

## Backend Endpoint Inventory

Endpoint extraction source: Spring controller mappings in `backend/src/main/java/com/dojostay/**/*Controller.java`.

Total resolved endpoints: **102**

- Auth: 6 (`backend/src/main/java/com/dojostay/auth/AuthController.java`)
- Users: 5 (`backend/src/main/java/com/dojostay/users/UserController.java`)
- Roles/Permissions: 2 (`backend/src/main/java/com/dojostay/roles/RoleController.java`)
- Organizations: 4 (`backend/src/main/java/com/dojostay/organizations/OrganizationController.java`)
- Students: 9 (`backend/src/main/java/com/dojostay/students/StudentController.java`)
- Property: 16 (`backend/src/main/java/com/dojostay/property/PropertyController.java`)
- Training: 7 (`backend/src/main/java/com/dojostay/training/TrainingController.java`)
- Bookings: 6 (`backend/src/main/java/com/dojostay/training/BookingController.java`)
- Credits: 3 (`backend/src/main/java/com/dojostay/training/CreditController.java`)
- Scopes: 3 (`backend/src/main/java/com/dojostay/scopes/DataScopeController.java`)
- Community: 17 (`backend/src/main/java/com/dojostay/community/CommunityController.java`)
- Moderation: 4 (`backend/src/main/java/com/dojostay/community/ModerationController.java`)
- Notifications: 3 (`backend/src/main/java/com/dojostay/community/NotificationController.java`)
- Credentials: 5 (`backend/src/main/java/com/dojostay/credentials/CredentialReviewController.java`)
- Risk: 5 (`backend/src/main/java/com/dojostay/risk/RiskController.java`)
- Ops: 9 (`backend/src/main/java/com/dojostay/ops/OpsController.java`)

## API Test Mapping Table

Test type classification key:
- `true no-mock HTTP`: request through Spring HTTP layer (`MockMvc` + `@SpringBootTest`) with no mocked execution-path dependencies in API test files
- `HTTP with mocking`: HTTP route with execution-path mocks
- `unit-only / indirect`: no HTTP request to route

### Auth (`tests/backend/api/com/dojostay/auth/AuthControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/auth/csrf` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#csrf_endpoint_returns_200_without_session` |
| POST `/api/auth/login` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#login_with_valid_credentials_returns_user_envelope` |
| POST `/api/auth/logout` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#logout_with_session_returns_success` |
| GET `/api/auth/me` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#me_without_session_returns_401` |
| POST `/api/auth/change-password` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#change_password_with_valid_session_succeeds` |
| POST `/api/auth/unlock/{userId}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/auth/AuthControllerIT.java` | `AuthControllerIT#unlock_as_admin_succeeds` |

### Users (`tests/backend/api/com/dojostay/users/UserControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/users` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/users/UserControllerIT.java` | `UserControllerIT#list_as_admin_returns_all_users_in_envelope` |
| GET `/api/users/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/users/UserControllerIT.java` | `UserControllerIT#get_user_by_id_as_admin_returns_user` |
| POST `/api/users` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/users/UserControllerIT.java` | `UserControllerIT#create_user_as_admin_succeeds` |
| PUT `/api/users/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/users/UserControllerIT.java` | `UserControllerIT#update_user_as_admin_succeeds` |
| PUT `/api/users/{id}/roles` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/users/UserControllerIT.java` | `UserControllerIT#assign_roles_as_admin_updates_user_and_writes_audit_rows` |

### Roles/Permissions (`tests/backend/api/com/dojostay/roles/RoleControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/roles` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/roles/RoleControllerIT.java` | `RoleControllerIT#list_roles_as_admin_returns_roles_with_permissions` |
| GET `/api/permissions` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/roles/RoleControllerIT.java` | `RoleControllerIT#list_permissions_as_admin_returns_permission_catalog` |

### Organizations (`tests/backend/api/com/dojostay/organizations/OrganizationControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/organizations` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/organizations/OrganizationControllerIT.java` | `OrganizationControllerIT#list_orgs_as_admin_returns_all_orgs` |
| GET `/api/organizations/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/organizations/OrganizationControllerIT.java` | `OrganizationControllerIT#get_org_by_id_as_admin_returns_org` |
| POST `/api/organizations` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/organizations/OrganizationControllerIT.java` | `OrganizationControllerIT#create_org_as_admin_succeeds` |
| PUT `/api/organizations/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/organizations/OrganizationControllerIT.java` | `OrganizationControllerIT#update_org_as_admin_succeeds` |

### Students (`tests/backend/api/com/dojostay/students/StudentControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/students/me` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#student_me_without_self_read_returns_403` |
| PUT `/api/students/me` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#update_my_profile_with_valid_student_succeeds` |
| GET `/api/students` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#admin_can_create_and_list_students` |
| GET `/api/students/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#get_student_by_id_as_admin_returns_student` |
| POST `/api/students` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#admin_can_create_and_list_students` |
| PUT `/api/students/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#update_student_as_admin_succeeds` |
| POST `/api/students/import` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#csv_bulk_import_creates_rows_and_reports_skips` |
| GET `/api/students/import/template` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#import_template_as_admin_returns_csv` |
| GET `/api/students/import/{jobId}/errors` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/students/StudentControllerIT.java` | `StudentControllerIT#import_errors_without_auth_returns_401` |

### Property (`tests/backend/api/com/dojostay/property/PropertyControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/property` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#list_properties_as_admin_returns_properties` |
| POST `/api/property` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#create_property_as_admin_succeeds` |
| GET `/api/property/{id}/availability` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#availability_returns_bed_info` |
| POST `/api/property/reservations` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#create_reservation_as_admin_succeeds` |
| DELETE `/api/property/reservations/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#cancel_reservation_as_admin_succeeds` |
| GET `/api/property/{id}/amenities` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#list_amenities_returns_empty_initially` |
| PUT `/api/property/{id}/amenities` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#upsert_amenity_succeeds` |
| DELETE `/api/property/{id}/amenities/{code}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#remove_amenity_succeeds` |
| GET `/api/property/{id}/images` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#list_images_returns_list` |
| POST `/api/property/{id}/images` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#upload_image_succeeds` |
| DELETE `/api/property/images/{imageId}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#delete_image_succeeds` |
| GET `/api/property/{id}/room-types` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#list_room_types_returns_types` |
| POST `/api/property/{id}/room-types` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#create_room_type_succeeds` |
| PUT `/api/property/room-types/{roomTypeId}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#update_room_type_succeeds` |
| GET `/api/property/room-types/{roomTypeId}/rates` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#list_nightly_rates_returns_list` |
| PUT `/api/property/room-types/{roomTypeId}/rates` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/property/PropertyControllerIT.java` | `PropertyControllerIT#upsert_nightly_rate_succeeds` |

### Training (`tests/backend/api/com/dojostay/training/TrainingControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/training/classes` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#list_classes_as_admin_returns_classes` |
| POST `/api/training/classes` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#create_class_as_admin_succeeds` |
| GET `/api/training/classes/{id}/sessions` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#list_sessions_for_class_returns_sessions` |
| GET `/api/training/sessions` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#filter_sessions_returns_results` |
| POST `/api/training/sessions` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#create_session_and_cancel_lifecycle` |
| DELETE `/api/training/sessions/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/TrainingControllerIT.java` | `TrainingControllerIT#create_session_and_cancel_lifecycle` |

### Bookings (`tests/backend/api/com/dojostay/training/BookingControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/bookings/mine` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#my_bookings_as_admin_returns_list` |
| GET `/api/bookings` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#list_bookings_by_session_returns_list` |
| POST `/api/bookings` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#create_booking_succeeds` |
| POST `/api/bookings/{id}/confirm` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#confirm_booking_succeeds` |
| DELETE `/api/bookings/{id}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#cancel_booking_succeeds` |
| POST `/api/bookings/{id}/refund` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/BookingControllerIT.java` | `BookingControllerIT#refund_booking_succeeds` |

### Credits (`tests/backend/api/com/dojostay/training/CreditControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/credits/students/{studentId}/balance` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/CreditControllerIT.java` | `CreditControllerIT#balance_as_admin_returns_zero_initially` |
| GET `/api/credits/students/{studentId}/history` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/CreditControllerIT.java` | `CreditControllerIT#history_returns_empty_initially` |
| POST `/api/credits/adjust` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/training/CreditControllerIT.java` | `CreditControllerIT#adjust_credits_as_admin_creates_transaction` |

### Scopes (`tests/backend/api/com/dojostay/scopes/DataScopeControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/scopes` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/scopes/DataScopeControllerIT.java` | `DataScopeControllerIT#list_all_scopes_as_admin_returns_rules` |
| GET `/api/scopes/users/{userId}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/scopes/DataScopeControllerIT.java` | `DataScopeControllerIT#list_scopes_for_user_returns_user_rules` |
| PUT `/api/scopes/users/{userId}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/scopes/DataScopeControllerIT.java` | `DataScopeControllerIT#replace_scopes_for_user_as_admin_succeeds` |

### Community (`tests/backend/api/com/dojostay/community/CommunityControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/community/posts` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#list_posts_as_admin_returns_empty_initially` |
| POST `/api/community/posts` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#create_post_succeeds` |
| GET `/api/community/posts/{postId}/comments` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#create_comment_and_list_comments` |
| POST `/api/community/comments` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#create_comment_and_list_comments` |
| POST `/api/community/posts/{postId}/like` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#like_and_unlike_post` |
| DELETE `/api/community/posts/{postId}/like` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#like_and_unlike_post` |
| POST `/api/community/comments/{commentId}/like` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#like_and_unlike_comment` |
| DELETE `/api/community/comments/{commentId}/like` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#like_and_unlike_comment` |
| POST `/api/community/users/{userId}/follow` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#follow_and_unfollow_user` |
| DELETE `/api/community/users/{userId}/follow` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#follow_and_unfollow_user` |
| GET `/api/community/me/following` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#follow_and_unfollow_user` |
| GET `/api/community/me/followers` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#list_followers_returns_list` |
| POST `/api/community/posts/{postId}/mute` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#mute_and_unmute_thread` |
| DELETE `/api/community/posts/{postId}/mute` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#mute_and_unmute_thread` |
| POST `/api/community/users/{userId}/block` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#block_and_unblock_user` |
| DELETE `/api/community/users/{userId}/block` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/CommunityControllerIT.java` | `CommunityControllerIT#block_and_unblock_user` |

### Moderation (`tests/backend/api/com/dojostay/community/ModerationControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/moderation/reports` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/ModerationControllerIT.java` | `ModerationControllerIT#list_reports_as_admin_returns_empty_initially` |
| POST `/api/moderation/reports` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/ModerationControllerIT.java` | `ModerationControllerIT#file_report_succeeds` |
| POST `/api/moderation/reports/{id}/resolve` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/ModerationControllerIT.java` | `ModerationControllerIT#resolve_report_succeeds` |
| POST `/api/moderation/restore` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/ModerationControllerIT.java` | `ModerationControllerIT#restore_content_succeeds` |

### Notifications (`tests/backend/api/com/dojostay/community/NotificationControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/notifications` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/NotificationControllerIT.java` | `NotificationControllerIT#list_notifications_as_admin_returns_list` |
| GET `/api/notifications/unread-count` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/NotificationControllerIT.java` | `NotificationControllerIT#unread_count_returns_count` |
| POST `/api/notifications/{id}/read` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/community/NotificationControllerIT.java` | `NotificationControllerIT#mark_read_succeeds` |

### Credentials (`tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/credentials/reviews` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java` | `CredentialReviewControllerIT#list_reviews_as_admin_returns_empty_initially` |
| POST `/api/credentials/reviews` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java` | `CredentialReviewControllerIT#submit_review_json_succeeds` |
| POST `/api/credentials/reviews/upload` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java` | `CredentialReviewControllerIT#submit_review_with_file_upload_succeeds` |
| POST `/api/credentials/reviews/{id}/decide` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java` | `CredentialReviewControllerIT#decide_review_approve_succeeds` |
| POST `/api/credentials/reviews/{id}/blacklist` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/credentials/CredentialReviewControllerIT.java` | `CredentialReviewControllerIT#blacklist_user_succeeds` |

### Risk (`tests/backend/api/com/dojostay/risk/RiskControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/risk/flags` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/risk/RiskControllerIT.java` | `RiskControllerIT#list_flags_as_admin_returns_empty_initially` |
| POST `/api/risk/flags` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/risk/RiskControllerIT.java` | `RiskControllerIT#raise_flag_succeeds` |
| POST `/api/risk/flags/{id}/clear` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/risk/RiskControllerIT.java` | `RiskControllerIT#clear_flag_succeeds` |
| GET `/api/risk/incidents` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/risk/RiskControllerIT.java` | `RiskControllerIT#list_incidents_as_admin_returns_list` |
| POST `/api/risk/incidents` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/risk/RiskControllerIT.java` | `RiskControllerIT#log_incident_succeeds` |

### Ops (`tests/backend/api/com/dojostay/ops/OpsControllerIT.java`)

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET `/api/ops/toggles` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#list_toggles_as_admin_returns_list` |
| POST `/api/ops/toggles` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#upsert_toggle_succeeds` |
| GET `/api/ops/backups` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#list_backups_as_admin_returns_list` |
| POST `/api/ops/backups` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#record_backup_succeeds` |
| GET `/api/ops/exports` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#list_exports_as_admin_returns_list` |
| POST `/api/ops/exports` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#request_export_succeeds` |
| GET `/api/ops/jobs` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#list_ops_jobs_as_admin_returns_list` |
| GET `/api/ops/jobs/{kind}` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#list_ops_jobs_by_kind_returns_list` |
| POST `/api/ops/jobs/trigger` | yes | true no-mock HTTP | `tests/backend/api/com/dojostay/ops/OpsControllerIT.java` | `OpsControllerIT#trigger_ops_job_succeeds` |

## Coverage Summary

- Total endpoints: **102**
- Endpoints with HTTP tests: **102**
- Endpoints with TRUE no-mock HTTP tests: **102**
- HTTP coverage: **100.00%** (102/102)
- True API coverage: **100.00%** (102/102)

## Unit Test Summary

Unit test files:
- `tests/backend/unit/com/dojostay/auth/LockoutServiceTest.java`
- `tests/backend/unit/com/dojostay/auth/PasswordPolicyValidatorTest.java`
- `tests/backend/unit/com/dojostay/scopes/DataScopeServiceTest.java`
- `tests/backend/unit/com/dojostay/security/SensitiveStringConverterTest.java`
- `tests/backend/unit/com/dojostay/remediation/SlotAlignmentTest.java`

Modules covered by unit tests:
- controllers: none directly (covered in API tests)
- services: `LockoutService`, `DataScopeService`
- repositories: mocked via unit tests (`UserLockStateRepository`, `DataScopeRuleRepository`)
- auth/guards/middleware: `PasswordPolicyValidator`, `SensitiveStringConverter` (converter), partial security filter checks in API tests

Important modules not directly unit-tested:
- `AuthService` (`backend/src/main/java/com/dojostay/auth/AuthService.java`)
- `UserService` (`backend/src/main/java/com/dojostay/users/UserService.java`)
- `PropertyService` (`backend/src/main/java/com/dojostay/property/PropertyService.java`)
- `TrainingService` (`backend/src/main/java/com/dojostay/training/TrainingService.java`)
- `BookingService` (`backend/src/main/java/com/dojostay/training/BookingService.java`)

## Tests Check

### API Test Classification

1. **True No-Mock HTTP**
   - All API suite files under `tests/backend/api/**` are `@SpringBootTest` + `MockMvc` with real Spring app context.
   - Evidence examples: `tests/backend/api/com/dojostay/auth/AuthControllerIT.java`, `tests/backend/api/com/dojostay/property/PropertyControllerIT.java`, `tests/backend/api/com/dojostay/ops/OpsControllerIT.java`.

2. **HTTP with Mocking**
   - None detected in backend API tests.

3. **Non-HTTP (unit/integration without HTTP)**
   - `tests/backend/feature/**` (service-level integration tests)
   - `tests/backend/unit/**`
   - `tests/frontend/unit/auth.store.spec.ts`

### Mock Detection

Detected mocking/stubbing (outside backend API tests):
- `tests/backend/unit/com/dojostay/scopes/DataScopeServiceTest.java` uses Mockito `mock(...)`
- `tests/backend/unit/com/dojostay/auth/LockoutServiceTest.java` uses Mockito `mock(...)`
- `tests/frontend/unit/auth.store.spec.ts` uses `vi.mock('@/api/auth', ...)`

No `@MockBean` / controller-service mocking found in backend HTTP API tests.

### API Observability Check

- Strong: API tests now consistently show method/path invocation, request payload/params, and response assertions.
- Strong examples:
  - request+response JSON assertions: `tests/backend/api/com/dojostay/property/PropertyControllerIT.java`
  - auth/permission assertions: `tests/backend/api/com/dojostay/security/SecurityFilterIT.java`
  - lifecycle assertions: `tests/backend/api/com/dojostay/training/BookingControllerIT.java`

### Test Quality & Sufficiency

- Success paths: broad coverage across all controller areas.
- Failure cases: 401/403 paths widely asserted.
- Edge/validation: present in several suites (booking/refund lifecycle, risk clear flow, credential file upload).
- Integration boundaries: good at route level; all controller endpoints have direct HTTP-path test evidence.
- `run_tests.sh` check: Docker-based only (compliant): `run_tests.sh` uses `docker compose --profile test ...`.

### End-to-End Expectations

- Project type is fullstack (backend + frontend) per `README.md` and repo layout.
- Added fullstack-style HTTP journey test: `tests/backend/api/com/dojostay/e2e/FullStackE2EBrowserIT.java`.
- Limitation explicitly documented in that test: no real browser harness (Playwright/Selenium) currently; MockMvc-based simulation only.
- Compensation status: strong API surface coverage substantially compensates, but strict real FE-browser execution remains absent.

## Test Coverage Score (0-100)

**92 / 100**

## Score Rationale

- Complete endpoint HTTP coverage (102/102) with no API mocking inflation.
- Strong request/response observability and permission checks.
- Remaining deductions:
  - no true browser-driven FE↔BE E2E execution
  - core services still light on pure unit-level isolation tests

## Key Gaps

- Fullstack E2E still API-simulated, not browser-driven.

## Confidence & Assumptions

- Confidence: **high** on endpoint inventory, endpoint-to-test mapping, and coverage percentages.
- Assumptions:
  - endpoint inventory limited to Spring controller mappings in `*Controller.java`
  - test classification based strictly on static code evidence in repository

Test Coverage Verdict: **PARTIAL PASS**

# README Audit

## Project Type Detection

- Top-of-file project type declaration present: `Project Type: Fullstack` (`README.md:3`).
- Inferred type from structure also fullstack (`README.md:16`, `README.md:17`).

## README Location

- Required `repo/README.md`: **present**.

## Hard Gate Failures

- None.

## High Priority Issues

- None.

## Medium Priority Issues

- None.

## Low Priority Issues

- None.

## README Verdict (PASS / PARTIAL PASS / FAIL)

**PASS**

Rationale: strict README gates are satisfied, including top-level project type declaration, Docker startup path, access + verification method, Docker-contained environment rules, and explicit demo credentials for all roles (`README.md:120`-`README.md:129`).
