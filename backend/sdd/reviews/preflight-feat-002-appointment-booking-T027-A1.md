# T027 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)\
Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T027`\
Status: implementation preflight; no T027 source edits at preflight.

## Outcome and acceptance

Implement atomic Receptionist check-in for a confirmed appointment. `POST /api/v1/appointments/{id}/check-in` accepts an empty JSON object and required `Idempotency-Key`, returns `CheckInResponse`, and uses the persisted inclusive early/late window configuration.

- Only Receptionist; Patient identity comes only from the locked appointment.
- First execution requires `CONFIRMED`, no inconsistent existing visit link, and current instant within `[start - earlyAllowance, start + lateGrace]` inclusive.
- Appointment transition/link, scheduled Visit, initial reception, immutable appointment audit and replay record commit/rollback together.
- Same actor/appointment/key and request fingerprint replays the stored result after current authorization, before current state/time checks; it never resets a progressed lifecycle. Same key with a different fingerprint returns `IDEMPOTENCY_CONFLICT`.
- Different keys and concurrent retries cannot create duplicate visit/reception/audit rows. Invalid existing linkage returns `VISIT_LINK_CONFLICT`; mandatory failures leave no partial success records.
- No queue entry, synthetic appointment, migration, other lifecycle command, frontend, production/Supabase access, or commit/push.

## Scout summary

- Java 21 Spring Boot modular monolith; Appointment booking HTTP/application/persistence are feature-owned. Visit has no check-in workflow implementation yet.
- `ReceptionWorkflowPort.checkInAtomically` is the existing public integration contract. `AppointmentCapacityPort` and `CoordinationGuardPort` show neutral contract conventions. Existing V007 visit/reception schema and V008 appointment audit/replay schema already support the needed records and constraints; V009/V010 protect visit-link and reception evidence. No schema change is expected.
- `AppointmentBookingProperties` binds early and late allowances; shared guard is acquired before owning-row lock. Existing Appointment handlers and JDBC adapters establish guard-first transactional behavior.
- Accepted PLAN §3.3 and SPEC §4.1/4.3/5.3 define replay ordering, status, inclusive window, unique link and rollback behavior. T012–T017 and T022 dependencies are complete; T027 owns cross-boundary orchestration.

## Implementation plan

1. Add Appointment check-in command/use case/handler and persistence port. In one transaction: acquire guard; resolve matching authorized replay before mutable state/window checks; lock appointment; reject inconsistent linkage/state; derive inclusive window; ask the public ReceptionWorkflowPort to create scheduled visit and initial reception; update appointment status+visit, append audit and store replay using the returned checked-in instant.
2. Implement the visit-owned adapter behind `ReceptionWorkflowPort`; it captures the shared Clock instant, enforces the supplied inclusive bounds before writes, and creates only Visit and initial reception records. Use the same Spring transaction manager and never access appointment tables from Visit persistence.
3. Add the Receptionist-only endpoint, strict empty-object parsing, required bounded `Idempotency-Key`, DTO and error mappings. Actor comes from authenticated principal; recorded `arrivedAt` is retained when present, otherwise it defaults to the Visit-captured `checkedInAt`.
4. Add handler and PostgreSQL HTTP tests for inclusive boundaries, replay after progression, changed payload/key conflict, different-key serialization, one-visit uniqueness, invalid links and rollback. Update T027 execution record, review and trace.

## Verification

- Focused handler/controller tests through Maven Wrapper from `backend`.
- Authenticated local PostgreSQL HTTP tests and full suite through `./scripts/test-local.ps1`, only against `127.0.0.1:5432/otcms_test_db`; do not inspect credentials or contact production/Supabase.

## Grant

`G-feat-002-T027-A1`, attempt 1, direct cooperative reference; runtime replay enforcement unverified. Consume immediately before first T027 source edit.
