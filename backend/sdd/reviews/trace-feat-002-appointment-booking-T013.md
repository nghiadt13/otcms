# Requirement Traceability — FEAT-002 T013

- Date: 2026-10-13 (Asia/Bangkok).
- Task: T013 — Appointment persistence, audit and replay foundation.
- Sources: `TASKS.md` T013; `SPEC.md` §7 persistence and relevant §5.2–5.5 lifecycle constraints; `PLAN.md` Phase 2; `CONTEXT.md` D08–D10 and the approved duration/buffer snapshot rules.
- Verification: user-run local PostgreSQL `scripts/test-local.ps1` at 2026-10-13 19:11:15 +07; 97/97 tests passed; `AppointmentT013PostgresIntegrationTest` 2/2; clean V001→V010 and upgrade V007→V010; Hibernate validation passed.

| Requirement | Implementation / evidence | Result | Limitation |
|---|---|---|---|
| Add appointment persistence after owner schemas while preserving existing history | V008 adds `appointments` and relationships to patient, service, Doctor, Doctor-service assignment, account and visit; restrictive FKs preserve historical references. T013 integration test exercises clean install and V007 upgrade while preserving a pre-existing visit. | PASS locally | No appointment application writer in T013. |
| Preserve source visit identity and same-patient appointment/visit links | Composite FKs enforce same-patient source visit, source appointment, visit, originating appointment, prior appointment and recorded arrival references. Integration test rejects cross-patient prior appointment linkage. | PASS locally | Operational walk-in acceptance remains subject to T014 transaction guard. |
| Persist lifecycle values, duration/buffer snapshots and valid appointment intervals/status/source | V008 stores status/source/times/duration/buffer/version and adds checks for allowed values, positive duration, nonnegative buffer, consistent end time and legal visit-state linkage. | PASS locally | Cross-row slot overlap/occupied-until enforcement is T015. |
| Preserve immutable audit history with actor, action, old/new snapshots, visit links, time and correlation | `appointment_audits`, restrictive FKs, validations, indexes and immutable-row trigger; integration test verifies mutation rejection. | PASS locally | Audit writer and command-level atomicity come in later tasks. |
| Persist immutable check-in replay keyed by actor/appointment/command/key and linked to result visit | `appointment_command_replays` uniqueness, command/key/fingerprint checks, linked-visit FK and immutable-row trigger; integration test verifies duplicate key and valid result linkage. | PASS locally | Replay writer and conflict-response semantics are later application work. |
| Check-in appointment/visit/reception state is consistent | Deferred constraint triggers enforce same linked scheduled visit, one appointment per visit, check-in status requires linked visit, and initial reception evidence. V009 avoids accessing appointment-only trigger fields on a `clinic_visits` trigger record. V010 guards deleting/moving the required reception row; tests cover both. | PASS locally | Atomic multi-owner writes require the T014 shared guard. |
| Clean migration and upgrade work without modifying applied migrations | V008–V010 are additive; local suite reached V010 cleanly and upgraded V007 through V008–V010. Existing V001–V009 checksums validated. | PASS locally | Production/Supabase was not accessed. |

## Human Review

- Status: APPROVED — requirement trace.
- Decision: `APPROVE implementation and trace T013`.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-13 (Asia/Bangkok; decision received in conversation).
