# UC59–UC63 final audit

AI RECOMMENDATION: PENDING HUMAN REVIEW
Human Final Review: not recorded by the agent.

Audit scope: entire current tracked diff and all Phase A/B untracked files in the requested backend workspace, branch feat/hung/reviews-notifications. No commit/push, baseline repair, feature expansion or migration edit. The initial audit added missing Swagger metadata, the public-review OpenAPI security correction, and one generated-OpenAPI integration check. The subsequently authorized follow-up fixes only the two validation findings below.

## Findings and commit recommendation

No security, privacy, transaction-integrity or migration-application BLOCKER was found in the inspected flows. Both original SHOULD FIX validation findings are now resolved by the explicitly authorized follow-up:

1. **RESOLVED — Unicode length differs between Java and PostgreSQL.** At the original audit, String.length accepted five U+1F600 emoji as 10 UTF-16 units while PostgreSQL counted 5. ReviewServiceImpl.validText now trims once and uses codePointCount for review (10–1000) and reply (1–1000), matching PostgreSQL text length. Focused HTTP and actual PostgreSQL boundary tests now cover the original failure and maximum boundaries. V044/V045 remain unchanged.
2. **RESOLVED — JSON text scalar coercion.** The original DTO probe coerced numeric text to String. ReviewTextDeserializer now accepts only VALUE_STRING on the two review request text fields. Numeric/boolean/object/array tokens fail with 400 error.code=MSG-57 and root fieldErrors for text. Null/missing bypass token deserialization into application validation and receive the same validation contract. There is no ObjectMapper-global configuration or response DTO change.

Existing decisions retained: CLOSED corresponds to Completed even though legacy cancellation can also close visits; SELF-only review scope; empty serviceName when walk-in source data has no service label; no new cancel/reschedule capability. Deferred FE work: REVIEW related kind, consumption of MSG from nested error.code rather than assuming root code, and alignment of existing UTF-16 UI counters with the newly explicit code-point policy. VISIT_RESULT/PAYMENT have supported kinds but no owner resolver/producer in this task, so availability fails closed.

## Git hygiene

Changed tracked files contain only producer constructor/call changes, backward-compatible ErrorResponse fields, the public-review security matcher, one OpenAPI public matcher and necessary test constructor dependencies. Untracked files are task production code, two migrations, eight test classes and three task reports. No unrelated formatter/refactor, dependency change, credential file, root debug/temp file, generated commit candidate, important TODO/FIXME, debug print or new async/event/REQUIRES_NEW publisher path was found.

Key/private-key pattern scan returned no hits in changed/new files. Credentials remain process environment only; test data uses synthetic UUIDs/names and fake password hashes. Logs and generated JSON are under ignored target/, confirmed with git check-ignore. They are not commit candidates. There is no source file recommended for removal from the manifest. The three task reports preserve required Action Records/evidence; generated target files must remain excluded.

## Migrations

- V044: unique visit_id; four review FKs and two history FKs; patient/status/author/replier/history indexes; BIGINT NOT NULL DEFAULT 0 version aligned with JPA @Version; immutable history entity, append-only repository API and database trigger rejecting UPDATE/DELETE. Real PostgreSQL checks verify uniqueness/FKs/index metadata, version increments, stale writes and mutation/history rollback in both directions. Its existing content/reply length CHECKs now align with Java code-point validation.
- V045: recipient account FK with dependent inbox ON DELETE CASCADE; APPOINTMENT/VISIT/VISIT_RESULT/PAYMENT/REVIEW; related kind/id null-pair check; recipient-created and partial unread index; read_at null means unread. Both migrations are success=true in the isolated PostgreSQL verification schema. No source migration or existing schema history was changed in this audit.

## Fourteen endpoint inventory

| # | Method | Path | Scope |
| --- | --- | --- | --- |
| 1 | GET | /api/v1/me/reviews/eligible-visits | PATIENT, own profile |
| 2 | GET | /api/v1/me/reviews | PATIENT, own profile |
| 3 | POST | /api/v1/reviews | PATIENT, own CLOSED visit |
| 4 | GET | /api/v1/public/reviews | Anonymous, PUBLISHED only |
| 5 | GET | /api/v1/admin/reviews | ADMIN |
| 6 | GET | /api/v1/admin/reviews/{id} | ADMIN |
| 7 | POST | /api/v1/admin/reviews/{id}/approve | ADMIN |
| 8 | POST | /api/v1/admin/reviews/{id}/hide | ADMIN |
| 9 | POST | /api/v1/admin/reviews/{id}/unhide | ADMIN |
| 10 | PUT | /api/v1/admin/reviews/{id}/reply | ADMIN |
| 11 | GET | /api/v1/me/notifications | Current authenticated recipient |
| 12 | GET | /api/v1/me/notifications/unread-count | Current authenticated recipient |
| 13 | POST | /api/v1/me/notifications/{id}/read | Current authenticated recipient |
| 14 | POST | /api/v1/me/notifications/read-all | Current authenticated recipient |

