# Doctor Imaging Review / Result Coordination — Action Record

## A. Git state

2026-11-05: clean working tree, UC33 committed at c733b6a and present on its remote
branch. `origin/product` is ef096b3; UC33 is not its ancestor (4/2 left/right commits).
Created stacked branch `feat/hung/KAN-XX-doctor-imaging-review` from c733b6a.
Ran requested status, branch, fetch, log, diff/stat/check and cached/stat commands.
Fetch required sandbox escalation for repository metadata. No merge/rebase/reset,
commit/push, deletion, frontend edit or import of another branch is authorized.

## B. Current product baseline

UC33/Medical Documents/Prescription/Core are present in the committed stacked baseline.
HEAD remains c733b6a1f9257c6e635866f5d3f8fe49dfbe7697. The index remains untouched and
empty; all checkpoint changes are unstaged/untracked. Frontend and V099/V100 have no diff.

## C. Requirements evidence

UC_description.html UC40/41/42/44 and appendix BR-45/47/48/49/50 define the workflow.
Only specific appendix rows were read. BR-02 and BR-04 currently describe sign-in and
passwords, not imaging; a clarification was requested. The explicit requested workflow
agrees with BR-47/49; no authentication rule will be repurposed for imaging.
MSG-39/40/41/42/45/47 retained.

## D. Existing Diagnostic audit

Existing reviews/findings/reports/completions, status, history, evidence, optimistic
versioning, idempotency, audit and outbox are reused. No new HTTP endpoint is planned.
Findings currently obtains its Visit evidence from an absent full VisitDiagnosticPort.
Existing current-care read adapter backs UC38 and status. Diagnostic outbox delivers
RESULT_READY and FINDINGS_RELEASE through the full port. Release requires matching
event/request/payload hash acknowledgements from BOTH Visit and Imaging before publication.

## E. Existing Long Imaging branch audit

Long reference branch: origin/feat/long/diagnostic-request-result at 35ec488; 55/5
behind/ahead origin/product. Read-only tree/source inspection confirms its ImagingStudyContractAdapter,
viewer grants, DICOMweb gateway and Orthanc adapter. It has evolved StudyEvidence and
ViewAuthorization contracts; no code is copied or branch merged here.

## F. Ownership boundary

Visit owns canonical patient/care facts and receipt persistence. Diagnostic owns request,
attempt, findings, report, audit and outbox. Clinical owns examination/advice/prescription.
New communication uses public services and value contracts, never foreign persistence.

## G. VisitDiagnosticPort gap

Baseline had only a VisitDiagnosticReadPort bean. Findings/history/outbox required the
missing full VisitDiagnosticPort. The old wiring absence assertion was deliberate and is
now replaced with one actual VisitDiagnosticReadAdapter implementing BOTH interfaces.

## H. Production adapter design / Shadow Plan

One VisitDiagnosticReadAdapter will implement the full VisitDiagnosticPort, retaining
one current-care rule and one read bean. It delegates commands/history facts to a
Visit-owned service. A narrow Diagnostic-owned public query validates canonical outbox,
request/Visit/patient/current-attempt/findings/report facts; Visit never reads Diagnostic
repositories. Adapter maps owner failures to Diagnostic outcomes, service owns transactions.

Shadow Plan: complete audit; persist this design/need for schema; add smallest owner
services/receipt/adapter; integrate actual production wiring; add authorization, event,
replay, findings and architecture tests; compile/focused/regression; record limitations.
Scope/checkpoint: explicit user request authorizes this backend integration and necessary
durable receipt after evidence, without granting delivery or unrelated workflow changes.
No Human reviewer identity or approval is inferred; no SDD/ADD skill or workers invoked.

## I. Current Doctor authorization

Current-care reads remain based on active account -> Doctor profile -> current Visit
assignment, OPEN and clinicalWorkAllowed. UC38 and status reuse exactly this rule and the
same single bean; no new imaging-request POST. Requesting Doctor must also be the current
responsible Doctor for UC42, including idempotent replay. Changed current care or inactive
account cannot use an old idempotency key. Visit/patient mismatch fails closed.

