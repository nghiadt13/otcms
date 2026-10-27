# Post-Code Review — FEAT-001 T006 A9

- **Status:** `REVISE_REQUIRED`
- **Reviewed at:** 2026-09-30
- **Human approval:** none inferred for another attempt

## Verification

- Worker command: `.\mvnw.cmd clean verify` reported success.
- Independent command: `.\mvnw.cmd clean verify` -> `BUILD FAILURE`; 35 tests, 1 failure, 0 errors.
- Failure: tampered JWT was accepted by `JwtTokenService.validate()`; expected rejection was not thrown.
- Testcontainers PostgreSQL `postgres:16-alpine` executed successfully.

## Blocking findings

- JWT filter and CSRF service are mocked, so real validation/enforcement is not proven.
- Protected endpoint assertion expects `200` rather than unauthenticated rejection.
- No concurrent sixth-login invariant test.
- Refresh-family isolation remains mock interaction-level, not real rotation/reuse.
- No real login cookie, bearer JWT or positive CORS HTTP assertion.
- Minimal CSRF `SecurityConfig` change is within A9 scope, but the JWT validation defect requires additional production scope beyond the approved A9 boundary.

## Required Human decision

Authorize a new bounded security correction that may modify JWT validation/filter production code, or explicitly revise the JWT contract. Until then T006 remains `BLOCKED`; no trace/sync closure or completion is authorized.