Exactly these operations are added by the four task controllers. No additional task endpoint exists.

## JSON and errors

Direct FE types/mock comparison confirms Patient/Public text versus Admin content; approve/hide/unhide/reply/editReply casing; NotificationItem's seven JSON fields; read/relatedRecord/available; unread [markRead] and read []. PublicReview has exactly id/rating/text/createdAt/clinicReply, with no Patient/account/visit projection. Backend entities and internal recipient/type/read_at/version fields are not response DTOs.

Reviews/Notifications advice uses shared ErrorResponse, no separate ProblemDetail. Observed validation response contains error.code=MSG-57 plus root fieldErrors and no root code. Mapping: 400 MSG-57 validation, 400 MSG-54 filters/query, 403 MSG-56 out-of-scope visit, 404 MSG-50 absent/foreign resource, 409 MSG-56 transition/unique/optimistic conflict, 500 MSG-04 unexpected failure. Authentication errors follow the existing filter chain. Shared ErrorResponse keeps all five nested error fields, original constructor/factories, and omits empty fieldErrors; compatibility is tested.

## Security, transactions and producers

Code and tests confirm SELF-only eligible/submission scope, foreign visit rejection, class-level ADMIN/PATIENT method security, public anonymous read, and public identity exclusion. Notification controller accepts no account/recipient selector; forged query values cannot alter principal scope. List/count/mark-read/read-all always constrain recipient. Missing and foreign notification produce the same 404 MSG-50 behavior, including Admin cross-account attempts.

Review submit inserts review, SUBMIT history and active-ADMIN REVIEW notifications in one transaction. Admin decisions/replies write review plus history together. NotificationPublisherImpl uses MANDATORY and saveAndFlush; no async/event/separate-commit path exists. Source rollback removes notifications; actual notification insert failure rolls back appointment plus audit or review plus history.

Producer ownership: booking and follow-up booking -> APPOINTMENT; doctor reassignment -> APPOINTMENT; legitimate WalkInVisitLifecycleServiceImpl.close -> VISIT; review submit -> REVIEW to exact active ADMIN (not OWNER/inactive Admin). Linked active Patient lookup skips unlinked walk-in profiles. Cancel helper does not emit a review invitation; disputed cancel/reschedule flows are untouched.

## Swagger and tests

Swagger gaps were fixed only for this task: operation summaries/descriptions, meaningful path/query descriptions, success/error response docs and unique DTO schema names. The global OpenAPI public matcher now recognizes GET public reviews, matching runtime security. No global error documentation refactor was made.

NotificationPostgresIntegrationTest.swaggerDocumentsAll14OperationsAndPrivatePublicSchemas fetches actual /v3/api-docs and checks 14 operations, summaries/descriptions, success responses, ErrorResponse schema for 500, protected/public security, no exposed account/principal selectors and safe PublicReview/NotificationItem schemas. Captured JSON: ignored target/uc59-uc63-openapi-audit.json.

Final targeted audit command `.\mvnw.cmd '-Dtest=NotificationServiceTest,NotificationControllerTest,NotificationPostgresIntegrationTest,ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest' test`: PASS 72/72, zero failures/errors/skipped. Reviews 43; Notifications 29 including the new Swagger audit, 18 PostgreSQL-context cases. Previous source-flow regression 36/36 PASS; no source business logic changed in this audit. Coverage includes eligibility, foreign scope, DB unique and optimistic conflict mapping, all transitions/history, public privacy, filters/global counts, recipient isolation, idempotent timestamp/tuple preservation, scoped read-all, dynamic availability, exact role recipients, producer persistence and bidirectional rollback.

