# KAN-29 — My account R3

## Audit and Action Record

- Approved base: `89c859611cbd7214186e74103b87eaff171896c5`; branch `feat/hung/KAN-29-my-account`, clean and 0/0 against origin/product before implementation.
- Human checkpoint: explicit implementation approval in this session, including the exact GET `/api/v1/me` forced-password gate exception; no agent self-approval or inferred reviewer identity.
- Sources: supplied KAN-29 ticket and final corrections; KAN-22 ownership confirmation assigns auth to Hùng. Separate KAN-22/R3 documents were unavailable; the supplied contract governs this bounded implementation. Obsolete FEAT-002 D03 and contradictory FE mock comments are excluded.
- Shadow plan: projection and PATCH; avatar persistence; separate email-change lifecycle; own administrative profile; scoped errors; focused/PostgreSQL/full tests; untouched-base comparison; final diff review.
- File boundary: auth account query/controller/services and new KAN-29 persistence/errors/DTOs; the exact auth JWT gate exception; new patient self-profile service/controller/errors; tests; V052; this document.
- V048–V051 and V070–V072 exist in product. V052 was unused. Add only `V052__my_account_r3.sql`; never edit history. Existing Flyway out-of-order configuration is sufficient.
- Commands: Java 21; focused Maven `-Dtest=... test`, `.\mvnw.cmd test`, `.\mvnw.cmd clean verify`, `git diff --check`, `git status --short`, `git diff --stat`.
- No frontend/configuration/common/dependency/registration changes; no stage, commit, push, merge or rebase.

## Contract and privacy

- One GET `/api/v1/me`, compatible id/accountType/email/displayName plus kind/username/external role/avatarUrl/mustChangePassword/status/allowedActions. Status is ACTIVE or INACTIVE. Explicit account display name wins over legacy fallbacks. No hashes, counters, locks, tokens, staff contacts or clinical data.
- PATCH `/me` updates only account display name, trimmed 2–100 code points, returning the projection. GET `/me/patient-account` remains.
- PUT `/me/avatar` uses multipart `photo`, 1–2097152 bytes, JPEG/PNG/WEBP signature and MIME checks; DELETE is idempotent. Authenticated GET `/accounts/{id}/avatar` returns stored bytes and supported type, no-store and nosniff.
- Existing multipart limits remain 2MB file / 3MB request. Signature checks identify formats; they are not a full decoder or malware scanner.
- Forced-password accounts may read exact GET `/me`; mutation routes remain blocked. Their projection advertises only supported password change.
- POST `/me/email-change` accepts newEmail; syntactically valid authorized requests supersede prior active challenges before eligibility/rate decisions, returning the same generic 202 for eligible/current/taken/suppressed targets. Delivery failure does not reveal eligibility. No plaintext recipient/OTP is logged by KAN-29.
- EmailOtpService supplies secure generation, password-encoder hash/matching, expiration and failed-attempt limit. Registration is unchanged. EmailDeliveryService and resend configuration are reused.
- Verify body is `{code}`; failed attempts persist. Correct verification locks account/challenge, coordinates with UC06's target-email namespace and rechecks uniqueness. The final account write uses prepared JDBC on the existing transaction connection, not Hibernate ORM, so a database collision cannot log raw target-email diagnostics through Hibernate. Database uniqueness is the final race guard. Email, verification timestamp and challenge consumption commit together without changing account state.
- Patient-only GET/PUT `/me/patient-profile` resolves the caller's linked profile through public auth boundaries. KAN-24 normalization/DTOs and managed optimistic version are reused. Its validation exceptions are translated to the scoped self-profile exception; KAN-24 advice is unchanged.
- Scoped errors retain the shared envelope and current request correlation ID. Stale profile writes retain HTTP 409 STALE_VERSION.

| Method | Path under `/api/v1` | Success |
| --- | --- | --- |
| GET | `/me` | 200 account projection |
| PATCH | `/me` | `{displayName}` → 200 account projection |
| PUT | `/me/avatar` | multipart `photo` → 200 account projection |
| DELETE | `/me/avatar` | 204, including repeated removal |
| GET | `/accounts/{id}/avatar` | 200 binary; missing avatar → safe 404 |
| POST | `/me/email-change` | `{newEmail}` → 202 `{"message":"MSG-06"}` for every eligible/current/taken/suppressed outcome |
| POST | `/me/email-change/verify` | `{code}` → 200 account projection |
| GET | `/me/patient-profile` | 200 KAN-24 administrative detail contract |
| PUT | `/me/patient-profile` | KAN-24 update body including version → 200 detail/version |

