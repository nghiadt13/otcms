# KAN-26 — Dependents R3

## Shadow Plan / Action Record

- Source: explicit KAN-26 R3 ticket and implementation instruction, 2026-11-02.
- Human checkpoint: user explicitly approved nullable phone/phone owner for new
  accountless dependents and extraction of UC06 OTP primitives, and instructed H1–H17.
  This records the instruction, not an agent-generated approval or reviewer identity.
- Branch: `feat/hung/KAN-26-dependents-r3`, base `1638a29` (`origin/product`).
- Preflight: clean tree; product highest migration V072; V049 free; Flyway out-of-order enabled.
- Boundary: Patient/care code and tests, auth profile persistence/directory, reusable auth OTP
  and registration integration/tests, V049, focused OpenAPI and this evidence document.
- Plan: H1 persistence; H2 OTP extraction; H3/H4 dependent flow/access; H5–H10 HTTP,
  update/revoke/counter; H11–H17 security, concurrency, acceptance, docs and verification.
- Commands authorized by user: focused Maven tests, `.\mvnw.cmd test`,
  `.\mvnw.cmd clean verify`, `git diff --check`, `git status`, `git diff --stat`; JDK 21.
- No dependency additions, historical migration edits, unrelated production changes,
  formatter, commit or push.
- Verification/results and residual blockers: pending execution; recorded below when observed.
- Sync-back: this document; final result remains PENDING HUMAN REVIEW.

## Review

AI Agent Recommendation: PENDING HUMAN REVIEW (final implementation).
Human Final Review: pending; reviewer, decision and timestamp are for the human reviewer.

## Behavior and API contract

All `/api/v1/me/...` operations below require active PATIENT plus a current self profile.
Counter APIs require active RECEPTIONIST. Method security excludes other roles; service
checks use the existing public identity/receptionist directories. Existing CSRF stays enabled.

| Method | Path under `/api/v1` | Success |
| --- | --- | --- |
| POST | `/me/dependents` | 201 LINKED, or 202 OTP_SENT/COUNTER_REQUIRED |
| POST | `/me/dependents/links/{linkId}/otp/verify` | 200 LINKED |
| POST | `/me/dependents/links/{linkId}/otp/resend` | 202 OTP_SENT |
| GET | `/me/patient-profiles` | 200 `{items}` |
| GET | `/me/dependents/{profileId}` | 200 administrative detail |
| PUT | `/me/dependents/{profileId}` | 200 administrative detail/version |
| DELETE | `/me/dependents/{profileId}` | 204 |
| GET | `/care-links/pending?query=` | 200 `{items}` |
| POST | `/care-links/{linkId}/counter-confirmation` | 200 LINKED |
| POST | `/care-links/{linkId}/counter-rejection` | 204 |

LINKED responses consistently include `result`, `linkId`, `profile`. OTP_SENT includes
`result`, `linkId`, `maskedEmail`. COUNTER_REQUIRED includes `result`, `linkId`.
Null result properties are omitted. Administrative responses and lists use no-store.

UC13 accepts fullName, dateOfBirth, gender, nationalId, identityDocumentType, relationship.
Matching uses KAN-24 trim/collapsed whitespace/NFC and exact case-insensitive normalized
name equality preserving accents, exact DOB and document number. Missing document number
matches null document numbers; more than one match is conflict. Non-null incompatible
document types conflict; legacy null type remains an existing candidate, never a reason
to silently create a duplicate. Self and existing live links conflict.

- **A, no candidate:** create accountless profile with null phone, phone owner and address;
  persist CONFIRMED/CREATED_WITHOUT_OTP with requester as verified actor. No account/login.
  Profile and link share one transaction; Directory's persisted profile ID is used.
- **B, existing account + usable account email + CCCD with nonblank nationalId:**
  REQUESTED/OTP, separate hashed challenge; send exclusively to that account's email.
- **C, other existing profile:** REQUESTED/COUNTER; no challenge or mail. This includes
  accountless, missing email/document, PERSONAL_ID/PASSPORT and legacy null document type.

