# UC38 Imaging Request API — Action Record

## Scope, authorization and Shadow Plan

- Authorization: explicit user instruction for UC38 HTTP, Clinical-owned durable replay persistence and bounded concurrency recovery. No SDD approval/Human Final Review is impersonated. Referenced `.claude/skills/_shared/ai-review-protocol.md` is absent in this checkout.
- Baseline: HEAD `ce393d9990245ae3e47468dcf9a1fc9186bec613`; branch feat/hung/KAN-XX-doctor-examination-flow; clean index/tree and synchronized fetched remote-tracking branch. origin/product=`b79ac838f58a541fdd14504ad24788a674f666c0`.
- Shadow Plan: inspect Git/current ordering, real Visit port and replay precedent; inspect current UC/BR/FE messages and fetched migration inventories; implement shared preparation, atomic round/orders/replay attempt and outer conflict coordinator; add DTO/controller/scoped errors; validate focused/transaction/MVC tests, Clinical/Visit/security regression, architecture and migration integrity; record real PostgreSQL blocker separately.
- File boundary: Clinical production/test package, one new Flyway migration and this record. No Visit/Imaging/Appointment/Billing/e-Prescription/frontend implementation change. No reset/restore/stash/clean/stage/commit/push/merge/rebase.

## Current requirements

Root `docs/requirements/UC_description.html:132–134` defines UC38 initial/additional X-ray/CT/MRI requests, body area and clinical reason linked to patient/visit. Appendix BR-45 restricts requesting to Orthopedic Doctor and requires imaging type/body area/reason. Type is resolved from the catalog, not accepted from FE.

UC24/BR-33 owns room assignment/related queue coordination; UC25/BR-34 owns room-queue visibility. These are separate from creating Clinical orders. No checked-in current requirement introduces AWAITING_CONCLUSION or a payment gate for UC38.

## Endpoint and authentication

`POST /api/v1/visits/{visitId}/imaging-requests`, required `Idempotency-Key` (nonblank, raw length <=128; trimmed for durable key scope as in Visit precedent).

JWT -> AuthenticatedAccount -> ClinicalDoctorIdentityResolver -> doctorProfileId -> VisitClinicalContextPort.

Method security requires ROLE_DOCTOR. Existing CSRF, correlation filter and no-store conventions apply. Body DTOs whitelist only orders and item serviceId/clinicalReason/bodyArea/instructions; unknown identity/status/round fields are ignored, never trusted. Missing Doctor profile is MSG-40. Each new submission and successful replay rechecks current responsible Doctor/clinical eligibility/patient linkage; replay does not create new work if care is no longer permitted.

First success: 201; replay: 200. No Location because no round GET route exists. JSON omits internal replay/fingerprint, patient ID and Doctor ID. There is no generic service-order HTTP endpoint.

```json
{"orders":[{"serviceId":"00000000-0000-0000-0000-000000000001","clinicalReason":"Suspected meniscal injury","bodyArea":"RIGHT_KNEE","instructions":"Weight-bearing AP/lateral if appropriate"}]}
```

Response fields: id, visitId, roundNumber, orderedAt, orders. Each order: orderId, orderCode, serviceId, serviceName, serviceType, clinicalReason, bodyArea, instructions, priceSnapshot, orderStatus, paymentStatus.

## Shared validation and snapshots

ClinicalOrderPreparationService factors the existing generic Visit/catalog checks. Generic submit preserves its public behavior and legacy validation codes, plus frozen OTHER/false compatibility metadata. UC38 selects imagingOnly validation and MSG-39 for invalid order data; LABORATORY/OTHER rejected, EXAMINATION rejected through the real catalog adapter. All items are prepared before writing a round. Active catalog identity/name/type/price are server-sourced; body area follows persisted VARCHAR(255), prices must fit NUMERIC(12,2). No invented order-count ceiling or TEXT limit.

Writer timestamps are truncated to microseconds to match PostgreSQL TIMESTAMPTZ precision; UC38 price snapshots normalize to scale 2. UC38 first/replay output orders sort by order UUID string. Request list order is still significant in the fingerprint. Existing generic returned-order sequencing is unchanged.

## Durable idempotency

