# T026 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)\
Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T026`\
Status: implementation preflight; no T026 source edits at preflight.

## Contract and acceptance

- Outcome: implement `POST /api/v1/appointments/{id}/reassign` for Receptionist only.
- Only `CONFIRMED`; appointment start before `Clock.instant()` rejects with `BOOKING_TIME_IN_PAST`; equality is allowed.
- Replacement Doctor must differ from current Doctor and be in the active directory, accepting new bookings, actively assigned to the appointment's service, and available at the exact original slot.
- Preserve appointment identity, Patient, service, purpose, start/end, duration/buffer snapshots, source and status. Client supplies only `doctorId` and mandatory nonblank reason.
- Acquire shared coordination guard before appointment row lock and all policy/capacity reads. Update same row and append immutable `DOCTOR_REASSIGNED` audit in one transaction. Failure leaves Doctor/status/occupancy/audit unchanged.
- Non-goals: T024 reschedule, T025 cancellation, T029+ lifecycle, schema migration, other owner writes/contracts, frontend, production/Supabase, commit/push.

## Scout summary

- Java 21 Spring Boot feature-first modular monolith. T020 already exposes the approved reassignment availability projection using active assignment IDs, accepting active-directory Doctors, stored snapshots, and self-excluding slot engine.
- T022/T024/T025 establish Appointment guard/transaction/JDBC audit patterns. Existing Staff and Service/Catalog public ports expose replacement eligibility without cross-feature persistence reads.
- V008 already allows `DOCTOR_REASSIGNED`; V013's exclusion constraint protects Doctor occupancy. No migration is expected.
- D07 excludes a Doctor removed from active directory as a replacement; account lock alone does not establish clinical unavailability. T032 protected owner mutations remain downstream.

## Shadow plan and boundary

1. Add command/use case/handler and Appointment-owned locked projection/update port.
2. Add Receptionist-only endpoint accepting exactly `doctorId` and `reason`.
3. Under one transaction acquire guard, lock current appointment, require CONFIRMED and start >= now, validate different replacement, current active assignment and accepting active-directory membership, then run exact same-instant slot availability using stored snapshot and excluded appointment ID.
4. Update only doctor_profile_id and updated_at; append immutable audit with old/new Doctors, same status/interval, reason, actor/time/correlation.
5. Add focused handler and local PostgreSQL HTTP tests for same-slot success, before/equal-now boundary, invalid replacement/state/role, collision and failure immutability; update trace/task evidence.

## Files and verification

- Appointment application, controller/error mapping and JDBC persistence adapter; focused unit and local PostgreSQL integration tests; this preflight, T026 execution record, review and trace.
- Focused: `./mvnw.cmd '-Dtest=ReassignAppointmentHandlerT026Test' test` from `backend`.
- PostgreSQL HTTP/full suite: `./scripts/test-local.ps1` from `backend`, local `127.0.0.1:5432/otcms_test_db` only.
- Grant reference: `G-feat-002-T026-A1`, attempt 1, direct cooperative reference. Runtime host replay enforcement is unverified. Consume immediately before first T026 source edit.