The last four operations are PATIENT-only. For normal eligible accounts the projection advertises updateDisplayName/updateAvatar, removeAvatar only when present, changePassword only with the existing active password capability, and changeEmail only for PATIENT. For forced-password accounts it advertises only the supported changePassword action. Anonymous requests retain the existing security entrypoint/CSRF behavior.

## Pre-fix verification

- Final focused selector: `MyAccountQueryServiceImplTest,Kan29MyAccountControllerTest,Kan29MyPatientProfileServiceTest,Kan29MyAccountPostgresIntegrationTest,Kan29MyAccountMigrationPostgresTest,Kan29AvatarHttpPostgresIntegrationTest`.
- Focused: **62 tests, 0 failures, 0 errors, 0 skipped**. Unit/controller: 15. Real PostgreSQL: **47** (44 account/profile HTTP/service integration, 2 migration, 1 real servlet HTTP).
- Real servlet test confirms exactly 2097152 file bytes accepted; 2097153 rejected as HTTP 400 MSG-08 with the supplied correlation ID. Multipart parsing can fail before controller selection; the existing scoped KAN-29 advice also implements a resolver restricted to that exception on exact PUT `/api/v1/me/avatar`.
- Initial test-only failures: Spring JSONPath's list-value assertion was replaced with array-size/element assertions; real HTTP test corrected the existing `csrfToken` response property and was separated from MockMvc's mutable test CSRF repository. No application security configuration was relaxed.
- Migration tests apply all migrations fresh and upgrade the product snapshot (V048–V051 plus V070–V072, without V052), then add V052 out of order. They assert unchanged V048–V051 history checksums, preserved account rows, avatar size/MIME/FK constraints and challenge FK/current-unique/index constraints. Both PASS.
- The wrapper could not bootstrap in the sandbox (`Cannot index into a null array`); approved outside-sandbox execution uses the requested wrapper and process-local Java 21. No dependency version changes.
- Docker Desktop was initially stopped. An isolated `otcms-kan29-postgres-test` PostgreSQL 18.6 container runs on loopback port 5432. Its generated credential is supplied through the approved test environment binding; only a Windows-protected copy is retained in the OS temporary directory. No existing database credentials or data were changed.
- Focused logs: `%TEMP%/kan29-focused.log`; diagnostic real-HTTP run: `%TEMP%/kan29-multipart.log`. Focused logs contain no email-like addresses.
- Exact `.\mvnw.cmd test`: **1033 tests, 7 failures, 21 errors, 0 skipped; BUILD FAILURE**.
- Exact `.\mvnw.cmd clean verify`: **1033 tests, 7 failures, 21 errors, 0 skipped; BUILD FAILURE**. It stops at the test gate, so no successful final package is claimed.
- Both full commands PASS all 62 tests in the KAN-29 focused selector. Their complete failed-case names and failure/error categories are identical (28 affected cases).
- Logs: `%TEMP%/kan29-full-test.log`, `%TEMP%/kan29-clean-verify.log`; machine-readable failed-case lists: `%TEMP%/kan29-full-test-failures.json`, `%TEMP%/kan29-clean-verify-failures.json`.
- Baseline comparison: PASS against an untouched complete `git archive` of `89c8596`, extracted to `%TEMP%/otcms-kan29-baseline-89c8596`. A second fresh container uses the identical PostgreSQL image and environment credential binding, avoiding V052 history contamination. The KAN-29 database container is stopped and retained; `otcms-kan29-baseline-postgres-test` remains running on loopback port 5432.
- Baseline command: `.\mvnw.cmd '-Dtest=AppointmentArrivalT053PostgresIntegrationTest,AdministratorAccountDatabaseVerificationTest,Kan27StaffAccountPostgresIntegrationTest,T005RealLoginIntegrationTest,T008PasswordRecoveryPostgresIntegrationTest,T032AdminResourceMutationPostgresIntegrationTest' test` under Java 21 in the untouched archive. **43 tests, 7 failures, 21 errors, 0 skipped**. All 28 failed/error case names and categories exactly match both full KAN-29 runs; zero comparison differences. No unrelated implementation or test was changed.
- Baseline evidence: `%TEMP%/kan29-baseline.log`, `%TEMP%/kan29-baseline-failures.json`. The baseline command reproduces all six failing classes; it is not a full baseline-suite run.

