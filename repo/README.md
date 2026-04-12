# DojoStay

DojoStay is an on-premise operations and community system for martial arts organizations.
It runs entirely on a local network with offline-first authentication, no external SaaS
dependencies, and role-based access control.

All phases (1-9) including the remediation pass are now shipped. See `docs/architecture.md`
for the full design and `docs/deployment-local.md` for how to boot the stack on your
local network.

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
- **Tests:** Unit + integration tests covering scope leakage, booking lifecycle, property
  richness, community features, export watermark, import template

## Quick start

See `docs/deployment-local.md` for full instructions. TL;DR:

```sh
# Backend
cd backend
mvn -Dspring.profiles.active=dev spring-boot:run

# Frontend (in another terminal)
cd frontend
npm install
npm run dev
```

The frontend dev server proxies `/api` and `/actuator` to `http://localhost:8080`.
Sign in with the bootstrap admin (`admin` / value of `dojostay.bootstrap.admin-password`).

## Four roles

| Role         | Access |
| ------------ | ------ |
| STUDENT      | Own profile, bookings, community, notifications |
| PHOTOGRAPHER | Assignments, events, notifications |
| STAFF        | Student management, moderation, credential review, property ops |
| ADMIN        | Full access including user/role management, feature toggles, exports |
