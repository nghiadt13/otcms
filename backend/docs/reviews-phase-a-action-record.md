# Reviews Phase A — Action Record

Scope: UC59–UC62 only, per the user's explicit Phase A implementation instruction.
Source order: supplied Jira/task decisions, supplied latest Report 3 confirmations, FE types/mock APIs, backend conventions.

Shadow Plan: add next Flyway migration, review persistence/history, own-profile and visit projections, ten review endpoints, scoped error contract, then unit/HTTP/PostgreSQL tests and affected backend tests.

File boundary: review feature; public patient/visit service projections; SecurityConfig public GET matcher; new migration; review tests and this record. Follow-up scope explicitly includes existing ErrorResponse compatibility and real PostgreSQL verification. No notifications, FE, existing migrations, commits or pushes.

Execution authorization: explicit user instruction in this conversation; this record does not impersonate a Human Final Review or approve an SDD artifact. The referenced `.claude/skills/_shared/ai-review-protocol.md` is absent in this checkout.

Baseline: branch feat/hung/reviews-notifications; clean tree; highest migration V043.
Verification uses `JAVA_HOME=C:\Program Files\Java\jdk-21`. The sandbox maps Maven's home incorrectly; approved execution outside the sandbox uses the repository Maven Wrapper without changing its files.

Deferred: UC63 and NotificationPublisher integrations. Backend notification domain must later include REVIEW; FE union mismatch remains for the FE integration task.

## Result and exact verification evidence

- Production compile: `.\mvnw.cmd -q -DskipTests compile` — PASS after correcting the Jackson import for the installed Jackson 3 API.
- Final Reviews: `.\mvnw.cmd '-Dtest=ReviewServiceTest,ReviewControllerTest,OwnPatientProfileServiceTest' test` — PASS, 32 tests, 0 failures/errors/skipped.
- Related regression: `.\mvnw.cmd '-Dtest=ReviewServiceTest,ReviewControllerTest,OwnPatientProfileServiceTest,T006HttpSecurityEvidenceTest,T010CommonSecurityContractTest,UC09AdministratorAccountControllerTest,PatientProfileServiceTest,PatientProfileReceptionVerificationT046Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitRoomCapacityT031Test,CreateWalkInVisitT043Test' test` — PASS, 54 tests at that revision (22 Reviews plus 32 existing tests). Subsequent changes only added ten Reviews test cases; final Reviews run passed all 32.
- PostgreSQL: `.\mvnw.cmd '-Dtest=ReviewServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest' test` — FAIL to initialize the integration context: SQLSTATE 28P01, local test database authentication rejected. Five integration cases could not execute. No credential was read from a secret file or changed.
- `git diff --check` — PASS.

The first test run also exposed a nested Mockito stubbing mistake and incorrect fieldErrors mapping for out-of-range rating integers. Both were fixed and covered by the passing final run; neither required a business-rule change.

## Scope delivered

Ten endpoints cover UC59–UC62. New tables: patient_reviews (UNIQUE visit_id, optimistic version, embedded current reply) and patient_review_moderation_history (append-only ORM/repository and database UPDATE/DELETE trigger). All history writes share the review mutation transaction. The initial ProblemDetail response was incorrect for the task and has been replaced with the existing ErrorResponse envelope: MSG in error.code, optional fieldErrors at root, no root code. Existing one-argument ErrorResponse constructor/factories remain compatible; empty fieldErrors are omitted.

New visit/appointment public read projections supply review context through service boundaries. Composition stays in review to avoid adding a visit-to-appointment dependency cycle. Existing production code changes are the public GET matcher in SecurityConfig and optional root fieldErrors in ErrorResponse; no appointment/visit write flows were changed.

The original database-authentication blocker is resolved; follow-up verification evidence is below.

Known source-data limitation: walk-in visits without an originating appointment have no service label in the existing visit schema, so serviceName remains an empty string; doctorName uses the latest care assignment when present. No labels are invented. Eligibility follows the explicit task decision CLOSED, including the existing production ambiguity that cancellation can also close a visit; this phase does not redefine visit lifecycle rules.

## Follow-up: error contract and real PostgreSQL verification

Root cause of SQLSTATE 28P01: the test profile reads OTCMS_TEST_DB_USERNAME / OTCMS_TEST_DB_PASSWORD, but neither was set in the Maven environment. The running otcms-postgres-test container has PostgreSQL 18.6, user postgres, database otcms_test_db, published at 127.0.0.1:5432. Container credentials were retrieved in memory and supplied only as process environment; no password is persisted or printed.

After authentication succeeded, stale target/classes migrations caused duplicate V026. Maven clean removed only generated build output. Then the existing public test schema failed Flyway validation on V020/V021 checksum mismatches. Its history was not changed or repaired. A new schema reviews_phase_a_verify_20261101 in the same otcms_test_db was used through temporary overrides:

- SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/otcms_test_db?currentSchema=reviews_phase_a_verify_20261101,public
- SPRING_FLYWAY_SCHEMAS=reviews_phase_a_verify_20261101
- SPRING_FLYWAY_DEFAULT_SCHEMA=reviews_phase_a_verify_20261101
- SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA=reviews_phase_a_verify_20261101
- OTCMS_TEST_DB_USERNAME / OTCMS_TEST_DB_PASSWORD from container environment

No test profile or production file was changed to solve authentication. No source migration was changed during this follow-up. No flyway repair, history edits, H2 substitution or disabled tests.

`.\mvnw.cmd '-Dtest=ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest' test`: PASS — 43 tests, 0 failures/errors/skipped; PostgreSQL subset 11/11 PASS, HTTP subset 6/6 PASS. Flyway applied V044 successfully from an empty isolated schema. Real unique-violation testing exposed an additional JDBC exception shape; the Reviews advice now maps SQLSTATE 23505 only for the named visit uniqueness constraint to 409 MSG-56.

PostgreSQL coverage: migration/table/FK/index metadata; actual FK rejection; UNIQUE visit rejection; actual version increments; stale optimistic update; both database exceptions passed through HTTP advice with error.code=MSG-56; exact SUBMIT/REPLY/EDIT_REPLY/APPROVE/HIDE/UNHIDE history sequence; UPDATE and DELETE history rejected; explicit rollback removes review plus history; history FK failure rolls back the review transaction. Transactional fixtures are rolled back and the old schema/history is preserved.

Actual HTTP MSG-57 response captured at target/reviews-error-response.json: error contains code/message/timestamp/correlationId/impact; fieldErrors is at root; root code is absent. FE client currently maps nested error.code into ApiError.errorCode, while mock MSG errors use ApiError.code. That FE integration mismatch is recorded, without changing FE.

Full `.\mvnw.cmd verify` with the same ephemeral environment overrides: FAIL at Surefire — 459 tests, 6 failures, 3 errors, 0 skipped (450 passed). Reviews remained 43/43 PASS, including PostgreSQL 11/11. No unrelated source/tests were changed. Failures outside Reviews:

| Test | Actual failure |
| --- | --- |
| AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks | Its V019 upgrade fixture inserts LAB_TECHNICIAN, rejected by ck_accounts_type in that older schema. |
| T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter | Failed count after attempt 3: expected 3, actual 2. |
| T008PasswordRecoveryPostgresIntegrationTest.changingPasswordInvalidatesPreviouslyIssuedResetLink | Mock expected sendPasswordReset; execution called sendStaffPasswordReset. |
| UC07AdministratorAccountPostgresIntegrationTest.migrationPreservesPatientEmailAndLegacyStaffAccountsWithoutUsernames | Account insert exceeds varchar(16). |
| UC09AdministratorAccountPostgresIntegrationTest.databaseSearchFiltersRoleStatusAndPagesAndHidesEveryOtherRole | Account insert exceeds varchar(16). |
| T032AdminResourceMutationPostgresIntegrationTest.mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites | Expected RESOURCE_MUTATION_BLOCKED, actual DOCTOR_RESOURCE_IN_PAST. |
| T032AdminResourceMutationPostgresIntegrationTest.adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits | Expected HTTP 409, actual 400. |
| T032AdminResourceMutationPostgresIntegrationTest.checkedInAppointmentAlsoBlocksTimeOffCreation | Expected HTTP 409, actual 400. |
| WalkInReceptionQueueT045PostgresIntegrationTest.rejectsInvalidInitialAndExitStatesAndProvidesFifoIndexes | Expected public-schema index count 1, actual 0. The test hard-codes schemaname='public'; verification uses the isolated schema. |

Evidence logs: target/reviews-final-postgres.log (separate full Reviews suite), target/reviews-full-verify.log (whole backend), target/reviews-postgres-only.log (separate PostgreSQL-only rerun). Public schema V020 checksum 1595489762 and V021 checksum -1130579379 remained unchanged. V044 success was independently confirmed from the new schema's Flyway history. Final PostgreSQL-only command `.\mvnw.cmd '-Dtest=ReviewPostgresIntegrationTest' test`: PASS — 11 tests, 0 failures/errors/skipped, BUILD SUCCESS.

Final git status: branch feat/hung/reviews-notifications; two modified tracked files (common/api/ErrorResponse.java and config/SecurityConfig.java), 29 untracked new files from Phase A (review feature, appointment/visit read projections, V044, four review test classes and this record). No commits or pushes.

Sync-back: PostgreSQL Reviews verification is now PASS. No SDD artifact has been marked APPROVED by the agent. No commit/push, FE changes, notification schema/API or publisher integration.
