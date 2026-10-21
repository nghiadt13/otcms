# Post-Code Review — FEAT-001 T006 A3

- **Status:** `REVISE_REQUIRED`
- **Review type:** Independent security, concurrency and regression review
- **Reviewed at:** 2026-09-30
- **Human approval:** none inferred; Human disposition required

## Verification evidence

- Exact command: `.\mvnw.cmd clean verify`
- Result: `BUILD SUCCESS`
- Tests: 28 run, 0 failures, 0 errors, 0 skipped
- Database evidence: PostgreSQL 18.1 and Flyway V001 validated through the configured local datasource
- Regression evidence: 23 existing T004/T005/context tests passed; 5 T006 tests passed
- Isolation limitation: T006 PostgreSQL tests use the local configured datasource rather than the Plan Q3 Testcontainers binding.

## Findings

### HIGH — Concurrent session limit is not transactionally enforced

`AuthSessionService.create(UUID, ...)` invokes another method on the same bean that carries `@Transactional`. Spring proxy interception is bypassed, so the PostgreSQL advisory transaction lock does not span the count/revoke/insert sequence used by controller-driven login. Concurrent sixth-session creation can exceed the five-session invariant. The current test exercises sequential creation only and does not prove deterministic concurrent oldest-session eviction.

### HIGH — Staff refresh/logout bypasses the staff network boundary

The shared refresh/logout endpoints do not resolve the trusted-proxy client address or invoke `StaffNetworkPolicy` before mutating a staff session. This leaves the approved internal-network/VPN policy enforced at staff login but not at staff refresh/logout.

### HIGH — Protected refresh cookies can be configured without `Secure`

Typed configuration rejects `SameSite=None` with `Secure=false`, but still permits `Secure=false` with `Strict` or `Lax`. The approved protected-cookie baseline requires `Secure=true` for all refresh cookies, so configuration must fail closed rather than weaken this control.

### MEDIUM — Required HTTP/security integration evidence is incomplete

The suite does not execute the JWT authentication filter through the Spring Security chain, login issuance and `Set-Cookie` attributes, CSRF 403-before-mutation behavior, invalid-refresh/idempotent-logout cookie clearing, exact-origin credentialed CORS allow/deny behavior, or explicit preservation of independent refresh families. It also lacks missing/short-secret and custom-expiry binding tests.

## Passing observations

- HS256 signing and strict header/signature/time checks are present.
- Refresh rotation uses row locking and reuse revocation is retained with `noRollbackFor`.
- Rotation preserves the token family; logout persistence is idempotent.
- Refresh plaintext is not persisted.
- Cookie path/host-only/HttpOnly behavior and CSRF-before-service ordering are present in implementation.
- No T006 migration, `pom.xml` or dependency mutation was observed.

## Required Human decision

Approve or reject a new bounded remediation checkpoint for the four findings above. Until then T006 remains `BLOCKED`; `/sdd-trace`, `/sdd-sync`, task completion, commit and push are not authorized.
