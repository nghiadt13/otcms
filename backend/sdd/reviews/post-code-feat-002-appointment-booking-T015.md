# Post-Code Review — FEAT-002 T015

- Date: 2026-10-14 (Asia/Bangkok).
- Task: T015 — Doctor occupied interval exclusion and occupied-until persistence.
- Reviewed boundary: additive V013 migration and `AppointmentT015PostgresIntegrationTest`.
- Requirements: FEAT-002 SPEC §3.3, §5.2, §7 and AC04; CONTEXT §4.4. See `trace-feat-002-appointment-booking-T015.md`.
- Verification: user-run `backend/scripts/test-local.ps1` reported BUILD SUCCESS at 2026-10-14 00:24:40 +07: 120 tests, 0 failures/errors/skips. The T015 PostgreSQL integration report records 3/3 passed. Local clean migration, V013 application, and Hibernate schema validation completed through the full test suite. Production/Supabase was not accessed.

## Findings

- V013 is additive and leaves all previously applied migrations unchanged. `occupied_until` is persisted and maintained by a row trigger because PostgreSQL rejects the equivalent generated expression as non-immutable (`42P17`). The trigger sets the value on insert and when end/buffer/occupied-until is updated; the check constraint rejects values before end time.
- A PostgreSQL GiST exclusion constraint combines Doctor equality with overlap of the half-open `tstzrange(start_time, occupied_until, '[)')`. Its predicate covers CONFIRMED, CHECKED_IN, IN_PROGRESS and COMPLETED, with no clock-dependent expiration. CANCELLED and NO_SHOW are excluded.
- Integration tests verify buffer overlap rejection, exact adjacency acceptance, cancelled-row rebooking, checked-in/in-progress/completed occupancy, and rollback of a preceding update when an overlap insert fails.
- Shared SQLSTATE `23P01` mapping returns HTTP 409 `SLOT_UNAVAILABLE`; its regression test is included in the passing full suite.
- No concrete defect found in the reviewed T015 slice. Local test evidence is user-reported; this review does not establish production extension availability or permissions.

## Human Review

- Status: APPROVED — implementation and requirement trace.
- Decision: `APPROVE implementation and trace T015`.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-14 (Asia/Bangkok; decision received in conversation).
