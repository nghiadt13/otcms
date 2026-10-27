# Post-Code Review — FEAT-001 T006 A4

- **Status:** `REVISE_REQUIRED`
- **Reviewed at:** 2026-09-30
- **Human approval:** none inferred

## Verification

- Exact command: `.\mvnw.cmd clean verify`
- Result: `BUILD SUCCESS`; 28 tests, 0 failures, 0 errors, 0 skipped
- PostgreSQL 18.1 and Flyway V001 validated; tests use local configured PostgreSQL
- T006 inventory: 5 tests (3 contract/unit, 2 PostgreSQL service-level)

## Corrections verified

- Session creation now places the advisory lock inside the externally invoked transaction covering count/revoke/insert.
- Staff refresh/logout resolves trusted-proxy client address and applies `StaffNetworkPolicy` before mutation.
- Refresh-token issue/rotation/clear cookies use `Secure=true`.

## Blocking evidence gaps

- No HTTP integration test executes the JWT filter through the Spring Security chain.
- No HTTP assertions cover login `Set-Cookie`, CSRF rejection before mutation, exact-origin CORS allow/deny, logout cookie clearing, or staff refresh/logout network rejection.
- No explicit controller-boundary test proves independent refresh families remain active after replay-family revocation.
- No concurrent session-creation test proves the five-session invariant under simultaneous sixth-login attempts; current session-limit test is sequential.
- No Testcontainers dependency is present, so PostgreSQL evidence is local-environment coupled.

## Required Human decision

Approve or reject a new bounded checkpoint for the remaining integration/concurrency evidence. Until then T006 remains `BLOCKED`; no trace/sync closure, task completion, commit or push is authorized.
