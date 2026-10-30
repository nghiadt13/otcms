# Requirement Traceability — FEAT-002 T015

- Date: 2026-10-14 (Asia/Bangkok).
- Task: T015 — Doctor occupied interval exclusion and occupied-until persistence.
- Sources: approved FEAT-002 `SPEC.md` §3.3, §5.2, §7, AC04; `CONTEXT.md` §4.4; `TASKS.md` T015.
- Verification: user-run local `backend/scripts/test-local.ps1` reported 120/120 tests passing with no failures, errors or skips at 2026-10-14 00:24:40 +07. `AppointmentT015PostgresIntegrationTest` passed 3/3 against local PostgreSQL. No production/Supabase database was accessed.

| Requirement | Implementation / evidence | Result | Limitation |
|---|---|---|---|
| Occupancy interval is `[start_time, end_time + buffer_minutes)` and exact adjacency is allowed | V013 persists `occupied_until`, maintained from appointment end and buffer; `tstzrange(..., '[)')` exclusion. Integration test confirms a booking inside the buffer is rejected and one beginning exactly at `occupied_until` is accepted. | PASS locally | None for this persistence slice. |
| Database protects concurrent/overlapping Doctor reservations for all four occupied statuses | GiST exclusion constraint covers CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED. PostgreSQL integration test confirms overlap rejection for confirmed, checked-in, in-progress and completed appointments. | PASS locally | End-to-end command concurrency belongs to the later booking workflow tasks. |
| Completed appointments retain the stored buffer and elapsed history is not erased by a time-based predicate | No `now()` predicate or cleanup is used; COMPLETED remains in the constraint predicate. The integration test verifies a completed appointment still rejects an overlapping range. | PASS locally | Future slots are naturally unaffected by non-overlapping historical ranges. |
| Exclusion failure is rolled back and reported as SLOT_UNAVAILABLE | PostgreSQL test verifies transaction rollback after SQLSTATE 23P01; existing shared handler maps 23P01 to HTTP 409 `SLOT_UNAVAILABLE`, covered by its test in the passing suite. | PASS locally | Full endpoint command integration is delivered by later tasks. |
| Existing data and clean schema migration remain valid | Full local suite passed after Flyway applied V013; T015 tests migrate isolated schemas and exercise inserts/state changes. | PASS locally | Production extension capability/permissions have not been verified. |

## Human Review

- Status: APPROVED — requirement trace.
- Decision: `APPROVE implementation and trace T015`.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-14 (Asia/Bangkok; decision received in conversation).
