# FEAT-002 T025 Trace — A1 (Approval Pending)

Date: 2026-10-14 (Asia/Bangkok)\
Task: CONFIRMED appointment cancellation\
Status: COMPLETE — implementation, verification, review and Owner Long approval recorded.

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Only Patient/Receptionist can cancel a booking; Patient is restricted to current linked self profile | `AppointmentBookingController` derives actor/account from the authenticated principal; `CancelAppointmentHandler` checks Patient through the Patient public authorization port. Missing and out-of-scope Patient appointment IDs share `APPOINTMENT_NOT_FOUND`. | `CancelAppointmentT025PostgresIntegrationTest` cross-profile case; `CancelAppointmentHandlerT025Test` authorization case. |
| Patient cancellation enforces the change deadline with equality allowed; Patient reason is optional | Handler rejects only when `startTime < now + patientChangeDeadlineMinutes`; optional supplied reasons are validated and trimmed. | Unit test at exact deadline and one minute inside; HTTP Patient cancellation with an empty reason. |
| Receptionist cancellation requires a nonblank reason and may override the Patient deadline | Handler validates the operational reason and applies no Patient deadline to Receptionist commands. | PostgreSQL HTTP test rejects a blank reason, then cancels an appointment inside the Patient deadline with a reason. |
| Cancellation is limited to supported lifecycle states; CHECKED_IN requires visit/reception cancellation | Handler updates only `CONFIRMED`; all other states return `APPOINTMENT_NOT_CHANGEABLE`. Controller exposes no CHECKED_IN branch before T029. | Unit test verifies CHECKED_IN denial; SPEC §5.3 dependency T029 remains incomplete. |
| Success changes status, releases occupancy and writes immutable audit atomically while retaining booking identity/times | Guard-first transaction locks the appointment, updates only status/updated_at, and inserts `CANCELLED` audit with old/new status, Doctor/times, actor, reason, linked/source visit references and correlation. V013 excludes CANCELLED from occupied ranges. | PostgreSQL test checks response/audit/correlation and inserts a second booking into the released slot. Failure cases verify unchanged state/no cancellation audit. |

## Verification

- Focused `./mvnw.cmd '-Dtest=CancelAppointmentHandlerT025Test' test`: BUILD SUCCESS, 3 tests, 0 failures/errors/skips (2026-10-14 21:47 +07:00); all 57 test sources compiled.
- User-run `./scripts/test-local.ps1` on local `otcms_test_db`: BUILD SUCCESS, 197 tests, 0 failures/errors/skips (2026-10-14 21:49:54 +07:00). Surefire report `CancelAppointmentT025PostgresIntegrationTest`: 3/3; `CancelAppointmentHandlerT025Test`: 3/3. Visit T012: 3/3.
- No schema migration or cross-owner contract change. Production/Supabase, frontend and commit/push were not touched.

## Implementation review

- Guard acquisition precedes appointment row lock and all policy reads. Patient authorization is current and occurs under the transaction boundary.
- Patient deadline equality and Receptionist override/reason semantics match CONTEXT §5.2 and SPEC REQ-BOOK-023–028/045.
- Update and audit use the same transaction; the endpoint cannot accept client-supplied actor, status, appointment identity or booking fields.
- CHECKED_IN cancellation is deliberately withheld until T029 provides atomic Visit/Reception cancellation and clinical-activity synchronization.
- No unresolved T025 implementation finding identified in this review. Owner Long approved implementation and trace on 2026-10-14 (Asia/Bangkok). T025 is complete; CHECKED_IN cancellation remains deferred to T029.
