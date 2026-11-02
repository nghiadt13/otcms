# Post-code Review — FEAT-001 T002 Auth Persistence Foundation

- **Feature:** `feat-001-auth-login`
- **Task:** `T002 — Add auth schema migration and persistence foundations`
- **Review type:** Persistence, PostgreSQL/Flyway, JPA mapping and repository review
- **Reviewed contract:** FEAT-001 Spec v1.1.0 `APPROVED & LOCKED`; Plan/TASKS v1.0.0
- **Execution evidence:** `E-feat-001-auth-login-T002-A2` and `E-feat-001-auth-login-T002-A3`

## Changed Boundary

Implementation is confined to the approved T002 boundary:

- `src/main/resources/db/migration/V001__auth_schema.sql`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/entity/`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/`

The change creates the additive auth-owned account, patient profile, external identity, consent evidence, email-verification challenge, password-reset challenge, auth session and authentication-audit persistence foundations. No controller, service, public API, configuration, dependency, POM, test, deployment, commit or push change is part of this task.

## Requirement Coverage

| Intent | Review evidence | Status |
| :--- | :--- | :--- |
| REQ-AUTH-003..005 | Account/profile schema, unique normalized email and non-unique indexed canonical phone support atomic registration foundations. | PASS — persistence foundation only |
| REQ-AUTH-013 | Consent evidence persists actor, server-selected terms/privacy versions and acceptance timestamp. | PASS — persistence foundation only |
| REQ-AUTH-014..020 | OTP hash-only challenge fields, expiry/attempt/consumed/superseded lifecycle, one-current-challenge partial unique index and pessimistic current-row lookup are present. | PASS — behavior remains for T004 |
| REQ-AUTH-038..040 | Unique refresh-token hash, session metadata, stable `(created_at, id)` eviction order index and bounded oldest-session query are present. | PASS — behavior remains for T006 |
| REQ-AUTH-048..050 | Audit entity stores correlation, duration, safe account/HMAC reference and bounded retention query without credential/token fields. | PASS — logging/audit behavior remains for T003/T006 |
| REQ-AUTH-054..065 | Reset token hash-only lifecycle, unique hash, one-current-challenge index, attempt bounds, expiry and pessimistic access paths are present. | PASS — behavior remains for T008 |
| NFR-AUTH-006 | Database uniqueness plus pessimistic repository access paths support later transactional account/challenge/session operations. | PASS for foundation; concurrency behavior needs dedicated tests |
| NFR-AUTH-007 | Database enforces unique normalized email, `(provider, provider_subject)`, refresh-token hash and one current OTP challenge/account. | PASS |
| NFR-AUTH-008 | Retention-supporting timestamps, indexes and paged purge-candidate queries exist for audit, OTP, reset, session and pending-account lifecycle. | PASS for foundation; scheduler/purge execution remains downstream |

## Technical Review Results

- Migration and JPA mappings align on table/column names, nullability, lengths, enum string values and UUID identifiers.
- PostgreSQL constraints cover required email, external identity, refresh/reset hash and current-challenge uniqueness invariants.
- All entity relationships are explicitly `LAZY`; no unintended cascade or `EAGER` association was introduced.
- Repository derived queries initialized successfully. Security-sensitive mutable rows now have pessimistic locking access paths; collection reads identified during review are bounded or paginated.
- Retention indexes and paged purge-candidate queries were added within the approved persistence boundary.
- Authentication audit account foreign-key behavior is coherent with the current no-delete/default-restrict model; no unexpected public contract change was introduced.
- Review findings raised during A2 for concurrency access, unbounded queries, retention foundations and audit FK semantics were addressed before A3 verification.

## Verification Evidence

- **Approved exact command:** `.\mvnw.cmd test`
- **A3 result:** exit code `0`; `BUILD SUCCESS`; tests run `1`, failures `0`, errors `0`, skipped `0`.
- **Flyway empty-schema evidence:** against local PostgreSQL `18.1`, schema history was absent initially; Flyway created its history table and applied `001 - auth schema` from an empty schema to version `v001`. Reviewed/resolved checksum: `-1835556165`.
- **JPA/repository evidence:** Spring Data discovered `8` repositories; Hibernate ORM `7.4.5.Final` initialized the default `EntityManagerFactory` against PostgreSQL without mapping/schema validation failure.
- **Execution boundary:** A3 was verification-only. No Flyway repair, Agent-performed reset, source mutation, commit, push or deployment occurred.

## Verification Gaps and Residual Risks

- The current suite executed only `OtcmsBeApplicationTests.contextLoads`; there are no dedicated persistence tests asserting each unique/check/partial-index constraint, pessimistic-lock behavior, concurrent duplicate attempts, retention queries or purge boundaries.
- Empty-schema application and JPA bootstrap are evidenced on the Human-reset local database, not an isolated Testcontainers database. Repeatability and isolation remain for T010.
- Transaction boundaries, concurrent attempt counting, session eviction/rotation and actual lifecycle purge execution belong to downstream implementation tasks and still require PostgreSQL concurrency/integration coverage.
- `V001` must remain immutable after application to a shared environment. Shared/staging/production execution still requires a separate migration review and environment-appropriate backup/recovery evidence.
- Runtime grant enforcement remains cooperative rather than host-atomic, as recorded in A3; this does not change source correctness but remains governance evidence risk.
- `/sdd-trace` is pending for final code/test links, and `/sdd-sync` is pending for the new auth-owned schema identifiers, constraints, concurrency access paths and retention indexes.

## AI Agent Recommendation

- **Status:** PENDING HUMAN REVIEW
- **Scope:** FEAT-001 T002 migration, auth entities and repositories only.
- **Recommendation:** Accept the T002 implementation as technically verified for its persistence-foundation scope, while retaining the dedicated PostgreSQL persistence/concurrency tests as a mandatory T010 follow-up and completing `/sdd-trace` plus `/sdd-sync` before delivery readiness.
- **Evidence:** A3 exact command succeeded; V001 was applied from an empty local PostgreSQL schema; Hibernate/JPA and eight repositories initialized; A2 technical review findings were corrected within the approved boundary.
- **Residual risks:** Only `contextLoads` currently covers the implementation; constraint, lock, retention and concurrency behavior lacks dedicated automated tests. Shared-environment migration safety requires a separate review and backup/recovery plan.
- **Required human decision:** An authorized Human persistence reviewer must record `APPROVED`, `REVISE`, or `REJECTED` for the T002 post-code persistence review, explicitly deciding whether the current verification evidence is sufficient to close T002 with the listed T010 and trace/sync follow-ups.
- **Next step after approval:** Persist the T002 completion/sync-back decision, run `/sdd-trace` and `/sdd-sync` as required, and retain dedicated PostgreSQL persistence/concurrency coverage in T010.

## Human Final Review

- **Status:** APPROVED
- **Decision:** Approve T002 persistence implementation. Accept T010 as follow-up for dedicated PostgreSQL constraint, concurrency and retention tests.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30T00:02:24.3618362+07:00
- **Follow-up:** Continue with T003; retain T010 persistence tests and trace/sync evidence.