## J. Study authorization

UC42 uses the request's current attempt and exact stored studyRef. PENDING, IN_PROGRESS,
CANCELLED, wrong studyRef, missing/stale attempt and previous-attempt study are rejected.
Existing confirmed-study evidence processing requires the Imaging owner's CONFIRMED_MATCH
and ingestComplete acknowledgement for exact event/request/Visit/patient/attempt; PENDING,
MISMATCH and REJECTED confirmations are denied. No viewer URL is constructed here.
Actual quarantine/grant/gateway runtime checks remain the Imaging owner's dependency.

## K. Result-ready coordination

DiagnosticResultEventQueryService verifies a canonical IMAGING_RESULT_READY outbox event,
request/Visit/patient linkage, available/reviewed state and current confirmed study.
Visit validates its own patient linkage and stores a receipt through an atomic
INSERT ON CONFLICT plus exact existing-row comparison. Duplicate delivery is safe;
wrong event/type/request/Visit/patient is rejected. This is durable Visit result-availability
evidence, not a queue-return acknowledgement. It never closes Visit, completes Examination,
issues Prescription or creates Invoice.

## L. Findings-release coordination

The public owner query verifies all eight event fields against the exact outbox payload,
current study, reviewed request, findings/report version and linkage, validated PDF/hash.
Visit acknowledges only that event/request/canonical SHA-256 fingerprint. Reuse with a
conflicting receipt fails. Receipts commit in REQUIRES_NEW, so Imaging-owner retry can reuse
a previous Visit acknowledgement without duplicate transitions; an owner rejection does
not poison the dispatcher's retry transaction. This requires committed producer events,
as provided by normal outbox dispatch. No Diagnostic business record is written by Visit.
Patient release still waits for BOTH owners; receipt alone never releases report/findings.
The adapter has no no-op success path.

## M. History authorization

Normal Diagnostic Doctor history requires an active Doctor and an IN_PROGRESS/COMPLETED
Visit-owned care assignment for that Doctor, including closed Visits. Active Patient
history gets canonical Visit-to-patient linkage only; Diagnostic must still independently
require PatientDiagnosticHistoryPort own/dependent authorization. No arbitrary HTTP patient
lookup is added. UC33 retains its dedicated current-care/different-historical-doctor path.

## N. UC42 existing-flow preservation

Existing review -> draft -> validated report -> completion -> outbox flow, expected-version
checks, Idempotency-Key, audits and mappings remain. Findings changes are limited to:
checking current-care evidence even before replay, preserving specific owner authorization
failures, requiring OPEN/eligible evidence, and persisting the actual verified PDF SHA-256.
The former upload code called digest.digest() twice, persisting the empty digest on the
second call; it now stores the hash already verified against consumed upload bytes.
No mutation endpoint or workflow was duplicated or rewritten.

## O. Secure viewer dependency

Secure DICOM viewer runtime integration = BLOCKED BY IMAGING OWNER MERGE.
The current branch has no Imaging runtime. No grant, Orthanc adapter or raw public DICOM
URL is added. The frontend dev viewer URL remains an out-of-scope follow-up.

## P. Queue-return behavior / blocker
Queue return = BLOCKED: current production has reception queue and care/room reservation
commands, but no supported imaging-result return-to-requesting-doctor command. A durable
Visit receipt acknowledges result availability only, never claims a queue transition.
Deferred no-show cancellation is outside this checkpoint; the existing port has no caller
in current production, and unsupported execution must fail closed rather than pretend success.

## Q. Migration decision — recorded BEFORE adding migration

New persistence is necessary for restart-safe Visit acknowledgement and conflicting-event
replay detection. Diagnostic outbox is producer-owned and cannot serve as a Visit receipt
without violating ownership. Visit has no result-availability/receipt table. Existing
visit_audits allows only WALK_IN_CREATED and requires an actor; care/room audit tables
have unrelated constrained payloads and cannot safely be repurposed. An in-memory set
cannot satisfy replay across restart/multiple instances. Therefore add only V101, creating
Visit-owned imaging result receipts keyed by event ID, linked to Visit/patient, recording
event kind, request and payload hash. No history duplication, study/report payload copy,
queue model or changes to V099/V100 or Long's migrations. PostgreSQL execution must be
reported separately from compilation and repository-double tests.