Final `.\mvnw.cmd verify` used the same JDK 21, PostgreSQL 18.6, environment-only container credentials and isolated schema as Phase B: FAIL at Surefire, 488 tests, 479 passed, 6 failures, 3 errors, zero skipped. Reviews 43/43 and Notifications/Swagger 29/29 passed in this run. The nine failing class/method pairs match previous Phase A/B evidence exactly: no new failure. Their current failure reasons were also inspected and match the categories below. Evidence: ignored target/uc59-uc63-final-audit-verify.log. No old public Flyway history was modified.

Audit-stage `git diff --check`: PASS. Before the validation follow-up the git status was feat/hung/reviews-notifications, 10 modified tracked files, 53 new untracked files. Index was empty. The current manifest below includes the additional scoped deserializer and focused HTTP test; no staged change, commit or push is performed.

## Follow-up Action Record — two validation fixes

Authorization/scope: the user's explicit request to fix only Unicode length and scoped JSON string coercion. Shadow Plan: change the private validation helper, attach a field-specific request deserializer, add focused submit/reply HTTP and PostgreSQL boundary checks, rerun task/source regression and full verify. File boundary: ReviewServiceImpl.java, ReviewDtos.java, new ReviewTextDeserializer.java, new ReviewTextValidationHttpTest.java, ReviewPostgresIntegrationTest.java and this report. No baseline/FE/response DTO/ObjectMapper-global/migration change.

Unicode business length is codePointCount after trim, not UTF-16 length; String.length is used only as the API's end-index argument. No grapheme-cluster algorithm was added. Request text stays String; deserializer annotations apply only to SubmitReviewRequest.text and ReplyRequest.text. Null/missing values remain application validation inputs.

Focused `.\mvnw.cmd '-Dtest=ReviewTextValidationHttpTest' test`: PASS 22/22. This covers eight non-string cases across submit/reply, four null/missing cases, nine Unicode HTTP boundary cases, and valid-string acceptance/trim on both endpoints. Every invalid case asserts HTTP 400, nested error.code=MSG-57, exactly one root fieldErrors entry with field=text/code=MSG-57, and no root code. Non-string cases assert the application service was never called; null/missing cases assert it was called and returned validation.

Regression `.\mvnw.cmd '-Dtest=ReviewTextValidationHttpTest,ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewPostgresIntegrationTest,NotificationServiceTest,NotificationControllerTest,NotificationPostgresIntegrationTest,CreateAppointmentServiceImplTest,ReassignAppointmentServiceImplT026Test,CreateFollowUpAppointmentT030Test,AppointmentBookingT022PostgresIntegrationTest,ReassignAppointmentT026PostgresIntegrationTest,CreateFollowUpAppointmentT030PostgresIntegrationTest,CreateWalkInVisitT043Test,VisitApiExceptionHandlerTest,VisitCareCapacityT031Test,VisitRoomCapacityT031Test' test`: PASS 138/138. Reviews 73/73; Notifications/Swagger 29/29; relevant Appointment/Visit 36/36. PostgreSQL Reviews 19/19 (eight added Unicode cases); PostgreSQL Notifications context 18/18 (17 persistence cases plus Swagger). Actual stored char_length agrees with 10/1000-emoji reviews and 1/1000-emoji replies; invalid five-emoji reviews and over-limit content/replies are rejected before persistence.

Environment and DB verification mechanism are unchanged from Phase B (JDK 21, local PostgreSQL 18.6, ephemeral credentials, isolated schema). V044 SHA-256: 384C1F96F858E98C3E5CC6D91911E9E910F4E8EBDC541761DB8634F4CAFA5909; V045 SHA-256: 511AA30A200513AEA09FB620B7B9A28B4F3B4686B582F6999D039040606AEE96. No migration was edited. Evidence: ignored target/review-text-focused.log and target/review-text-regression.log.

Follow-up `.\mvnw.cmd verify`: FAIL at Surefire, 518 tests, 509 passed, 6 failures, 3 errors, zero skipped. Reviews 73/73 and Notifications/Swagger 29/29 remain PASS. Programmatic comparison against the pre-fix audit log confirms the exact same nine class/method pairs: baseline=9, current=9, new=0, removed=0. No baseline source/test was changed or disabled. Evidence: ignored target/review-text-full-verify.log. `git diff --check`: PASS. Final branch feat/hung/reviews-notifications; 10 modified tracked files and 55 untracked files (both phases plus audit/fixes); no staged change/commit/push. No remaining BLOCKER/SHOULD FIX from the two audit findings; no claim is made that out-of-scope baseline failures were fixed.

## Baseline qualification

