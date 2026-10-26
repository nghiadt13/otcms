# T027 Post-Code Review — A1

Date: 2026-10-14 (Asia/Bangkok)  
Status: APPROVED — implementation and local PostgreSQL/full-suite verification passed; Owner Long approved on 2026-10-14 (Asia/Bangkok).

## Scope reviewed

Receptionist-only `POST /api/v1/appointments/{id}/check-in` with required `Idempotency-Key`, inclusive configured window, single scheduled Visit and initial reception, appointment transition/link, immutable audit, and replay evidence in one guarded transaction.

## Review findings

- Endpoint derives actor from the authenticated principal and restricts access to Receptionist. It accepts only `{}` and validates the idempotency header before invoking the use case. CORS now allows `Idempotency-Key` for browser clients.
- Appointment handler acquires `CoordinationGuard` before replay lookup and row lock. A matching authorized replay returns stored identifiers/time before current state or window checks; a fingerprint mismatch returns `IDEMPOTENCY_CONFLICT`. A non-replay progressed appointment cannot create another check-in.
- Appointment persistence owns the appointment row, audit and replay records. Visit persistence owns only scheduled visit and initial reception records and checks any recorded arrival against the appointment's patient. Cross-owner work uses `ReceptionWorkflowPort`; no cross-feature table query was added to Appointment.
- Visit adapter captures one checked-in instant, enforces inclusive bounds before creating records, and returns that timestamp for appointment audit/replay. Appointment transition/link, audit and replay then use that exact instant. The service transaction spans both adapters; a later persistence exception is mapped to `DEPENDENCY_UNAVAILABLE` after the transactional use case has rolled back.
- T027 uses existing V007–V010 schema and constraints; no migration or queue/walk-in behavior was added. No unresolved code issue was identified in the source review.
- Database-backed tests cover inclusive boundaries, replay after the window and progression, concurrent different-key requests, and injected audit failure/rollback. The user-run local helper completed successfully against local PostgreSQL.

## Verification evidence

- `CheckInAppointmentHandlerT027Test`: 4/4, zero failures/errors/skips (2026-10-14 22:23:14 +07:00).
- Maven compiled 232 production sources and 61 test sources.
- `CheckInAppointmentT027PostgresIntegrationTest`: 5/5, zero failures/errors/skips.
- User-run `./scripts/test-local.ps1`: BUILD SUCCESS, 211 tests, zero failures/errors/skips (2026-10-14 22:28:21 +07:00); Visit T012 PostgreSQL integration: 3/3.

## Human disposition

Owner Long decision: APPROVE implementation and trace T027, received 2026-10-14 (Asia/Bangkok).
