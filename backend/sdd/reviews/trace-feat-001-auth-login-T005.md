# Requirement Traceability — FEAT-001 T005 Credential Login

- **Feature:** `feat-001-auth-login`
- **Task:** `T005 — Implement local patient and staff credential login`
- **Spec:** v1.1.0 `APPROVED & LOCKED`
- **Trace scope:** `REQ-AUTH-021..028`, `REQ-AUTH-052`, `REQ-AUTH-064`, `NFR-AUTH-001`, `NFR-AUTH-014`
- **Execution evidence:** T005 final remediation/diagnosis; `.\mvnw.cmd test` `BUILD SUCCESS`; 23 tests, 0 failures, 0 errors, 0 skipped
- **Post-code review:** T005 Human `APPROVED`

## Requirement Traceability Matrix

| Requirement range | Implementation evidence | Verification | Status |
|---|---|---|---|
| `REQ-AUTH-021..023` | `CredentialLoginService`, patient/staff controllers, normalized email, generic authentication failure | unit + real PostgreSQL login tests | `COVERED` |
| `REQ-AUTH-024..025` | `StaffNetworkPolicy`, typed/fail-closed CIDR parsing, trusted `Forwarded`/XFF resolution | contract and real integration tests | `COVERED` |
| `REQ-AUTH-026..028` | failure counter, temporary lockout, reset-on-success policy | PostgreSQL lockout diagnosis and rerun | `COVERED` |
| `REQ-AUTH-052` | PostgreSQL advisory-lock login IP limiter, HMAC identifier, no raw IP audit | PostgreSQL persistence tests | `COVERED` |
| `REQ-AUTH-064` | canonical 401/403/429/503 error mapping | `T005ContractTest` | `COVERED` |
| `NFR-AUTH-001` | bounded credential policy and safe generic response | unit/integration tests | `COVERED` |
| `NFR-AUTH-014` | safe audit outcome, HMAC identifier and no raw client IP | PostgreSQL audit assertions | `COVERED` |

## Verification

- Exact command: `.\mvnw.cmd test`
- Result: `BUILD SUCCESS`; 23 tests, 0 failures, 0 errors, 0 skipped.
- PostgreSQL 18.1 and Flyway V001 initialized successfully.
- Trace status: `VERIFIED — PENDING HUMAN TRACE REVIEW`.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T005 credential-login requirement traceability.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