**pre-existing based on comparison with previous Phase A/Phase B runs, not independently reproduced on clean product HEAD**

Historical qualification above applies to the original Phase A/B runs. H7 subsequently verified clean origin/product independently; the dated H7 update below supersedes that qualification for the new evidence.

| Test class.method | Reason in prior runs | Before Phase B | Relation to this diff |
| --- | --- | --- | --- |
| AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks | V019 fixture violates ck_accounts_type for LAB_TECHNICIAN | Yes | Account migration/type fixture unchanged; no observed ErrorResponse regression |
| T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter | Expected failed count 3, got 2 | Yes | Login/lockout implementation and test unchanged |
| T008PasswordRecoveryPostgresIntegrationTest.changingPasswordInvalidatesPreviouslyIssuedResetLink | Expected sendPasswordReset; actual sendStaffPasswordReset | Yes | Recovery/mail implementation and test unchanged |
| UC07AdministratorAccountPostgresIntegrationTest.migrationPreservesPatientEmailAndLegacyStaffAccountsWithoutUsernames | Account type exceeds varchar(16) | Yes | Old account schema/type fixture unchanged |
| UC09AdministratorAccountPostgresIntegrationTest.databaseSearchFiltersRoleStatusAndPagesAndHidesEveryOtherRole | Account type exceeds varchar(16) | Yes | Old account schema/type fixture unchanged |
| T032AdminResourceMutationPostgresIntegrationTest.mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites | Expected RESOURCE_MUTATION_BLOCKED, got DOCTOR_RESOURCE_IN_PAST | Yes | Resource/date rules and test unchanged |
| T032AdminResourceMutationPostgresIntegrationTest.adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits | Expected 409, got 400 | Yes | Resource/date rules and test unchanged |
| T032AdminResourceMutationPostgresIntegrationTest.checkedInAppointmentAlsoBlocksTimeOffCreation | Expected 409, got 400 | Yes | Resource/date rules and test unchanged |
| WalkInReceptionQueueT045PostgresIntegrationTest.rejectsInvalidInitialAndExitStatesAndProvidesFifoIndexes | Hard-coded public index count 1, got 0 | Yes | Verification schema differs; old test hard-codes public and is unchanged |

No clean-HEAD runtime proof is claimed. No baseline test/module was fixed or disabled. The two validation findings above are separate audit findings, not part of these nine baseline failures.

## Commit manifest

### H7 OpenAPI fix - 2026-11-02

Scope authorized by the user: OpenAPI documentation/configuration, its runtime regression test and existing audit/action records only. No controller, business logic, ErrorResponse, SecurityConfig, migration, FE or baseline test changes; no commit/push.

OpenApiConfiguration now customizes exactly 14 Review/Notification operations using the existing OpenApiCustomizer after the unchanged Appointment customization. It retains inferred DTO schemas, normalizes success/error media to application/json, uses shared ErrorResponse, documents endpoint-specific MSG-57/54/50/56/04 statuses and supplies reusable component error examples. Public reviews has no bearer requirement; the other 13 operations require bearerAuth. No endpoint or operationId is created/replaced.

Explicit JSON request/success examples are present for POST /api/v1/reviews and PUT /api/v1/admin/reviews/{id}/reply. MyReview preserves text, PENDING, clinicReply:null, SELF and +07:00; Admin ReviewDetail uses content, a reply object and lowercase hide/editReply. UUIDs/names are documentation samples, not real account credentials. Existing Appointment 201/Location/JSON documentation remains verified.

NotificationPostgresIntegrationTest.swaggerDocumentsAll14OperationsAndPrivatePublicSchemas verifies actual /v3/api-docs: 14 unique operationIds, recursive local schema/example reference resolution, JSON content/schema on every documented response, scoped business errors, exact request/response example fields, five shared MSG examples and protected/public security. It also verifies /swagger-ui.html redirects to /swagger-ui/index.html and the index returns 200.

