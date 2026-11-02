# SDD Sync — FEAT-001 T005 Credential Login Contract

- **Feature:** `feat-001-auth-login`
- **Reason:** Sync patient/staff credential-login API, security policy and audit/rate-limit behavior after T005 approval.
- **Sync type:** public auth API plus security/persistence contract update
- **Producer:** T005 patient/staff login controllers and `CredentialLoginService`
- **Consumers:** Patient Portal, Staff Portal and downstream T006 session boundary
- **Compatibility:** generic authentication failure remains anti-enumerating; staff access is fail-closed; canonical 401/403/429/503 error envelope; no password/token/raw IP leakage
- **Evidence:** T005 Human approval; `.\mvnw.cmd test` `BUILD SUCCESS`; 23 tests, 0 failures/errors/skips; T005 trace report
- **Schema sync:** no new migration; existing account/audit schema and PostgreSQL advisory-lock strategy remain authoritative
- **Residual risk:** broader load/concurrency stress beyond bounded integration tests remains future hardening.

## Sync disposition

- **Status:** `SYNCED — PENDING HUMAN SYNC REVIEW`
- **Recommendation:** Accept the additive patient/staff credential-login contract and preserve canonical error, network and audit policies for T006 consumers.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T005 credential-login contract sync.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
