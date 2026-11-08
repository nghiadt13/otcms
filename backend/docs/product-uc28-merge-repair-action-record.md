# Product / UC28 merge repair - Action Record

## Scope and Shadow Plan

User explicitly authorizes semantic repair in this workspace; compile, test-compile, focused and broad non-database validation. Preserve product viewer, Hung coordination and UC28 Phase 3. No push, reset, stash drop, blanket side selection, Flyway repair or historical migration changes. No Human review identity inferred; referenced review protocol absent from this workspace.

Inspection found three conflict delimiters in one request repository block. Phase 3 tracked edits and five untracked files were absent from the working tree but present in stash@{0}. Restore only reviewed Phase 3 paths, retain both repository query branches, inspect production coordination/viewer, then validate in requested order. Phase 3 Action Record documents completion predicate and concurrency limits.

Boundary: eleven Phase 3 files, request repository conflict, this record and necessary merged-contract fixtures. Exact commands: .\mvnw.cmd -DskipTests compile; .\mvnw.cmd test-compile; .\mvnw.cmd -Dtest=<focused selector> test; .\mvnw.cmd -Dtest=<non-database selector> test; git diff --check; git status -sb.

## Results

PENDING validation. First restoration patch check failed because PowerShell pipeline changed patch bytes; no tracked patch was applied. Repeated using binary subprocess transport. Stash retained.

## Final repair and validation

One conflict block (three delimiter lines) repaired in DiagnosticRequestJpaRepository. The product current-study/current-attempt query is unchanged against index stage 3; Hung released-history query is unchanged against stage 2. Both viewer lookups and both Phase 3 batch queries appear once. No unresolved source markers remain. Decorative separators/log output and a documented search command are not source conflicts.

The UC28 stash had not actually been restored. Restored its five untracked files and six tracked patches, preserving product additions. Stash remains 6be5cf0fecac27cc60552cf001bf209a0151a9f6. No stash drop, reset, blanket side selection, commit or push.

Receptionist-only closure, completed examination, outstanding Clinical work rejection, canonical Visit/Patient/required-order Diagnostic proof and incomplete-imaging rejection retained. Queue exit, reservation release, audit and review notification retained. Existing VisitDiagnosticPort/ReadAdapter already retain result-ready publication, findings acknowledgement, imaging history and current-care authority. Long's secure viewer/ImagingStudyContractAdapter implementation retained.

Added direct Diagnostic viewer tests and extended Hibernate query semantic validation to cover current-study lookup. First focused run had 180 tests, zero assertion failures and two fixture errors. Corrected mocks to product seven-argument confirmation with verification evidence; verified that exact call. Corrected cancellation fixture to cancel an ORDERED order. No production authorization rule or assertion weakened.

Default JDK was rejected; all final checks use installed JDK 21 through process-local JAVA_HOME. New viewer test accessor corrected from authorized() to allowed() before execution. Compile PASS: 987 sources. Final test-compile PASS: 256 sources.

Focused exact command: .\mvnw.cmd '-Dtest=Diagnostic*Test,VisitClosure*Test,VisitCompletionProductionWiringTest,VisitDiagnosticArchitectureTest,ImagingStudyContractAdapter*Test,ImagingViewerGrant*Test,ImagingDicomWeb*Test,DoctorImagingReviewIntegrationTest,PastMedicalRecordsQuerySemanticTest,!*Postgres*' test.
PASS: 180 tests, zero failures/errors/skips. Log: target/merge-repair-focused-final.log.

Broad exact command: .\mvnw.cmd '-Dtest=*Test,*Tests,!*Postgres*,!*Database*,!OtcmsBeApplicationTests,!T005RealLoginIntegrationTest,!T006A8EvidenceTest,!ContentAuditMigrationOrderTest' test.
PASS: 1295 tests, zero failures/errors/skips. Log: target/merge-repair-broad.log.

Local PostgreSQL TCP 127.0.0.1:5432 unavailable. DiagnosticFindingsT014PostgresIntegrationTest compiles with completion assertions preserved but was not executed. Real SQL, trigger and concurrency validation remains unverified. No Flyway repair or historical migration edit. No frontend or dependency edit by this task; pre-existing staged product changes retained.

Only DiagnosticRequestJpaRepository is staged by this task to clear its unmerged entry. Existing staged changes and other restored/modified WIP retained. Staged/unstaged diff checks and final status are run after this record write. A first final-record guard misread PowerShell UTF-16 logs; it stopped before record/staging changes. Repeated with BOM-aware decoding.

Sync-back: merge repair and non-database validation complete. No UC44 projection, broader Phase 3 completion, database pass, Human approval or delivery approval inferred.

Files repaired/restored or added:
- DiagnosticRequestJpaRepository.java; DiagnosticFindingsVersionJpaRepository.java.
- DiagnosticVisitCompletionEvidenceService.java; DiagnosticVisitCompletionEvidenceServiceImpl.java; WalkInVisitLifecycleServiceImpl.java.
- DiagnosticFindingsT014PostgresIntegrationTest.java; VisitClosureServiceTest.java; VisitDiagnosticArchitectureTest.java; VisitCompletionProductionWiringTest.java; DiagnosticVisitCompletionEvidenceServiceTest.java.
- DoctorImagingReviewIntegrationTest.java; DiagnosticViewerAuthorizationServiceTest.java; PastMedicalRecordsQuerySemanticTest.java.
- backend-completion-phase3-action-record.md restored; this Action Record added.