Existing administrative validation was extracted to `AdministrativeProfileFields`.
Receptionist APIs retain required phone/phone owner, create address and client version.
Dependent PUT reads current version inside the transaction and retains JPA optimistic locking;
it requires no client version, preserves accountId and phoneOwnerType, and writes no clinical fields.
For the new accountless contact-less profile, phone can remain null or be populated while
phoneOwnerType stays null; no ownership value or requester contact is fabricated.

## OTP reuse and verification evidence

`EmailOtpService` extracts the existing six-digit SecureRandom generation, delegating password
encoder hash/match and 600-second TTL. Registration uses that component, preserving its own
account/challenge/consent lifecycle, rate behavior, error codes and expiry comparison.
Its legacy constructor remains for existing direct unit callers; Spring injects the shared component.

Care uses `care_link_otp_challenges`, never registration challenges. Care expiry is strict:
`now >= expiresAt` is invalid. Invalid current challenges (wrong, expired, consumed,
superseded or exhausted) use OTP_INVALID without revealing the cause. Replay after successful
confirmation reaches link-state validation first and returns 409 CARE_LINK_NOT_AVAILABLE.
Fifth failure supersedes. Only the dedicated
`CareOtpInvalidException` commits failed-attempt evidence; other failures roll back.

Resend counts the same configurable resend window/limit as UC06, scoped to both link and
recipient account, so creating another link cannot reset that recipient's quota. It supersedes
and flushes the old challenge before inserting its replacement. It never updates a pending
care link. A short guarded transaction commits the pending link and replacement challenge.
SMTP runs afterward, without a transaction or coordination/row locks. The stored hash initially
represents a non-code; the actual OTP hash remains in memory until delivery succeeds. A second
short guarded transaction activates only that exact, still-current challenge. It does not reset
attempts or resurrect a superseded challenge. A later resend can therefore win safely.

Delivery failure returns 503 and supersedes only the failed delivery's challenge in a short
compensating transaction. The link remains pending and recoverable through the selector/resend.
Even if compensation fails, its staged hash cannot match a valid six-digit code. Failed delivery
attempts count toward the same resend quota; repeated add conflicts instead of resetting quota.
A delivered code whose activation fails remains unusable; retry through resend. SMTP and DB
commit cannot be atomic. No outbox/library or new API field is added.

Only hashes are persisted; email/code/hash are not logged or returned. The entire email
local part is masked, including one-character and quoted local parts containing `@`; the final
`@` separates the domain. Recipient account is re-derived on resend;
verify checks it still agrees with the link and profile account.

OTP verification records requester as verifiedBy; counter confirmation records receptionist.
confirmedAt and verifiedAt use the same Clock instant. Pending reason clears on confirm/revoke.

## Persistence and concurrency

### Review-fix Shadow Plan / Action Record

- Human checkpoint: latest explicit instruction authorizes only H1 SMTP isolation, M1 UTF-8
  messages, M2 masking, related tests and this evidence update. Human re-review remains pending.
- Boundary: DependentProfileServiceImpl, care challenge repository, care exception handler,
  focused tests and this document. No registration, appointment production, historical
  migration, dependency or shared error-envelope changes are part of these fixes.
- Plan: short guarded preparation; SMTP outside transactions (`Propagation.NEVER` also rejects
  callers with an existing transaction); short guarded activation/compensation; focused and full
  regression verification. Plain OTP exists only in a redacted in-memory delivery envelope.
- Tests include blocked initial/resend delivery with unrelated real HTTP appointment booking,
  compensation racing a replacement, real HTTP optimistic conflict and explicit selector statuses.
- Exact commands/results for this review-fix execution are recorded below after verification.

V049 is additive; V001–V048 and V070–V072 are untouched. Product head is V072 and
out-of-order migration is enabled. V049 relaxes phone nullability for accountless profiles,
retains required contact for account-linked profiles, extends care metadata, and creates
care OTP constraints/indexes. Application validation for existing create/update flows remains.

