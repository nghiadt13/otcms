# Clinical ↔ Visit current-care integration — Action Record

## Scope, authorization and Shadow Plan

- Authorization: explicit user instruction to add a Visit-owned public read capability, real Clinical adapter and replace legacy Clinical eligibility. No SDD approval/Human review is impersonated. Referenced `.claude/skills/_shared/ai-review-protocol.md` is absent in this checkout.
- Baseline: HEAD `b201bbcf75d1d0bef9c357b2b85a85ccb5b11ab3`; branch feat/hung/KAN-XX-doctor-examination-flow; clean working tree/index and synchronized remote-tracking branch. No reset/restore/stash/clean/commit/push/merge/rebase performed.
- Shadow Plan: inspect Git/contracts and real Visit persistence/care commands; establish read-only current-care query; add Clinical adapter; replace legacy eligibility and obsolete transition method; retain Ordering signature with neutral compatibility metadata; update fixtures and add service/adapter/wiring/HTTP regression; compile/testCompile/focused/Clinical/Visit/security suites; document DB blocker and ownership handoff.
- File boundary: VisitClinicalCareContextService, its implementation/test and one VisitCareAssignmentRepository read method; Clinical port/adapter and affected Examination/Advice/Ordering implementations/tests; frozen SubmitRoundResult documentation only; this record. Existing Visit lifecycle/room/queue commands, entities, frontend, Imaging, Appointment, Billing and migrations remain unchanged.

## Exact Visit facts

- V007 defines persisted OPEN/CLOSED visits and RESERVED/IN_PROGRESS/COMPLETED/RELEASED assignments; V020 renames tables. ClinicVisitJpaEntity starts walk-in visits OPEN.
- ManageVisitCareAssignmentServiceImpl.startWalkIn saves VisitCareAssignmentJpaEntity.inProgress and completes through the existing assignment command. VisitWorkflowRepository.insertScheduledCare writes IN_PROGRESS; findActiveAssignment selects IN_PROGRESS ordered by assigned_at DESC/id DESC. These are treating-care facts, unlike broad RESERVED/IN_PROGRESS capacity queries.
- Current treating Doctor comes only from deterministic IN_PROGRESS selection. RESERVED is not active clinical treatment; COMPLETED/RELEASED are not reused as responsible-Doctor authorization.
- clinicalWorkAllowed = visit OPEN AND IN_PROGRESS assignment exists AND doctor_profile_id is non-null. No Clinical/Imaging/payment/invoice/examination dependency.

## Visit public contract and adapter

`VisitClinicalCareContextService.findClinicalCareContext(UUID)` returns Optional of immutable ClinicalCareContext(visitId, patientProfileId, activeCareAssignmentId, responsibleDoctorProfileId, visitOpen, clinicalWorkAllowed).

Implementation is `@Service @Transactional(readOnly=true)` with only ClinicVisitJpaRepository and VisitCareAssignmentRepository. Null/missing ID returns Optional.empty. Missing Visit never queries assignments. Existing Visit without IN_PROGRESS care returns context with null Doctor/assignment and false eligibility. CLOSED returns false visitOpen/eligibility even with a stale IN_PROGRESS assignment; any actual selected assignment fields are preserved as facts. Exceptions propagate rather than becoming absence.

Derived method: `findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS")`. No native SQL, getter/entity change, persistence mutation or migration. Spring Data PartTree unit test validates property names, selection limit and descending assignedAt/id ordering; actual multi-row PostgreSQL selection remains DB-blocked.

`clinical.integration.visit.VisitClinicalContextAdapter` is a real `@Component` implementing VisitClinicalContextPort. It depends only on VisitClinicalCareContextService and maps visitId/patientProfileId/responsibleDoctorProfileId/clinicalWorkAllowed to Clinical visitId/patientId/responsibleDoctorId/clinicalWorkAllowed. Optional.empty maps to null, preserving MSG-41 handling. No Visit repository/entity imports in Clinical main source.

## Removed legacy assumption and Ordering compatibility

Repository-wide src search before edit found markAwaitingConclusion only on the interface and tests, no production caller. Removed it entirely; there is no fake command or no-op adapter. ClinicalVisitContext now contains boolean eligibility, not a Clinical lifecycle enum.

OPEN is NOT mapped to IN_EXAMINATION. No AWAITING_CONCLUSION mutation/state was created. ClinicalVisitStatus enum remains only for frozen SubmitRoundResult compatibility; record signature is unchanged.

Successful submit returns previousVisitStatus=OTHER and requiresAwaitingConclusionTransition=false. These fields do not report real Visit lifecycle. No Visit mutation occurs after Clinical's local ordering transaction. getImagingOrder, markImagingResultAvailable, isLatestRoundReadyForReview, cancel and OrderView behavior/signatures are unchanged.

## Examination and Advice authorization

