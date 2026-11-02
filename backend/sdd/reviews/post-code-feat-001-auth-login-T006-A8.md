# Post-Code Review — FEAT-001 T006 A8

- **Status:** `REVISE_REQUIRED`
- **Reviewed at:** 2026-09-30
- **Human approval:** none inferred for another attempt

## Verification

- Exact command: `.\mvnw.cmd clean verify`
- Result: `BUILD SUCCESS`; 35 tests, 0 failures, 0 errors, 0 skipped
- Testcontainers: PostgreSQL 16-alpine executed successfully
- Scope: test-only source plus test-scope dependencies; no production source/migration/runtime dependency drift observed

## Blocking findings

- `T006A8EvidenceTest` mocks `JwtAuthenticationFilter` and `CsrfTokenService`; real JWT valid/invalid behavior and real CSRF filter rejection are not exercised.
- No actual login HTTP request asserts `Set-Cookie` attributes.
- Protected endpoint test expects `200 OK` instead of unauthenticated rejection.
- Staff network test does not assert the intended `403` for an untrusted forwarded address.
- No concurrent sixth-login test proves the five-session invariant under simultaneous creation.
- Refresh-family isolation is mock interaction-level evidence, not independent real rotation/reuse behavior.
- Existing `T006HttpSecurityEvidenceTest` still uses standalone MockMvc and is not filter-chain evidence.

## Required Human decision

Provide a new bounded checkpoint if these evidence defects must be corrected. Until then T006 remains `BLOCKED`; no trace/sync closure or completion is authorized.
