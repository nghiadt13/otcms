# FEAT-002 T022 Trace — A1

Date: 2026-10-14 (Asia/Bangkok)\
Task: Patient online and Receptionist-assisted atomic appointment creation\
Status: COMPLETE — local PostgreSQL execution and Owner Long approval recorded.

## Requirements and implementation mapping

| Requirement / contract | Implementation | Evidence |
| --- | --- | --- |
| REQ-BOOK-003 / API §4.1: create via `POST /api/v1/appointments`, 201 + Location | `AppointmentBookingController` parses the approved body and returns `AppointmentResponse` | `AppointmentBookingT022PostgresIntegrationTest` verifies Patient and Receptionist HTTP responses; passed |
| REQ-BOOK-005 / D03: Patient books only the current linked self profile | `AuthCurrentPatientProfileAdapter` uses Auth's public directory API; handler re-authorizes omitted or supplied profile through frozen Patient port | Handler unit and PostgreSQL HTTP scope tests passed; full PostgreSQL class passed 5/5 |
| REQ-BOOK-006: Receptionist can select an existing profile without an account | Receptionist request requires `patientProfileId`; appointment FK validates existence without Appointment querying Patient-owned data | PostgreSQL `receptionistCanBookAccountlessProfileAndClientCannotForgeSourceOrCreator` passed |
| REQ-BOOK-010–018: actor/source, service/Doctor eligibility, notice, horizon, grid and availability | Handler derives `PATIENT_ONLINE` or `RECEPTIONIST_ASSISTED`, checks current owner projections/time policy, and runs the exact T018 slot computation | Handler unit suite passed 3/3; PostgreSQL HTTP class passed 5/5 including role/source, slot collision and concurrent creation |
| REQ-BOOK-044/049 and V008/V013: appointment snapshots, occupied interval, immutable `CREATED` audit and overlap protection | Parameterized JDBC insert plus audit insert in the handler transaction; DB trigger computes occupied-until and V013 exclusion protects overlaps | PostgreSQL response/audit assertions, concurrent collision and audit-failure rollback passed |
| API §4.1 error envelope and protected server-owned fields | Controller rejects unknown fields; scoped advice returns safe errors and correlation ID | PostgreSQL forged-source rejection, cross-profile scope and role denial assertions passed |

## Verification record

- Main/test compilation passed; focused handler suite passed 3/3, rerun at 19:31:20 +07:00 after purpose normalization.
- User-run approved `backend/scripts/test-local.ps1` completed with BUILD SUCCESS at 2026-10-14 19:40:22 +07:00: full suite 174 tests, 0 failures/errors/skips; Visit T012 3/3. Surefire confirms `AppointmentBookingT022PostgresIntegrationTest` 5/5 and `CreateAppointmentHandlerTest` 3/3. Across 49 Surefire XML reports the total is 174, 0 failures/errors/skips; newest report timestamp matches 19:40:22 +07:00.
- Independent implementation, debugger and evidence reviews found no blocking finding. PostgreSQL assertions cover concurrent independent requests and audit-write rollback at runtime.
- No production/Supabase DB, credentials, frontend route or follow-up workflow was touched. No migration or shared contract changed.

## Human decision

Owner Long decision: `APPROVE implementation and trace T022` — received 2026-10-14 (Asia/Bangkok). T022 is `[x]` in TASKS.md.