Focused command: `.\mvnw.cmd '-Dtest=NotificationPostgresIntegrationTest#swaggerDocumentsAll14OperationsAndPrivatePublicSchemas,ReviewControllerTest,NotificationControllerTest' test`: 34/34 PASS. Final regression command: `.\mvnw.cmd '-Dtest=NotificationMessagesTest,NotificationServiceTest,NotificationControllerTest,NotificationPostgresIntegrationTest,ReviewServiceTest,OwnPatientProfileServiceTest,ReviewControllerTest,ReviewTextValidationHttpTest,ReviewPostgresIntegrationTest' test`: 165/165 PASS, no failures/errors/skipped. Reviews 108, Notifications 57; PostgreSQL Reviews 31 and Notifications 28. JDK 21.0.9/PostgreSQL 18.6 with environment-only credentials and isolated test schemas. Evidence logs: ignored target/h7-openapi-fix-focused.log and target/h7-openapi-fix-regression.log. Runtime JSON uses the existing ignored target/uc59-uc63-openapi-audit.json; no target artifact is a commit candidate.

Independent full-verify evidence from the preceding H7 audit: clean feature e148ddf has 581 tests (572 PASS, 6 failures, 3 errors); clean origin/product 0ce92ff has 416 (406 PASS, 7 failures, 3 errors). All nine feature failing/error class.method pairs reproduce on product; feature-only failures=0. Product additionally fails BillingInvoicePostgresIntegrationTest.migratesBillingSchemaAndCreatesOneCorrectInvoicePerSourceWithPartialCounterPayment: it expects three successful rows for 042/043/044, but product ends at V043. Reviews V044 makes that count assertion pass on feature. Both valid runs were sequential with the same JDK/DB/environment strategy; initial parallel connection-exhaustion runs were discarded. Temporary product worktree was clean and removed. No source/test baseline modification or repair was used.

Full verify is not rerun for this documentation-only fix. The verify-green DoD remains blocked by the independently reproduced product baseline; reviewer request, PR update and Swagger screenshot attachment remain separate H7 work. OpenAPI portion: DONE. `git diff --check`: PASS.

### Original Phase A/B commit manifest

Exact paths relative to backend/. Candidates only; no git add/commit/push.

Production: 51 files (including two unchanged SQL migrations). Tests: 11 files. Documentation: 3 files. Total: 65 files.

### Production

- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/AppointmentReviewContextService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/impl/AppointmentNotificationRecordResolver.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/impl/AppointmentReviewContextServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/impl/CreateAppointmentServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/impl/CreateFollowUpAppointmentServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/appointment/service/impl/ReassignAppointmentServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/NotificationRecipientDirectoryImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/NotificationRecipientDirectory.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/common/api/ErrorResponse.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/config/OpenApiConfiguration.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/config/SecurityConfig.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/controller/MyNotificationController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/exception/NotificationExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/exception/NotificationNotFoundException.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/model/dto/NotificationDtos.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/model/entity/NotificationJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/model/NotificationMessage.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/model/NotificationRelatedKind.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/repository/NotificationRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/service/impl/MyNotificationServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/service/impl/NotificationPublisherImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/service/MyNotificationService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/service/NotificationPublisher.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/notification/service/NotificationRelatedRecordResolver.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/controller/AdminReviewController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/controller/PatientReviewController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/controller/PublicReviewController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/exception/ReviewApiExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/exception/ReviewOperationException.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/dto/ReviewDtos.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/dto/ReviewRatingDeserializer.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/dto/ReviewTextDeserializer.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/entity/PatientReviewHistoryJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/entity/PatientReviewJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/model/ReviewStatus.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/repository/PatientReviewHistoryRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/repository/PatientReviewRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/impl/OwnPatientProfileServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/impl/ReviewNotificationRecordResolver.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/impl/ReviewServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/impl/ReviewVisitContextServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/OwnPatientProfileService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/ReviewService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/review/service/ReviewVisitContextService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitReviewSourceRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitNotificationRecordResolver.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitReviewSourceServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/WalkInVisitLifecycleServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitReviewSourceService.java`
- `src/main/resources/db/migration/V044__create_patient_reviews.sql`
- `src/main/resources/db/migration/V045__create_notifications.sql`

### Tests

- `src/test/java/fu/fa26/sep490/g58/otcms_be/appointment/CreateAppointmentServiceImplTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/appointment/CreateFollowUpAppointmentT030Test.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/appointment/ReassignAppointmentServiceImplT026Test.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/notification/NotificationControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/notification/NotificationPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/notification/NotificationServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/review/OwnPatientProfileServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/review/ReviewControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/review/ReviewPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/review/ReviewServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/review/ReviewTextValidationHttpTest.java`

### Documentation

- `docs/notifications-phase-b-action-record.md`
- `docs/reviews-phase-a-action-record.md`
- `docs/uc59-uc63-final-audit.md`
