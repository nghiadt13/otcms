# Post-Code Review — FEAT-001 T006 A5

- **Status:** `REVISE_REQUIRED`
- **Reviewed at:** 2026-09-30
- **Human approval:** none inferred for another attempt

## Verification

- Exact command: `.\mvnw.cmd clean verify`
- Independent result: `BUILD SUCCESS`; 31 tests, 0 failures, 0 errors, 0 skipped
- PostgreSQL evidence: local PostgreSQL 18.1/Flyway V001; no Testcontainers dependency or configuration
- The intermediate JWT assertion failure reported by the worker was not reproducible in the independent rerun.

## Findings

- `T006HttpSecurityEvidenceTest` uses `MockMvcBuilders.standaloneSetup`, not the Spring Security filter chain. It therefore does not prove JWT filter authentication, Spring CSRF enforcement, actual CORS allow/deny, login endpoint cookies or staff network rejection.
- No concurrent sixth-login test proves the five-session invariant under simultaneous creation.
- No controller-level test creates two independent refresh families and proves replay revokes only the affected family.
- Cookie checks cover refresh/logout controller behavior but not patient/staff login `Set-Cookie` through HTTP.
- Testcontainers PostgreSQL evidence is absent. Adding it requires a dependency/POM change, explicitly prohibited by the A5 Human scope.

## Required Human decision

Resolve whether Testcontainers dependency/POM changes are authorized and, if so, approve a new bounded evidence attempt covering the remaining HTTP and concurrency tests. Until then T006 remains `BLOCKED`; no trace/sync closure or completion is authorized.
