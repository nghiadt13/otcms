# Post-Code Review — FEAT-002 T007

- Date: 2026-10-13 (Asia/Bangkok)
- Task: T007 — independent patient profile identity and Receptionist search/create contract.
- Reviewed boundary: V003, independent PatientProfile JPA identity and optional account link, auth registration persistence bridge, Patient feature domain/application/web/persistence adapter, focused tests, and T007 execution record.
- Decision checkpoint: migration scope approved by Owner Long; patient-profile-search-create v1.0.0 approved by Owner Long on 2026-10-13.
- Verification: user-provided `scripts/test-local.ps1` output on `127.0.0.1:5432/otcms_test_db`: BUILD SUCCESS, 80 tests, 0 failures/errors/skips; `PatientProfileT007PostgresIntegrationTest` 4/4 passed. Prior output confirms Flyway V002→V003 and Hibernate validation. No production/Supabase access.

## Findings

- V003 is additive and preserves V001/V002. It assigns independent UUIDs, makes `account_id` nullable, and adds unique account linkage. Integration tests cover clean install and V002-shaped existing data with account/profile preservation.
- Patient API is limited to `/api/v1/patient-profiles`, enforces `ROLE_RECEPTIONIST` at method and principal checks, bounds page size, rejects blank/unbounded search, and returns the approved minimal projection. Create generates a separate identity and does not auto-merge candidates.
- Registration continues to link its profile to the account through the persistence boundary. The patient feature does not directly own or query JPA repositories.
- Local full suite passed. Unit test covers normalized data and duplicate-as-candidate behavior; PostgreSQL tests cover migration and API behavior.

## Limitations / observations

- The current name matching uses case-insensitive substring matching and NFC whitespace normalization; confirm this meets the intended “normalized name” contract. Accent-insensitive matching is not implemented.
- Phone normalization assumes Vietnam country code `+84`; clinic locale is currently Vietnam, but this is a policy coupling worth documenting if the service expands.
- Search response currently uses Spring `Page` metadata in addition to the minimal patient fields; verify the approved contract permits standard pagination metadata.
- API error mapping is local to this feature and emits the shared `ErrorResponse`; only focused expected validation/authorization behavior is evidenced here.
- Local migration/test evidence does not authorize production rollout; production inventory and rollout checkpoint remain outstanding.

## AI Agent Recommendation

- Recommendation: APPROVE the bounded T007 implementation and local verification, subject to reviewer disposition of the observations above and the recorded production rollout boundary.
- Required human decision: approve/revise/reject implementation review and trace.

## Human Review

- Status: APPROVED
- Decision: APPROVE implementation and trace.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-13 (Asia/Bangkok; decision received in conversation).
