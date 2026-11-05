# Imaging Completion -> Return Patient to Requesting Doctor Queue

## Decision

Doctor queue return = BLOCKED by missing production queue model/contract

Outcome B from the requested checkpoint applies. No production code, queue schema,
endpoint, migration or test behavior is changed. Existing committed result coordination
remains intact. A result receipt is not evidence of a queue return.

## Git state, scope and Shadow Plan

- Observed clean tree at `e8cd026f96ed8f026bb912294f05663cd7fe2f7e`, with
  `feat: integrate doctor imaging review coordination` immediately after UC33 `c733b6a`.
- Initial active branch was `feat/hung/KAN-XX-doctor-imaging-review`; the requested
  `feat/hung/KAN-XX-imaging-return-doctor-queue` already pointed to the same commit.
  Switched to that branch without changing the commit or files.
- Ran `git status -sb`, `git branch --show-current`, `git log --oneline --decorate -5`,
  and Git tracked-file verification for V101. From backend, tracked paths are reported
  relative to backend; V101 is `src/main/resources/db/migration/V101__create_visit_imaging_result_receipts.sql`.
- User scope: inspect first; implement only if a suitable production queue exists;
  otherwise stop implementation, document evidence/proposed contract, and verify the baseline.
- Shadow Plan: inventory source/migrations -> inspect actor/lifecycle/history constraints
  -> inspect result trigger and receipt semantics -> choose Outcome A/B -> record a minimal
  proposal without self-approval -> compile/focused/regression/DB probe -> exact manifest.
- No commit, push, merge, rebase, reset, deletion, frontend/Imaging/Room/Laboratory changes.
  No SDD/ADD skill, worker or inferred Human approval is used.

## BR-02 / BR-47 interpretation

Read `docs/requirements/UC_description.html` UC38-UC44 and its UC40 consolidation note,
plus only specific appendix table rows. UC40 is matched study submission before Doctor
review/findings. BR-47 explicitly requires return toward the requesting Doctor queue.
The HTML consolidation note also associates return-to-Doctor with BR-02. However, the
current `docs/appendix-rules.md` BR-02 row is a sign-in eligibility rule, not Visit/queue
coordination. Treat that as a documented numbering conflict; do not edit auth rules or
derive new queue semantics from it. The user's explicit desired behavior and BR-47 agree,
but detailed trigger/gating/queue lifecycle still require a queue-domain contract.

## Existing queue model audit and inventory

Search scope: all `src/main/java` and all Flyway migrations for queue, waiting, waiting
list, position, reception/room/service/Doctor queue and queue history; inspect Visit,
Appointment, Clinical and related integration code. No independent `queue`, `room` or
`reception` business package supplies a Doctor waiting queue. Room/reception persistence
is in Visit. Reporting/read DTO terminology is not a queue command or persistence model.

| Concern | Actual production representation | Limitation for Doctor return |
| --- | --- | --- |
| Queue tables | `walk_in_reception_queue_entries`, `walk_in_reception_queue_history` (V023) | Walk-in reception only; no Doctor/profile/service destination |
| Queue entities | `WalkInReceptionQueueEntryJpaEntity`, `WalkInReceptionQueueHistoryJpaEntity` | Identity is reception entry + Visit, not Doctor waiting work |
| Queue repositories | `WalkInReceptionQueueEntryJpaRepository`, `WalkInReceptionQueueHistoryJpaRepository` | Existing write is reception entry/exit; no Doctor-return operation |
| Queue services | `WalkInReceptionQueueServiceImpl`, `ExitWalkInReceptionQueueServiceImpl` | Create reception waiting entry, then terminal reception exit |
| Queue read | `VisitReceptionReadRepository`, `VisitReceptionReadServiceImpl`, `VisitReceptionReadController` | `/api/v1/reception-queue`, FIFO computed from arrival time/id, receptionist access |
| Queue statuses | `WAITING`, `LEFT_RECEPTION` | No Doctor destination or post-imaging transition |
| Queue history | DB-generated initial WAITING and WAITING -> LEFT_RECEPTION | No IMAGING_RESULT_READY source request/event or Doctor target |
| Actor permissions | Active Receptionist inserts; active Doctor exits for CARE_STARTED/VISIT_CLOSED; Receptionist exits for CANCELLED | Trusted imaging event has no permitted insertion transition |
| Care assignments | V007 `visit_care_assignments`, renamed `visit_assignments` by V020 | RESERVED/IN_PROGRESS/COMPLETED/RELEASED are care reservations/activity, not waiting order/history |
| Doctor active list | `VisitCareAssignmentRepository.findDoctorActiveCare`, `VisitDoctorActiveCareReadServiceImpl` | Reads OPEN Visit + IN_PROGRESS care only; not a pending Doctor queue |
| Room reservations | V017 `visit_room_reservations`, renamed `room_reservations` by V020 | IN_PROGRESS/CLEANING/RELEASED are occupancy states, not Doctor queue entries |
| Clinical stage | `ClinicalVisitStatus` IN_EXAMINATION/AWAITING_CONCLUSION/OTHER | Enum does not create queue persistence/transition/history |
| Result receipts | V101 `visit_imaging_result_receipts` | Event receipt only; no destination, queue lifecycle or logical return cycle |