ClinicalImagingRequestReplayJpaEntity / ClinicalImagingRequestReplayRepository own metadata in clinical_imaging_request_replays. Scope: doctor_profile_id + trimmed idempotency_key. SHA-256 fingerprint is lowercase 64-character hex.

Exact canonical binary format (DataOutputStream, signed 32-bit big-endian lengths/counts):

1. Length-prefixed UTF-8 `clinical-imaging-request-v1`.
2. Length-prefixed Doctor profile UUID string.
3. Length-prefixed Visit UUID string.
4. Order count as int32.
5. For each item in request order: service UUID string, clinicalReason, bodyArea, instructions, each length-prefixed UTF-8.

Null instructions encodes length -1; empty string length 0. UUID strings are standard Java UUID lowercase form. Text whitespace is preserved. No timestamps/random/order IDs/JSON property ordering/key value enter the fingerprint. Idempotency key itself selects the replay row. Command list is defensively copied after validation.

Same Doctor/key + same fingerprint returns the original persisted round/orders without catalog lookup or writes. Different fingerprint returns IDEMPOTENCY_CONFLICT/409 before new work. Different key is a distinct operation. Different Doctor has an independent namespace. Readback reflects current persisted order statuses if another authorized workflow updated them; no original-response JSON is stored and replay never mutates statuses.

## Transaction boundaries and concurrency

ClinicalImagingRequestServiceImpl.submit is NOT_SUPPORTED: suspend any ambient transaction, validate/fingerprint and coordinate at most three attempts outside failed transactions.

ClinicalImagingRequestTransactionService.submit is REQUIRED and owns one local transaction:

```text
lookup replay
  existing -> compare fingerprint, authorize current care, reconstruct round/orders
  absent   -> authorize current care, prepare all IMAGING catalog snapshots
              -> ClinicalOrderingTransactionService.createRound (REQUIRED joins same transaction)
              -> saveAndFlush unique replay (flushes round + orders + replay)
COMMIT before proxy returns to coordinator
```

No REQUIRES_NEW round commit. Uniqueness failure leaves the attempt; Spring rolls back the whole attempt before coordinator handles it. Named replay Doctor/key unique violation then invokes recoverReplay in a new read-only transaction, compares the winner fingerprint and reconstructs it. No catch-and-continue in rollback-only context.

Only SQLSTATE 23505 with exact constraint identity is classified: Hibernate constraintName or quoted constraint name in SQLException message/chain. Unrelated integrity failures propagate to sanitized infrastructure errors. Missing winner after claimed collision also propagates; no fabricated success.

Round-number collision (uk_clinical_round_visit_number) is distinct: retry a fresh whole attempt, checking replay first. Maximum three attempts; persistent contention -> MSG-42/409, safe to retry using the same key. Existing V073 uniqueness remains. Two exact same-key contenders may first collide on round number; after winner commit, retry sees its replay. Different-Visit same-key contenders collide on replay uniqueness; losing round/orders roll back.

Read authorization is still a Visit-owned snapshot, not atomic serialization with concurrent Visit close/reassignment. No Visit locks/commands or queue protocol have been invented. This remains a cross-owner coordination concern, independent of durable Clinical idempotency.

## Migration selection/integrity

Selected V075__create_clinical_imaging_request_replays.sql after inspecting local inventory (through V074), origin/product (through V072), and all fetched origin remote refs from repository root for V075+ collisions; none found at inspection time. Recheck before merge if other branches add migrations.

Actual FK target from V073: clinical_ordering_rounds(id). Named constraints:

- uk_clinical_imaging_replay_doctor_key: UNIQUE(doctor_profile_id,idempotency_key)
- uk_clinical_imaging_replay_round: UNIQUE(ordering_round_id)
- fk_clinical_imaging_replay_round: FK ordering_round_id, ON DELETE RESTRICT
- nonblank key and lowercase 64-hex fingerprint CHECKs

Replay fields: UUID PK, Doctor UUID, key VARCHAR(128), fingerprint VARCHAR(64), round UUID, created_at TIMESTAMPTZ; all NOT NULL. V073/V074 unchanged. No cross-feature table FK/query or response JSON persistence.

## HTTP errors

