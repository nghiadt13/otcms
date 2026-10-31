# T022 Post-code Review — A1

Date: 2026-10-14 (Asia/Bangkok)\
Review scope: create-only Appointment implementation and focused tests. Independent reviewer: `/root/review_t021`; evidence reviewer: `/root/verify_t020`; debugger: `/root/t021_debugger`.

## Implementation reviewed

- `AppointmentBookingController` exposes `POST /api/v1/appointments`, permits only Patient and Receptionist authorities, derives the actor from `AuthenticatedAccount`, rejects fields outside the approved request contract, and returns `201`, `Location`, correlation ID, and the approved DTO.
- `CreateAppointmentHandler` acquires the T014 guard before owner reads, resolves and re-authorizes the Patient profile for Patient requests, derives source and audit actor from the authenticated account type, re-reads current service/provider/assignment eligibility, checks the configured time policy, and reuses the raw T018 slot use case for the exact selected Doctor/date/instant.
- `JdbcAppointmentBookingAdapter` inserts the appointment and `CREATED` audit through the same transactional command using parameterized SQL and maps only the patient-profile foreign-key failure to the scoped missing-profile response. No schema migration or frozen contract change was made.
- Zero-minute service buffer is accepted; this was corrected after independent review identified that zero is permitted by both slot context and schema.
- Purpose is trimmed before the 1000-character validation and persistence, matching the handler normalization and database column boundary. Independent follow-up review found no issue.

## Independent review result

The independent code review found no remaining concrete implementation blocker. It confirmed backend role/profile enforcement, server-derived source/creator, current eligibility and availability checks, and transaction-level appointment/audit persistence.

The debugger review found no likely fixture or MockMvc runtime issue by inspection: the requested slot is within the seeded shift, on the configured grid, and beyond Patient minimum notice. It also reviewed concurrency/rollback test design. This is source review only; the PostgreSQL HTTP tests have not yet run.

## Verification evidence and limitations

- `mvnw.cmd -DskipTests compile`: PASS.
- `mvnw.cmd -DskipTests test-compile`: PASS at 19:35:33 +07:00; all 51 test sources compile, including five T022 PostgreSQL methods.
- Focused `CreateAppointmentHandlerTest`: PASS, 3 tests, 0 failures/errors/skips at 19:31:20 +07:00 (rerun after purpose normalization).
- The PostgreSQL HTTP test source defines five methods covering Patient self-profile create/audit/response/correlation; Receptionist account-less profile create, duplicate slot conflict and forged-source rejection; other-profile denial and Doctor role denial; concurrent same-slot requests; forced audit insert failure and appointment rollback. Test cleanup removes only task-owned fixtures in a transaction and restores the audit trigger's original `tgenabled` mode.
- User-run approved `scripts/test-local.ps1` completed with BUILD SUCCESS at 2026-10-14 19:40:22 +07:00: 174 tests, 0 failures/errors/skips; Visit T012 passed 3/3. Surefire confirms `AppointmentBookingT022PostgresIntegrationTest` passed 5/5 and `CreateAppointmentHandlerTest` passed 3/3. Aggregating the 49 current Surefire XML reports yields the same 174/0/0/0 result; newest report timestamp is 19:40:22 +07:00. No credentials were read or printed here.
- The PostgreSQL class now has explicit tests for independent concurrent requests racing for one slot and for forced audit-insert failure followed by an appointment-row rollback assertion. The source uses the T014 guard and V013 exclusion. These test sources compile, but their runtime assertions remain unverified until the approved local PostgreSQL suite runs.
- Initial compile attempts exposed a missing Boot 4 JSON databind dependency and Jackson 3 API differences. These were corrected by adding the matching Boot Jackson starter and using `tools.jackson.databind.JsonNode`; the subsequent compile and test-compile passed.

## Disposition

Implementation review: no blocking findings. Owner Long approved `APPROVE implementation and trace T022` on 2026-10-14 after the local suite passed. T022 is complete. No commit or push was made.
