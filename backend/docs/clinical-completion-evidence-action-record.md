# Clinical completion evidence — Action Record

## Scope and Shadow Plan

- Authorization: explicit user instruction to implement only Clinical-owned completion evidence for current UC28 composition. No SDD approval or Human Final Review is impersonated; the referenced `.claude/skills/_shared/ai-review-protocol.md` is absent from this checkout.
- Baseline: branch `feat/hung/KAN-XX-doctor-examination-flow`; HEAD `26f1f2a3449a55dc2c1e28c4af8af90b9f2a0207`; working tree and index clean. Earlier advice/catalog work is already committed at this HEAD.
- Shadow Plan: inspect Git; re-read Clinical entities/enums/repositories/contracts and V073/V074; add immutable public evidence and all-visit order query; implement pure Clinical read; add focused tests; compile/testCompile/focused and Clinical regression; audit forbidden imports, legacy evidence drift and migration inventory; hand off to Visit owner.
- File boundary: new ClinicalVisitCompletionEvidenceService interface, its implementation/test, ServiceOrderRepository and this record. No controller, new persistence state or migration; no Visit/Imaging/Appointment/Billing/e-Prescription/frontend implementation changes. No reset/restore/stash/clean/stage/commit/push/merge/rebase.

## Contract and behavior

Public interface: `fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService`.
Method: `ClinicalCompletionEvidence getCompletionEvidence(UUID visitId)`.
Nested immutable record fields: visitId, examinationExists, examinationCompleted, activeOrderCount, outstandingOrderCount, allNonCancelledOrdersHaveResults, imagingOrderIds.

Implementation is a constructor-injected `@Service` with `@Transactional(readOnly = true)` and only ClinicalExaminationRepository / ServiceOrderRepository dependencies.

- Examination status alone determines completion: absent false/false; DRAFT true/false; COMPLETED true/true. Advice and diagnosis counts are not inspected.
- ServiceOrderRepository.findByVisitId is a Spring Data derived query over all rounds. No latest-round query, native SQL or OrderingRoundRepository dependency.
- CANCELLED is ignored; ORDERED is active/outstanding; RESULTS_AVAILABLE is active/not outstanding. Zero active orders means allNonCancelledOrdersHaveResults=true, even if examination is absent. These facts do not authorize closure.
- Imaging IDs include all non-cancelled IMAGING requests, ordered or available; exclude LABORATORY/OTHER. Sorted by createdAt then UUID string. Record constructor defensively copies IDs.
- Payment status is not read. Study/result availability never stands for findings completed/released. No canCloseVisit, Visit state, queue state, imaging findings or invoice fields.
- Null visitId throws ClinicalOperationException MSG-39. No Clinical records for a UUID returns empty evidence without proving that a Visit exists.
- No entity/list mutation; no repository exception swallowing or conversion into empty evidence.
- V073 already supplies clinical_examinations.visit_id uniqueness and ix_clinical_orders_visit; V074 advice fields are unrelated. No schema addition required.

## Tests added

18 cases: no Clinical records; DRAFT with advice remains incomplete; COMPLETED with no advice/diagnosis dependency; examination with zero orders; ORDERED; RESULTS_AVAILABLE; CANCELLED; mixed statuses; outstanding older round with ready latest round; reverse multi-round; IMAGING inclusion/exclusion for every supported service type/status; deterministic timestamp/UUID order despite repository ordering; immutable output with no repository-list/entity mutation; record defensive copy; RESULTS_AVAILABLE with PENDING_PAYMENT; null UUID MSG-39 before persistence; examination read failure propagation; order read failure propagation.

## Legacy evidence audit and Visit handoff

Read-only inspection: VisitWorkflowRepository.findFollowUpEvidence reads legacy visit_examinations.completed and follow_up_indicated scoped by doctor. New Clinical writes clinical_examinations.status and UC35 follow_up_recommendation TEXT. No dual-write or legacy table change. Free-text follow-up advice does not establish the legacy boolean; a Visit-owned follow-up integration decision remains necessary.

Conceptual composition, to be implemented by Visit/Reception owner:

```java
ClinicalCompletionEvidence evidence =
        clinicalVisitCompletionEvidenceService.getCompletionEvidence(visitId);
```

Visit owner must verify actual visit exists/open/closable, examinationCompleted, allNonCancelledOrdersHaveResults, Imaging-owned completed/released findings for every imagingOrderId, and Visit-owned queue/care constraints. It must authorize the Receptionist and coordinate concurrent closure, examination/order writes and cancellation. This read query alone is not a cross-context atomic closure guarantee. No payment/PAID/invoice-balance requirement is added.

Current evidence: root UC_description.html UC28/29/35/38/40/42 and appendix BR-37/38/42/47/49. MSG-39 is current invalid/incomplete clinical input. No checked-in evidence found requiring payment completion for Clinical completeness.

## Verification

Session-only JAVA_HOME `C:\Program Files\Java\jdk-21`; java -version reports 21.0.9.
Maven executable: `C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
All Maven invocations use `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS outside sandbox after the sandbox run failed accessing cached spring-boot-starter-jackson JAR. No wrapper/dependency changes.
- `test-compile`: PASS, 166 test source files compiled with release 21.
- `-Dtest=ClinicalVisitCompletionEvidenceServiceImplTest test`: PASS 18/18, zero failures/errors/skipped.
- `-Dtest=ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ServiceCatalogClinicalAdapterTest test`: PASS 52/52 (13 examination, 12 advice, 19 ordering, 8 adapter), zero failures/errors/skipped. Combined with focused suite this exhausts current Clinical unit classes; only PostgreSQL integration class is excluded.
- Final combined `-Dtest=ClinicalVisitCompletionEvidenceServiceImplTest,ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalOrderingServiceImplTest,ServiceCatalogClinicalAdapterTest test`: PASS 70/70, zero failures/errors/skipped, BUILD SUCCESS at 2026-11-04T17:03:37+07:00.
- PostgreSQL integration: BLOCKED; TCP probe 127.0.0.1:5432 unavailable. No DB startup/reset/install or integration execution attempted this checkpoint.
- Static boundary: PASS; searches over the new service/interface find no Visit/Imaging/Billing/Appointment/catalog imports, and only two Clinical repository dependencies. Existing ClinicalOrderingService and ClinicalOrderingServiceImpl have no diff; frozen Imaging signatures and behavior are unchanged.
- Migration inventory: no changed/new migration, latest remains V074. No V075.
- git diff --check: PASS after final suite and record update; index remains empty. Exactly five files changed/added within the declared boundary.

Sync-back: no existing Clinical or Imaging-facing contract changed; no SDD artifact approval, delivery or final canClose decision. Next backend checkpoint after PASS: define the Doctor-facing Clinical REST API contract for examination, imaging requests and advice, with explicit authenticated identity/authorization and unresolved Visit eligibility prerequisites; no frontend implementation.
