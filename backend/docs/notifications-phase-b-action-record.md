# Notifications Phase B — Action Record

Scope: UC63 and transactional NotificationPublisher integrations explicitly requested by the user. Source order and scope refinements remain those recorded for Phase A. Branch feat/hung/reviews-notifications; no checkout, commit or push.

Shadow Plan: verify ErrorResponse compatibility and prior verify evidence; add next migration; implement scoped inbox and owner-provided related-record resolvers; attach publisher to existing successful write flows; run unit/HTTP, PostgreSQL, Reviews regression, relevant source-flow tests and full verify.

File boundary: new notification feature; auth-owned recipient directory; owner-provided resolvers in appointment/visit/review; five producer service implementations; constructor updates in relevant source unit tests; notification tests and this record. No global error refactor, FE edits, old migration changes or cancel/reschedule business-rule changes. Authorization is the explicit user Phase B instruction; no Human review identity or SDD approval is invented.

## B0 evidence

ErrorResponse retains error.code/message/timestamp/correlationId/impact, its one-argument constructor and existing factories. Optional root fieldErrors is omitted when empty. NotificationServiceTest verifies the legacy response serializes with exactly one root property (error), all five existing error fields, and identical JSON through the original constructor. Validation adds root fieldErrors without root code. No ErrorResponse changes were needed in Phase B.

Before any Phase B changes, Phase A full verify had 459 tests: 450 passed, 6 failures and 3 errors. The complete nine-case list and exact failure reasons are preserved in reviews-phase-a-action-record.md. These failures are confirmed pre-Phase-B, not claimed universally reproduced on an untouched HEAD runtime. Read-only git show HEAD also confirms the older V019 upgrade test iterates all current AccountType values, accounts.account_type starts as varchar(16), and the walk-in index test hard-codes schemaname='public'. No baseline checkout or alternate workspace was created.

## Database and API

Highest migration was V044 immediately before creation; no V045 existed. Added V045__create_notifications.sql with recipient FK, nullable read_at and related_kind including REVIEW. Recipient deletion cascades its dependent inbox records; related records are polymorphic references, checked dynamically rather than foreign keys. Recipient/time and partial unread indexes support inbox queries.

Endpoints: GET /api/v1/me/notifications; GET /unread-count; POST /{id}/read; POST /read-all. All take identity from AuthenticatedAccount. List/read/count/update operations always scope by recipient account; no client-supplied account selects scope. Missing and foreign notification both return 404 error.code=MSG-50; unexpected errors return 500 error.code=MSG-04 using shared ErrorResponse.

Default sort is createdAt DESC, id DESC. Page JSON uses items/page/size/totalItems/totalPages, default size 20, cap 100. Mark-read locks the owned row, preserves an existing timestamp and performs no second save. Read-all updates only the caller's unread rows.

## Dependency and transaction design

Notification owns NotificationRelatedRecordResolver; implementations live with appointment/visit/review owners. Notification does not import those modules. Resolver queries return booleans for missing/unauthorized records rather than catching transaction-marking denial exceptions. Infrastructure errors propagate safely. VISIT_RESULT/PAYMENT are supported enum/database values but fail closed (available=false) until an owner resolver is added; no producer was added for them.

NotificationPublisher uses MANDATORY propagation and saveAndFlush: no independent notification transaction. It resolves linked active Patient accounts and exact active ADMIN recipients through an auth public directory, excluding OWNER and inactive Admins from review notifications.

Attached producers:
- CreateAppointmentServiceImpl.create: APPOINTMENT_BOOKED.
- CreateFollowUpAppointmentServiceImpl.create: APPOINTMENT_BOOKED for the existing follow-up booking flow.
- ReassignAppointmentServiceImpl.reassign: APPOINTMENT_DOCTOR_REASSIGNED.
- WalkInVisitLifecycleServiceImpl.close: VISIT_CLOSED invitation; not its shared cancel helper.
- ReviewServiceImpl.submit: REVIEW_SUBMITTED to active ADMIN only, with review + SUBMIT history in the same transaction.

Cancel/reschedule mismatch: current code contains Receptionist cancel/reschedule, while the supplied latest Report 3 disallows those capabilities. Their existence is not treated as confirmation of product validity. No new endpoint, rule change or publisher attachment was added to those disputed flows. Scheduled care completion does not close the visit in current product code; no close workflow was invented.

FE mismatch: backend stores and returns REVIEW as required by Jira. FE NotificationRelatedRecord.kind/labels/link resolver still lack REVIEW and must be updated by the later FE integration task. No email/push/SMS or user-facing publication endpoint was added.

## Verification

JDK 21 and Maven Wrapper are used. PostgreSQL 18.6 is the existing otcms-postgres-test container on 127.0.0.1:5432/otcms_test_db. Container credentials are passed only in ephemeral OTCMS_TEST_DB_USERNAME/OTCMS_TEST_DB_PASSWORD process environment, never printed/persisted. Schema notifications_phase_b_verify_20261101 uses temporary datasource currentSchema + Flyway schemas/default-schema + Hibernate default-schema overrides, preserving older public schema/history and Phase A evidence.

Initial combined command `.\mvnw.cmd '-Dtest=NotificationServiceTest,NotificationControllerTest,NotificationPostgresIntegrationTest,ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest' test`: PASS 68/68 (25 notification tests including 14 PostgreSQL, 43 Reviews including 11 PostgreSQL). V045 successfully applied from the empty isolated schema. Subsequent transaction-safety cases and final regression/full-verify results are recorded below.

