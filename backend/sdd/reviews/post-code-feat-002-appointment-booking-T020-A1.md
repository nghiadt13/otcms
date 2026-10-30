# Post-code review — FEAT-002 T020 A1

- Status: COMPLETE — implementation, independent review, dedicated HTTP verification, and Owner Long approval are recorded.
- Requirement/task: approved SPEC v0.8.0 §4.2, §6 error contract, REQ-BOOK-004/010–014/023–026/033–035/049/050; TASKS v0.3.0 T020; approved PLAN v0.4.0 Phase 4; CONTEXT D05–D08.
- Scope: authenticated reschedule/reassignment availability GET endpoints, Appointment-owned minimal read projection, current Patient self authorization, T018 snapshot slot engine and approved T018 owner projections. No command mutation, schema migration, or new shared contract.

## Implementation

`GET /api/v1/appointments/{id}/reschedule-availability` accepts only Patient and Receptionist. It loads the appointment-owned context, authorizes a Patient against the currently linked self profile before disclosing status, requires CONFIRMED, enforces the Patient old-start change deadline and new notice (Receptionist bypasses notice), and generates same-Doctor availability using persisted service/duration/buffer snapshots with exact appointment self-exclusion. Existing-service deactivation and Doctor active-directory removal do not convert this into a new selection flow.

`GET /api/v1/appointments/{id}/reassignment-availability` is Receptionist-only. It requires CONFIRMED and stored start >= Clock now, intersects active service assignments with active-directory accepting Doctors, excludes the current Doctor, and returns only availability at the appointment's original instant using stored snapshots and self-exclusion. No mutation occurs.

The appointment query adapter reads only Appointment-owned appointment fields plus Doctor display name for its response. Staff/Catalog/Patient data remains behind existing public ports. Both endpoints use the shared `{slots: []}` envelope and errors follow SPEC §6.

## Verification

- Focused `GetAppointmentChangeAvailabilityHandlerTest`: 5 tests passed, 0 failures/errors/skips; Maven compilation completed.
- Initial Codex-shell full-suite attempt could not authenticate to PostgreSQL because datasource configuration resolved the literal username `${OTCMS_TEST_DB_USERNAME}`; no pass was claimed from that attempt.
- Owner-provided local `scripts/test-local.ps1` result on 2026-10-14 supersedes the initial environment limitation: `BUILD SUCCESS`, 151 tests, 0 failures/errors/skips; `VisitT012PostgresIntegrationTest` passed 3/3. The successful run used the configured dedicated local PostgreSQL test database. No production/Supabase connection was used.
- In response to Owner Long's request for dedicated HTTP assertions, added `AppointmentChangeAvailabilityT020PostgresIntegrationTest` with route success, DTO/privacy, Patient self-scope, role gates, deadline/past/state errors, inactive-service/removed-provider reschedule, and same-slot eligible reassignment coverage. Owner-provided local `scripts/test-local.ps1` result after adding this class: `BUILD SUCCESS`, 155 tests, 0 failures/errors/skips; `VisitT012PostgresIntegrationTest` passed 3/3. This supersedes the earlier shell authentication failure.
- Independent review found initial error-code mismatches (`RESOURCE_NOT_FOUND`/`CHANGE_DEADLINE_PASSED`) versus SPEC's `APPOINTMENT_NOT_FOUND`/`APPOINTMENT_CHANGE_TOO_LATE`, and an unused catalog dependency. All were corrected. Reviewer found no remaining concrete implementation blocker in the reviewed paths.

## Pending closeout

Owner Long approved implementation and trace on 2026-10-14. Dedicated HTTP assertions and full local PostgreSQL-backed suite passed 155/155; T020 is complete.
