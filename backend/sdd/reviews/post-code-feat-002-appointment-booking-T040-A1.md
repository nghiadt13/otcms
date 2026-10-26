# T040 Post-Code Review — A1

Date: 2026-10-14 (Asia/Bangkok)  
Status: APPROVED — implementation, verification evidence and independent review complete; Owner Long approved implementation and trace on 2026-10-14.

## Scope

Align FEAT-002 Appointment package layout with `backend/CONSTITUTION.md` ARCH-01 and approved Architecture Profile v1.3.0. No route, database schema, frozen integration contract, or intended business behavior change.

## Changes reviewed

- HTTP controllers and web support moved to `appointment.controller`.
- API response records moved to `appointment.dto.response`; conversion moved to `appointment.mapper.AppointmentMapper`.
- Appointment persistence implementations and persistence contracts moved to `appointment.repository`.
- Service contracts, commands, and result types moved to `appointment.service`; orchestration implementations moved to `appointment.service.impl`.
- Scheduling/booking value models remain in `appointment.domain`.
- Added `GetAppointmentsUseCase`; query controller now depends on that service interface, not the implementation.
- Repository result records and booking exceptions are in the domain boundary; query controllers receive service-owned search results rather than repository records. Check-in persistence returns `CheckedInAppointment`; only the controller mapper creates the API response DTO.
- Updated FEAT-002 Plan/TASKS and repository architecture guidance. No Constitution edit was needed; ARCH-01 already defines this target.

## Findings and verification limits

- No intended API or schema change was made. Request bodies remain parsed as `JsonNode` to preserve the existing strict-field and error behavior; typed Jakarta-validated request DTO conversion is outside this package-only migration and remains a known ARCH-01/API-01 follow-up.
- Static inspection found 77 production Appointment Java files matching their package paths, no remaining Appointment `application`/`adapter` package declarations/imports, no Appointment repository imports in controllers, and no Appointment service imports in repositories.
- Initial user runs of `./mvnw.cmd test` and `./mvnw.cmd clean verify` hit a compile error in the authorized replay branch of `CheckInAppointmentHandler`. The branch now maps the stored replay to `CheckedInAppointment`.
- Focused verification: `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,GetAppointmentsHandlerT023Test' test` — BUILD SUCCESS, 10 tests, 0 failures/errors/skips.
- User-run full local suite: reported BUILD SUCCESS, 211 tests, 0 failures/errors/skips, finished 2026-10-14 23:06:01 +07:00. The supplied summary did not include database/Flyway profile details or per-test class breakdown.
- Independent review initially found that `AppointmentBookingController.checkIn` translated `DataAccessException`, violating ARCH-01. Translation moved to `CheckInAppointmentHandler`.
- Re-review found that a catch inside the same `@Transactional` method would miss deferred-constraint failures raised during commit. Transaction execution was extracted to `CheckInAppointmentTransaction`, a separate Spring service with a public `@Transactional` method; the non-transactional `CheckInAppointmentHandler` catches failures after the transaction proxy completes and maps them to `DEPENDENCY_UNAVAILABLE`, preserving the endpoint's 503 contract. Controller no longer imports or handles persistence exceptions.
- Re-review confirmed no remaining ARCH-01 package-direction blockers. The stale Clean Architecture tree, prose, and example flow in `docs/architecture.md` were aligned with the layered structure.
- Owner Long's follow-up identified empty legacy directories and a malformed nested package path in the IDE view. Confirmed all contained zero files, removed them, and retained the active Appointment package. Static check after cleanup: 78 production Java files, 0 package/path mismatches.
- Post-review focused verification: `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,CheckInAppointmentHandlerTest' test` — BUILD SUCCESS, 5 tests, 0 failures/errors/skips; T027 handler 4/4 and facade mapping 1/1.
- Review limits/follow-ups: no PostgreSQL test deliberately triggers a deferred constraint at commit; current rollback coverage exercises an immediate persistence failure. Existing `*UseCase` names differ from the `*Service` naming examples in AGENTS.md; this is a naming consistency follow-up and did not block architecture approval.

## Human disposition

- Owner Long requested completion of independent review and approval of T040 implementation/trace on 2026-10-14 (Asia/Bangkok). Approved; T040 is complete.
