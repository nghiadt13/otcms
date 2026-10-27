# FEAT-002 T026 Trace — A1

Date: 2026-10-14 (Asia/Bangkok)  
Task: Reassign a confirmed appointment to another Doctor, preserving its slot  
Status: COMPLETE — implementation, tests, review and Owner Long approval recorded.

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Only Receptionist may reassign | `AppointmentBookingController` protects `POST /api/v1/appointments/{id}/reassign` with `ROLE_RECEPTIONIST`; actor is taken from the authenticated principal. | `ReassignAppointmentT026PostgresIntegrationTest` rejects unauthorized role and confirms state/audit immutability. |
| Reassignment applies only to CONFIRMED; appointment start must not be in the past (equality allowed) | `ReassignAppointmentHandler` checks the locked current state and compares start against the injected clock, rejecting only `startTime < now`. | `ReassignAppointmentHandlerT026Test` covers `start == now`, past start and non-changeable state. |
| Replacement must be a different eligible Doctor, active and accepting in directory, assigned to the service | Handler validates different Doctor; the adapter queries active accepting directory state and active Doctor-service assignment. Account lock is not used as a substitute for directory eligibility. | Handler unit and PostgreSQL tests cover same/ineligible/unassigned replacements. |
| Replacement Doctor must be free for the exact same slot | Handler calls the public slot availability use case using the stored duration/buffer snapshots and excludes the current appointment from occupancy. | Unit test inspects exact-slot query/snapshot/exclusion; PostgreSQL integration rejects an occupied replacement slot without mutation. |
| Preserve booking identity, slot, status and snapshots; require reason; append immutable audit atomically | Adapter changes only Doctor and `updated_at`, then inserts `DOCTOR_REASSIGNED` audit within the transaction. Audit records old/new Doctor, reason, actor, correlation and preserved booking/visit references. | PostgreSQL success case asserts response/audit and unchanged patient, service, slot, status and snapshots; failure cases assert no reassignment mutation/audit. |
| Serialize against competing guarded appointment mutations | Handler acquires `CoordinationGuard` before row lock and policy checks. | Unit test verifies guard precedes appointment read; database-backed integration executes guarded mutation path. |

## Verification

- Focused `./mvnw.cmd '-Dtest=ReassignAppointmentHandlerT026Test' test`: BUILD SUCCESS, 2 tests, 0 failures/errors/skips (2026-10-14 21:59 +07:00); all 59 test sources compiled.
- User-run `./scripts/test-local.ps1` against local `otcms_test_db`: BUILD SUCCESS, 202 tests, 0 failures/errors/skips (2026-10-14 22:03:30 +07:00). Surefire reports: `ReassignAppointmentT026PostgresIntegrationTest` 3/3, `ReassignAppointmentHandlerT026Test` 2/2; Visit T012 3/3.
- No schema migration or owner contract change. No production/Supabase access.

## Implementation review and disposition

Post-code review is recorded at `sdd/reviews/post-code-feat-002-appointment-booking-T026-A1.md`; no unresolved implementation finding was identified. Owner Long explicitly approved implementation and trace on 2026-10-14 (Asia/Bangkok). T026 is complete.