Existing live-link partial uniqueness is retained. Different requesters may link one profile;
one requester/profile has at most one REQUESTED/CONFIRMED row. Revoke keeps history;
future linking inserts a new row. New inserts require R3 metadata. Legacy rows retain null
metadata and are never assigned invented relationship/verification evidence. Legacy rows
without a truthful R3 relationship projection are excluded from the selector and counter queue;
legacy CONFIRMED state still follows the helper's stated access contract. Reconcile any such
live legacy data before rollout if it needs to appear in the selector.

The original history trigger is retained, including its ban on REQUESTED→REQUESTED.
The added trigger protects relationship and verified evidence. Constraints enforce state/evidence,
chronology, max attempts, one current OTP/link and challenge timestamp validity.

Mutating care operations use the existing READ COMMITTED coordination guard, shared with
booking, plus locked current challenges. Authorization and dependent update/revoke happen
inside the guarded transaction. Failed-attempt commits release the same guard. This serializes
same-tuple add, verify/resend and revoke/update/booking without introducing another lock system.
Read requests already in flight may finish using their snapshot; new requests after revoke
commit lose access. No cross-request authorization cache is added.

## Access helper and booking acceptance

`PatientProfileAccessService` returns self UNION requester-owned CONFIRMED dependent IDs,
deduplicated. Inactive/non-PATIENT/missing self returns empty/false. Pending/revoked/unrelated
links grant nothing; a dependent does not need an account.

`AuthorizePatientProfileServiceImpl` delegates and returns SELF or DEPENDENT accurately.
The billing authorization adapter delegates within Patient, preserving its existing List contract.
No billing production code changes. Actual POST appointment acceptance uses real authorization,
persists dependent patientProfileId and keeps requester as appointment creator.

## Follow-ups for PR / Long (production code unchanged)

| File / method | Follow-up |
| --- | --- |
| `appointment/service/impl/GetAppointmentsServiceImpl.resolve`, `list`, `detail` | Resolve accessible profiles instead of only self |
| `appointment/repository/AppointmentQueryRepository.criteria` | PATIENT scope currently has one profile ID |
| `appointment/service/impl/AppointmentArrivalApplicationServiceImpl.recordQr` | Replace independent self-profile comparison |
| `appointment/service/impl/AppointmentNotificationRecordResolver.available` | Use helper for patient related-record access |
| `visit/service/impl/VisitNotificationRecordResolver.available` | Use helper for patient related-record access |
| `review/service/impl/OwnPatientProfileServiceImpl.requireOwnProfile` | KAN-14 follow-up after merge |
| `review/service/impl/ReviewServiceImpl.eligible`, `mine`, `submit`, `patient` | Expand self-only eligibility/access and SELF projection in KAN-14 follow-up |

Book/cancel/reschedule/reschedule availability and visit progress already call the public
Patient authorization port and inherit confirmed-dependent authorization. Appointment list/detail
still self-filter and can therefore omit a dependent appointment after successful booking.
The old disabled care URLs have no production consumer; their controller behavior is replaced.
Historical dormant request/confirm classes are not registered as beans or exposed by the R3 APIs.

## Error contract

Existing ErrorResponse and CorrelationIdFilter remain unchanged; no messageCode added.

| HTTP | Application code / condition |
| --- | --- |
| 400 | VALIDATION_FAILED (MSG-17), OTP_INVALID (MSG-82 semantics) |
| 403 | ACCESS_DENIED: wrong role/inactive actor |
| 404 | CARE_LINK_NOT_AVAILABLE: missing/wrong owner/revoked; dependent not confirmed |
| 409 | CARE_LINK_NOT_AVAILABLE: ambiguity, duplicate live link, wrong pending state/type |
| 409 | STALE_VERSION: optimistic update race |
| 429 | RATE_LIMIT_EXCEEDED |
| 503 | EMAIL_DELIVERY_UNAVAILABLE |
| 500 | MSG-04 |

