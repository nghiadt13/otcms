# SDD Sync — FEAT-001 T006 Session Security Contract

- **Feature:** `feat-001-auth-login`
- **Reason:** Sync the JWT/session/refresh-cookie/CSRF/CORS security contract after T006 verification.
- **Sync type:** authentication API, browser security and persistence lifecycle
- **Producer:** `JwtTokenService`, `JwtAuthenticationFilter`, `AuthSessionService`, `SessionSecurityController`, `SecurityConfig`
- **Consumers:** Patient Portal, Staff Portal and protected backend endpoints
- **Compatibility:** access JWT remains JSON/bearer-compatible; refresh token is cookie-only; logout is idempotent; CSRF and CORS are fail-closed.
- **Schema sync:** existing V001 auth schema remains authoritative; no applied migration or dependency was changed.
- **Evidence:** T006 A12 review; `.\mvnw.cmd clean verify` passed with 40 tests.
- **Residual risk:** production configuration values remain subject to Human Security review.

## Sync disposition

- **Status:** `SYNCED — PENDING HUMAN SYNC REVIEW`
- **Recommendation:** Accept the additive T006 security boundary and preserve the documented browser/authentication contracts.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T006 implementation, requirement traceability and SDD sync.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
