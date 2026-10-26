# T025 Post-Code Review — A1

Date: 2026-10-14 (Asia/Bangkok)  
Status: COMPLETE — implementation review and Owner Long approval recorded.

## Scope reviewed

`POST /api/v1/appointments/{id}/cancel` cancels `CONFIRMED` appointments for the currently authorized Patient self profile or a Receptionist. Patient cancellation applies the configured change deadline and permits an omitted reason. Receptionist cancellation requires a nonblank reason and bypasses that Patient deadline. CHECKED_IN cancellation remains unavailable until T029 supplies an atomic Visit/Reception boundary.

## Review findings

- Guard is acquired before the appointment row is locked and current authorization/state are read.
- Persistence changes only appointment status and updated timestamp, then appends a `CANCELLED` audit in the same Spring transaction. Audit includes actor, old/new state, Doctor, old/new interval, optional/required reason, visit references, timestamp and correlation.
- Request parser rejects unsupported fields and derives role/account from the authenticated principal. Patient out-of-scope appointments return the scoped 404.
- No schema or foreign persistence access was introduced. The existing V013 exclusion predicate releases occupancy only after status becomes CANCELLED.
- CHECKED_IN state is rejected; no partial visit/reception mutation is exposed before T029.
- No unresolved T025 implementation issue identified.

## Verification evidence

- Unit: `CancelAppointmentHandlerT025Test` passed 3/3.
- PostgreSQL HTTP: `CancelAppointmentT025PostgresIntegrationTest` passed 3/3, covering Patient success/audit/slot release, cross-profile denial, Receptionist reason/deadline override, and Patient deadline rejection.
- User-run local full suite: 197 tests, 0 failures/errors/skips (2026-10-14 21:49:54 +07:00); Visit T012 3/3.

## Human disposition

Owner Long approved implementation and trace on 2026-10-14 (Asia/Bangkok).