Ownership is checked before detailed OTP state. MSG-82 is absent from the current appendix;
the R3 endpoint documents the supplied wrong/expired semantics and uses OTP_INVALID.
Anonymous requests with valid CSRF yield 401; missing CSRF can yield 403 before authentication.

## Manual checks

1. Apply migrations on a local/test database; inspect V049 and out-of-order history.
2. Sign in as Patient with a self profile and obtain the existing CSRF token.
3. POST `/api/v1/me/dependents` with the UC13 fields and a unique tuple. Expect 201 LINKED;
   inspect null account/contact/address and confirmed metadata. Read selector/detail.
4. For an existing account-backed CCCD profile, POST the exact tuple. Expect 202 OTP_SENT
   with masked email. Receive the code only from the configured mailbox. Wrong code gives
   OTP_INVALID; valid code confirms. Resend invalidates the previous current challenge.
5. For an existing accountless or non-CCCD profile, expect COUNTER_REQUIRED. An active
   receptionist finds it by name/document, confirms with `identityChecked:true` or rejects it.
6. GET/PUT dependent administrative fields; no client version. Other Patients cannot access.
7. POST `/api/v1/appointments` using confirmed dependent ID and a valid available slot.
   Pending/revoked IDs are denied. Self booking remains available.
8. DELETE dependent; confirm access disappears but profile/appointment/history remain.
9. Inspect generated `/v3/api-docs` for all R3 paths and error envelopes. No frontend mock
   contract or unrelated production endpoint is replaced.

## Original implementation verification evidence (before review fixes)

- Initial compile/focused checkpoint: 48 tests, 0 failures/errors/skips.
- First expanded focused run: 107 tests, 1 failure/1 error; both were legacy-type fixtures
  submitting a document number without the required request document type. Fixtures were
  corrected to valid CCCD requests matching legacy stored null type; validation was not weakened.
- Expanded focused regression: 141 tests, 0 failures/errors/skips, including 37 R3 PostgreSQL
  cases, actual HTTP booking, constraints, out-of-order upgrade, UC06 and KAN-24 regressions.
- Additional revoke/booking race coverage is included in the subsequent full runs.
- Wrapper initially failed inside sandbox (`Cannot index into a null array`); approved
  outside-sandbox JDK 21 wrapper execution succeeded. No Maven/dependency changes.
- PostgreSQL 18.6 runs in dedicated loopback-only test containers on 5433 and 5432; the
  second port supports existing integration tests that hardcode 5432. Test-only trust auth
  uses no production credentials. Source/fixtures and temporary baseline use isolated schemas.
- Logs are retained under the OS temporary `otcms-kan26-evidence` directory, outside `target`
  so clean verify cannot erase them. Full test, clean verify and product baseline results follow.

### Full test / untouched product comparison

- Exact `.\mvnw.cmd test`, JDK 21: 775 tests, 7 failures, 5 errors, 0 skipped.
  Four ActivityLog errors (three test cases plus class cleanup) were caused by the initially
  unavailable hardcoded port 5432. The dedicated 5432 test container resolves that environment gap.
- The remaining eight failing methods were reproduced on untouched `origin/product` commit
  `1638a29`, exported with `git archive` into the OS temporary directory. No baseline source
  was patched and the working checkout/branch was not changed.
- Baseline selection: AppointmentArrivalT053PostgresIntegrationTest,
  AdministratorAccountDatabaseVerificationTest, T005RealLoginIntegrationTest,
  T008PasswordRecoveryPostgresIntegrationTest, T032AdminResourceMutationPostgresIntegrationTest,
  ActivityLogPostgresIntegrationTest and ClinicServicePostgresIntegrationTest.
- Baseline result: 33 tests, 7 failures, 1 error, 0 skipped. ActivityLog (3) and ClinicService (7)
  passed. Non-environment failing suite/method/kind lists exactly match the feature full run.