| Reproduced baseline suite | Failures | Errors |
| --- | ---: | ---: |
| AppointmentArrivalT053PostgresIntegrationTest | 2 | 0 |
| AdministratorAccountDatabaseVerificationTest | 0 | 1 |
| Kan27StaffAccountPostgresIntegrationTest | 0 | 20 |
| T005RealLoginIntegrationTest | 1 | 0 |
| T008PasswordRecoveryPostgresIntegrationTest | 1 | 0 |
| T032AdminResourceMutationPostgresIntegrationTest | 3 | 0 |

KAN-27's 20 errors stem from its existing context failing to bind `app.auth.login` to `AuthProperties.RateLimit`; repeated cases then hit Spring's context-failure threshold. This reproduces without KAN-29. No change to AuthProperties, AccountAccessService or KAN-27 is included.

## Pre-fix review and residual risks

- Whole-diff review covers six modified files and 23 new files, including tests and this document. Tracked `git diff --check` and separate whitespace checks of all untracked additions PASS. Git's Windows LF/CRLF conversion notices were distinguished from whitespace errors; no repository Git configuration was changed.
- SHA-256 comparison of all 54 pre-existing migration files against the untouched base finds zero differences; V052 is the sole new migration. HEAD remains `89c8596`, ahead/behind remains 0/0, and the index is empty.
- The combined diff including new files is captured at `%TEMP%/kan29-whole-diff.patch`. `git diff --stat` covers tracked changes only: six files, 130 insertions and 30 deletions; the 23 untracked additions are separately inventoried below.
- Security/privacy review: explicit DTO projections; service-level role/status/ownership checks; exact forced-password GET exception; no new shared security configuration; no secrets/tokens/OTP/hash disclosure; generic email-change request responses; account/challenge locks and final DB uniqueness; persisted wrong-attempt counters; profile optimistic concurrency; no-store responses; stored supported image types with nosniff.
- Full verification is not green because of the independently reproduced baseline failures. Final merge readiness remains a human decision; no package/build success is claimed.
- Avatar validation checks supported format signatures, not full decoding or malware scanning. Live SMTP is not exercised; tests use a fake sender and assert recipient/hash/lifecycle behavior. Email-change history retention is not added in this ticket.
- Local test containers, generated test schemas, protected credential and baseline archive are retained for inspection. They are independent of application/shared data.
- Action Record result: requested implementation and verification completed within the approved boundary. Sync-back: this document records API/privacy decisions, exact commands, results, inventory and residual risks without changing architecture policy or asserting human approval. No staged changes, commit, push, merge or rebase.

## Final review blocker corrections

