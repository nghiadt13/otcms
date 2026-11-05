# Clinical — Current requirement reconciliation and integration Action Record

## Scope, authorization and Shadow Plan

- Authorization: explicit user instruction for this review checkpoint, bounded Clinical fixes, real catalog adapter and exact JDK/Maven verification. No Human Final Review or SDD approval is impersonated. Referenced `.claude/skills/_shared/ai-review-protocol.md` is absent in this checkout.
- Shadow Plan: preserve Git baseline and UC35; read current requirements and complete Clinical sources/tests; inspect Visit-owned public contracts and persistence read-only; reconcile capability/ownership; implement catalog adapter and bounded null/error fixes; compile/testCompile/unit regression/context/PostgreSQL verification; record blockers and exact stage scope.
- File boundary: Clinical production/test package and this record plus UC35 record. V073/V074 unchanged. No Visit, Appointment, Imaging, Billing or authentication implementation changes. No reset/restore/clean/stash/checkout/rebase/merge/stage/commit/push.
- Baseline HEAD: `a6443618b96cd3ee5d8a46488146dfca585baf8d`; `origin/product`: `b79ac838f58a541fdd14504ad24788a674f666c0`. Index empty; one modified entity plus five untracked UC35 files; no unrelated changes. Remote-tracking refs were inspected locally; no fetch was performed, so live remote freshness is not asserted.

## Current requirement map

Primary evidence: root `docs/requirements/UC_description.html` UC28–UC45 and exact rows in root `docs/appendix-rules.md`. Individual UC28/29/35/38 specs are absent. Architecture: root `AGENTS.md`, backend `AGENTS.md`, root `docs/architecture.md`; testing: backend `docs/local-testing.md`; Flyway conventions: root `AGENTS.md` and existing migration inventory.

| Capability | Current UC / BR | Existing implementation | Owner / classification |
| --- | --- | --- | --- |
| Close visit | UC28 / BR-37 | WalkInVisitLifecycleService.close | Visit/Reception; current Doctor-only implementation conflicts with current actor |
| Examination | UC29 / BR-38 | ClinicalExaminationService | Clinical; supported behavior with legacy eligibility blocker |
| Patient results | UC34 / BR-39 | Clinical has no patient release/read API | Clinical result projection/release integration remains incomplete |
| Advice | UC35 / BR-42 | AfterVisitAdviceService | Clinical; supported |
| e-Prescription | UC36 / BR-43 | Not implemented in Clinical | Unassigned here; out of scope |
| Imaging request | UC38 / BR-45 | ClinicalOrderingService IMAGING orders | Clinical request core; Imaging-owned transport/study coordination to integrate |
| Imaging queue/read | UC39 / BR-46 | getImagingOrder projection only | Imaging developer; request listing not delivered by Clinical |
| Study submission | UC40 / BR-47 | No Clinical study implementation | Imaging + Visit coordination |
| Findings | UC42 / BR-49 | Not Clinical RESULTS_AVAILABLE | Imaging developer; Doctor actor |
| Imaging status | UC44 / BR-50 | Order status projection, no findings/release status | Imaging integration |
| Invoice | UC45 / BR-51 onward | Existing billing feature | Billing; out of scope |

Numbering drift: current BR-02 concerns sign-in eligibility, BR-04 password policy. They are not Visit/Queue or patient-result rules. Relevant current Visit rules are BR-31–37; patient access BR-39 and BR-48/49. Current UC45 is Create Invoice, not paraclinical ordering. Older frontend/admin docs still contain incompatible numbers and lifecycle claims; they do not override current UC/appendix scope.

## Audit findings

### Visit state and ownership

- V007 permits only OPEN/CLOSED; V020 renames clinic_visits to visits and visit_care_assignments to visit_assignments. Care statuses: RESERVED, IN_PROGRESS, COMPLETED, RELEASED. Doctor profile lives on visit_assignments.doctor_profile_id, not visits.
- IN_EXAMINATION/AWAITING_CONCLUSION exist as Clinical enum values and frontend contract vocabulary. Visit reception reads current stage as coalesce(active care step_code, visits.status). No Visit implementation stores either Clinical value or implements markAwaitingConclusion.
- No production VisitClinicalContextPort implementation exists. Existing public services cannot supply the full clinical eligibility context: VisitWorkflowService supplies follow-up evidence; reservation query is doctor/time scoped without visit id; assignment service provides commands, not visit-context reads; reception read is receptionist/patient scoped. None establishes a safe OPEN-to-IN_EXAMINATION translation.
- Existing follow-up evidence reads legacy visit_examinations.completed/follow_up_indicated. Clinical writes clinical_examinations instead. Completing Clinical currently does not update follow-up evidence. Clinical must not write the Visit-owned table directly.
- Walk-in cancel: Receptionist, only before care started, persists CLOSED with CANCELLED audit/queue reason. Walk-in close: Doctor, OPEN/WALK_IN and no IN_PROGRESS care, persists CLOSED, exits reception queue, releases RESERVED assignments and emits closure notification. It does not query examination completion, required orders or findings. Scheduled care completion ends assignment/room use, not the visit. UC28 is therefore present only as a conflicting/incomplete old capability; not merely cancellation.