Scoped ClinicalApiExceptionHandler includes only the three Clinical controllers. MSG-39=400, MSG-40=403, MSG-41=404, MSG-42=409, IDEMPOTENCY_CONFLICT=409, DEPENDENCY_UNAVAILABLE=503. Missing header/malformed JSON/transport validation use existing VALIDATION_FAILED/400 with shared ErrorResponse and optional fieldErrors. Unexpected failures use sanitized MSG-04/500. No new MSG number; IDEMPOTENCY_CONFLICT is existing Visit/backend convention (FE fallback behavior unchanged).

## Ownership handoff

Visit mutation: NONE. No synthetic Visit state, AWAITING_CONCLUSION, care completion, room reservation or queue mutation.
Room/queue mutation: NONE; separate Visit UC24/UC25 workflow.
Payment: existing PENDING_PAYMENT initialization remains; no Billing call, no PAID mutation, no payment gate. Frozen result-availability semantics remain unchanged.
Imaging: technician/Imaging owner consumes created IMAGING orders through existing frozen projection/result methods and future worklist integration. No DICOM/study/findings/viewer implementation.

## Verification and Action Record results

JDK 21.0.9, session-only JAVA_HOME=C:\Program Files\Java\jdk-21 and PATH. Maven executable:
`C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
Every invocation uses `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

Initial sandbox compile failed accessing cached spring-boot-starter-jackson JAR; exact invocation escalated, no dependency/wrapper changes.

- `-DskipTests compile`: PASS outside sandbox, BUILD SUCCESS.
- `test-compile`: PASS, 175 test source files compiled with release 21, including the new PostgreSQL suite.
- `-Dtest=ClinicalImagingRequestServiceImplTest,ClinicalImagingRequestConcurrencyTest,ClinicalImagingRequestControllerTest test`: PASS 52/52 (23 service/fingerprint, 8 orchestration/transaction boundaries, 21 MVC/security), zero failures/errors/skipped. Log target/uc38-focused-tests.log.
- `-Dtest=ClinicalImagingRequestServiceImplTest,ClinicalImagingRequestConcurrencyTest,ClinicalImagingRequestControllerTest,ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest,DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest,ServiceCatalogClinicalAdapterTest,VisitClinicalCareContextServiceImplTest test`: PASS 183/183 (171 all Clinical unit/MVC/wiring tests + 12 Visit query), zero failures/errors/skipped. Existing Clinical counts: examination 13, advice 18, ordering 20, completion evidence 18, Visit adapter 4, wiring 1, Doctor MVC 33, identity 4, catalog 8. Log target/uc38-all-clinical-tests.log.
- `-Dtest=StaffRoomQueryControllerTest,PatientProfileControllerTest,ReviewControllerTest,AdminClinicServiceControllerTest,T010CommonSecurityContractTest test`: PASS 42/42, zero failures/errors/skipped. Log target/uc38-security-regression.log.

New tests cover service replay/fingerprint field changes/catalog/authorization, safe HTTP/security/JSON, named uniqueness recovery and bounded retry. A real Spring TransactionInterceptor test records loser rollback before winner read; it is transaction-control evidence with mocked persistence, not DB rollback proof.

PostgreSQL integration test added for concurrent exact requests, different-Visit duplicate-key loser rollback, different-key same-Visit round allocation and migration metadata (four cases). Actual execution BLOCKED: TCP 127.0.0.1:5432 unavailable, confirmed again outside sandbox. No DB/full application run, migration application, install/start/reset or real database concurrency PASS claimed.

Architecture checks PASS: no Visit repository/entity, Imaging repository, room/queue service or synthetic-transition references in Clinical main source. Existing frozen getImagingOrder/markImagingResultAvailable/isLatestRoundReadyForReview/cancel implementations and public signatures remain unchanged; generic submit delegates shared preparation. Old Doctor MVC test retains DTO whitelist checks and removes the obsolete "imaging mutation not exposed" assertion because UC38 now exists.

Migration static inspection PASS: exact table/column/constraint/FK names align with entity; V073/V074 have no diff. Final git diff --check PASS; index empty; exactly 20 files changed/added, confined to Clinical, V075 and this Action Record. No source changes after passing suites. No stage/commit/delivery, no SDD artifact approval. Next backend checkpoint after review: Doctor workspace read model for current Visit/patient context and imaging request status, without implementing Imaging-owned findings.
