# Doctor Clinical REST API — Action Record

## Scope, authorization and Shadow Plan

- Authorization: explicit user instruction for UC29 examination and UC35 advice HTTP adapters, Advice read capability and bounded tests. This record does not approve an SDD artifact or impersonate Human Final Review. The referenced `.claude/skills/_shared/ai-review-protocol.md` is absent from this checkout.
- Baseline: branch feat/hung/KAN-XX-doctor-examination-flow; HEAD `54f0794034bce01fb148d2feaffdb604c4ac3287`, clean working tree/index, ahead remote-tracking branch by one existing completion-evidence commit. No commit/push/merge/rebase/reset/restore/stash/clean performed.
- Shadow Plan: inspect Git; read existing principal/JWT/security, Staff identity service, Visit/Appointment controllers, error/correlation conventions and four controller test suites; add DTO/mapping/identity helper/two controllers/scoped handler; add Advice read through existing authorization boundary; verify web security/JSON/service regressions, build and source boundaries; document blocked Imaging HTTP and production Visit integration.
- File boundary: Clinical controller, mapper, DTO, security helper, exception handler, Advice service interface/implementation, Clinical tests and this record. No Visit/Imaging/Appointment/Billing/e-Prescription/frontend changes, no persisted state/migration, no ordering/completion-evidence contract changes.

## Implemented endpoints

All return HTTP 200 on success, direct DTO JSON, Cache-Control: no-store and shared X-Correlation-ID. No `/doctor` URL prefix.

| Method | URL | Clinical service |
| --- | --- | --- |
| GET | /api/v1/visits/{visitId}/examination | getExamination |
| PUT | /api/v1/visits/{visitId}/examination | saveDraft |
| POST | /api/v1/visits/{visitId}/examination/completion | completeExamination |
| GET | /api/v1/visits/{visitId}/after-visit-advice | getAdvice |
| PUT | /api/v1/visits/{visitId}/after-visit-advice | recordAdvice |

Examination completion only completes the examination record; it does not close the Visit or release patient results.

## Authentication and identity

Every handler has `@PreAuthorize("hasAuthority('ROLE_DOCTOR')")` and `@AuthenticationPrincipal AuthenticatedAccount`.

`AuthenticatedAccount.accountId -> ClinicalDoctorIdentityResolver -> DoctorProfileIdentityQueryService.findDoctorProfileId -> doctorProfileId`.

Resolver rejects null principal/account id, wrong principal role (even if authority is forged in a test), and missing Doctor profile with MSG-40. Existing JWT filter/AccountAccessService remains responsible for token/session/account eligibility. No new authentication mechanism or Staff repository crossing.

Body DTOs have no doctorId/doctorProfileId/accountId/visitId/patientId/status/version fields. Unknown JSON properties are explicitly ignored, never mapped into commands. Path supplies visitId; server identity supplies Doctor profile. No optimistic concurrency precondition has been added. Service version values are returned as provided and may precede transaction flush increment, as recorded in the prior integration review.

## FE JSON contract

Examination PUT and POST completion use the same body:

```json
{
  "reasonForVisit": "Pain",
  "symptoms": "Swelling",
  "examinationFindings": "Tenderness",
  "injuryBodySite": "Knee",
  "injurySide": "LEFT",
  "injuryMechanism": "Twist",
  "diagnoses": [{"code": "M17.0", "type": "PRIMARY"}]
}
```

Draft accepts `{}` and incomplete content. injuryBodySite max 120, injurySide max 24; no arbitrary TEXT limits. Null diagnosis elements rejected at transport; code/type and clinical completion rules remain domain-owned. DiagnosisType accepts PRIMARY/SECONDARY. Existing M/S-only domain/schema rule is retained.

Examination response adds id, visitId, status (DRAFT/COMPLETED) and version to those clinical fields; diagnoses have only code/type.

Advice PUT body:

```json
{
  "homeCareAdvice": "Rest",
  "activityAdvice": "Avoid impact",
  "rehabilitationAdvice": "Exercise",
  "warningSigns": "Swelling",
  "followUpRecommendation": "Review"
}
```

Advice response adds visitId, examinationStatus, updatedAt (ISO Instant) and version. Fields may be null; GET on an existing examination without advice succeeds with empty advice fields. Missing examination is MSG-41/404. Advice GET is read-only and checks current responsible Doctor through VisitClinicalContextPort; it never creates an examination. PUT still requires at least one nonblank advice field.

Mutations use existing CSRF protection: clients need the established X-CSRF-TOKEN/cookie mechanism in addition to Bearer authentication. No CSRF exception added for Clinical. Shared CorrelationIdFilter propagates a safe UUID X-Correlation-ID; controllers generate no independent ID. Established no-store headers are explicit on both success and Clinical handler errors; Spring Security also retains its default cache headers.

## Error contract

Uses common ErrorResponse: nested error.code/message/timestamp/correlationId/impact and optional root fieldErrors; no raw exception messages/SQL/stack traces.

| Code / condition | HTTP |
| --- | --- |
| MSG-39 domain validation | 400 |
| MSG-40 / legacy MSG-14 permission | 403 |
| MSG-41 unavailable | 404 |
| MSG-42 / optimistic locking | 409 |
| legacy MSG-08 | 400 |
| legacy MSG-04 / unexpected exception | 500 |
| DataAccessException / TransactionException | 503 DEPENDENCY_UNAVAILABLE |
| malformed JSON, enum/path type conversion, bean validation | 400 VALIDATION_FAILED |
| method-security denial | 403 MSG-40 |
| anonymous | 401 from existing SecurityConfig |