### Clinical quality and safety

- Examination read previously dereferenced null context/responsible doctor. Fixed with a shared guard: missing visit/examination MSG-41; null/wrong/unassigned doctor MSG-40; null command/visit MSG-39. Save/read still require a trusted doctor profile supplied by authenticated application composition.
- Existing examination write validation (mandatory clinical fields, exactly one primary, M/S-only ICD regex) is retained. Current requirements mention diagnosis/injury but no checked-in source found justifying M/S-only. V073 also enforces it; removal needs explicit reconciliation and a new migration. Completed examination can be overwritten, and saveDraft can downgrade it; no editing/release rule currently decides this. No speculative lock was introduced.
- Advice preserves five nullable TEXT fields and examination identity/status/clinical fields. Empty/whitespace-only advice rejected. Added null command/visit/doctor guards and missing/unassigned visit-context tests. CLOSED eligibility remains unresolved until Visit contract exists; no fake status mapping added.
- Ordering now rejects null command/visit/doctor/order/service id and mismatched catalog snapshot identity before writes. Existing validation, price snapshots and local transaction are retained.
- Catalog adapter uses only GetClinicServiceCatalogService. Explicit IMAGING/LABORATORY mapping; EXAMINATION rejected MSG-39. Known SERVICE_NOT_FOUND translated MSG-41; unknown catalog errors propagate. OTHER is preserved in existing enum/schema for compatibility, never produced by adapter.
- LABORATORY exists in current service catalog and result-read docs, so it is a useful broader internal capability. Generic OrderingRound/OTHER support is not mandated by current UC38. No generic REST API was exposed.
- Frozen Imaging methods/OrderView unchanged. Latest-round readiness remains an internal aggregate; BR-47 returns a patient for each submitted study, so it must not become a queue-return gate or all-services-complete signal. RESULTS_AVAILABLE means study/result availability, not findings completed/released under BR-49.
- paymentStatus is retained historical per-order metadata with no production billing updater found. Current invoice/payment ownership is Billing (BR-51–57); it is not authoritative invoice balance and does not block Imaging result availability.
- Numbering race remains: max(round_number)+1 can collide; database UNIQUE(visit_id,round_number) prevents duplicates and transaction rolls back. No automatic retry/exception translation was added: callers must not blindly retry a possibly committed submission. A future named-constraint translation must happen outside the failed local transaction, with bounded retry and command idempotency defined. Actual rollback is not verified in this run because PostgreSQL is unavailable.
- Optimistic versions exist on examination/orders; timestamp mutation is explicit. JPA detects overlapping writes at flush. AdviceView/version is formed before commit, so returned version may precede its flush increment; no request-version precondition exists. Do not treat the DTO version as verified post-commit evidence.
- No Clinical REST controller exists. Comparison against doctorId is not proof of authenticated Orthopedic Doctor identity. Future transport/composition must resolve AuthenticatedAccount through active Doctor account/profile public services; cancel/result/query methods also require actor/study authorization at their trusted caller boundary. No authentication redesign here.
- Existing MSG-08/14/04 remain in untouched ordering/state/diagnosis paths and do not match current MSG-39/40/41/42 semantics. New guards use precise current codes; no mass renumbering or frozen Imaging error change.

## Minimal Visit-owned public contract needed (proposal, not implemented)

```java
package fu.fa26.sep490.g58.otcms_be.visit.service;

public interface VisitClinicalCareQueryService {
    ClinicalCareContext requireClinicalCareContext(
            UUID visitId, UUID authenticatedDoctorProfileId);

    record ClinicalCareContext(
            UUID visitId,
            UUID patientProfileId,
            UUID responsibleDoctorProfileId,
            UUID activeCareAssignmentId,
            boolean visitOpen,
            boolean clinicalWorkAllowed) { }
}
```