## Schema and history evidence

- V007: Visit status OPEN/CLOSED; care assignments reserve timed Doctor capacity.
- V015/V016: care-assignment audits describe STARTED/EXTENDED/COMPLETED/CAPACITY_CONFLICT
  and related care operations. Not waiting-list return history.
- V017: room reservations/occupancy and their audits. Reusing these would introduce Room
  Assignment, explicitly outside this checkpoint.
- V020: table renames do not add queue capabilities.
- V023: only WALK_IN Visits enter reception queue; arrival must equal Visit arrival;
  statuses WAITING/LEFT_RECEPTION, reasons CARE_STARTED/CANCELLED/VISIT_CLOSED. Unique
  active reception entry per Visit. No requesting Doctor, service, source event or return cycle.
- V024: history insertion is generated by queue transitions; direct history inserts rejected.
- V025: terminal LEFT_RECEPTION entries cannot reopen/change; Visit/arrival identity immutable.
- V027: active actor type enforced by transition; no system imaging-return transition.
- V101: durable immutable receipts keyed by event ID. This preserves receipt replay,
  not one logical queue return across distinct imaging events.

`VisitReceptionReadRepository.findQueue/findPosition` computes reception FIFO by
`arrived_at,id`; the `queuePosition` response is not a generic queue. Existing care-start
command exits reception and starts care/room reservation; invoking it on result readiness
would start clinical activity and allocate room/capacity, not enqueue a waiting Patient.
It is not reused as a return-to-Doctor shortcut.

## Trigger decision

The appropriate future hook is existing `IMAGING_RESULT_READY` delivery:
`DiagnosticEvidenceServiceImpl.linkConfirmedStudy` confirms exact request/Visit/Patient/
attempt and matched complete ingest through ImagingStudyPort, sets RESULT_AVAILABLE,
updates the Clinical order to RESULTS_AVAILABLE, and enqueues IMAGING_RESULT_READY.
Dispatcher calls `VisitDiagnosticPort.publishResultReady`; the committed Visit adapter
delegates to `VisitImagingCoordinationServiceImpl.recordResultReady`.
This precedes UC41 review/UC42 findings and matches BR-47 submission. Findings-release
delivery must not independently enqueue a second Doctor return. No new trigger is installed.

## Requesting Doctor resolution / Visit-Patient linkage

DiagnosticRequest `ordering_doctor_id` is the requesting Doctor ACCOUNT ID, while
Visit assignments use Doctor PROFILE ID. Existing `DoctorProfileIdentityQueryService`
resolves account -> profile; the Visit adapter already uses it for current-care reads.
Future return would need Diagnostic-owned canonical requesting account evidence plus
Visit-owned responsible/current-care validation and account/profile resolution. The current
ResultEvent contract exposes event/request/Visit/Patient/type/hash, not a requesting Doctor.
Do not invent a target, equate account/profile IDs, or accept client doctorId/queueId.

Existing result-event query validates canonical outbox/request/current-study evidence;
Visit verifies its own Patient linkage before storing a receipt. No production code changes
are needed merely to re-prove this existing safe path. Missing queue destination/Doctor
policy is not bypassed by reusing a Patient ID or choosing a convenient room.