- Examination writes: current responsible Doctor plus clinicalWorkAllowed; false eligibility now MSG-42. Missing Visit MSG-41; wrong/unassigned Doctor MSG-40. Reads retain responsible-Doctor checks without the write-eligibility gate. Existing domain validation remains unchanged.
- Ordering submit uses the same real eligibility and current MSG-40 permission semantics, and still requires Visit-owned patientId. No catalog/write calls on rejected eligibility.
- UC35 HTML describes advice for current visit; BR-42 assigns advice to its examination record and Orthopedic Doctor. No checked-in exception found granting advice edits after active care ends. Write now requires clinicalWorkAllowed, MSG-42 otherwise, and still requires an existing examination; no auto-create.
- Advice GET does not check clinicalWorkAllowed, so false write eligibility alone does not block a known responsible Doctor. It remains read-only.
- Important historical-read boundary: this current-care query only proves Doctor responsibility while an IN_PROGRESS assignment exists. Once it becomes COMPLETED/RELEASED, responsibleDoctorId is null and existing GET authorization fails closed (MSG-40). This is not an affirmative historical-access policy and not a fake completed-assignment fallback. A historical Doctor read capability requires separate Visit/Clinical authorization evidence; this checkpoint does not grant access based on an ended assignment or infer it from free text. CLOSED with a stale IN_PROGRESS assignment exposes false write eligibility but known Doctor facts; read and write gates remain separate.
- Missing current Doctor is permission denial before eligibility evaluation; known Doctor with false eligibility is state conflict. Existing HTTP handler already maps MSG-42 to 409. Added MockMvc test passes actual domain guards through HTTP for 404/403/409 without writes. No DTO/endpoint changes.

## Production bean evidence and limitations

Exactly one main-source implementation is expected: VisitClinicalContextAdapter, with @Component and constructor dependency on the real Visit-owned @Service. Isolated ApplicationContextRunner test scans that production adapter package, imports the production Visit read service and mocks only the two persistence repositories. It checks a single port/service bean and reads through the real chain. This resolves the missing-port wiring blocker without a main-source fake/mock/fallback.

This isolated context is not a full application/database test. Read eligibility is a snapshot, not atomic coordination with concurrent care completion, reassignment or close. No lifecycle lock/protocol was changed. Cross-context ordering idempotency/coordination is still required before Imaging Request REST.

## Visit files for Long's ownership review

- src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitClinicalCareContextService.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitClinicalCareContextServiceImpl.java
- src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitCareAssignmentRepository.java
- src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitClinicalCareContextServiceImplTest.java

No Visit entity or existing service implementation changed. No start/extend/complete/cancel/close, room or queue behavior refactor.

## Tests and exact verification

JDK 21.0.9, session-only JAVA_HOME=C:\Program Files\Java\jdk-21 and PATH.
Maven executable: `C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
Every Maven invocation uses `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS outside sandbox after cached-JAR access failure inside sandbox; wrapper/dependencies unchanged.
- `test-compile`: PASS, 171 test source files compiled with release 21, including updated PostgreSQL fixture for boolean context.
- `-Dtest=VisitClinicalCareContextServiceImplTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest test`: PASS 17/17 (12 Visit query, 4 adapter, 1 real production wiring), zero failures/errors/skipped. Log target/clinical-visit-context-focused.log.
- `-Dtest=ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest,DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest,ServiceCatalogClinicalAdapterTest,VisitClinicalContextAdapterTest,VisitClinicalContextProductionWiringTest test`: PASS 119/119 (13 examination, 18 advice, 20 ordering, 18 completion evidence, 33 Doctor MVC, 4 identity, 8 catalog, 4 adapter, 1 production wiring), zero failures/errors/skipped. Log target/clinical-visit-context-all-clinical.log.
- `-Dtest=VisitCareCapacityT031Test,VisitRoomCapacityT031Test,CreateWalkInVisitT043Test,VisitApiExceptionHandlerTest,StaffRoomQueryControllerTest,PatientProfileControllerTest,ReviewControllerTest,AdminClinicServiceControllerTest,T010CommonSecurityContractTest test`: PASS 57/57 (15 Visit unit + 42 MVC/security regression), zero failures/errors/skipped. Log target/clinical-visit-context-owner-regression.log.
- TCP 127.0.0.1:5432 unavailable, confirmed again outside sandbox. PostgreSQL/full application context BLOCKED; not attempted against an unavailable DB, no DB startup/reset/install. No claim of actual persistence-query or rollback verification.
- Architecture search: exactly one production VisitClinicalContextPort implementation; no Clinical main-source Visit persistence imports; no markAwaitingConclusion usage remains in src.
- Migration inventory: no changed/new migration, latest remains V074; no V075. Final git diff --check PASS; index empty; exactly eighteen files changed/added within the declared scope. No existing Visit lifecycle command/entity, frontend or other owner feature changed.

## Sync-back and next checkpoint

No stage/commit/delivery or SDD approval. Prior Action Records describe historical blockers; this checkpoint supersedes only the missing current-care read/port wiring blocker. Legacy visit_examinations follow-up integration and historical read authorization remain separate work.

Imaging Request REST remains NOT EXPOSED. Its blocker is now durable idempotent cross-context ordering/Visit coordination, not missing Visit read context. Next checkpoint: design and implement Idempotency-Key Imaging Request orchestration/API without inventing Visit lifecycle states, accounting for retries after local commit and current-care rechecks.
