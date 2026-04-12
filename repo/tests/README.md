# DojoStay tests

All automated tests for DojoStay live under this directory rather than next to source code.

```
tests/
  backend/
    unit/         JUnit5 unit tests against pure-Java services and validators
    api/          @SpringBootTest controller integration tests (full ApplicationContext)
    feature/      cross-module behavioral tests (auth flows, lockout, audit chains)
    integration/  reserved for repository / DB-backed tests against H2 in MODE=MySQL
    resources/    additional test resources, e.g. application-test overrides
  frontend/
    unit/         component / store / composable tests (Vitest + jsdom)
    component/    Vue Test Utils mounted-component tests
  e2e/            reserved for Phase 9 end-to-end smoke tests (Playwright)
```

## Backend wiring

`backend/pom.xml` uses `build-helper-maven-plugin` to add
`tests/backend/{unit,api,feature,integration}` as additional test source directories
and `tests/backend/resources` as an additional test resource directory.

Run:
```
cd backend
mvn -Dspring.profiles.active=test test
```

## Frontend wiring

`frontend/vite.config.ts` configures Vitest with:
```
test: {
  include: ['../tests/frontend/**/*.{spec,test}.ts'],
}
```

Run:
```
cd frontend
npm test
```