## Idempotency and multiple imaging requests

V101 uses event-ID primary key, INSERT ON CONFLICT, and comparison of request/Visit/Patient/
type/hash. Same-event replay cannot create another receipt; conflicting reuse is rejected.
Receipts are immutable. This remains unchanged and is NOT represented as exactly-once queue return.
Different requests/events may legitimately produce distinct receipts for the same Visit.

Clinical service-order statuses are ORDERED/RESULTS_AVAILABLE/CANCELLED; confirmed-study
processing updates the linked order. `ClinicalVisitCompletionEvidenceServiceImpl` exposes
outstanding ALL-service count and active imaging order IDs. It does not define which group
of required imaging must finish before returning, what additional-order cycles mean, or
how a Doctor queue entry coalesces multiple results. No "first result" or "all complete"
queue rule is inferred. No queue entries are created for single or simultaneous results;
no new multiple-request queue test is claimed PASS. That behavior is blocked with the model.

## Queue transition / failure / Room non-scope

No queue transition is implemented or falsely acknowledged. Existing receipts remain safe
result-availability acknowledgements only. Existing coordination has no Visit state mutation:
even if a delayed event is receipted for a closed Visit, it never reopens that Visit. A
receipt for a CLOSED Visit must not later be interpreted as a successful return.
Examination, findings, Prescription, Patient release and Invoice are not created/completed
by receipt processing. Existing Imaging/Room behavior is preserved, not duplicated.

Existing missing Visit -> RECORD_NOT_FOUND/MSG-41; linkage failure -> EVIDENCE_MISMATCH/
MSG-39; current-care denial -> UNAUTHORIZED_RECORD/MSG-40; invalid care/state -> MSG-42;
infrastructure -> DEPENDENCY_UNAVAILABLE. Receipt conflicts retain IDEMPOTENCY_CONFLICT.
There is no new public endpoint exposing record existence. Queue-impossible/Doctor-target/
closed-Visit outcomes for a new command are proposed below, not implemented.

## Minimal proposed contract — PENDING HUMAN REVIEW, not an approved schema

1. Visit owns an internal return command invoked from canonical IMAGING_RESULT_READY
   delivery, not an HTTP payload selecting a Doctor/queue. Inputs are canonical event,
   request, Visit, Patient and requesting-account evidence from owner services.
2. Required destination data: Visit/Patient identity, resolved Doctor profile, logical
   imaging-return cycle, pending return identity, ordered-at timestamp and lifecycle state.
   Product/domain review must define supported states and Doctor claim/resume semantics.
3. Transitions to specify: waiting return -> Doctor resumes care; cancellation/ineligible
   Visit; Doctor reassignment/inactive/unavailable; handling an existing active care assignment.
   OPEN and correct linkage/care must be checked; CLOSED must never reopen.
4. Define the required imaging group and readiness policy before deciding when a return
   is actionable. Distinct ready events coalesce into at most one pending logical return
   per approved scope/cycle; event replay cannot duplicate transition/history.
5. Preserve ordered history with Visit/Patient, prior location/state, target Doctor queue,
   reason IMAGING_RESULT_READY, canonical source request/event and time. Use an approved
   queue-history contract; do not force these fields into reception history.
6. Reuse V101 event recognition where appropriate. Current receipt may commit before any
   future queue return; receipt existence alone must NOT suppress a pending/deferred return.
   Design receipt/queue/history atomicity or explicit processing progress and recovery for
   already-receipted events before implementation. No parallel in-memory dedupe is proposed.
7. Define concurrency serialization for simultaneous results and a single return-cycle
   uniqueness boundary. Reject wrong linkage (MSG-39), unauthorized target/care (MSG-40),
   unavailable record (MSG-41), closed/ineligible/conflicting state (MSG-42); retry actual
   infrastructure failure. Do not return fake success for missing queue support.

Required Human decision: approve queue ownership/lifecycle, readiness grouping, reassignment,
ordering, history and receipt/transition recovery contracts before considering any new schema.
No CREATE TABLE design or V102 is supplied; the audit proves a domain gap, not an approved
data model. No migration can resolve these missing business decisions by itself.