- Human explicitly approved only the two final-review fixes and the legacy username presentation case. No shared logging, SecurityConfig, common, frontend, migration or dependency changes are included.
- External role now maps OWNER → CLINIC_OWNER and DOCTOR → ORTHOPEDIC_DOCTOR. Backend accountType remains OWNER/DOCTOR. Explicit tests assert both fields against literal contract values. Other established roles retain their FE values.
- Legacy accounts with a null stored username return an empty presentation string. No username is inferred from email, and stored credentials are unchanged.
- `EmailChangeAccountWriter` executes one prepared UPDATE bound to the authenticated account id, normalized target and a single verification timestamp. Only normalized_email/email_verified_at/updated_at are updated. `JdbcTemplate` uses the existing JPA transaction's bound DataSource connection; it does not create an independent transaction.
- Verification still obtains `registration-email:<normalized target>` coordination before the final availability check and JDBC update. Normal UC06 registration is serialized; the existing accounts UNIQUE constraint remains authoritative for non-cooperating writers.
- The writer catches SQLException before framework error translation/logging, examines SQLSTATE only and emits a cause-free `EmailChangeWriteException`. SQLSTATE 23505 is COLLISION; every other SQL/persistence failure is OPERATION_FAILED. The sanitized exception also disables suppressed exceptions so statement-close diagnostics cannot attach. No parameter, OTP/hash, raw exception message or PostgreSQL detail is logged or retained.
- Wrong OTP throws `MyAccountOperationException` under noRollbackFor: attempt increments and fifth-attempt supersession commit. JDBC write failures use a different exception with explicit rollbackFor: 23505 rolls back the entire verification and maps to MSG-08; other failures roll back and map to MSG-04.
- The managed AccountJpaEntity is never assigned the new email or timestamps. After successful JDBC update, only that account is detached to prevent stale OpenEntityManagerInView projection and any second ORM account UPDATE. The challenge remains managed and its consumption flushes within the same transaction. No global persistence-context clear is used.
- Deterministic test uses a Mockito spy on the real scoped writer: pause after verification's precheck, commit another account's JDBC email claim in a second transaction, resume the real writer and force 23505. Assertions cover the sanitized COLLISION exception without raw cause, HTTP MSG-08/correlation, unchanged original email/verification timestamp/updated_at, unchanged unconsumed challenge and exactly one database email owner.
- OutputCaptureExtension captures application/Hibernate output for that forced collision and asserts the literal generated target email and delivered OTP do not appear. There is no production test hook or logging configuration change.
- First targeted command: `.\mvnw.cmd '-Dtest=MyAccountQueryServiceImplTest,EmailChangeAccountWriterTest,Kan29MyAccountPostgresIntegrationTest#wrongOtpPersistsAttemptsAndExhaustion,Kan29EmailChangeCollisionPostgresIntegrationTest' test` with Java 21: **15 tests, 0 failures/errors/skips**. Log: `%TEMP%/kan29-fix-targeted.log`.
- Final complete focused command: `.\mvnw.cmd '-Dtest=MyAccountQueryServiceImplTest,EmailChangeAccountWriterTest,Kan29MyAccountControllerTest,Kan29MyPatientProfileServiceTest,Kan29MyAccountPostgresIntegrationTest,Kan29MyAccountMigrationPostgresTest,Kan29AvatarHttpPostgresIntegrationTest,Kan29EmailChangeCollisionPostgresIntegrationTest' test` under Java 21: **70 tests, 0 failures/errors/skips**. This includes 22 unit/controller cases and 48 PostgreSQL cases. Log: `%TEMP%/kan29-fix-focused.log`.
- Separate PostgreSQL command: `.\mvnw.cmd '-Dtest=Kan29MyAccountPostgresIntegrationTest,Kan29MyAccountMigrationPostgresTest,Kan29AvatarHttpPostgresIntegrationTest,Kan29EmailChangeCollisionPostgresIntegrationTest' test`: **48 tests, 0 failures/errors/skips**. Log: `%TEMP%/kan29-fix-postgres.log`.
- The forced post-precheck collision/log-privacy case passes in both commands: **literal target email absent from captured output**, no raw exception cause, unchanged patient email/timestamps/challenge and exactly one target-email owner. Wrong-OTP exhaustion remains persisted. Success-path tests confirm that the returned projection contains the JDBC-updated email, with no stale managed-account response.
- Post-fix exact `.\mvnw.cmd test`: **1041 tests, 7 failures, 21 errors, 0 skipped; BUILD FAILURE**. All KAN-29 tests pass. Failures remain in the same six existing suites listed above. Log: `%TEMP%/kan29-fix-full-test.log`; failed-case names/categories: `%TEMP%/kan29-fix-full-test-failures.json`.
- Post-fix exact `.\mvnw.cmd clean verify`: **1041 tests, 7 failures, 21 errors, 0 skipped; BUILD FAILURE** at the test gate. All 70 KAN-29 cases pass again, including deterministic collision/log privacy. Full-test and clean-verify failed-case names/categories are identical (28 cases, zero differences). Log: `%TEMP%/kan29-fix-clean-verify.log`; failed-case list: `%TEMP%/kan29-fix-clean-verify-failures.json`.
- Repeat untouched-base comparison: **43 tests, 7 failures, 21 errors, 0 skipped** using the same six-suite selector above in the unchanged `89c8596` archive. All 28 failed/error case names and categories exactly match both post-fix full commands (zero differences). Logs/lists: `%TEMP%/kan29-fix-baseline.log`, `%TEMP%/kan29-fix-baseline-failures.json`. This is the six failing suites, not a full baseline-suite run.
- The previous test container is stopped and retained; fresh `otcms-kan29-fix-baseline-postgres-test` remains on loopback port 5432 with the identical image, preventing V052 history contamination. No shared/application database data is used. Recomputed SHA-256 comparison confirms all 54 historical migration files unchanged. V052 is unchanged by this fix and remains the only new migration.
- Privacy result: literal generated target email is absent from the deterministic test's captured application/Hibernate logs. An additional scan of the complete post-fix focused log finds no email-like addresses. No production logging configuration was changed.

