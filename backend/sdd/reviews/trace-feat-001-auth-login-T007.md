# Requirement Traceability — FEAT-001 T007 Google OIDC

- **Feature:** `feat-001-auth-login`
- **Task:** `T007 — Implement Google OIDC exchange and identity linkage`
- **Spec:** v1.1.0 `APPROVED & LOCKED`
- **Trace scope:** `REQ-AUTH-029..036`, `NFR-AUTH-009`
- **Execution evidence:** T007 post-code review; `.\mvnw.cmd clean verify` passed.

## Requirement Traceability Matrix

| Requirement | Implementation evidence | Verification | Status |
|---|---|---|---|
| `REQ-AUTH-029` | `GoogleOidcAdapter`, PKCE token exchange, Nimbus JWKS validation, issuer/audience/expiry checks | fake-provider and full integration suite | `COVERED` |
| `REQ-AUTH-030` | invalid claims/unverified email fail with `AUTHENTICATION_FAILED` before persistence | OIDC unit tests | `COVERED` |
| `REQ-AUTH-031` | existing provider subject lookup and active patient session issuance | Google service tests | `COVERED` |
| `REQ-AUTH-032..033` | verified email matching and conflict-safe `ExternalIdentity` linkage | PostgreSQL linkage tests | `COVERED` |
| `REQ-AUTH-034` | onboarding request creates account, profile, consent and Google identity transactionally | PostgreSQL onboarding tests | `COVERED` |
| `REQ-AUTH-035` | missing onboarding data returns `GOOGLE_ONBOARDING_REQUIRED` without persistence | PostgreSQL rollback test | `COVERED` |
| `REQ-AUTH-036` | subject/email mismatch returns `IDENTITY_LINK_CONFLICT` without new linkage | PostgreSQL conflict test | `COVERED` |
| `NFR-AUTH-009` | provider failure and persistence failure do not create partial active onboarding state | PostgreSQL rollback tests | `COVERED` |

## Verification

- `.\mvnw.cmd clean verify` → `BUILD SUCCESS`; 47 tests, 0 failures, 0 errors, 0 skipped.
- Trace status: `VERIFIED — PENDING HUMAN TRACE REVIEW`.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T007 implementation, requirement traceability and SDD sync.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
