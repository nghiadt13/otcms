# Requirement Traceability — FEAT-001 Authentication Login

- **Feature:** `feat-001-auth-login`
- **Spec:** v1.1.0 `APPROVED & LOCKED`
- **Trace scope:** T002 persistence migration, JPA entities and repositories
- **Trace execution evidence:** `E-feat-001-auth-login-T002-A3`; exact command `\.\mvnw.cmd test`; `BUILD SUCCESS`; Flyway applied V001 from empty local PostgreSQL schema; Hibernate initialized 8 repositories
- **Trace reviewed at:** 2026-09-29

## Requirement Traceability Matrix

| Requirement range | Plan component | Task | Implementation evidence | Test status | Coverage/consistency |
|---|---|---|---|---|---|
| `REQ-AUTH-003..005` | Registration persistence foundations | T002 | `V001__auth_schema.sql`; `Account`, `PatientProfile`; `AccountRepository` | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for duplicate/constraint behavior |
| `REQ-AUTH-013` | Consent evidence persistence | T002 | `consent_evidence`; `ConsentEvidence`; `ConsentEvidenceRepository` | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for immutable/version constraints |
| `REQ-AUTH-014..020` | OTP challenge persistence | T002 | `email_verification_challenges`; partial current-row index; pessimistic current-row repository lookup | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for expiry, attempt and concurrent consumption |
| `REQ-AUTH-038..040` | Refresh-session persistence | T002 | `auth_sessions`; unique refresh hash; active-session index; bounded eviction/family repository methods | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for uniqueness, eviction ordering and rotation concurrency |
| `REQ-AUTH-048..050` | Authentication audit persistence | T002/T003 boundary | `authentication_audits`; safe account/HMAC reference check; retention index/query | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for safe audit and retention behavior |
| `REQ-AUTH-054..065` | Password-reset challenge persistence | T002 | `password_reset_challenges`; hash uniqueness; partial current-row index; pessimistic lookup; retention query/index | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for token lifecycle, attempt limits and concurrent reset |
| `NFR-AUTH-006` | Transaction and concurrency persistence support | T002 | Foreign keys, unique/check constraints and pessimistic repository access paths | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for transactional/concurrent behavior |
| `NFR-AUTH-007` | Database integrity | T002 | Unique normalized email, provider subject, refresh/reset hash and current challenge partial indexes | `contextLoads` only | `COVERED` schema evidence; `MISSING TEST` for direct constraint enforcement |
| `NFR-AUTH-008` | Retention persistence support | T002 | Cutoff indexes and paged candidate queries for audit, challenges, sessions and pending accounts | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for purge candidate boundaries |

## Implementation and Contract Findings

- Migration/entity column names, nullability, lengths, enum values, UUID identifiers and `TIMESTAMPTZ`/`Instant` mappings validated during A3 application-context startup with `ddl-auto=validate`.
- Relationships are `LAZY` and no unintended cascade or public API/entity exposure was introduced.
- Auth-owned schema identifiers and constraints are new shared persistence contracts. `/sdd-sync` is required before delivery readiness.
- No orphan REQ was found for the T002 persistence boundary. Behavior for registration, OTP, login/session, recovery and audit remains downstream in T003–T010.
- No `@ears` code trace was added because T002 contains persistence foundations only; service/use-case trace belongs to downstream tasks.

## Consistency Status

- **Spec/Plan/Tasks versions:** consistent for T002; Spec v1.1.0 is locked, Plan v1.0.0 and TASKS v1.0.0 are approved.
- **Execution evidence:** present in TASKS A2/A3 Action and Execution Records.
- **Exact verification evidence:** present; A3 passed `\.\mvnw.cmd test` with 1 test, 0 failures, 0 errors, 0 skipped.
- **Post-code review:** persisted at `.sdd/reviews/post-code-feat-001-auth-login-T002.md`; Human decision remains pending.
- **Shared contract:** schema names/constraints/indexes are new auth-owned persistence contracts; sync decision pending.
- **Trace status:** `MISSING TEST` for dedicated PostgreSQL constraint, repository, concurrency and retention coverage; not delivery-complete.

## AI Agent Recommendation

- **Status:** `PENDING HUMAN REVIEW`
- **Scope:** FEAT-001 T002 requirement traceability and impact analysis
- **Recommendation:** Accept the trace as foundation-level coverage for T002, keep dedicated PostgreSQL persistence/concurrency tests mandatory in T010, and complete `/sdd-sync` for the new auth-owned schema contract before delivery readiness.
- **Evidence:** A3 Flyway empty-schema apply, checksum validation, Hibernate schema validation, 8 repository bootstrap, post-code review and this matrix.
- **Risks and assumptions:** The current test suite only runs `contextLoads`; runtime constraint, lock, retention and concurrent behavior remain unproven until T010. Shared environments require immutable V001 handling and separate backup/recovery evidence.
- **Alternatives considered:** Marking all requirements fully tested was rejected because no dedicated persistence tests exist; moving service behavior into T002 was rejected because it exceeds the approved file boundary.
- **Required human decision:** An authorized Human reviewer must record disposition for this trace, including acceptance of the T010 test follow-up and the `/sdd-sync` requirement.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T002 trace with foundation-level coverage; accept missing dedicated persistence tests as T010 follow-up.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30T00:02:49.7996267+07:00
- **Follow-up:** Continue T010 coverage and rerun `/sdd-trace` if the contract changes.
