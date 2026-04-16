# DojoStay

**Project Type: Fullstack** (Spring Boot backend + Vue 3 frontend)

DojoStay is an on-premise operations and community system for martial arts organizations.
It runs entirely on a local network with offline-first authentication, no external SaaS
dependencies, and role-based access control.

All phases (1-9) including the remediation pass are now shipped. See `docs/architecture.md`
for the full design.

## Repository layout

```
repo/
  backend/        Spring Boot 3.x + Java 21 + JPA + Flyway + MySQL
  frontend/       Vue 3 + TypeScript + Vite + Pinia + Vue Router
  tests/          All tests, segregated by tier (unit / api / feature / e2e)
```

Tests intentionally live outside the source trees so they can be run, audited, and
extended independently. The backend uses `build-helper-maven-plugin` to add
`tests/backend/{unit,api,feature,integration}` as additional test source directories;
the frontend uses Vitest with `test.include = ['../tests/frontend/**/*.{spec,test}.ts']`.

## What's included

- **Foundation:** Spring Boot bootstrap, Flyway migrations (V1-V9), `ApiResponse` envelope,
  `GlobalExceptionHandler`, request correlation id
- **Identity & Auth:** Users, roles, permissions, BCrypt hashing, 12-char password policy,
  5-failure / 15-min lockout, CSRF cookie strategy, account blacklist (HTTP 423)
- **Data Scopes:** Three-layer authorization (role -> permission -> data scope), deny-by-default,
  existence-hiding 404s on out-of-scope reads
- **Students:** Master data (school/program/classGroup/housing), bulk CSV import with error
  report, import template download, AES-GCM-256 PII encryption with masked DTOs
- **Property & Lodging:** Properties with description/policies, amenity CRUD, image upload,
  room types, nightly rate calendar, bed availability engine
- **Training & Booking:** Classes, sessions (VENUE/ONLINE, sparring filters), 30-min slot grid,
  booking lifecycle (INITIATED -> CONFIRMED -> CANCELED -> REFUNDED), conflict detection,
  credit ledger with refund-as-credit
- **Community:** Posts, threaded comments (reply/quote), likes, follows, @mentions with
  notifications, thread mute, user block (feed + notification suppression), moderation
  queue with restore
- **Credentials:** File upload with SHA-256 dedup, mime/size whitelist, blacklist workflow
- **Ops:** Export jobs with watermark metadata, feature toggles, backup records
- **Frontend:** Vue 3 SPA with role-aware sidebar, functional pages for dashboard, students,
  training, property, community, notifications, moderation, admin users
- **Tests:** Unit + API integration + feature tests covering scope leakage, booking lifecycle,
  property richness, community features, export watermark, import template

## Docker startup

Everything runs inside Docker. No local runtime installs required.

```sh
docker compose up --build
```

This starts three services:

| Service    | Description                         | Internal Port |
|------------|-------------------------------------|---------------|
| `mysql`    | MySQL 8.4 database                  | 3306          |
| `backend`  | Spring Boot API server              | 8080          |
| `frontend` | Nginx serving Vue 3 SPA + API proxy | 80            |

The `backend` waits for `mysql` to be healthy before starting; the `frontend` waits for
`backend` to be healthy before starting. First boot takes a few minutes for image builds.

## Access

After `docker compose up` completes and all health checks pass:

| What        | URL                        |
|-------------|----------------------------|
| Application | http://localhost:8080      |
| API base    | http://localhost:8080/api  |
| Health check| http://localhost:8080/actuator/health |

The frontend Nginx container is exposed on port 8080 and proxies `/api` and `/actuator`
requests to the backend service.

## Verification

After startup, verify the system is working:

### API check

```sh
# Health endpoint (no auth required)
curl -s http://localhost:8080/actuator/health | jq .

# CSRF cookie fetch (no auth required)
curl -s http://localhost:8080/api/auth/csrf

# Login as admin
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMe!2026Docker"}' \
  -c cookies.txt | jq .

# List users (authenticated)
curl -s http://localhost:8080/api/users \
  -b cookies.txt | jq .
```

Expected: health returns `{"status":"UP"}`, login returns `{"success":true,"data":{"username":"admin",...}}`,
user list returns a paginated envelope.

### UI verification flow

1. Open http://localhost:8080 in a browser
2. Login with admin credentials (see below)
3. Verify the dashboard loads with the role-aware sidebar
4. Navigate to Students, Training, Property, and Community pages
5. Create a test student, training class, or community post to confirm write operations work

## Demo credentials

| Role         | Username       | Password                | Access                                                    |
|--------------|----------------|-------------------------|-----------------------------------------------------------|
| ADMIN        | `admin`        | `ChangeMe!2026Docker`   | Full access: users, roles, students, training, property, community, ops, exports |
| STAFF        | `staff-hq`     | `StaffPass!2026Docker`  | Student management, moderation, credential review, property ops |
| STUDENT      | `demo-student` | `Student!2026Docker`    | Own profile, bookings, community, notifications |
| PHOTOGRAPHER | `demo-photo`   | `PhotoPass!2026Docker`  | Assignments, events, community, notifications |

All four demo accounts are created automatically on first boot in Docker mode.
The STUDENT account has a linked student profile, so the self-service endpoints
(`/api/students/me`) work immediately after login.

## Running tests

Tests run inside Docker with an isolated H2 in-memory database:

```sh
docker compose --profile test run --rm backend-test
docker compose --profile test run --rm frontend-test
```

Or use the convenience script:

```sh
./run_tests.sh
```

## Four roles

| Role         | Access |
| ------------ | ------ |
| STUDENT      | Own profile, bookings, community, notifications |
| PHOTOGRAPHER | Assignments, events, notifications |
| STAFF        | Student management, moderation, credential review, property ops |
| ADMIN        | Full access including user/role management, feature toggles, exports |
