# T025 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)\
Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T025`\
Status: implementation preflight; no T025 source edits at preflight.

## Contract and acceptance

- Outcome: implement `POST /api/v1/appointments/{id}/cancel` for `CONFIRMED` appointments only.
- Patient: current linked self-profile only; existing Patient change deadline applies with equality allowed; optional nonblank reason up to 1000 chars.
- Receptionist: may cancel `CONFIRMED` regardless of the Patient deadline; nonblank operational reason up to 1000 chars is mandatory.
- Both: current appointment authorization/state read under `CoordinationGuardPort`, row lock after guard, atomic status update and immutable `CANCELLED` audit; preserve appointment identity, Doctor, service, purpose, times, snapshots, source and visit references; any failure preserves state and audit.
- `CHECKED_IN` cancellation is withheld until T029 supplies the reviewed Visit/Reception atomic cancellation boundary. Do not add an endpoint branch, stub, or partial cancel.
- Non-goals: T026 reassignment; T027/T029 lifecycle work; visit/reception changes; schema migration; frontend; production/Supabase; commit/push.

## Scout summary

- Java 21 / Spring Boot 4 modular monolith, feature-first Clean Architecture.
- T024 supplies the Appointment command/controller/exception conventions and T022 the guard-first transaction and atomic audit pattern.
- Appointment status and audit are Appointment-owned; Patient authorization and coordination guard are consumed through public ports. No foreign repository/entity access is needed.
- V008 already permits `CANCELLED` and `CANCELLED` audit action; V013 occupancy excludes cancelled rows. No migration is expected.
- T029 is incomplete; cancellation-after-check-in must remain unavailable.

## Shadow plan and boundary

1. Add cancellation command/use case/handler and Appointment-owned write port.
2. Add `POST .../{id}/cancel`, allow only `reason`, derive actor/account from the authenticated principal, and return existing `AppointmentResponse`.
3. In one guarded transaction lock appointment, perform Patient self authorization or Receptionist authorization, require CONFIRMED, apply Patient deadline, then update status and insert audit atomically.
4. Map Patient deadline to 422, unsupported state to the SPEC 409 `APPOINTMENT_NOT_CHANGEABLE`, cross-profile/missing to scoped 404, and preserve correlation ID.
5. Add focused unit coverage and local PostgreSQL HTTP tests for success/audit, actor rules, exact deadline and failure immutability; record trace and task evidence.

Files: appointment application, web and JDBC persistence; focused unit and PostgreSQL integration tests; this preflight, T025 execution record, post-code review and trace. No other feature/schema/contract changes.

## Verification

- Focused: `./mvnw.cmd '-Dtest=CancelAppointmentHandlerT025Test' test` from `backend`.
- PostgreSQL HTTP/full suite: `./scripts/test-local.ps1` from `backend`; fixed local DB `127.0.0.1:5432/otcms_test_db` only.
- Grant reference: `G-feat-002-T025-A1`, attempt 1, direct cooperative reference. Runtime host replay enforcement is unverified. Consume immediately before first T025 source edit.