## R. Tests and exact commands

All Maven commands ran from backend with installed Java 21 (Enforcer passed):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test-compile
.\mvnw.cmd "-Dtest=DoctorCoreProductionWiringTest,DiagnosticControllersT016Test,DoctorImagingRegistrationTest,DiagnosticPatientHistoryQueryServiceImplTest,ClinicalPastMedicalRecordsServiceImplTest,DoctorImagingReviewIntegrationTest,VisitDiagnosticArchitectureTest" test
.\mvnw.cmd "-Dtest=Clinical*Test,Doctor*Test,PastMedicalRecordsQuerySemanticTest,AfterVisitAdviceServiceImplTest,VisitClinicalCareContextServiceImplTest,VisitClinicalContextProductionWiringTest,VisitClinicalContextAdapterTest,VisitDoctorActiveCareReadServiceImplTest,VisitPatientHistoryQueryServiceImplTest,VisitDiagnosticArchitectureTest,DiagnosticControllersT016Test,DiagnosticTransitionPolicyTest,DiagnosticStatusT015Test,DiagnosticHistoryT015Test,DiagnosticPatientHistoryQueryServiceImplTest,ServiceCatalogClinicalAdapterTest,!*Postgres*" test
git diff --check
```

- Compile: BUILD SUCCESS, 885 production sources.
- Final test-compile: BUILD SUCCESS, 216 test sources, including guarded DB tests.
- Final focused: 73 tests, 0 failures, 0 errors, 0 skipped; includes the exact five requested
  classes plus real-owner integration and architecture tests.
- Regression: 445 tests, 0 failures, 0 errors, 0 skipped. Covers Examination, Advice,
  Prescription, Medical Documents, UC38, UC33, Diagnostic status/history/transitions,
  current care, MVC mapping and production wiring. This run preceded two additional
  integration cases; both passed in final focused verification (30 integration cases).
- Production wiring asserts one full VisitDiagnosticPort, identical to the read bean,
  production services backed by persistence doubles, and exactly one UC38 POST mapping.
- Real-owner integration doubles only persistence and absent external Imaging/storage
  contracts. Tests cover current/incorrect/inactive/former Doctor, Patient mismatch,
  closed Visit, states/studies/attempt/version, reviewed draft, validated report, exact
  release/replay/conflicts, missing Visit, confirmed-study outbox delivery, missing Imaging,
  bad Imaging acknowledgement -> retry with one Visit receipt -> successful publication,
  PDF hash integrity, historical Doctor evidence and Patient mapping separation.
- First integration run exposed fixture mistakes (missing DB-created timestamp and
  non-reviewable fixtures retaining a confirmed attempt). Corrected fixture facts to
  match the existing domain invariants; no production rule or assertion was weakened.
- Existing Diagnostic PostgreSQL fixture's Visit port is now @Primary to disambiguate
  its explicit fixture from the newly available production bean. This only affects those
  isolated Diagnostic persistence tests, not real-owner composition tests.
- Surefire evidence: `backend/target/surefire-reports/` (text/XML).

## S. PostgreSQL

POSTGRESQL = BLOCKED. TCP 127.0.0.1:5432 unavailable inside AND outside sandbox.
No DB-backed test was executed or claimed passed. Migration V101 is not applied/SQL-tested.
VisitImagingResultReceiptPostgresIntegrationTest compiles and targets only the guarded
local test database with rollback fixtures. It tests duplicate/conflicting insert retention,
immutability trigger and Visit FK rejection. Existing Diagnostic findings/evidence/status
DB suites and UC33 PostgreSQL suite also remain unexecuted. No secrets read, DB created,
or local service started.

## T. Architecture

VisitDiagnosticArchitectureTest checks whole Visit/Diagnostic source trees for foreign
repository/JPA-entity imports and Clinical for Imaging persistence imports. Existing
Diagnostic outcome enums are values, not imported JPA entities. It also checks receipt-only
coordination and absence of automatic clinical mutation. No ArchUnit/dependency added.
The only Visit write is its new receipt table. Diagnostic canonical-event reads use its
own persistence through a public service. UI and Imaging runtime are untouched.

## U. Files changed

16 backend files: one Action Record; Diagnostic public event-evidence service/interface;
small findings integration fixes; existing Visit adapter/care query; Visit coordination
service/interface, receipt entity/repository and V101; production wiring, real-owner
integration, architecture and guarded DB tests; existing DB fixture bean selection.
No files are staged. No existing work was deleted.

## V. Exact safe files to stage

Repository-root-relative manifest; stage only this explicit set after review:

```text
backend/docs/doctor-imaging-review-result-coordination-action-record.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DiagnosticResultEventQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticResultEventQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticFindingsServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/integration/diagnostic/VisitDiagnosticReadAdapter.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/model/entity/VisitImagingResultReceiptJpaEntity.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitCareAssignmentRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/VisitImagingResultReceiptRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitImagingCoordinationService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitImagingCoordinationServiceImpl.java
backend/src/main/resources/db/migration/V101__create_visit_imaging_result_receipts.sql
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorImagingReviewIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DiagnosticRequestT012PostgresIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitDiagnosticArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitImagingResultReceiptPostgresIntegrationTest.java
```

## W. Ready to stage?

YES for this bounded checkpoint; all changes remain unstaged for Human review. This is
not delivery approval or a claim of database/secure-viewer/queue end-to-end readiness.
No automatic commit, push, merge, rebase, reset, deletion or frontend edit occurred.
User retains final staging/review/delivery. Exact final Git/manifest checks are recorded
by the accompanying session; HEAD and V099/V100 remain unchanged.

## X. Remaining Doctor-flow gaps

- Secure DICOM viewer runtime integration = BLOCKED BY IMAGING OWNER MERGE. Long's evolved
  study-evidence/view-authorization contracts need owner-led reconciliation; not copied here.
- Return-to-requesting-Doctor queue transition = BLOCKED by missing supported command/domain
  transition; the result receipt never claims that transition succeeded.
- Deferred no-show coordination remains explicitly unsupported and fails closed.
- PostgreSQL receipt/migration/concurrency and database-backed owner integration verification.
- Frontend wiring to secure DOCTOR_REVIEW grant/gateway after owner integration.
- Internal Laboratory / Blood Test exists in the agreed Doctor workflow, but current
  requirements still lack a dedicated production UC/BR. Not implemented in this checkpoint.
- Stale BR-02/BR-04 imaging references need documentation reconciliation; implementation
  uses the explicit requested behavior and matching canonical BR-47/BR-49.

## Local Doctor stack integration — 2026-11-05

User authorizes local tested checkpoint commits, with no push/PR/rebase/reset/cleanup
or unrelated/frontend edits. Shadow Plan: commit inspected backend-only Medical Documents
merge -> merge that local parent into UC33 and test -> merge verified UC33 into Imaging
Review and test -> verify ancestry/migrations/full portable regression -> stop at existing
queue/Laboratory/secure-viewer blockers. No new production feature is authorized.

Medical Documents: local merge `1fd780a` integrates product `ef096b3` into `3da0a15`.
Compile/test-compile and 49 Medical Documents/wiring + 39 Prescription tests passed in
the preceding repair checkpoint; source was unchanged before this commit. Reviewed and
staged only the valid Medical Documents repair record in addition to backend merge files.
All pre-existing frontend paths and docs/doctor-frontend.md were individually unstaged,
without changing their content. Root-level untracked Java file remained untouched.
The merge's first-parent diff contains backend paths only.

UC33: local merge `5712c41`, parent Medical Documents `1fd780a`. Six conflicts were
resolved individually, preserving the seven-controller advice, batched owner history
reads and deterministic Prescription race harness. Source and tests remain identical
to the prior UC33 commit; no frontend delta. Compile/test-compile passed under Java 21;
159 focused/compatibility tests passed with 0 failures/errors/skips. UC33 worktree is clean.

Imaging Review: merge the LOCAL UC33 branch using --no-commit --no-ff. Merge is clean,
with no source/test/migration/frontend changes relative to `e8cd026`; only inherited
verification documents and ancestry are new. Thus production VisitDiagnosticPort, V101
receipts, UC38/UC42, owner authorization, idempotency and different-historical-Doctor
semantics are preserved without choosing a merge side or weakening tests.

Imaging compile/test-compile: BUILD SUCCESS, Java 21 Enforcer passed; 885 production
and 216 test sources compiled. Focused integration/compatibility: 223 tests, 0 failures,
0 errors, 0 skipped. Broad portable regression: 474 tests across 49 classes,
0 failures, 0 errors, 0 skipped; BUILD SUCCESS. No production source or test assertions
changed during integration. Local pre-commit validation: READY.

Exact focused command, from the isolated Imaging worktree backend with
JAVA_HOME=C:/Program Files/Java/jdk-21:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test-compile
.\mvnw.cmd "-Dtest=DoctorCoreProductionWiringTest,DiagnosticControllersT016Test,DoctorImagingRegistrationTest,DiagnosticPatientHistoryQueryServiceImplTest,ClinicalPastMedicalRecordsServiceImplTest,DoctorImagingReviewIntegrationTest,VisitDiagnosticArchitectureTest,ClinicalImagingRequestServiceImplTest,ClinicalImagingRequestControllerTest,ClinicalImagingRequestConcurrencyTest,ClinicalMedicalDocumentServiceImplTest,ClinicalMedicalDocumentControllerTest,ClinicalMedicalDocumentArchitectureTest,ClinicalPrescriptionServiceImplTest,ClinicalPrescriptionControllerTest,ClinicalPrescriptionArchitectureTest" test
```