Transport field errors use root fieldErrors entries with field and MSG-39. Clinical advice is scoped only to the two new controllers. Current appendix and frontend appMessages were inspected. MSG-08 is ambiguous because existing domain uses it for both clinical validation and legacy Visit eligibility; it remains MSG-08/400 rather than guessing an HTTP 409. Explicit MSG-42 remains 409. No mass-renumbering.

FE source comment calls appendix outdated and refers to R3 Drive, while this checkpoint explicitly uses current checked-in requirements. Inspected MSG-39/40/41/42 meanings are compatible for this adapter; no external source was substituted or frontend file edited.

## Controller and service tests

DoctorClinicalControllerTest has 32 test invocations covering these 15 methods/scenarios:

1. All five endpoints reject anonymous and every non-Doctor role (five parameterized invocations).
2. All five endpoints reject a Doctor without profile (five invocations).
3. Examination GET resolves profile and returns exact response fields.
4. Draft maps all clinical fields and ignores spoofed body identities/status/version.
5. Empty draft remains valid; completion invokes only completeExamination.
6. Advice GET maps every declared response field.
7. Advice PUT uses path/auth identity and shared correlation filter.
8. Missing examination and empty advice return domain errors.
9. Seven current/legacy error-code HTTP mappings for completion.
10. Both injury-field transport limits.
11. Malformed JSON, invalid enum, null diagnosis and invalid UUID use validation envelope.
12. Optimistic, database and unexpected errors are sanitized.
13. All three mutations retain CSRF enforcement.
14. Principal role mismatch and incompatible principal are rejected despite Doctor authority.
15. Request DTO field whitelists are exact and Imaging Request POST is not registered.

ClinicalDoctorIdentityResolverTest adds four tests for null identity, wrong roles, missing profile and successful account-to-profile resolution. Advice service adds five tests for read-only field mapping, examination with no advice, unavailable visit/examination, wrong/null/unassigned Doctor and null visit/read-only annotation. Existing service mutation tests remain passing.

## Explicitly NOT implemented

**POST imaging requests: NOT IMPLEMENTED / BLOCKED BY VISIT ORCHESTRATION.**

Documentation-only future contract:

```http
POST /api/v1/visits/{visitId}/imaging-requests
Idempotency-Key: <client operation key>
```

```json
{"orders":[{"serviceId":"<uuid>","clinicalReason":"Pain","bodyArea":"Knee","instructions":"Weight-bearing"}]}
```

No doctorId. Not registered in Spring MVC. Current Clinical submit commits local round/orders before external Visit coordination; a response failure and retry can duplicate rounds. A real Visit public care-context capability, post-commit orchestration and durable idempotency/retry design are prerequisites. Frozen getImagingOrder/markImagingResultAvailable/isLatestRoundReadyForReview and OrderView are unchanged.

## Production integration status

VisitClinicalContextPort still has no production implementation. No fake/no-op/fallback bean or conditional disabling has been added. MVC tests mock Clinical services only at the web boundary; they do not establish production Visit authorization integration. PostgreSQL TCP probe 127.0.0.1:5432 is unavailable; no DB startup/reset/install or full application context verification is attempted in this checkpoint.

Required final status after executable tests pass: WEB CONTRACT COMPILES; CONTROLLER TESTS PASS; PRODUCTION CLINICAL MUTATIONS STILL BLOCKED BY VISIT INTEGRATION.

## Verification

JDK 21.0.9 via session-only JAVA_HOME `C:\Program Files\Java\jdk-21` and PATH.
Maven: `C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
Every invocation uses `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS outside sandbox after sandbox failed accessing cached spring-boot-starter-jackson JAR. No wrapper/dependency changes.
- `test-compile`: PASS, 168 test source files compiled with release 21.
- `-Dtest=DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest test`: PASS 36/36 (32 MockMvc + 4 identity). Initial test run exposed Mockito restubbing of an already-throwing mock in the sanitization test; corrected to doThrow without a production change. Log: target/doctor-clinical-controller-tests.log.
- `-Dtest=ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,ServiceCatalogClinicalAdapterTest test`: PASS 75/75 (13 examination, 17 advice, 19 ordering, 18 evidence, 8 catalog).
- `-Dtest=DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest,ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,ServiceCatalogClinicalAdapterTest test`: PASS 111/111, zero failures/errors/skipped; final production DTO source recompiled after removing deprecated duplicate container @Valid (element validation retained). Compile/testCompile also PASS in this lifecycle. Log: target/doctor-clinical-all-tests.log.
- `-Dtest=StaffRoomQueryControllerTest,PatientProfileControllerTest,ReviewControllerTest,AdminClinicServiceControllerTest,T010CommonSecurityContractTest test`: PASS 42/42, zero failures/errors/skipped. Log: target/doctor-clinical-security-regression.log.
- Full context/PostgreSQL: BLOCKED; no full-context run this checkpoint, TCP unavailable and missing production Visit bean independently confirmed by source inspection. MVC slice startup PASS is not full application integration PASS.
- Static boundary: controllers/identity helper import no repositories/entities; forbidden owner source, frozen ordering interface/implementation and migrations have no diff. No new migrations or Imaging Request mapping.
- Final git diff --check: PASS; index empty; exactly twelve changed/added files confined to Clinical and this Action Record. No source changes after the final passing suites.

## Sync-back and next checkpoint

No SDD approval, stage/commit/delivery or frontend modification. Next checkpoint: resolve real Clinical ↔ Visit public care-context integration with Visit owner and define durable idempotent Imaging Request orchestration before exposing its mutation API. Examination/Advice FE JSON is defined; production availability remains blocked separately.
