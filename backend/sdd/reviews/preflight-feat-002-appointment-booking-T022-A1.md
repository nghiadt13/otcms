# T022 preflight and Shadow Plan

## Request and expected outcome

Execute TASKS v0.3.0 T022: implement atomic Patient-online and Receptionist-assisted appointment creation with backend-derived actor/source, current authorization and eligibility checks, final slot/capacity recheck, immutable creation audit, and PostgreSQL-backed HTTP evidence.

## Constraints and non-goals

- Use only approved CONTEXT v0.16.0, SPEC v0.8.0, PLAN v0.4.0 and TASKS v0.3.0; follow D02/D03/D05/D07/D08/D11 and REQ-BOOK-003/005/006/010–018/044/049.
- Keep appointment, audit and booking transaction inside Appointment; acquire the T014 coordination guard first and re-read current Patient/service/Doctor/schedule/occupancy facts within that transaction.
- Reuse public owner ports and the T018/T019 availability engine. No cross-feature repository/entity/table access and no new shared contract unless a concrete contract gap is found; stop for owner review if a new frozen contract shape is necessary.
- Only `PATIENT_ONLINE` and `RECEPTIONIST_ASSISTED` are in scope. Doctor follow-up belongs to T030; no list/detail/change/check-in/status workflow, room reservation, notifications, queue, schema rewrite, production access, or Supabase access.
- Do not enable production booking; T031–T039 remain readiness/closure gates.

## Scout summary

- Backend is Java 21/Spring Boot REST, Maven modular monolith, feature-first Clean Architecture, PostgreSQL/Flyway. Approved validation commands are `.\mvnw.cmd test` and `.\mvnw.cmd clean verify`; DB integration uses only `scripts/test-local.ps1` and `127.0.0.1:5432/otcms_test_db`.
- T008, T014–T021 are checked complete. T008 current self-only authorization, T014 guard, T015 appointment/audit schema and occupancy exclusion, T016 Clock/policy configuration, T018 slot engine, T019 eligibility/query, T020 change projections and T021 limiter are available.
- Appointment currently has slot/read/change code and a JDBC occupancy query, but no appointment create input port, handler, write adapter, controller or create tests. `V008` already stores both booking sources, snapshots, creator/timestamps and append-only audits. `V013` enforces Doctor occupied-range exclusion.
- Patient, Service/Catalog, Staff and Visit expose their own public ports; the T009 neutral contracts and T018 availability contract are frozen. The current create API and response are fully specified in approved SPEC §4.1/4.3/6.
- No T022-specific contract/API/schema blocker was found. Patient's current-profile query is already exposed by the public `PatientProfileDirectory` interface; resolve it through an Appointment outbound adapter and re-authorize with the frozen Patient port. Existing patient-profile FK enforces a Receptionist-selected profile's existence without Appointment querying Patient-owned tables; map that specific FK failure to `PATIENT_PROFILE_NOT_FOUND` after transaction rollback.

## Shadow Plan

1. Define pure application input/result ports and a focused domain command value for the approved create contract. Validate required values and actor-specific inputs; reject client-supplied protected fields instead of silently ignoring them.
2. Implement the Appointment handler as one `@Transactional` command: validate actor role; acquire the T014 guard before owner reads; capture injected Clock after acquisition; derive an omitted Patient profile ID through the existing public `PatientProfileDirectory.findPatientProfile(accountId)` query and then re-authorize the resolved/supplied ID through `PatientAuthorizationPort`; derive source from actor role; resolve active service and accepting/assigned Doctor through owner ports; enforce current booking horizon, Patient notice, not-past and local grid rules; run the raw T018 `GenerateAvailableSlotsUseCase` for the selected Doctor/date/instant within the guarded transaction. This keeps all availability checks tied to the single Clock instant captured after the guard; then persist appointment snapshots and `CREATED` audit together.
3. Implement a JDBC Appointment write adapter using parameterized SQL and generated UUIDs; map database exclusion/guard errors through the existing T005 exception contract. Do not introduce JPA cross-module links or a migration unless the current V008/V013 schema demonstrably cannot satisfy approved fields.
4. Add thin `POST /api/v1/appointments` DTO/controller with `201`, `Location`, actor-only Patient/Receptionist authority, current error envelope and response DTO. Ensure source, creator, end time, snapshots, status and follow-up fields cannot be controlled by clients.
5. Add handler tests and `AppointmentBookingT022PostgresIntegrationTest` for role/source derivation, self-scope, Receptionist no-account profiles, service/Doctor eligibility, time/grid boundaries, exact HTTP/Location/DTO/error/correlation, audit/rollback, concurrent same-slot collision and forbidden payload fields.
6. Run focused Maven tests, secure local PostgreSQL suite, independent code review and trace. Record every failure/fix, clean/upgrade/migration result if applicable, residual production-readiness boundary, and request Owner Long review before closure.

## File boundary

- `backend/src/main/java/.../appointment/{domain,application,adapter/in/web,adapter/out/persistence}` — create-only Appointment behavior.
- `backend/src/main/java/.../config/SecurityConfig.java` only if explicit route-level security configuration is needed; the controller must still enforce role and ownership.
- Focused Appointment unit/PostgreSQL tests; `backend/sdd/reviews/` T022 preflight/post-code, `backend/sdd/traces/` T022 trace, and this feature's `TASKS.md` execution record.
- No changes to Patient/Service/Staff/Visit owner persistence, frozen shared contract shapes, applied migrations, frontend, or environment secrets.

## Checkpoint and exact verification

- TASKS v0.3.0 and the T022 inputs are APPROVED. No new API or schema checkpoint is needed for the already specified `POST /api/v1/appointments` contract and V008/V013 persistence. Any discovered need to change frozen shared contracts, migration semantics or booking policy pauses implementation for the relevant owner checkpoint.
- Focused: from `backend/`, `.\mvnw.cmd '-Dtest=CreateAppointmentHandlerTest,AppointmentBookingT022PostgresIntegrationTest' test` (PostgreSQL integration itself only through approved local test configuration).
- Full local DB suite: `.\scripts\test-local.ps1` from `backend/`, which invokes approved `.\mvnw.cmd test` against only loopback `otcms_test_db`. Do not inspect or print credentials.
- Final profile command `.\mvnw.cmd clean verify` only if required after the suite and within the approved local DB environment.

## Grant and review gates

- Governance resolves to `Project Ownership: team`, `Agent Execution: direct`, shared_context v3.3.7. Runtime identity/host enforcement remains UNVERIFIED.
- Task grant: `G-feat-002-T022-A1`, attempt 1, consumer `codex-direct-session-T022-A1` (cooperative reference; not host replay-prevention evidence). Persist as DISPATCHED/UNCONSUMED before action, then CONSUMED immediately before first source edit.
- Independent post-code review, test/debug review and Owner Long final implementation/trace approval are required. No commit/push or production/Supabase access.