Broad regression command (49 classes, includes all portable tests in the three owners):

```powershell
$stackTests = @(rg --files src/test/java/fu/fa26/sep490/g58/otcms_be/clinical src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic src/test/java/fu/fa26/sep490/g58/otcms_be/visit | Where-Object { $_.EndsWith('Test.java') -and $_ -notmatch 'Postgres' } | ForEach-Object { [System.IO.Path]::GetFileNameWithoutExtension($_) })
$stackTests += @('DoctorCurrentCareAuthorizationTest','PatientAuthorizationT008Test','PatientProfileAccessServiceTest')
$stackTests = @($stackTests | Sort-Object -Unique)
.\mvnw.cmd ('-Dtest=' + ($stackTests -join ',')) test
```

Migration blobs compared with original owners and unchanged:
V099 b8cb1ec80c135a3d5222890afc4e81550bf1500e (product);
V100 98168658b8101186764e5f8bddc77223516874a8 (Medical Documents);
V101 a42864135f60f732f3d41ab67a7694f6729e1bf9 (Imaging Review).
No new migration.

POSTGRESQL = BLOCKED: outside-sandbox TCP connection to 127.0.0.1:5432 failed.
All DB tests compile but none were executed/claimed PASS. No SQL replay/concurrency,
database migration application, or secure viewer runtime verification is implied.

Main workspace preserves the user's unrelated local modifications and untracked files.
Separate Git worktrees perform integration without stash or checkout of those paths.
SHA-256/absence comparison verified all 69 enumerated unrelated paths unchanged after
unstaging and branch integration. Both integration worktrees are retained for inspection;
no worktree or user file is deleted. Final report distinguishes these clean backend
worktrees from the intentionally dirty original workspace.

No Doctor queue, Laboratory, Orthanc, grant/gateway or frontend feature is implemented.
Local delivery checkpoint requires backend-only staged paths, no unresolved conflicts,
no source conflict markers and clean whitespace checks. No Human business/architecture
approval is inferred. Final local commit uses the user's requested merge message.