## Migration / architecture / verification

New migration: NONE. V101, V099/V100 and all queue migrations unchanged. No production/test
source edits. Visit continues to own receipt/queue persistence; Diagnostic has no Visit
repository imports, Clinical has no queue ownership, Visit reads Diagnostic via its public
owner service. Existing architecture tests verify those boundaries. No frontend or Long
branch work is merged/copied. No Laboratory or Room Assignment implemented.

## Tests and exact commands

From backend, using installed JDK 21 without changing repository configuration:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test-compile
.\mvnw.cmd "-Dtest=DoctorImagingReviewIntegrationTest,DoctorCoreProductionWiringTest,VisitDiagnosticArchitectureTest,VisitDoctorActiveCareReadServiceImplTest,VisitClinicalCareContextServiceImplTest,ClinicalImagingRequestServiceImplTest,ClinicalVisitCompletionEvidenceServiceImplTest" test
.\mvnw.cmd "-Dtest=Clinical*Test,Doctor*Test,PastMedicalRecordsQuerySemanticTest,AfterVisitAdviceServiceImplTest,VisitClinicalCareContextServiceImplTest,VisitClinicalContextProductionWiringTest,VisitClinicalContextAdapterTest,VisitDoctorActiveCareReadServiceImplTest,VisitPatientHistoryQueryServiceImplTest,VisitDiagnosticArchitectureTest,DiagnosticControllersT016Test,DiagnosticTransitionPolicyTest,DiagnosticStatusT015Test,DiagnosticHistoryT015Test,DiagnosticPatientHistoryQueryServiceImplTest,ServiceCatalogClinicalAdapterTest,!*Postgres*" test
git diff --check
git status -sb
```

- Compile: BUILD SUCCESS; Java 21 Enforcer passed.
- Test-compile: BUILD SUCCESS; all 216 test sources, including existing guarded DB tests.
- Focused: 104 tests, 0 failures, 0 errors, 0 skipped.
- Regression: 447 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- Existing integration covers confirmed-study -> outbox -> real Visit receipt, duplicate
  delivery, wrong Visit/Patient, missing Visit, exact release acknowledgements, current
  Doctor authorization, retries, and absence of automatic Visit/Examination mutation.
- Regression includes UC38, UC41/42, UC33 different-historical-Doctor behavior, Diagnostic
  findings/status/history support, current care, mapping, wiring and architecture tests.
- No new queue tests were invented: nonexistent Doctor queue behavior cannot be certified
  by testing reception instead. Existing reception SQL suites are DB-backed and blocked.
- Evidence is in `backend/target/surefire-reports/`. Passing repository-double tests do
  not establish SQL concurrency or queue-return behavior.

## PostgreSQL

POSTGRESQL = BLOCKED. TCP connection to 127.0.0.1:5432 failed outside the sandbox.
No DB test was executed or claimed passed. Existing reception queue/history, receipt,
Diagnostic and UC33 DB tests compile. No database created, secrets read, schema applied,
or service started. V101 SQL replay/concurrency remains unverified in this run.

## Files changed / exact safe files to stage

Only this new Action Record changes. All production/test sources and migrations are
identical to the committed Imaging Review baseline. The index remains untouched.

Exact repository-root-relative staging manifest:

```text
backend/docs/imaging-return-doctor-queue-action-record.md
```

## Ready to stage? / remaining gaps

YES, for this Action Record only. Final `git diff --check` passes; `git status -sb` shows
only this untracked document on `feat/hung/KAN-XX-imaging-return-doctor-queue`.
Readiness here concerns this audit document only, not
approval or implementation of Doctor queue return. The proposal remains PENDING HUMAN REVIEW.
Remaining blockers: an approved Doctor queue/domain contract and multi-request readiness/
return-cycle/history/recovery semantics; canonical requesting Doctor exposure and reassignment
policy; BR-02 reference reconciliation; PostgreSQL verification; secure Imaging viewer owner
integration; frontend wiring afterward; Laboratory's dedicated production UC/BR.
No commit/push/merge/rebase/reset or automatic staging performed.
