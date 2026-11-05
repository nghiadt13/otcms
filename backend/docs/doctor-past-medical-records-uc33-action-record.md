# UC33 Past Medical Records — Action Record

The original implementation notes below are preserved. The continuation record dated
2026-11-05 at the end supersedes their pending verification and staging statements.

## A. Git / B. Dependency base

Nhánh feat/hung/KAN-XX-doctor-past-medical-records, HEAD 3da0a150aa289e575f9ef12dbd3cc2a064bab236 (Medical Documents). Sau fetch origin/product = ef096b37f1fa207b4aa208148c01868a87691b42, chứa UC36/V099. Working tree ban đầu đã có product changes ở index: 76 A, 8 M, 1 D và 2 UU; bao gồm frontend do user chuẩn bị. Không có MERGE_HEAD. Không stage/commit/push/merge/rebase/reset/restore/stash/clean. Hai conflict backend được sửa nội dung để giữ cả Document và Prescription, không stage resolution; index của user được giữ nguyên. Snapshot index/cached paths lưu ở target/uc33-initial-*.txt. HEAD chưa được tự thay để giả vờ đã sync ancestry.

Shadow Plan: audit owner services và FE; hoàn chỉnh hai file conflict cần compile; public Visit history scope; batch Clinical exam/advice, Prescription, Documents và Diagnostic released reads; current-Visit Doctor-only orchestration/API; focused/security/wiring/architecture/deferred DB tests; regression và manifest riêng UC33. Không sửa frontend hoặc staged dependency khác ngoài thay đổi UC33 được nêu rõ. Không tự nhận Human approval.

## C. Requirements / D. UC12 khác UC33 / E. Workflow

UC33/BR-41 cho Doctor đang chăm sóc xem các Visit/diagnoses/advice/prescriptions/imaging trước đây. UC28/BR-37 xác lập CLOSED là final state dùng cho history. BR-38/39/40/42/43/48/49/50 và MSG-39/40/41/42 được đối chiếu. UC12 PatientMedicalHistoryService là conditions/allergies/procedures/injuries/orthopedicHistory của profile, không bị copy, migrate hoặc trộn vào UC33. UC33 hỗ trợ read trước/trong examination; không mutate records, Visit, room/queue, Billing hoặc Diagnostic workflow.

## F. Audit owner services

Frontend PastVisit chỉ là demo có laboratory placeholder; doctorLiveApi.pastRecords=false và listPastVisits trả []; chưa có HTTP/JSON UC33 production contract. Không sao chép route Patient-ID demo. Advice nằm trên clinical_examinations. Prescription có public history query (ISSUED), nhưng đọc lines theo từng prescription; sẽ thêm batch capability vào chính service đó. Medical Documents có metadata/payload tách bảng và UC32 content authorization; chỉ metadata released được tổng hợp. VisitReviewSourceService.closedVisits chưa page và không phân biệt cancelled-CLOSED. DiagnosticHistoryService hiện yêu cầu Doctor của historical Visit và cần VisitDiagnosticPort; không dùng endpoint đó làm UC33.

## G. Current-care / H. Eligible Visits

HTTP chỉ DOCTOR. JWT accountId -> existing ClinicalDoctorIdentityResolver -> profile; kiểm active Doctor qua public Auth service; VisitClinicalContextPort phải tồn tại, responsible Doctor khớp, clinicalWorkAllowed=true, Patient lấy từ context. Không nhận Patient/Doctor selector để authorize. Historical scope do Visit owner đọc: cùng Patient, CLOSED, không phải current Visit, arrivedAt không sau current Visit và có COMPLETED care assignment. Existing cancellation đóng Visit nhưng cấm IN_PROGRESS/COMPLETED care, nên predicate này loại cancellation thay vì coi mọi CLOSED là đã khám. Không có closedAt trong schema; trả arrivedAt thực, không bịa timestamp từ updatedAt. Sort arrivedAt DESC, id DESC; page theo Visits, size 1..50.

## I. Examination / J. Advice / K. Prescription / L. Documents

Chỉ COMPLETED exam; diagnoses batch từ bảng owner, code/type thực, không ICD labels giả. Advice đọc cùng exam aggregate, không tạo persistence khác. Reuse ClinicalPrescriptionHistoryQueryService với batch Patient + selected Visit IDs và batch lines; chỉ ISSUED. Document history query tin cậy trả metadata released, không payload; chưa release còn editable nên không coi là finalized history. Content vẫn qua UC32 endpoint và current-care authorization.