Visit owns lookup, nonexistent-visit handling, responsible-doctor selection from real assignments (including scheduled and walk-in), and determination of active care eligibility. OPEN alone must not grant clinical work. The query must not imply that a read snapshot atomically authorizes a later write; Visit owner must also define coordination/recheck semantics with close/reassignment. Once implemented, Clinical can use a real adapter and replace legacy status gating in a coordinated checkpoint without changing frozen Imaging methods.

Separate BR-47 Visit-owned command proposal:

```java
public interface ReturnImagingPatientToDoctorQueueService {
    void returnToRequestingDoctor(ImagingStudyReturnCommand command);

    record ImagingStudyReturnCommand(UUID requestId, UUID studyId,
            UUID visitId, UUID patientProfileId, UUID requestingDoctorProfileId,
            UUID actorAccountId, String correlationId) { }
}
```

Imaging must first validate patient/request/study matching and actor. Visit must validate eligible real visit/care relationship, update its actual queue/stage state and audit atomically, and make replay of the same study/request idempotent. It must not implement an AWAITING_CONCLUSION no-op. Reliable delivery/retry across committed study submission and queue command needs owner coordination, not a fabricated distributed transaction.

UC28 needs Clinical-owned evidence of completed examination and remaining effective required orders across the visit (not just the latest round), plus Imaging-owned completion/release evidence for required studies/findings. Visit/Reception owns aggregation, close authorization and queue/care termination. Billing consumes final visit/charge evidence but does not implement close here. No separate current DoctorConclusion UC/entity is required by the checked-in UC map: completion is represented by UC29, UC42, UC35 and UC28. No exact current requirement found for another conclusion table/field.

## Verification and results

JDK 21.0.9 verified with java -version; session-only JAVA_HOME and PATH. Maven executable:
`C:\Users\ad\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd`.
All commands use `-Dmaven.repo.local=C:\Users\ad\.m2\repository`.

- `-DskipTests compile`: PASS outside sandbox after cached-JAR access failure inside sandbox; no dependency change.
- `test-compile`: PASS.
- `-Dtest=AfterVisitAdviceServiceImplTest,ClinicalExaminationServiceImplTest,ClinicalOrderingServiceImplTest,ServiceCatalogClinicalAdapterTest,LayeredArchitectureTest test`: PASS 55 tests (12 advice, 13 examination, 19 ordering, 8 adapter, 3 existing architecture), zero failures/errors/skipped. Existing architecture suite covers content/activitylog/reporting, not a new Clinical architecture guarantee.
- `-Dtest=OtcmsBeApplicationTests,ClinicalOrderingTransactionPostgresIntegrationTest test`: attempted; application contexts fail at Flyway datasource initialization, SQLSTATE 08001, connection refused 127.0.0.1:5432; two test errors. Classified BLOCKED by unavailable test database, not a passing context or rollback test. Log: target/clinical-context-postgres-review.log. No database started/reset/installed; no migrations applied.
- Static inspection independently confirms absent production VisitClinicalContextPort bean. DB failure occurs before proving that bean failure at full application startup. Catalog bean registration exists but full application integration remains BLOCKED.
- Migration inventory in local origin/product ends V072; V073 is committed, V074 local. No conflict found in inspected ref; no claim about unseen remote commits. Entity/table/column types, enums, uniqueness, version/timestamps and indexes align structurally. Some JPA unique-constraint names differ from PostgreSQL-generated names; no schema change. No business FK to Visit/catalog was added, only local Clinical FKs. V073/V074 database application/validation still BLOCKED.
- Final source verification after adapter import cleanup: same 55-test command PASS, BUILD SUCCESS at 2026-11-04T16:41:28+07:00, including recompilation of all 722 production and 165 test source files with release 21. Final git diff --check PASS; index remains empty and changes remain confined to Clinical and its two action records.

## Sync-back and next checkpoint

COMPILES; UNIT TESTS PASS; APPLICATION INTEGRATION BLOCKED. No SDD artifact approval, commit or delivery performed.

Next Hùng checkpoint: finish Clinical ↔ Visit clinical-care query/eligibility integration with Visit owner, including convergence of legacy follow-up evidence, real Spring wiring and OPEN/CLOSED/care authorization integration tests. It must remove the legacy awaiting-transition assumption through agreed contracts, preserve frozen Imaging compatibility and leave UC28 implementation with Visit/Reception.

Imaging developer can keep the frozen projection/result methods, but must implement per-study BR-47 queue-return coordination separately and distinguish study availability from completed/released findings. No latest-round gate for individual study return; no payment gate added by Clinical.
