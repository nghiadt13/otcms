# FEAT-002 T040 Architecture Migration Trace — A1

Date: 2026-10-14 (Asia/Bangkok)  
Task: Align Appointment packages to Spring layered architecture  
Status: APPROVED — focused and user-reported full-suite verification passed; independent review complete; Owner Long approved implementation and trace on 2026-10-14.

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
|---|---|---|
| Constitution ARCH-01: controller -> service -> repository -> entity | Appointment endpoints are in `appointment.controller`; query controller depends on `GetAppointmentsUseCase` and service-owned query results; service implementation owns orchestration/transactions; persistence contracts/implementations are in `appointment.repository`; pure result/value types remain in `appointment.domain`. Check-in uses a non-transactional service facade around a separate transactional worker so commit-time persistence failures map to the specified service error. | Independent package/dependency review; focused post-review tests; user-reported full suite. |
| API-01: DTO boundary, no persistence entity returned | Response records live under `appointment.dto.response`; `AppointmentMapper` maps service-owned results to API DTOs. | Static review. Existing request parsing remains JsonNode to preserve behavior; typed Jakarta-validated request DTO remains an identified follow-up. |
| Feature ownership and cross-module isolation | Appointment-owned JDBC stays in its repository package; Visit/Auth/service/staff contracts remain consumed through the approved integration/service boundary. No schema or shared contract change. | File/package diff review; architecture test not run. |
| Existing API and persistence behavior preserved | Routes, SQL, schema, Flyway migrations, security annotations, and request field handling were not intentionally changed. Check-in result is separated from HTTP response DTO. | Compilation and focused/full-suite verification below; no production database access. |

## Execution evidence

- All 77 production Appointment Java package declarations match their source paths. No Appointment `application` or `adapter` package declarations/imports remain; controllers do not import repositories and repositories do not import Appointment services.
- Initial user `test` and `clean verify` runs failed to compile due to replay result type mismatch in `CheckInAppointmentHandler`; fixed by mapping the persisted replay into `CheckedInAppointment`.
- Focused verification: `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,GetAppointmentsHandlerT023Test' test` — BUILD SUCCESS, 10 tests, 0 failures/errors/skips.
- User-reported local full suite: BUILD SUCCESS, 211 tests, 0 failures/errors/skips; finished 2026-10-14 23:06:01 +07:00. Database/Flyway profile and per-test breakdown were not present in the supplied summary. This run predates the final service transaction-boundary adjustment.
- Initial independent review found persistence-exception translation in the HTTP controller and that a same-method transactional catch would miss deferred-constraint failures at commit. Both were corrected: the controller now calls the use case and maps the response; `CheckInAppointmentHandler` catches exceptions emitted by the separate `@Transactional` `CheckInAppointmentTransaction` proxy, mapping them to `DEPENDENCY_UNAVAILABLE`/503. Re-review confirmed no remaining ARCH-01 dependency-direction blocker.
- Owner follow-up found empty `appointment/adapter`, `appointment/application`, and a nested duplicate package path in the IDE tree. Verified each path had no source files and removed the empty directories. The active package remains `fu.fa26.sep490.g58.otcms_be.appointment`; post-cleanup check: 78 files, 0 package/path mismatches.
- Post-review focused command `./mvnw.cmd '-Dtest=CheckInAppointmentHandlerT027Test,CheckInAppointmentHandlerTest' test` — BUILD SUCCESS, 5 tests, 0 failures/errors/skips (T027 handler 4/4; facade mapping 1/1).
- Review limits: no dedicated PostgreSQL test deliberately fails a deferred constraint at transaction commit; existing rollback coverage exercises an immediate persistence failure. `*UseCase` naming vs `*Service` examples in AGENTS.md is a consistency follow-up, not a dependency-boundary blocker.
- No database, migration, production/Supabase, API route, or frozen cross-feature contract change.

## Disposition

Post-code review: `sdd/reviews/post-code-feat-002-appointment-booking-T040-A1.md`. Owner Long requested “hoàn tất independent review và phê duyệt implementation/trace T040” on 2026-10-14 (Asia/Bangkok); approval recorded. T040 is complete.
