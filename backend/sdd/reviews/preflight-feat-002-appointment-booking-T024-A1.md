# T024 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)  
Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T024`  
Status: preflight only; no T024 source edits or tests yet.

## Brainstorm contract reused from approved requirements

- Outcome: implement ordinary appointment reschedule for Patient self-profile and Receptionist at `PATCH /api/v1/appointments/{id}/reschedule`.
- Constraints: stay within T024; use one guarded write transaction; update the same appointment row; preserve patient, Doctor, service, source, purpose and duration/buffer snapshots; re-check current authorization, deadline/notice, horizon/grid and slot/care capacity; append immutable audit in the same transaction; any failure must leave appointment/reservation/audit unchanged.
- Non-goals: cancellation T025, reassignment T026, check-in/care workflows, schema migration, frontend, production/Supabase, commit/push.
- Observable acceptance: Patient and Receptionist can reschedule only CONFIRMED appointments within their authority; Patient meets old change deadline and new online notice (equal boundaries allowed); Receptionist supplies a nonblank operational reason and may reschedule from now (equal boundary allowed); past targets reject; collision returns SLOT_UNAVAILABLE; old appointment remains unchanged on any failure; success persists new interval/same row and one RESCHEDULED audit with actor, reason, old/new interval, timestamp and correlation ID.

## Scout summary

- Project: Java 21 Spring Boot REST modular monolith; Appointment application/use-case and JDBC adapters, Spring MVC/Jackson 3, PostgreSQL/Flyway.
- Approved inputs: TASKS v0.3.0 T024; SPEC v0.8.0 §§3.3–3.4, 4.1/4.3, 5.2 and AC07/AC22; CONTEXT v0.16.0 §§3.4/5.2 and D03/D05/D07/D08; PLAN v0.4.0 Phase 4; Architecture Profile; `shared_context.md` v3.3.8.
- Dependencies T020/T022 are complete. T020 owns reschedule availability reads and already derives same-Doctor stored snapshots/self-exclusion; T022 establishes the guarded create and atomic audit pattern. T023 established authenticated scoped query/DTO/error patterns.
- Relevant existing public ports: `CoordinationGuardPort`, `PatientAuthorizationPort`, `StaffScheduleAvailabilityPort`, `VisitCareReservationQueryPort`; existing `GenerateAvailableSlotsUseCase` excludes an appointment ID and includes appointment plus active-care occupancy. Appointment-owned `AppointmentChangeQueryPort` currently supplies the reschedule availability projection. V008/V013 already provide RESCHEDULED audit fields and a Doctor occupied-range exclusion constraint; no migration is expected.
- Critical implementation rule: acquire the shared guard before reading/locking the Appointment row; use one transaction. Under guard/row lock, resolve current scoped booking, authorize current Patient profile or Receptionist, validate time policy, and re-run exact target-slot availability with the existing appointment excluded. Then update only start/end/updated time and insert audit. Database exclusion violations map to SLOT_UNAVAILABLE; transaction rollback prevents partial state/audit.
- Compatibility: `AppointmentResponse`, API error envelope, frozen contracts, V008/V013 schema stay unchanged. No Auth/Patient/Staff/Visit persistence access from Appointment.

## Shadow plan

1. Add T024 command/request/use-case/handler and Appointment-owned reschedule persistence port, with locked projection and atomic update+audit adapter.
2. Acquire `CoordinationGuardPort`; resolve the appointment for update; apply Patient current self-authorization (same scoped 404) or Receptionist authority; require CONFIRMED.
3. Validate actor-specific rules: Patient old-start deadline and target online notice; Receptionist mandatory reason and target >= now; both reject past, out-of-horizon, non-grid seconds/fractions; keep target and original booking facts immutable except the interval.
4. Reuse `GenerateAvailableSlotsUseCase` with stored service/duration/buffer snapshot, original Doctor/patient and `excludedAppointmentId`; accept only exact requested instant. Include visit care reservation occupancy and existing appointment occupancy; database exclusion is the final race guard.
5. Atomically update same row and insert RESCHEDULED audit with correlation. Map conflict/validation/authorization errors through a T024-specific correlated advice; do not expose an endpoint to other roles.
6. Add focused unit tests and local PostgreSQL HTTP integration tests for role/scope, deadlines/notices/now boundaries, time grid/horizon, exact self-excluding availability, success snapshot preservation/audit, collision rollback and failure immutability. Obtain independent implementation/debugger/test-evidence review; update trace/TASKS; seek Owner Long final approval.

## File boundary

- Appointment `application/`: reschedule command/input/use-case/handler and Appointment-owned write port/projection.
- Appointment `adapter/in/web/`: T024 PATCH route, strict role-specific JSON validation and correlated error handler; no changes to T025/T026 workflow.
- Appointment `adapter/out/persistence/`: locked Appointment projection, interval update and append-only audit insert, in one transaction.
- Appointment focused unit tests and `RescheduleAppointmentT024PostgresIntegrationTest`.
- `backend/sdd/reviews/`, `backend/sdd/traces/`, and FEAT-002 `TASKS.md` evidence.
- No migration, existing frozen-contract changes, other feature persistence access, production/Supabase, unrelated APIs, commit or push.

## Verification and grant

- Focused: from `backend`, `./mvnw.cmd '-Dtest=RescheduleAppointmentHandlerT024Test' test`.
- PostgreSQL HTTP/full suite: only `./scripts/test-local.ps1` from `backend`, pinned to `127.0.0.1:5432/otcms_test_db`; no credential inspection/output. User-run result is required for trace closure.
- Governance: `Project Ownership: team`, `Agent Execution: direct` from `shared_context.md` v3.3.8; host/runtime replay enforcement UNVERIFIED.
- Grant: `G-feat-002-T024-A1`, attempt 1, consumer `codex-direct-session-T024-A1`, cooperative reference only. Persist DISPATCHED/UNCONSUMED in TASKS now; consume immediately before the first T024 source edit. No prior attempt/source edit exists.
- Checkpoint: no new public contract/schema decision expected. Stop before implementation if approved rules cannot be met without changing a frozen owner contract, schema, or scope.