## M. Diagnostic / N. Different historical Doctor

Dedicated trusted Diagnostic query nhận Patient + selected historical Visit IDs + actor account từ authorized composition. Chỉ kết quả có trạng thái final, findings COMPLETED, đúng request/Visit/study và required PDF report VALIDATED/released; latest completed findings, không draft/storageRef/technician data. Không yêu cầu Doctor hiện tại là Doctor cũ, không nới public Diagnostic endpoint. Access audit ghi vào bảng Diagnostic có sẵn; read-only ở business records, transaction cho phép duy nhất access-audit insert theo precedent. Không có history aggregate mới hoặc migration.

## O. Pagination / P. Performance / Q. Endpoint

GET /api/v1/visits/{currentVisitId}/past-medical-records?page=0&size=20. Page theo Visit, max50; children không chia page. Batch owner queries và in-memory grouping, không N queries theo số Visit. No-store/shared correlation/error envelope Clinical. Empty history trả 200 items=[], total chính xác. Clinical không import foreign persistence.

## R. Migration / W. Laboratory gap

Migration NONE, không V101. Blood Test/Laboratory chưa có dedicated UC/BR và production ordering/specimen/processing/result contract. Không thêm Lab entity/API, không dùng Imaging thay Lab, không trả internal lab results giả. Báo cáo xét nghiệm ngoài đã upload/release có thể xuất hiện như EXTERNAL_REPORT metadata.

## S-Z. Verification / staging

Sẽ ghi exact test/command, DB blocker, kiến trúc, file manifest và trạng thái index sau validation. Không claim full DB integration hoặc completed dependency merge khi chưa có evidence.

## Continuation record — 2026-11-05

### Scope, checkpoint and Shadow Plan

Continue the existing working tree under the user's explicit instructions in this session:
inspect first; preserve all existing work; review the two combined conflict files; stage
only those two files; compile; run focused and relevant regression tests; finish this record.
The authorization covers backend integration fixes and test corrections. It does not
authorize commits, pushes, merges, rebases, resets, cleanup, new migrations, frontend changes,
or implementing Laboratory. No SDD/ADD skill or worker was invoked. This record documents
the user's requested boundary; it does not invent a Human reviewer identity or mark a
governance artifact APPROVED. The referenced `.claude/skills/_shared/ai-review-protocol.md`
is absent from both backend and repository root.

Shadow Plan: inspect Git/index and existing UC33 sources; verify semantic union in the
shared handler and production wiring; correct concrete integration defects; stage those
two paths; verify compile and tests; record PostgreSQL evidence and a bounded stage manifest.

### Git state and unfinished merge resolution

- Branch: `feat/hung/KAN-XX-doctor-past-medical-records`.
- HEAD: `3da0a150aa289e575f9ef12dbd3cc2a064bab236`; unchanged during continuation.
- Initial commands: `git status`, `git status -sb`,
  `git diff --name-only --diff-filter=U`, `git diff --check`, `git diff --cc`.
- Observed state differs from the expected conflict state: no unmerged index entries,
  no conflict markers, and `git rev-parse --verify -q MERGE_HEAD` exits 1. There is no
  active Git merge to conclude. Product changes are already staged, alongside unstaged
  and untracked UC33 work. No merge was started, aborted or committed.
- Reviewed the existing union manually. Handler retains all seven controllers, including
  Prescription, Medical Documents and UC33. Corrected `UNAUTHORIZED_qRECORD` to the valid
  `UNAUTHORIZED_RECORD` enum constant.
- Error behavior retained: MSG-40/MSG-14 -> 403; MSG-41 -> 404;
  MSG-42/IDEMPOTENCY_CONFLICT -> 409; DEPENDENCY_UNAVAILABLE -> 503;
  MSG-39/MSG-08/MSG-47 -> 400. Unsupported media and missing multipart part remain
  transport validation errors; MultipartException remains MSG-47.
- Wiring retains Examination, Advice, Imaging and real Clinical/Diagnostic/Visit adapters;
  Prescription command/history services and repositories; Medical Document service/access/
  validator, Auth/Patient implementations and their real persistence dependencies; all UC33
  owner query implementations. No owner service interface was replaced with a no-op/mock.
- Added unique Prescription issuance mapping/production-service assertions and an actual
  composed-owner test for another historical doctor's Prescription and Imaging result.
