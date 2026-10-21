# Post-Code Review — FEAT-002 T013

- Date: 2026-10-13 (Asia/Bangkok).
- Task: T013 — Appointment persistence, audit and replay foundation.
- Reviewed boundary: additive V008 appointment/history schema, V009 correction to deferred appointment/visit trigger, V010 protection of linked check-in reception evidence, and PostgreSQL migration/constraint integration tests.
- Verification: user-run `scripts/test-local.ps1` against local `127.0.0.1:5432/otcms_test_db` reported BUILD SUCCESS at 2026-10-13 19:11:15 +07: 97 tests, 0 failures/errors/skips. `AppointmentT013PostgresIntegrationTest` passed 2/2; clean migration reached V010 and upgrade from V007 applied V008–V010. Hibernate validation initialized. No production/Supabase access.

## Findings

- V008 adds appointment, audit and command-replay persistence after owner schemas without changing V001–V007. Patient/service/Doctor/assignment/account and visit links are restrictive; source visit, source appointment, prior appointment and linked visit relationships enforce patient identity through composite keys.
- Appointment state, source, duration, buffer and interval constraints are persisted. Unique links prevent a visit being assigned to multiple appointments. Deferred constraints require checked-in appointments to have their same-patient scheduled visit and initial reception evidence.
- Audit and replay rows are append-only. Replay uniqueness is scoped to actor, appointment, command and idempotency key; replay result must reference the appointment's linked visit.
- V009 corrects a PostgreSQL trigger-record access error found during the first local integration run. It keeps appointment-only `NEW` field access inside the appointments branch; V008 remains unchanged.
- Review found that later deletion or movement of initial reception evidence could leave a checked-in appointment without reception. V010 adds a deferred consistency trigger covering deletion and updates; regression tests cover both paths. The fresh local run passed after this fix.
- Re-review of V010 and the regression cases found no remaining concrete schema defect. The 97/97 local test result is user-reported and is not attributed to the reviewer.

## Limitations

- T013 provides persistence constraints only. Appointment command handlers/writers and cross-feature atomic guard wiring belong to T014 and later tasks.
- Doctor/time-range exclusion and occupied-until enforcement belong to T015; no booking API or lifecycle workflow is claimed complete here.
- This review establishes local implementation and test evidence, not production rollout readiness.

## Human Review

- Status: APPROVED — implementation and requirement trace.
- Decision: `APPROVE implementation and trace T013`.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-13 (Asia/Bangkok; decision received in conversation).
