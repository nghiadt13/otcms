# FEAT-002 T023 Trace — A1 (Approved)

Date: 2026-10-14 (Asia/Bangkok)  
Status: COMPLETE — implementation, independent review, local PostgreSQL HTTP/full-suite verification, and Owner Long approval recorded.

## Requirement-to-code trace

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Patient reads only the currently linked, self-authorized profile's appointments. | `GetAppointmentsHandler` resolves the current Patient profile through `CurrentPatientProfileQueryPort`, calls `PatientAuthorizationPort`, and sends PATIENT scope to the query port. | `GetAppointmentsHandlerT023Test`; `AppointmentQueryT023PostgresIntegrationTest` patient list/detail cases (passed 6/6). |
| Doctor reads only appointments for their current Doctor profile; locked/non-active Doctor account cannot access Doctor operations. | Auth `DoctorAccountAccessPort` checks current account type/status and exposes only an active boolean. Handler denies before resolving Staff profile; current profile comes through `StaffDirectoryPort`; SQL applies Doctor profile scope. | `GetAppointmentsHandlerT023Test`; locked Doctor HTTP case in `AppointmentQueryT023PostgresIntegrationTest` (passed 6/6); frozen `doctor-account-access-read` v1.0.0. |
| Receptionist can read clinic-wide appointments; Admin/Owner and other roles have no general read access. | Receptionist gets CLINIC scope. Other authenticated roles receive correlated `ACCESS_DENIED` 403; anonymous requests remain gated by the security filter chain. | `AppointmentQueryT023PostgresIntegrationTest` role/list cases (passed 6/6). |
| Missing and out-of-scope detail are indistinguishable; prevent data leakage during reassignment. | One parameterized SELECT includes appointment ID and Patient/Doctor owner predicate. Read handler uses REPEATABLE_READ for identity/scope and query consistency. Out-of-scope/missing returns `APPOINTMENT_NOT_FOUND` 404. | Independent reviewer confirmed the former two-query TOCTOU is closed; handler test and HTTP detail cases (passed in 6/6 PostgreSQL HTTP tests). |
| Date filters independently optional, clinic-local, inclusive, invalid ranges rejected; no history horizon. | Controller converts `from` to local-day start inclusive and `to` to next local-day start exclusive, validates parse/range/overflow, and passes UTC instants. JDBC query intersects filters with actor scope. No booking horizon is applied. | HTTP from/to/inclusive/invalid-range cases in `AppointmentQueryT023PostgresIntegrationTest` (passed 6/6). |
| Pagination and response contract. | `page>=0`, default page 0; `size` default 20 and bounded 1..100; deterministic `start_time DESC,id DESC`; Page response has `items,page,size,totalElements,totalPages`. DTO matches SPEC §4.1 and omits account/contact/clinical records. | Handler unit test; HTTP pagination/detail DTO assertions (passed 6/6). |

## Review and validation record

- `./mvnw.cmd '-DskipTests' test-compile`: BUILD SUCCESS; 210 production and 53 test sources compiled.
- `./mvnw.cmd '-Dtest=GetAppointmentsHandlerT023Test' test`: BUILD SUCCESS; 6 tests, 0 failures/errors/skips, 2026-10-14 20:43:12 +07:00.
- Independent implementation, debugger and fixture reviews found no remaining concrete findings. The initial detail-query TOCTOU was fixed and independently re-reviewed.
- First user-run approved local helper was pinned to `127.0.0.1:5432/otcms_test_db`, Flyway validated v013, and the suite ran 186 tests with 2 errors in T023 date filtering due to PostgreSQL not inferring a type for `Instant` parameters. Both bounds now bind as `Timestamp`; independent reviewer confirmed the correction. Successful user-run helper rerun completed at 2026-10-14 20:57:05 +07:00: BUILD SUCCESS, 186 tests, 0 failures/errors/skips. Aggregated 51 Surefire XML reports confirm 186/0/0/0; T023 PostgreSQL HTTP tests 6/6 and handler unit tests 6/6. Flyway validated v013.
- No migration, production/Supabase access, commit or push. Only the Human-approved additive Auth account-access read contract v1.0.0 was added; account status mutation is outside scope.

## Human disposition

Owner Long approved the Auth-owned read-only Doctor account-access port checkpoint on 2026-10-14. Owner Long approved `APPROVE implementation and trace T023` on 2026-10-14 (Asia/Bangkok). T023 is complete.