### Final verification

- `.\mvnw.cmd '-Dtest=NotificationServiceTest,NotificationControllerTest,NotificationPostgresIntegrationTest,ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest,CreateAppointmentServiceImplTest,ReassignAppointmentServiceImplT026Test,CreateFollowUpAppointmentT030Test,AppointmentBookingT022PostgresIntegrationTest,ReassignAppointmentT026PostgresIntegrationTest,CreateFollowUpAppointmentT030PostgresIntegrationTest,WalkInReceptionQueueT047PostgresIntegrationTest,CreateWalkInVisitT043Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitRoomCapacityT031Test' test`: PASS, 107 tests, zero failures/errors/skipped. Evidence: target/notifications-related-suite.log. Actual executed source-flow cases are listed in that log; the T047 selector has no matching class in this checkout.
- Notification tests: 28/28 PASS (7 unit including shared DTO compatibility, 4 HTTP, 17 actual PostgreSQL integration).
- Reviews regression: 43/43 PASS (including 11 actual PostgreSQL integration).
- Relevant Appointment/Visit regression: 36/36 PASS, including booking/reassignment/follow-up PostgreSQL tests.
- `.\mvnw.cmd verify`: FAIL at Surefire, 487 tests, 6 failures, 3 errors, zero skipped; 478 passed. Notifications 28/28 and Reviews 43/43 remained PASS in full verify. Evidence: target/notifications-full-verify.log.
- `git diff --check`: PASS.
- Read-only Docker/psql confirmation: V044 and V045 both success=true in notifications_phase_b_verify_20261101.flyway_schema_history.

The nine full-verify failure class/method pairs were compared programmatically with target/reviews-full-verify.log from before Phase B: BeforePhaseB=9, AfterPhaseB=9, NewFailures=0, RemovedFailures=0. This establishes no new failing test in this suite, while not claiming an untouched-HEAD runtime verification.

| Test class.method | Failure | Observed before Phase B |
| --- | --- | --- |
| AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks | V019 fixture LAB_TECHNICIAN rejected by ck_accounts_type. | Yes |
| T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter | Failed count after attempt 3: expected 3, actual 2. | Yes |
| T008PasswordRecoveryPostgresIntegrationTest.changingPasswordInvalidatesPreviouslyIssuedResetLink | Expected sendPasswordReset; sendStaffPasswordReset was invoked. | Yes |
| UC07AdministratorAccountPostgresIntegrationTest.migrationPreservesPatientEmailAndLegacyStaffAccountsWithoutUsernames | Account insert exceeds varchar(16). | Yes |
| UC09AdministratorAccountPostgresIntegrationTest.databaseSearchFiltersRoleStatusAndPagesAndHidesEveryOtherRole | Account insert exceeds varchar(16). | Yes |
| T032AdminResourceMutationPostgresIntegrationTest.mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites | Expected RESOURCE_MUTATION_BLOCKED, got DOCTOR_RESOURCE_IN_PAST. | Yes |
| T032AdminResourceMutationPostgresIntegrationTest.adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits | Expected HTTP 409, got 400. | Yes |
| T032AdminResourceMutationPostgresIntegrationTest.checkedInAppointmentAlsoBlocksTimeOffCreation | Expected HTTP 409, got 400. | Yes |
| WalkInReceptionQueueT045PostgresIntegrationTest.rejectsInvalidInitialAndExitStatesAndProvidesFifoIndexes | Hard-coded public-schema index count expected 1, got 0 under isolated schema verification. | Yes |

PostgreSQL tests verify recipient isolation (including Admin cross-account), newest ordering/unread filters/counts, idempotent mark-read with unchanged read_at and physical tuple after the second call, read-all counts, allowedActions, live missing/revoked record checks, no transaction poisoning on unavailable records, actual booking/reassignment/close/review producers, exact active Admin recipients excluding Owner, unlinked walk-in skip, rollback of notification with the source, and source appointment/audit or review/history rollback on actual PostgreSQL notification insert failure. Publisher rejects use outside a source transaction. Cancel emits no review invitation.

### Changed files and sync-back

New: notification feature (controller, DTO/entity/repository, related kind/message, inbox/publisher/resolver interfaces and implementations, scoped exception handler); auth NotificationRecipientDirectory + implementation; owner Appointment/Visit/Review notification resolvers; V045; NotificationServiceTest, NotificationControllerTest and NotificationPostgresIntegrationTest; this record.

Updated producer files: CreateAppointmentServiceImpl, CreateFollowUpAppointmentServiceImpl, ReassignAppointmentServiceImpl, WalkInVisitLifecycleServiceImpl, and Phase A's ReviewServiceImpl. Relevant unit tests only received the new required publisher constructor dependency; no assertions were weakened, no baseline failures were fixed or disabled. Phase A ReviewServiceTest also supplies a publisher mock.

Final workspace status includes both phases: 9 modified tracked files and 52 new untracked files. Phase A's ErrorResponse/SecurityConfig changes remain; no additional shared DTO or security change was made for Phase B. All changes remain in the requested backend workspace on feat/hung/reviews-notifications. No commit/push or notification channel integration beyond the database inbox.