| Suite | Method | Kind |
| --- | --- | --- |
| AppointmentArrivalT053PostgresIntegrationTest | timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow | failure |
| AppointmentArrivalT053PostgresIntegrationTest | timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace | failure |
| AdministratorAccountDatabaseVerificationTest | upgradeFromV019PreservesExistingAccountsAndSessionLinks | error |
| T005RealLoginIntegrationTest | wrongPasswordsPersistLockoutAndSuccessResetsCounter | failure |
| T008PasswordRecoveryPostgresIntegrationTest | changingPasswordInvalidatesPreviouslyIssuedResetLink | failure |
| T032AdminResourceMutationPostgresIntegrationTest | mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites | failure |
| T032AdminResourceMutationPostgresIntegrationTest | adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits | failure |
| T032AdminResourceMutationPostgresIntegrationTest | checkedInAppointmentAlsoBlocksTimeOffCreation | failure |

These failures concern existing arrival/time-sensitive fixtures, account-type upgrade fixtures,
login lockout/reset expectations and staff-password-reset sender expectations. They are not
fixed or bypassed by KAN-26. Generated test credentials/proofs are not copied into this document.

### Clean verification

- Exact `.\mvnw.cmd clean verify`, JDK 21: **774 tests, 7 failures, 1 error, 0 skipped**.
- The eight suite/method/failure-kind entries exactly equal the untouched product baseline
  recorded above. The initial ActivityLog connection errors are absent; ActivityLog and
  ClinicService pass after local test port readiness was corrected.
- KAN-26 new suites: DependentProfileServiceTest 18/0/0/0;
  DependentsR3PostgresIntegrationTest 38/0/0/0; PatientProfileAccessServiceTest 2/0/0/0;
  EmailOtpReuseTest 6/0/0/0. Total: **64 new tests, all PASS**.
- UC06 regression: PatientRegistrationT004Test 9/0/0/0 and T004PostgresIntegrationTest 6/0/0/0.
- KAN-24 regression: PatientProfileServiceTest 30/0/0/0; PatientProfileControllerTest 8/0/0/0;
  PatientProfileT007PostgresIntegrationTest 9/0/0/0; reception-verification suite also PASS.
- Original AppointmentBookingT022PostgresIntegrationTest: 5/0/0/0, in addition to new
  dependent actual-POST and revoke/booking concurrency acceptance.
- Clean verify stops in the test phase because of the reproduced baseline failures;
  it is not a successful package/verify result. No unrelated failure is suppressed or fixed.
- Machine-readable comparisons: `baseline-failures.json`, `full-test-failures.json`,
  `clean-verify-failures.json` in the temporary evidence directory contain only suite/method/kind.
- After clean verification, only legacy test method names were clarified to describe R3
  and absent legacy URLs accurately; the final focused suite verifies this final test source.
- Final focused command (same 14-suite selection recorded above): **142 tests, 0 failures,
  0 errors, 0 skipped; BUILD SUCCESS**. This includes all 38 R3 PostgreSQL cases and the
  final source after test-name cleanup. Log: `focused-final.log` in the evidence directory.

## Regression and delivery boundary

- KAN-24 validation/DTO/account linkage/client-version behavior remains intact.
- KAN-25 appointment/availability production code is untouched; original booking suite passes.
- UC06 preserves generation/hash/TTL, registration consume/supersede/attempt/rate behavior.
- Appointment, Visit, Billing, Review/KAN-14 and Notification production directories are unchanged.
- Historical migrations and dependency files are unchanged. Only new V049 is added.
- No commit, push, formatter or dependency installation was performed.
- Final implementation recommendation remains **PENDING HUMAN REVIEW**; full-project green
  verification remains limited by the explicitly reproduced baseline failures above.
- Original Git whitespace check PASS. Pre-fix inventory: 19 modified tracked files and 20 new
  untracked files, nothing staged. Tracked `git diff --stat`: 248 insertions, 249 deletions;
  Git's tracked-only stat excludes the 20 new files listed below.