| Post-fix command | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Initial targeted selector | 15 | 0 | 0 | 0 |
| Complete KAN-29 selector | 70 | 0 | 0 | 0 |
| Separate PostgreSQL subset | 48 | 0 | 0 | 0 |
| Full test | 1041 | 7 | 21 | 0 |
| Clean verify | 1041 | 7 | 21 | 0 |
| Untouched baseline, six failing suites | 43 | 7 | 21 | 0 |

Final scoped code review: PASS for the OWNER/DOCTOR contract, legacy username presentation, no-rollback wrong-attempt persistence, explicit JDBC failure rollback, sanitized collision handling, narrowly detached account state, and atomic challenge consumption. The complete working-tree review includes six modified and 27 new files. No unrelated source/configuration/frontend/dependency/migration change was introduced. Final tracked diff stat is six files, 161 insertions and 30 deletions; it excludes the 27 untracked additions listed below. The index remains empty and HEAD remains `89c8596` (0/0 against origin/product). Full build readiness remains qualified by the reproduced baseline failures; no successful package is claimed. Human commit review is still required, and no commit/push/merge/rebase was performed.

Exact files changed for this fix:

- `auth/service/impl/MyAccountQueryServiceImpl.java`
- `auth/service/impl/EmailChangeServiceImpl.java`
- `auth/exception/MyAccountApiExceptionHandler.java`
- `auth/exception/EmailChangeWriteException.java` (new)
- `auth/repository/EmailChangeAccountWriter.java` (new)
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/MyAccountQueryServiceImplTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29MyAccountPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/EmailChangeAccountWriterTest.java` (new)
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29EmailChangeCollisionPostgresIntegrationTest.java` (new)
- `docs/kan29-my-account-r3.md`

## File inventory

Java paths below are relative to `src/main/java/fu/fa26/sep490/g58/otcms_be/` unless stated otherwise.

Modified:

- `auth/controller/CurrentPatientAccountController.java`
- `auth/controller/MyAccountController.java`
- `auth/security/JwtAuthenticationFilter.java`
- `auth/service/MyAccountQueryService.java`
- `auth/service/impl/MyAccountQueryServiceImpl.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/MyAccountQueryServiceImplTest.java`

Added:

- `auth/exception/MyAccountApiExceptionHandler.java`
- `auth/exception/MyAccountOperationException.java`
- `auth/exception/EmailChangeWriteException.java`
- `auth/model/dto/MyAccountDtos.java`
- `auth/model/entity/AccountAvatarJpaEntity.java`
- `auth/model/entity/EmailChangeChallengeJpaEntity.java`
- `auth/repository/AccountAvatarRepository.java`
- `auth/repository/EmailChangeChallengeRepository.java`
- `auth/repository/EmailChangeAccountWriter.java`
- `auth/service/EmailChangeService.java`
- `auth/service/MyAccountService.java`
- `auth/service/impl/EmailChangeServiceImpl.java`
- `auth/service/impl/MyAccountServiceImpl.java`
- `patient/controller/MyPatientProfileController.java`
- `patient/exception/MyPatientProfileApiExceptionHandler.java`
- `patient/exception/MyPatientProfileException.java`
- `patient/service/MyPatientProfileService.java`
- `patient/service/impl/MyPatientProfileServiceImpl.java`
- `src/main/resources/db/migration/V052__my_account_r3.sql`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29MyAccountControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29MyAccountMigrationPostgresTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29MyAccountPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/EmailChangeAccountWriterTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29EmailChangeCollisionPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan29AvatarHttpPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/Kan29MyPatientProfileServiceTest.java`
- `docs/kan29-my-account-r3.md`

## Human review

AI Agent Recommendation: PENDING HUMAN REVIEW.
Human Final Review: final code review PENDING; the implementation scope was explicitly approved in-session. No reviewer identity or final approval is invented.
