# FEAT-002 T027 Trace — A1

Date: 2026-10-14 (Asia/Bangkok)\
Task: Atomic Receptionist check-in for a confirmed appointment\
Status: APPROVED — unit, PostgreSQL integration and full-suite checks pass; Owner Long approved on 2026-10-14 (Asia/Bangkok).

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Receptionist-only check-in; client cannot choose patient | `AppointmentBookingController.checkIn` requires `ROLE_RECEPTIONIST`, accepts only `{}`, derives actor from authenticated principal. | `CheckInAppointmentT027PostgresIntegrationTest` includes non-Receptionist denial. |
| Required bounded idempotency key; successful same-key retry precedes current lifecycle/window checks | Controller validates `Idempotency-Key`; `CheckInAppointmentHandler` acquires the guard, looks up replay by actor/appointment/key and compares the request fingerprint before row lock/state/time checks. | Unit test covers matching replay, replay fingerprint conflict, and no repeated Visit call; PostgreSQL test replays after lifecycle progression and after the check-in window. |
| First check-in requires CONFIRMED and inclusive early/late configured window | Handler rejects invalid lifecycle and derives the inclusive bounds from appointment start and `AppointmentBookingProperties`; Visit owner captures the command instant and rejects only strictly outside the bounds. | Unit test covers exact early boundary. PostgreSQL test covers exact early and late boundaries and outside-window immutability. |
| Appointment and Visit/reception ownership stays explicit; one Visit/initial reception | Appointment port owns status/link/audit/replay; Visit adapter behind `ReceptionWorkflowPort` writes scheduled Visit and `APPOINTMENT_CHECK_IN` initial reception; duplicate originating Visit/link is rejected. | PostgreSQL tests assert status/link, patient/source, arrival/check-in time, initial reception and uniqueness; they cover invalid/inconsistent link error paths. |
| Atomic success audit and replay; mandatory failures roll back all writes | Appointment update, immutable `STATUS_CHANGED` audit and replay insert run in the same Spring transaction as Visit/reception writes. Audit/replay use the Visit-returned `checkedInAt`. Persistence failures map to `DEPENDENCY_UNAVAILABLE`. | PostgreSQL test injects an audit insertion failure and asserts appointment, Visit, reception, audit and replay remain unchanged. |
| Recorded arrival and check-in timestamp remain distinct | Visit adapter uses matching `recorded_appointment_arrivals.arrived_at` when present; otherwise arrival defaults to checked-in instant. `checked_in_at` is the single captured operation instant. | PostgreSQL test records an arrival before check-in and asserts distinct Visit arrival/check-in times. |

## Verification

- Focused `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test' test`: BUILD SUCCESS, 4 tests, 0 failures/errors/skips (2026-10-14 22:23:14 +07:00); all 61 test sources compiled.
- `CheckInAppointmentT027PostgresIntegrationTest`: 5/5, zero failures/errors/skips. User-run `./scripts/test-local.ps1`: BUILD SUCCESS, 211 tests, zero failures/errors/skips (2026-10-14 22:28:21 +07:00); Visit T012 PostgreSQL integration 3/3.
- No schema migration, queue behavior, frontend change or production/Supabase access.

## Implementation review and disposition

Post-code review: `sdd/reviews/post-code-feat-002-appointment-booking-T027-A1.md`. Review found no unresolved code issue. Owner Long decision: APPROVE implementation and trace T027, received 2026-10-14 (Asia/Bangkok). T027 is complete.