- Staged only `ClinicalApiExceptionHandler.java` and `DoctorCoreProductionWiringTest.java`
  using their full explicit paths. Preserved the other pre-existing staged files, including
  frontend changes. All other UC33 work stays unstaged/untracked for user review.

### Requirements, authorization and composition

UC33 is read-only aggregation for current care under BR-41. Its dedicated Markdown UC
file is not present; the repository's `docs/requirements/UC_description.html` identifies
UC33 as View Past Medical Records, and the user's task supplies the detailed semantics.
Only specific BR/MSG rows were read from `docs/appendix-rules.md`.

UC12 records longitudinal patient-profile medical history (conditions, allergies,
procedures, injuries and orthopedic history). UC33 composes prior finalized Visit records;
it neither copies UC12 data nor mutates that workflow.

`GET /api/v1/visits/{currentVisitId}/past-medical-records?page=0&size=20` requires DOCTOR.
The controller resolves the authenticated JWT account to its Doctor profile; the service
checks active Doctor account, current Visit existence, responsible Doctor, and
`clinicalWorkAllowed`. Patient scope comes exclusively from that current Visit. There is
no client Patient ID selector. Owner responses are checked for patient/Visit scope before
returning data. Missing Visit, wrong Doctor, inactive account, invalid pagination and
out-of-scope owner records have negative coverage.

Visit owns historical selection: same patient, CLOSED, current Visit excluded,
`arrivedAt <= current.arrivedAt`, and an existing COMPLETED care assignment. Other active
Visits and cancelled Visits without completed care are excluded. The completed assignment
predicate distinguishes actual completed care from cancellation's CLOSED state.

Composition uses owner services:

- Examination / Diagnosis: COMPLETED examinations only; diagnoses loaded in one batch,
  sorted by type/code, attached to their examination. No draft examination is returned.
- Advice: the same completed examination aggregate supplies home-care/activity/rehab/
  warning/follow-up advice. No duplicate Advice persistence.
- Prescription: reuse `ClinicalPrescriptionHistoryQueryService`; batch patient + selected
  Visit IDs, ISSUED headers only, then a batch item query ordered by line number.
- Medical Documents: released metadata only, scoped to patient + selected Visit IDs;
  no payload repository, bytes, storage reference or binary content in UC33. Content
  access remains a separately authorized UC32 operation.
- Imaging History: dedicated internal `DiagnosticPatientHistoryQueryService`; request
  status RESULT_AVAILABLE/REVIEWED, latest COMPLETED findings with completion timestamp,
  matching request/Visit/study and required VALIDATED, released report. Draft findings
  and unreleased reports cannot be selected by the owner query.
- Different historical Doctor: the current Doctor is authorized on CURRENT care only.
  Neither historical Prescription nor the dedicated Diagnostic query compares the actor
  with the historical treating Doctor. The existing public Diagnostic history endpoint
  is unchanged. Unit and production-composition tests cover this distinction; actual
  PostgreSQL execution of its fixture remains blocked as described below.

Pagination is by Visit, size 1..50, order `arrivedAt DESC, id DESC`, with total count and
empty-page behavior. Children remain attached to their parent Visit. Owner batch queries
and in-memory grouping avoid per-Visit and per-Prescription line queries on the UC33 path.
The number of owner queries is bounded independently of page size; child volume within
one Visit is not separately paginated. A repeatable-read transaction keeps owner reads
consistent and allows only the existing Diagnostic access-audit insert; clinical business
records remain read-only. No query timing or PostgreSQL execution plan is claimed.

Clinical imports public Visit/Diagnostic services rather than their repositories/entities;
Patient/Billing persistence is also absent from UC33 composition. Owner implementations
read only their own persistence. Existing structure tests and real Hibernate JPQL parsing
verify these boundaries and query syntax without claiming SQL execution.

Migration: NONE for UC33. V099 Prescription and V100 Medical Documents were not edited.
V099 is a pre-existing staged product addition. No V101 or history table was added.

Internal Laboratory / Blood Test exists in the agreed Doctor workflow,
but current requirements still lack a dedicated production UC/BR.
Do not implement Laboratory in UC33.

### Verification and integration correction

