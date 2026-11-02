# FEAT-002 T024 Trace — A1 (Approval Pending)

Date: 2026-10-14 (Asia/Bangkok)\
Task: Ordinary appointment reschedule\
Status: COMPLETE — implementation, verification, review, and Owner Long approval recorded.

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Patient self-only and Receptionist-only reschedule of CONFIRMED appointments | `AppointmentBookingController` derives actor from authenticated account type; `RescheduleAppointmentHandler` checks current Patient authorization and appointment state under the shared guard. | `RescheduleAppointmentHandlerT024Test`; `RescheduleAppointmentT024PostgresIntegrationTest` (full-suite pass). |
| Patient old change deadline and new online notice; Receptionist reason; both enforce past, clinic horizon and grid | `RescheduleAppointmentHandler` applies the configured clock/time-zone policies and requires a nonblank Receptionist reason. | Unit tests cover self flow and required reason; PostgreSQL HTTP test verifies Receptionist reason rejection. |
| Same Doctor/service/patient/purpose/duration/buffer snapshots; permit self-overlap while checking appointment and care occupancy | Handler uses `SlotServiceContext.snapshot(...)`, includes the appointment ID for self-exclusion, and accepts only the exact requested instant returned by the slot engine. Persistence updates only `start_time`, `end_time`, and `updated_at`. | Unit assertions verify Doctor, appointment/patient self-exclusion IDs and duration/buffer snapshots; HTTP response verifies appointment identity, patient, purpose, duration and buffer remain unchanged. |
| Atomic slot change and immutable `RESCHEDULED` audit; failures preserve old booking and audit | `JdbcAppointmentRescheduleAdapter` updates the same row and inserts the audit in one Spring transaction. PostgreSQL exclusion SQLSTATE `23P01` maps to `SLOT_UNAVAILABLE`; the API error carries the request correlation ID. | PostgreSQL HTTP test verifies same-row success and audit fields, as well as occupied-target conflict preserving the original start and audit history. |
| Request/error correlation and protected client fields | PATCH accepts only `startTime` and `reason`; controller derives actor/account and correlation ID from trusted request state. Shared guarded-database error mapping uses the request correlation ID. | HTTP success verifies response correlation header; focused `CoordinationFailureExceptionHandlerTest` passes 3/3. |

## Verification

- Focused command `./mvnw.cmd '-Dtest=RescheduleAppointmentHandlerT024Test,CoordinationFailureExceptionHandlerTest' test`: BUILD SUCCESS, 5 tests, 0 failures/errors/skips (2026-10-14 21:32 +07:00).
- After adding the PostgreSQL HTTP class, `./mvnw.cmd '-Dtest=RescheduleAppointmentHandlerT024Test' test`: BUILD SUCCESS, 2 tests, 0 failures/errors/skips; all 55 test sources compiled (2026-10-14 21:38 +07:00).
- User-run `./scripts/test-local.ps1` on `127.0.0.1:5432/otcms_test_db`: BUILD SUCCESS, 191 tests, 0 failures/errors/skips (2026-10-14 21:40 +07:00). This run includes the three `RescheduleAppointmentT024PostgresIntegrationTest` cases and Visit T012 3/3. Earlier full-suite evidence reports Flyway v013; no production/Supabase connection was used.
- No migration or frozen cross-owner contract changed. No commit or push.

## Review and human disposition

- Owner Long approved implementation and this trace with `Ok` on 2026-10-14 (Asia/Bangkok). T024 is complete.