- Dedicated KAN-26 PostgreSQL containers are stopped after verification; their test data,
  temporary product archive and evidence logs remain available for human inspection.

## File inventory

Modified (19):

- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/entity/PatientProfileJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/PatientProfileRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/PatientProfileDirectory.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/PatientProfileDirectoryServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/RegistrationServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/controller/CareRelationshipController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/exception/CareRelationshipExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/mapper/CareRelationshipMapper.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/CareRelationship.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/entity/CareRelationshipJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/repository/CareRelationshipRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/AuthorizePatientProfileService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/AuthorizePatientBillingProfileServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/AuthorizePatientProfileServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/PatientProfileServiceImpl.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/CareRelationshipT008PostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/CareRelationshipT008Test.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/CareRelationshipT008WebTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientAuthorizationT008Test.java`

New (22, including the review-fix HTTP message and masking suites):

- `docs/kan26-dependents-r3.md`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/EmailOtpService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/EmailOtpServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/config/DependentOpenApiConfiguration.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/controller/CounterCareLinkController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/exception/CareOtpInvalidException.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/dto/DependentDtos.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/model/entity/CareLinkOtpChallengeJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/repository/CareLinkOtpChallengeRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/CounterCareLinkService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/DependentProfileService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/PatientProfileAccessService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/AdministrativeProfileFields.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/DependentProfileServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/PatientProfileAccessServiceImpl.java`
- `src/main/resources/db/migration/V049__extend_patient_caregivers_for_r3.sql`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/EmailOtpReuseTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/DependentProfileServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/DependentsR3PostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/PatientProfileAccessServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/CareErrorMessageWebTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/patient/service/impl/DependentEmailMaskingTest.java`

## Review-fix verification evidence (2026-11-02)

- Runtime: JDK 21; dedicated local PostgreSQL 18.6 on 5433 and 5432, isolated schemas,
  unchanged test-only trust setup. Logs are outside target at
  `%TEMP%/otcms-kan26-fixes-evidence/`, so clean does not erase them.
- Initial targeted run: 75 tests, one failure in the new optimistic-conflict test. Its PUT
  initially repeated unchanged data, so Hibernate correctly emitted no UPDATE. The test now
  changes fullName and interleaves a real independent committed version update; no production
  workaround was introduced.
- Final focused command: `.\mvnw.cmd -Dtest=DependentEmailMaskingTest,CareErrorMessageWebTest,DependentProfileServiceTest,DependentsR3PostgresIntegrationTest,PatientProfileAccessServiceTest,EmailOtpReuseTest,PatientProfileServiceTest,PatientProfileControllerTest,PatientProfileT007PostgresIntegrationTest,PatientProfileReceptionVerificationT046Test,PatientRegistrationT004Test,T004PostgresIntegrationTest,CareRelationshipT008PostgresIntegrationTest,PatientAuthorizationT008Test,CareRelationshipT008Test,CareRelationshipT008WebTest test`.
- Result: **161 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (`focused.log`,
  `focused-results.json`). Includes all 42 R3 PostgreSQL tests, six UTF-8 HTTP message cases,
  nine masking cases and UC06/KAN-24 regressions. Nineteen cases were added to the original 142.
- H1 evidence: `slowCareSenderDoesNotBlockUnrelatedRealBooking(false/true)` blocks initial
  delivery/resend, verifies the delivery code remains unusable, completes unrelated real
  POST /appointments with 201 before releasing SMTP, then completes care with 202 and verifies
  its delivered code. The real coordination guard and authorization remain active.
- `failedEarlierDeliveryCannotInvalidateSuccessfulReplacement` proves failed older SMTP
  compensation cannot invalidate a newer successfully sent challenge. Delivery failure test
  proves pending-link retention, superseding, no current failed challenge and deterministic quota.
- Existing concurrent duplicate add, resend/verify, two verifies and revoke/booking tests PASS.
- M1: `CareErrorMessageWebTest` asserts exact Vietnamese messages without `?` for validation,
  unavailable link, OTP, access, stale version and unexpected error; envelope/statuses unchanged.
- M2: ordinary and one-character locals become `***@example.com`; quoted local
  `"alice@internal"@example.test` becomes `***@example.test`, exposing neither alice nor internal.
  Null/blank/malformed defensive cases become `***`; email validation is unchanged.
- `dependentPutRealConcurrentPersistenceConflictReturns409` exercises real HTTP, persistence
  and JPA optimistic locking; it returns STALE_VERSION without client version. The Directory
  spy only schedules the independent DB write and calls the real implementation.
- Selector explicitly asserts SELF first/CONFIRMED, confirmed dependent CONFIRMED,
  PENDING_OTP, PENDING_COUNTER, deterministic ordering and no revoked entry.
- Baseline source integrity rechecked: 807 source/pom files in the existing untouched product
  archive match origin/product byte-for-byte; HEAD and origin/product remain `1638a29`.
- Exact `.\mvnw.cmd test`: **793 tests, 7 failures, 1 error, 0 skipped; BUILD FAILURE**.
  The eight suite/method/kind entries exactly equal the untouched product baseline listed
  above (`full-test-failures.json`). ActivityLog PostgreSQL passes; no KAN-26-only failure.
  No claim of full-project green.
- Exact `.\mvnw.cmd clean verify`: **793 tests, 7 failures, 1 error, 0 skipped;
  BUILD FAILURE**, stopping in the test phase. All eight suite/method/kind entries exactly
  equal that same baseline (`clean-verify-failures.json`); no additional ActivityLog error.
- Both full runs agree on the count. The 793 cases equal the previous clean 774 plus 19
  added review-fix cases; no test was removed or skipped. All 83 cases in the six new KAN-26
  suites PASS, including 42 PostgreSQL cases. Detailed regression results are retained in
  `clean-verify-regression-results.json`.
- UC06 primitive/registration suites: 6 + 9 + 6 PASS; KAN-24 service/controller/PostgreSQL/
  reception suites: 30 + 8 + 9 + 3 PASS. Original booking suite: 5 PASS. The real dependent
  POST acceptance and guard/concurrency cases also PASS. Production appointment, visit,
  review/KAN-14, notification and KAN-25 code remain unchanged.
- Latest fixes change only the care orchestration, challenge lookup, UTF-8 care advice,
  related tests and this document. Registration extraction, access helper, counter flow,
  V049 and historical migrations, dependencies and shared ErrorResponse are unchanged by fixes.
- Final Git inventory: 19 modified tracked files, 22 new files, nothing staged; tracked
  diff stat remains 248 insertions / 249 deletions (new files are excluded by git diff).
  Whitespace check PASS. No formatter, commit or push.
- Sync-back: this evidence and final report; implementation remains PENDING HUMAN RE-REVIEW.

## Activation persistence-context fix — Shadow Plan / Action Record

- Human checkpoint: explicit instruction to fix only the remaining stale-challenge activation
  finding. Scope: care challenge repository, activation block, related unit/PostgreSQL tests
  and this evidence. No schema, dependencies, appointment production or UC06 changes.
- Plan: first reproduce the attempts reset against the old implementation; replace activation
  entity mutation with a conditional hash-only bulk update; require exactly one affected row;
  clear persistence context afterward; verify attempts/replacement races and all required suites.
- Preserve short guarded DB phases and SMTP outside transactions, inactive placeholder,
  compensation, quota, expiry, five-attempt policy, errors, masking and access semantics.
- Commands: JDK 21 focused Maven tests, `.\mvnw.cmd test`, `.\mvnw.cmd clean verify`,
  `git diff --check`, `git status`, `git diff --stat`. Results follow after execution.
- Sync-back: this document and final report; PENDING HUMAN FINAL REVIEW. No commit or push.

### Activation implementation and reproduction

- Before-fix regression: both blocking initial-add and resend cases FAIL exactly at the new
  post-activation assertion: expected failed_attempts 1, actual 0. DB already showed 1 while
  SMTP was blocked. Log: `%TEMP%/otcms-kan26-activation-evidence/before-fix.log`.
- `CareLinkOtpChallengeRepository.activateIfCurrent` is a JPQL bulk mutation with
  `@Modifying(flushAutomatically=true, clearAutomatically=true)`. It updates **only otpHash**
  for exact id/linkId, unconsumed, unsuperseded, unexpired and attempts below five.
- Activation requires exactly one affected row. Zero rows maps to CARE_LINK_NOT_AVAILABLE;
  no challenge entity is loaded, merged or saved for activation. The hash-only SQL cannot
  write failed_attempts or other timestamps. Clearing after bulk mutation prevents continued
  use of stale managed challenge state under OpenEntityManagerInView. Flushing first is safe:
  this phase makes no managed challenge mutations before the bulk query.
- SMTP remains outside transactions; guard, placeholder, compensation and quota are unchanged.
- Existing blocking tests now check attempts 1 both before and after activation for add/resend.
  Existing replacement test now covers old SMTP success (409/zero eligible update) and failure
  (503/compensation), with a wrong attempt on B and an unchanged full DB snapshot of B's hash,
  attempts and timestamps after A completes. Hashes/OTP values are not printed by assertions.
- No migration or dependency change. Focused/full/clean verification results follow.

### Activation verification results

- JDK 21, same dedicated PostgreSQL 18.6 test containers and isolated schemas. Logs persist
  outside target in `%TEMP%/otcms-kan26-activation-evidence/`.
- Before-fix command: `.\mvnw.cmd -Dtest=DependentsR3PostgresIntegrationTest#slowCareSenderDoesNotBlockUnrelatedRealBooking test`:
  **2 tests, 2 failures, 0 errors**. Both specifically report attempts expected 1, actual 0.