Commands run from `backend`. Maven Wrapper initially failed inside the sandbox; outside
it, the default JDK failed the project's Java 21 Enforcer rule. Subsequent Maven commands
use the installed JDK without changing repository config or bypassing Enforcer:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test-compile
.\mvnw.cmd "-Dtest=ClinicalPastMedicalRecordsServiceImplTest,ClinicalPastMedicalRecordsControllerTest,ClinicalPastMedicalRecordsArchitectureTest,ClinicalHistoryOwnerQueryTest,PastMedicalRecordsQuerySemanticTest,DiagnosticPatientHistoryQueryServiceImplTest,VisitPatientHistoryQueryServiceImplTest,DoctorCoreProductionWiringTest" test
.\mvnw.cmd "-Dtest=ClinicalExaminationServiceImplTest,AfterVisitAdviceServiceImplTest,ClinicalPrescriptionServiceImplTest,ClinicalPrescriptionControllerTest,ClinicalPrescriptionArchitectureTest,ClinicalMedicalDocumentServiceImplTest,ClinicalMedicalDocumentControllerTest,ClinicalMedicalDocumentArchitectureTest,DoctorClinicalControllerTest,ClinicalDoctorIdentityResolverTest,DoctorCurrentCareAuthorizationTest,DoctorActiveVisitReadServiceImplTest,DoctorActiveVisitControllerTest,VisitDoctorActiveCareReadServiceImplTest,VisitPatientHistoryQueryServiceImplTest,DiagnosticHistoryT015Test,DiagnosticPatientHistoryQueryServiceImplTest,DoctorCoreMvcMappingTest,DoctorCoreArchitectureTest,DoctorCoreProductionWiringTest" test
```

- Compile: BUILD SUCCESS, Java 21 Enforcer passed; 879 production sources compiled.
- Test compilation: BUILD SUCCESS; 213 test sources, including the PostgreSQL test.
- Focused UC33: 55 tests, 0 failures, 0 errors, 0 skipped (8 classes).
- Regression first run: 223 tests, 1 failure, 0 errors. Existing Prescription race test
  observed the in-memory double's uncommitted delete/replacement gap; its production
  service uses transactional SQL, while the unit double has no transaction visibility.
- Corrected that test to synchronize both commands at their conditional version writes.
  Kept the one-winner, MSG-42, version, item count and immutable-content assertions.
  No Prescription production mutation code was changed. A first corrective run exposed
  Mockito `when` restubbing invoking the old answer with null matcher arguments; corrected
  the overrides to `doAnswer` before rerunning. Neither failure was hidden or skipped.
- Final regression: BUILD SUCCESS; 223 tests, 0 failures, 0 errors, 0 skipped (20 classes).
  Surefire text/XML reports are in `backend/target/surefire-reports/`; the latest
  Prescription service report contains 19 tests, 0 failures, 0 errors, 0 skipped.
- Final conflict/whitespace checks: `git diff --name-only --diff-filter=U`,
  `git diff --check`, and `git diff --cached --check` have no output.
  `rg -n '<<<<<<<|=======|>>>>>>>' src/main src/test` has no matches (exit 1).
  These are the requested backend marker checks run from `backend`.

POSTGRESQL = BLOCKED. TCP connect to `127.0.0.1:5432` failed both inside and outside the
sandbox. `PastMedicalRecordsPostgresIntegrationTest` was compiled, not executed and not
claimed PASS. No datasource secrets were read, no database/schema was created, and no
service was started. The existing integration test targets the guarded local test database.
Its closed/active/cancelled pagination and different-doctor released Imaging fixtures
remain available for a later database-backed run.

### Files changed and exact safe files to stage

Continuation edits are limited to the shared handler, production wiring test, Prescription
race unit test and this Action Record. The existing UC33 implementation is preserved.
The manifest below includes those edits plus the existing UC33 source/query/test work;
it excludes unrelated product/frontend changes already staged by the user.
Paths are relative to the repository root, not `backend`.

```text
backend/docs/doctor-past-medical-records-uc33-action-record.md
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/controller/ClinicalPastMedicalRecordsController.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/exception/ClinicalApiExceptionHandler.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/model/dto/PastMedicalRecordsDtos.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalDiagnosisRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalExaminationRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalMedicalDocumentRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalPrescriptionItemRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/repository/ClinicalPrescriptionRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalExaminationHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalMedicalDocumentHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalPastMedicalRecordsService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/ClinicalPrescriptionHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalExaminationHistoryQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalMedicalDocumentHistoryQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalPastMedicalRecordsServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalPrescriptionHistoryQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/repository/DiagnosticRequestJpaRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/DiagnosticPatientHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/diagnostic/service/impl/DiagnosticPatientHistoryQueryServiceImpl.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/repository/ClinicVisitJpaRepository.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/VisitPatientHistoryQueryService.java
backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/service/impl/VisitPatientHistoryQueryServiceImpl.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalHistoryOwnerQueryTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPastMedicalRecordsArchitectureTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPastMedicalRecordsControllerTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPastMedicalRecordsServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/ClinicalPrescriptionServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/DoctorCoreProductionWiringTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/PastMedicalRecordsPostgresIntegrationTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/clinical/PastMedicalRecordsQuerySemanticTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/diagnostic/DiagnosticPatientHistoryQueryServiceImplTest.java
backend/src/test/java/fu/fa26/sep490/g58/otcms_be/visit/VisitPatientHistoryQueryServiceImplTest.java
```

### Final disposition / sync-back

Ready to stage: YES, for the exact 33-file manifest above. Only the two named conflict
files were staged during continuation; the other 31 manifest files need user staging.
The current index alone does not contain the entire UC33 feature. Review the existing
staged product/frontend changes separately before any eventual user commit.
PostgreSQL verification remains BLOCKED;
staging readiness does not imply database validation, Human approval or delivery readiness.
No commit, push, merge, rebase, reset, restore, cleanup, deletion, frontend edit or migration
was performed. User owns review of the remaining manifest and existing product index.

Remaining Doctor backend gaps: database-backed UC33 verification requires local PostgreSQL;
the dedicated UC33 Markdown requirement is still absent; Laboratory needs its own agreed
production UC/BR and workflow contract. Existing broader Diagnostic result coordination
through `VisitDiagnosticPort` remains unimplemented in the baseline (the production wiring
test explicitly verifies it is not hidden by a no-op); UC33's dedicated internal released
history query does not depend on that missing port. No broader workflow completion is claimed.

## Local parent integration — 2026-11-05

User now authorizes local commits after tested checkpoints; no pushes/PRs/rebases or
unrelated edits. Parent is locally verified Medical Documents merge `1fd780a`, which
contains origin/product `ef096b3` and only backend changes relative to its first parent.
Original Medical Documents workspace retains the user's frontend and root untracked
Java file; integration uses a separate worktree to avoid touching those bytes.

Shadow Plan: merge local parent with --no-commit -> inspect every conflict -> preserve
UC33 owner queries/auth/composition and Prescription/Documents -> compile/test-compile
under Java 21 -> compatibility tests -> local pre-commit validation -> requested merge commit.

Six conflicts were examined individually: shared handler; Prescription item/header
repositories; Prescription history interface/implementation; Prescription service test.
Retained the seven-controller handler, batch history APIs and ordered item queries, and
the existing deterministic version-write concurrency harness with all original assertions.
Incoming parent contained the older subset of these declarations. No business logic or
test assertion was removed. Wiring merged without conflict and retains all production
owner implementations. Resolved source/test content is identical to pre-merge UC33 HEAD.
The inherited Medical Documents Action Record supplies parent merge/test evidence.
No frontend file differs from pre-merge UC33.

Commands from the worktree backend with JAVA_HOME=C:/Program Files/Java/jdk-21:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test-compile
.\mvnw.cmd "-Dtest=ClinicalPastMedicalRecordsServiceImplTest,ClinicalPastMedicalRecordsControllerTest,ClinicalPastMedicalRecordsArchitectureTest,ClinicalHistoryOwnerQueryTest,PastMedicalRecordsQuerySemanticTest,DiagnosticPatientHistoryQueryServiceImplTest,VisitPatientHistoryQueryServiceImplTest,DoctorCoreProductionWiringTest,ClinicalMedicalDocumentServiceImplTest,ClinicalMedicalDocumentControllerTest,ClinicalMedicalDocumentArchitectureTest,ClinicalPrescriptionServiceImplTest,ClinicalPrescriptionControllerTest,ClinicalPrescriptionArchitectureTest,DoctorCurrentCareAuthorizationTest,DoctorCoreMvcMappingTest" test
```

Compile and test-compile: BUILD SUCCESS, Java 21 Enforcer passed; 879 production and
213 test sources compiled. Compatibility suite: 159 tests, 0 failures, 0 errors,
0 skipped; BUILD SUCCESS. Local pre-commit validation: READY.
No new migration or DB verification claim. Conflict/whitespace/marker checks are clean.
