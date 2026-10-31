# Post-Code Review — FEAT-002 T006

- Date: 2026-10-13 (Asia/Bangkok)
- Task: T006 — explicit staff account types and auth compatibility.
- Changed boundary: explicit `AccountType` values; staff portal login/recovery/refresh network classification; demo staff seed; additive V002; auth contract and PostgreSQL tests.
- Requirement coverage: AC01, AC02, AC17; detailed mapping in `trace-feat-002-appointment-booking-T006.md`.
- Verification: user-provided output from `backend/scripts/test-local.ps1`; PostgreSQL 18.1 dedicated local DB; Flyway V001→V002 success; `.\mvnw.cmd test` BUILD SUCCESS, 73 tests, 0 failures, 0 errors, 0 skipped.
- Database scope: local `otcms_test_db` only. Supabase/production was not accessed. User reported no existing accounts; local V001→V002 migration succeeded. V002 explicitly aborts if a legacy `STAFF` row exists.

## Review findings

- `AccountType` now matches the approved single-role set: PATIENT, RECEPTIONIST, DOCTOR, OWNER, ADMIN. `isStaff()` enumerates the four staff types explicitly.
- Staff login accepts all four staff types and rejects PATIENT; patient login remains exact-type matching. Staff password recovery uses the same staff-only portal boundary.
- Refresh/logout retains staff network enforcement for all staff types. JWT claims and Spring authorities carry the explicit persisted type.
- Demo seed uses RECEPTIONIST as directed by the user. V001 remains unchanged; V002 rejects unresolved legacy STAFF rows before replacing the check constraint.
- Focused unit/contract and PostgreSQL tests passed within the full 73-test suite.

## Limitations and residual risk

- Zero existing-account inventory is human-reported; the migration's successful execution proves the dedicated local test DB had no blocking STAFF row. The production/Supabase database was not queried or migrated.
- The V002 failure branch for a non-empty legacy STAFF population was not exercised by the test run; it is fail-closed in SQL and must be reviewed before production rollout.
- No production deployment or production migration is authorized by this task.

## AI Agent Recommendation

- Status: APPROVED
- Scope: FEAT-002 T006 implementation and local verification.
- Recommendation: The bounded T006 implementation and local test evidence are accepted, subject to the recorded production inventory limitation.
- Evidence: T006 source/test diff; `V002__replace_staff_account_type.sql`; user-provided successful `test-local.ps1` output; linked requirement trace.
- Risks and assumptions: Production may contain legacy STAFF rows even though the user reports an empty database and local migration passed. V002 will stop rather than assign roles if it finds them.
- Alternatives considered: Retain generic STAFF or infer account roles. Neither matches the approved role set or the user's no-account/demo-role decisions.
- Required human decision: Resolved by the Human Final Review below.

## Human Final Review

- Status: APPROVED
- Decision: APPROVE implementation and local verification evidence.
- Reviewer: Current user in this conversation; name/role not exposed by the session.
- Reviewed at: 2026-10-13 (Asia/Bangkok; approval received in conversation).
- Follow-up: Trace approved. Shared auth persistence contract sync remains pending its contract owner's decision; production/Supabase inventory remains a deployment gate.