- Final complete focused command uses the same 16-suite selection as the previous review-fix
  focused run: **162 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Detailed results:
  `focused.log` and `focused-results.json`. R3 PostgreSQL: **43 PASS**; six new KAN-26 suites:
  **84 PASS**. One additional case covers older successful SMTP after replacement.
- Both blocking initial-add/resend cases now preserve attempts = 1 before and after activation
  and retain real unrelated booking isolation. Both old-delivery success/failure cases preserve
  replacement B's full persisted snapshot and allow B's delivered code to confirm.
- Wrong OTP persistence, fifth-attempt exhaustion, correct confirmation, exact expiry,
  resend/verify concurrency, duplicate add and revoke/booking remain PASS.
- Exact `.\mvnw.cmd test`: **794 tests, 7 failures, 1 error, 0 skipped; BUILD FAILURE**.
  All eight suite/method/kind entries match the untouched product archive's actual XML reports
  (33 baseline tests, 7 failures, 1 error). No KAN-26-only failure. Machine-readable evidence:
  `full-test-failures.json`.
- Exact `.\mvnw.cmd clean verify`: **794 tests, 7 failures, 1 error, 0 skipped;
  BUILD FAILURE**. The eight suite/method/kind entries again exactly match the untouched
  baseline listed earlier; no KAN-26-only failure. It stops in the test phase, so this is not
  full-project green. Evidence: `clean-verify-failures.json`, `clean-verify-results.json`.
- Both full runs PASS the 43-case R3 PostgreSQL suite, 18 service cases, UC06 registration/
  primitive regressions, UTF-8 HTTP messages and masking tests. KAN-24, access helper,
  counter/revoke and actual dependent booking regression cases remain PASS.
- Final scope: two care production files, two existing test files and this evidence doc.
  No migration (including V049), dependencies, appointment production or unrelated code changed.
- Final Git: branch unchanged, HEAD `1638a29`; 19 modified tracked files + 22 new files,
  nothing staged. Tracked diff stat: 248 insertions / 249 deletions (excludes new files).
  Whitespace check PASS. No formatter, commit or push. PENDING HUMAN FINAL REVIEW.
