# Laboratory / Blood Test — Requirement Gap and Backend Contract Design

LABORATORY IMPLEMENTATION BLOCKED BY REQUIREMENT GAP

Design status: PENDING HUMAN REVIEW. This document is an evidence-backed proposal,
not an approved requirement, public API, schema, UC allocation or implementation plan.
SOURCE-SUPPORTED means observed in the current repository; it does not promote existing
generic code or frontend mock behavior into an approved Laboratory business workflow.

## A. Git baseline

- Date: 2026-11-05. Branch: `feat/hung/KAN-XX-laboratory-requirement-design`.
- Initial tree clean; HEAD `e8cd026f96ed8f026bb912294f05663cd7fe2f7e`,
  `feat: integrate doctor imaging review coordination`, following UC33 `c733b6a`.
- V101 `V101__create_visit_imaging_result_receipts.sql` is tracked at this baseline.
- Ran requested status, branch, eight-commit log and whitespace checks. No branch change.
- Scope authorized by user: requirement/code/frontend audit and this design document only.
  Shadow Plan: verify baseline -> collect requirement and source evidence -> distinguish
  catalog/generic ordering from operational workflow -> gap matrix -> proposed boundaries,
  lifecycle/APIs/schema/test matrix -> required team decisions -> final Git checks.
- No production/test/frontend source, migration, dependency or existing document changed.
  No queue work, Imaging merge/copy, commit, push or automatic staging. No UC/BR assigned.

## B. Requirement evidence

SOURCE-SUPPORTED sources read:

- Repository `AGENTS.md` and `docs/architecture.md`: feature-owned Spring layered modular
  monolith, public owner services across features, backend role/record authorization,
  DTO HTTP boundaries, Flyway, no speculative excluded workflows.
- `docs/requirements/UC_description.html`: UC38-UC44 specify Imaging, not internal
  Laboratory. Searches across this HTML and current UC Markdown files for laboratory,
  lab, blood, specimen/sample, test result/reference range/abnormal and Vietnamese
  equivalents found no dedicated internal Laboratory use case. General clinical-service
  references in Visit/room descriptions do not define collection or results.
- Specific appendix rows only: BR-37 requires necessary clinical services to be complete
  before Visit closure; BR-39 restricts completed examination/report Patient access until
  clinic availability; BR-41 concerns Doctor history for current care; BR-45/47/48/49 are
  Imaging request/submission/view/findings rules, not Laboratory workflow requirements.
- Targeted appendix searches for Laboratory/Blood/specimen/measurement concepts found
  no dedicated rule. BR-33 mentions next-service rooms/waiting lists, not lab processing.
- Current BR-04 is PASSWORD POLICY. It must not be cited as a Laboratory/clinical-release
  rule. BR-39 and Imaging BR-49 provide distinct existing release precedents only.
- MSG-39/40/41/42 provide existing invalid-information/access/unavailable/state vocabulary;
  no new Laboratory message numbers are allocated here.

No source specifies lab order actors/operational scope, collection, specimen rejection,
processing, verification, release timing, Doctor review or Patient Laboratory result access.
The agreed Doctor Blood Test branch supplied by the user is scope intent; its operational
contract remains missing. External uploaded medical reports are not internal lab results.

## C. Current backend inventory

Paths below are relative to `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/` unless noted.

| Layer | SOURCE-SUPPORTED evidence | What it does NOT establish |
| --- | --- | --- |
| Roles/accounts | `common/security/AccountRole`, `auth/model/entity/enums/AccountType` include LAB_TECHNICIAN; V035 adds account type/staff profiles | No order-specific Lab worker assignment, collector/verifier permission or worklist authorization |
| Staff support | `StaffShiftAdminService`, Auth staff administration/directory support Lab role; room lookup permits Lab staff | Scheduling/room access does not authorize specimens/results |
| Catalog entity/repository | `servicecatalog/model/entity/ClinicServiceJpaEntity`, `repository/ClinicServiceRepository` | Service definition, not order/sample/result persistence |
| Catalog types/values | `ClinicServiceType.LABORATORY`, `LabIndicator(name, unit, refMale, refFemale)`, numeric `ReferenceRange(min,max)` | No measured result or patient-specific reference selection rule |
| Catalog serialization/validation | `LabIndicatorListConverter`, `ClinicServiceValidation` require named/unit-bearing indicators, finite ordered male/female bounds for Laboratory | No stable indicator code, demographic/method/reference version, verification or abnormal-result engine |
| Catalog APIs/DTOs/services | `ClinicServiceController`, catalog application services; `ServiceRequest`, `ServiceResponse` expose indicators; `PublicServiceResponse` intentionally hides internal ranges | Catalog CRUD/list APIs are not Laboratory ordering/result APIs |
| Generic Clinical entities | `OrderingRoundJpaEntity`, `ServiceOrderJpaEntity` contain Visit/Patient, service/name/price snapshots, requesting Doctor profile, reason/instructions, version | No operational Laboratory aggregate or specimen linkage |
| Generic repositories/services | `OrderingRoundRepository`, `ServiceOrderRepository`, `ClinicalOrderingServiceImpl`, `ClinicalOrderPreparationService`, `ClinicalOrderingTransactionService` | Internal generic submit can persist LABORATORY; it does not register downstream Lab work or guarantee a Lab-specific HTTP workflow |
| Existing public order endpoint | `ClinicalImagingRequestController` -> Imaging transaction calls `prepare(..., true)` | Imaging-only endpoint rejects non-Imaging; do not route Lab through it |
| Clinical DTO/port | `ClinicalOrderingService` records and `ClinicServiceCatalogPort.ClinicalServiceSnapshot` | Catalog port currently returns id/name/type/price/active only; no indicator/reference definition snapshot |
| Existing Visit completion read | `ClinicalVisitCompletionEvidenceServiceImpl` counts all noncancelled ORDERED services as outstanding | No approved Lab completion/cancellation/release threshold |
| Existing readiness read | `ClinicalOrderingServiceImpl.isLatestRoundReadyForReview` requires all effective orders in latest round RESULTS_AVAILABLE | Generic existing method is not an approved Lab return-to-Doctor/queue rule |
| Rooms | Laboratory room type, supported services and LAB_ROOM capability | No Laboratory staff/worklist, specimen or result lifecycle |
| Lab operational layers | No Laboratory order/specimen/result entity, repository, service, controller or DTO found | Catalog `LabIndicator` is not a result-line entity |

Migration inventory relevant to Lab:

- V035: Lab account role and general staff profile support.
- V037: Laboratory catalog/room types and supported-room services. Keyword-based legacy
  service classification is not a validated operational test/specimen definition.
- V038: `clinic_services.lab_indicators` TEXT serialization; not a results table.
- V071: staff announcement audience can name LAB_TECHNICIAN; not workflow authorization.
- V073: `clinical_service_orders` allows LABORATORY; ORDERED/RESULTS_AVAILABLE/CANCELLED,
  PENDING_PAYMENT/PAID, price snapshot and reason exist as generic Clinical fields.
- V099 Prescription, V100 Medical Documents, V101 Visit/Diagnostic receipts already exist;
  none creates Laboratory processing or release. No Laboratory migration is proposed now.

Catalog support != generic order storage != operational ordering != specimen workflow !=
verified result workflow. In particular, `markImagingResultAvailable` validates IMAGING;
there is no equivalent Laboratory owner completion callback to reuse unchanged.

## D. Current frontend assumptions — read-only evidence

Read `frontend/src/features/doctor-exam/types/doctorExam.ts`,
`api/doctorExamLiveOrders.ts`, `api/doctorExamLiveApi.ts`, `api/doctorExamApi.ts`,
`api/doctorExamDemoApi.ts`, `api/doctorExamDemoStore.ts`, `components/LabResultsPanel.tsx`,
`components/ParaclinicalResultsSummary.tsx`, `components/OrderServicesSheet.tsx`.

- Types: LabResult has orderId/serviceName/lines; each line has indicator/value/unit/
  reference as strings and abnormal as boolean. No specimen ID, result version, verification,
  release timestamp, method, applicable demographic or audit actor is represented.
- `DoctorExamApi.listLabResults(visitId)` is a TypeScript interface method, NOT evidence
  of an HTTP endpoint. Live implementation returns `[]` locally without a Lab request.
- Live `capabilities.laboratoryOrders=false`; OrderServicesSheet locks the Laboratory
  category. `submitOrders` rejects any selected service whose kind is not IMAGING with
  MSG-39 before HTTP, including mixed selections. Thus live does not send Lab orders.
- Live catalog reads `GET /api/v1/staff/clinic-services` and recognizes LABORATORY.
  Live order submission uses only `POST /api/v1/visits/{visitId}/imaging-requests` with
  Idempotency-Key; listing uses Diagnostic Imaging request/history routes. There is no
  Laboratory order/result URL to implement verbatim from this frontend.
- Demo has `laboratoryOrders=true`, generic service selections and timer-driven
  PENDING_PAYMENT -> PAID -> RESULTS_AVAILABLE. It labels waiting as collection pending;
  no actual specimen collection/verification occurs.
- Demo `listLabResults` clones the same hardcoded DEMO_LAB_LINES for each available Lab
  order. Its result orderId/serviceName originate from demo orders; all measurement/unit/
  reference/abnormal values are fixture data, not production measurements or calculations.
- LabResultsPanel says results appear after technician approval, displays values/ranges/
  flags, and shows a backend-gap notice in live. ParaclinicalResultsSummary trusts the
  supplied abnormal flag. OrderServicesSheet says payment precedes work. These are UI/demo
  assumptions to resolve, not approved verification/payment/collection requirements.
- Frontend comments use legacy UC numbers; none is assigned to Laboratory in this design.

## E. Supported capabilities and gap matrix

Legend: SUPPORTED = concrete support within its stated scope; PARTIAL = generic/adjacent
support only; MISSING = no dedicated requirement/implementation; UNDEFINED = policy not
specified. Backend support is not evidence of requirement approval. Every unresolved row
requires team acceptance before workflow code.

| Capability | Current requirement support | Current code support | Frontend assumption | Decision required? |
| --- | --- | --- | --- | --- |
| Doctor creates lab order | MISSING dedicated UC/BR | PARTIAL generic Clinical writer; no Lab HTTP/registration | PARTIAL demo; blocked live | Yes: order scope/registration |
| Lab test type | PARTIAL general service management | SUPPORTED catalog LABORATORY + indicators | SUPPORTED catalog recognition | Yes: approved test/panel definitions |
| Clinical reason | MISSING Lab-specific rule | PARTIAL mandatory generic reason | PARTIAL shared draft reason | Yes: required content/limits |
| Specimen type | MISSING | MISSING | MISSING in result DTO | Yes: types/per-test requirements |
| Specimen collection | MISSING | MISSING | PARTIAL demo wording only | Yes: who/when/rejection/recollection |
| Lab technician | MISSING operational actor permissions | SUPPORTED role/admin; MISSING work scope | PARTIAL panel approval comment | Yes: assignment/collector/verifier |
| Sample status | UNDEFINED | MISSING | MISSING | Yes: whether separately tracked |
| Processing status | UNDEFINED | PARTIAL generic order states | PARTIAL timers | Yes: actual lifecycle |
| Result lines | MISSING | PARTIAL catalog indicators only | PARTIAL hardcoded line array | Yes: panel identity/completeness/value kinds |
| Unit | UNDEFINED Lab result policy | SUPPORTED catalog string; no measured-unit binding | PARTIAL display string | Yes: units/precision/conversion |
| Reference range | UNDEFINED patient/method selection | PARTIAL male/female catalog bounds | PARTIAL formatted fixture string | Yes: applicability/version/text ranges |
| Abnormal flag | UNDEFINED | MISSING result calculation | PARTIAL hardcoded boolean | Yes: derivation/unknown/override |
| Result verification | MISSING | MISSING | PARTIAL technician approval comment | Yes: verifier role and sign-off |
| Doctor visibility/review | MISSING Lab-specific rule | PARTIAL current-care pattern only | PARTIAL panel expects approved lines | Yes: verified vs draft visibility/review action |
| Patient visibility | UNDEFINED; BR-39 covers examination/report | MISSING Lab access path | MISSING in Doctor feature | Yes: own/dependent release contract |
| Correction/amendment | UNDEFINED | MISSING Lab versions | MISSING Lab revision fields | Yes: correction authority/history |
| Result release | UNDEFINED | MISSING Lab release | MISSING release metadata | Yes: actor, timing, corrections |
| Visit completion dependency | PARTIAL BR-37 general rule | PARTIAL all-order outstanding count | UNDEFINED Lab closure policy | Yes: required/optional + completion milestone |
| Queue behavior | UNDEFINED Lab-specific trigger | MISSING Doctor queue contract | UNDEFINED | Yes: separate queue-domain dependency |
| Billing relationship | UNDEFINED Lab charge trigger | PARTIAL catalog price/order payment snapshot | PARTIAL demo payment gate | Yes: charge/payment/cancel/recollect policy |

## F. Missing capabilities

Dedicated operational order registration, scoped Lab worklists, specimen collection and
rejection/recollection, processing, result-line persistence, verification, correction and
Patient release/access contracts are missing. A stable indicator identity and reference
selection policy are also absent from the operational layer. Catalog and generic order
support can be reused only after these contracts are approved; neither should be rebuilt
as a competing source of truth. Missing requirements and missing code are separate gaps.

## G. Undefined decisions

No operational implementation is ready until the team decides the boundaries below.
Do not silently define every catalog service as a required test, all technicians as
verifiers, each measurement as a billable order, or RESULT_AVAILABLE as Patient release.
Internal performed tests and external uploaded reports must be distinguished. Whether
this project processes specimens internally or receives verified external results is an
initial scope decision; it determines whether collection/processing features are needed.

## H. Proposed domain model — PROPOSED, not source-approved

Reuse Clinical service order identity instead of creating a second Doctor request aggregate.
One Visit may have multiple Clinical Laboratory orders. One ordered service/panel may have
multiple indicators; individual indicators are not separate service orders by default.

- Clinical order: existing id/Visit/derived Patient/requesting Doctor profile/service/reason/
  price/name snapshot/instructions/timestamps/version. A future HTTP caller supplies serviceId,
  reason and allowed instructions only; no Patient or Doctor identity. Avoid Imaging bodyArea.
- Laboratory operational order: id, unique clinicalOrderId, canonical Visit/Patient,
  ordering account AND resolved profile if both are needed, service-definition snapshot,
  operational status, createdAt, version. Any duplicated linkage is validated owner evidence,
  never client authority; Clinical order remains the one source of Doctor intent/price.
- Specimen: only if internal collection is approved. Candidate specimenId/orderId/type,
  collectedAt/collectorAccountId and acceptance/rejection evidence. Decide order-to-specimen
  cardinality, shared samples, labelling, rejection/recollection and custody before schema.
  Do not mandate one specimen per indicator or build inventory/instrument workflows.
- Result revision: resultId/orderId/revision/status, recordedBy/recordedAt, verifiedBy/
  verifiedAt, version and optional correction predecessor/reason. A verified revision must
  have stable content; Patient release metadata is independent.
- Lines: stable indicator identity within the approved definition/revision, display name,
  typed value plus original representation, unit snapshot, reference bounds OR referenceText,
  applicable reference provenance and deterministic display order. No arbitrary indicator
  injection from a request body; names alone are unsafe keys when they repeat/change.
- Existing male/female double ranges are catalog hints only. PROPOSED result values and
  numeric bounds use exact decimal representation; textual/qualitative and qualified values
  must not be forced into floating-point or treated as normal. Team chooses supported types.
- PROPOSED abnormal handling: derive from validated value + same-unit applicable snapshotted
  reference + approved comparison rule; never accept a client boolean as authority. Preserve
  reference/calculation provenance so catalog edits do not reinterpret old results. For
  text, missing/inapplicable reference, ambiguous demographic or unsupported qualifiers,
  return UNKNOWN/not-assessed rather than false. Any manual assessment/override needs an
  approved qualified actor/reason/audit. Do not invent thresholds or clinical ranges.
  The frontend boolean cannot faithfully represent UNKNOWN; eventual DTO/UI adaptation is
  a separate approved integration task. A flag is not a diagnosis or clinical recommendation.

## I. Proposed ownership / comparison with Imaging

SOURCE-SUPPORTED: Imaging already separates Clinical intent, Diagnostic operational
request/evidence/findings/report/outbox, Visit facts/receipts and Imaging archive. Reuse
those boundary principles and request/version/idempotency conventions, not DICOM attempt,
studyRef, PDF requirement, first-use billing or automatic patient-release semantics.

PROPOSED boundaries, with feature names/interface names illustrative:

| Owner | Responsibility / public boundary |
| --- | --- |
| Clinical | Authorize Doctor order intent; create existing service order; supply trusted order evidence; receive idempotent operational-availability updates |
| Laboratory | `LaboratoryOrderRegistrationService` validates canonical intent and creates operational work; owner services handle collection/results/verification/revisions |
| Service Catalog | Public definition query provides approved panel/indicator/unit/reference metadata snapshot; enrich owner service rather than import its repository |
| Visit | Current-care facts, lifecycle/completion coordination; public service receives approved result-readiness evidence, never Lab repositories |
| Billing | Pricing/charge/payment/cancellation decisions through public billing contracts; not a side effect of entering measurements |
| Patient/access | Own/dependent permission via existing patient access service; Lab owner returns only released result projection after permission checks |

Clinical composes Lab public reads; Lab obtains Visit/Clinical evidence through public
services/ports; Patient presentation never reads Lab persistence. New laboratory feature,
if approved, follows controller -> service interface/impl -> repository -> entity, DTOs,
constructor injection and owner exceptions. No actor-named feature or new architecture tool.

## J. Proposed state machine

SOURCE-SUPPORTED generic Clinical order states remain ORDERED/RESULTS_AVAILABLE/CANCELLED.
Do not reuse Imaging RESULT_AVAILABLE/REVIEWED or add every candidate label to this enum.

PROPOSED minimum operational labels for review: ORDERED -> RESULT_AVAILABLE; CANCELLED
only under explicitly approved conditions. RESULT_AVAILABLE would mean verified Doctor-
visible result under the recommended policy below, NOT automatic Patient release.
Draft result is a private revision, separate from operational order progress.

| Candidate | Evaluation before adoption |
| --- | --- |
| ORDERED | Proposed minimal operational entry; registration must acknowledge canonical Clinical intent |
| COLLECTED | Only needed with approved specimen collection; preferably a specimen fact, not duplicated order status |
| PROCESSING | Add only if staff worklist/processing operations require it; timers are not evidence |
| RESULT_AVAILABLE | Proposed Doctor-visibility milestone; exact verification requirement needs approval |
| COMPLETED | Omit as redundant unless it means a separately approved review/operational completion milestone |
| CANCELLED | Requires rules for collection already performed, partial results, rejection and billing effects |
| RELEASED | Separate release metadata/status on a result revision; not synonymous with Doctor availability |

PROPOSED revision states: DRAFT -> VERIFIED; no in-place editing after verification. If
corrections are in scope, create a successor draft and keep the previous visible revision
until the replacement passes the approved verification/release gates. Supersession,
withdrawal and pending-correction Patient behavior require explicit decisions.

## K. Proposed APIs — illustrative, not locked

Current conventions: `/api/v1/visits/{visitId}/imaging-requests` creates Clinical intent;
Diagnostic uses `/api/v1/imaging-requests/{requestId}/reviews`, `/findings`, `/reports`,
`/findings/completions`. No Laboratory endpoint exists. Prefer plural nouns, action
subresources and one canonical order identifier; decide bulk round versus single-order
submission and Clinical/operational ID exposure before freezing DTOs.

| Purpose | Proposed route / contract |
| --- | --- |
| Doctor creates | POST `/api/v1/visits/{visitId}/laboratory-orders`; serviceId/reason/instructions, Idempotency-Key; no identity selectors |
| Doctor list/detail | GET `/api/v1/visits/{visitId}/laboratory-orders`; GET `/api/v1/laboratory-orders/{orderId}`; current-care and canonical linkage checks |
| Staff worklist | GET `/api/v1/laboratory/orders`; server-scoped to authorized Lab work; pagination/stable order |
| Collection, if approved | POST `/api/v1/laboratory/orders/{orderId}/collections`; plural alternative to supplied `/collection`, not a final path |
| Draft result | POST `/api/v1/laboratory/orders/{orderId}/results`; approved line/value DTO, expected version and idempotency |
| Verification | POST `/api/v1/laboratory/orders/{orderId}/verifications`; only if approved verifier action distinct from entry |
| Completion/release | Evaluate `/completions` and `/releases` only if they are distinct approved actions; never expose ambiguous completion that implicitly releases |

No Patient route is allocated until release/dependent-access policy and ownership of the
Patient-facing read composition are agreed. Proposed responses should expose canonical
order/result revision IDs, linkage allowed to that actor, operational state, resource
version and verification/release availability; private drafts/audit data are actor-filtered.
Patient success cannot expose unpublished revision content through a Doctor/staff route.
Define validation limits, numeric/string encoding, pagination and shared error-envelope
mapping before implementation; existing MSG vocabulary may be reused only as applicable.

## L. Authorization

PROPOSED Doctor ordering: JWT accountId -> active Doctor account -> Doctor profile ->
current Visit -> responsible Doctor -> clinicalWorkAllowed -> Patient derived from Visit.
Verify active LABORATORY catalog service and immutable canonical linkage. accountId and
profileId are distinct identifiers. Recheck permission for replay/read as in current UC42.

PROPOSED Doctor results: Visit-scoped current-care query to Lab public service, never
authorization from a client Patient ID. Historical access policy must be separate; if
eventually included in UC33, authorize through current care rather than requiring the
same Doctor on every historical Visit. No UC33 integration is implemented here.

Lab staff: active LAB_TECHNICIAN is necessary but insufficient. Team must define assigned
room/worklist/organization scope, collector/verifier abilities, and any separation of
duties. Do not give every Lab staff arbitrary order/Patient access just because role exists.
Patient: active account plus own/approved-dependent authorization plus released revision;
match Patient/Visit/order/result at every boundary. Do not trust supplied IDs or flags.

## M. Result release

UNDEFINED today for Laboratory. BR-04 cannot justify release: its current text concerns
passwords. BR-39 is an examination/report precedent; Diagnostic publication waits for
validated report and owner acknowledgements under Imaging-specific rules. No Lab PDF or
automatic release requirement follows from these precedents.

PROPOSED conservative policy for approval: private DRAFT -> qualified verification ->
Doctor-visible RESULT_AVAILABLE; Patient availability requires a separate authorized
release of a specific immutable revision. Doctor visibility and Patient release timing
are independent. Decide whether Doctor review/sign-off is required, who may release,
partial-panel publication, corrections/withdrawals, delayed releases and notifications.
The existence of result lines/verifiedAt alone must not authorize Patient access.

## N. Visit completion dependency

SOURCE-SUPPORTED BR-37 requires required clinical services complete. Current Clinical
completion evidence counts every noncancelled ORDERED service, including a generic Lab
row, as outstanding. It does not know Lab verification/Patient release. This existing
behavior is not proof that Lab's business completion condition has been approved.

PROPOSED decision: a required Lab order should block closure until a named, approved
clinical milestone or valid cancellation/waiver; consider verified Doctor availability
as candidate, not automatic Patient release. Team must decide whether Doctor review,
all indicators, partial results, external pending results or optional orders permit
closure. Implement no change to closure now. After approval, Lab informs Clinical order
state through its owner service, and Visit consumes public completion evidence.

## O. Queue impact

UNDEFINED for Laboratory. No production Doctor waiting queue/return contract exists at
this baseline; reception queue and care/room assignments are different models. The prior
queue blocker is not implemented here. Do not reuse IMAGING_RESULT_READY, V101 receipts
or reception history for Lab without a separate approved event/consumer contract. Decide
whether collection/result readiness requires any queue transition and how multiple orders
coalesce; no room assignment or automatic return is designed as an implicit side effect.

## P. Billing impact

SOURCE-SUPPORTED: catalog price, Clinical service name/price snapshot and payment status,
and general Billing owner services exist. No dedicated Lab billing/collection charge
trigger was found. Demo payment timers and payment-before-work wording are not a policy.
PROPOSED: Billing owns charges; team chooses order-time/payment-before-collection/first-use
milestone, cancellation/refund, rejected specimen/recollection and panel pricing. Link a
charge to canonical service order and dedupe it; do not bill each indicator automatically,
copy Diagnostic contrast/first-use rules, or create invoices when a result is entered.

## Q. Idempotency / concurrency

PROPOSED consistent patterns: Idempotency-Key on mutations, actor/operation/resource-
scoped payload fingerprint; identical authorized replay returns original result,
conflicting payload rejects; expected version on the resource actually mutated, not a
stale unrelated order version. Doctor care/role checks still apply to replay.
Clinical order creation + Lab registration must not leave orphaned work or double orders;
choose local transaction or durable registration/retry contract within the monolith.

Concurrent collection/cancellation and verify/correction/release need row/version checks
and atomic transitions. Completed/verified content is immutable; correction uses a new
revision with reason/author and prior revision retained if corrections are approved.
An order version alone does not protect concurrent measurement edits: define result-
revision versioning too. Add durable events/receipts only for real cross-owner delivery
needs; V101 is Imaging-specific, not a generic Lab inbox. No extra broker is implied.

## R. Proposed schema — conceptual only, no Flyway version

Reuse existing Clinical ordering-round/service-order tables and catalog service identity;
do not duplicate Doctor ordering records or create one service order per indicator.
Likely minimum Laboratory-owned persistence after approval:

- Operational order linked uniquely to Clinical service order, validated canonical linkage,
  definition snapshot, approved operational state, timestamps and version.
- Result revisions linked to operational order, unique revision number, state/content
  version, author/verification metadata and approved release metadata.
- Result lines keyed within a revision, stable indicator identity/order, typed measured
  value/unit/reference snapshot and approved interpretation provenance.
- Specimen and collection/rejection facts only if internal collection scope/cardinality is
  approved. No sample schema is needed merely for displaying an external verified report.
- Minimal mutation audit/idempotency records, and durable notification/owner-delivery
  events only where approved consistency guarantees require them. Evaluate reuse of
  suitable owner infrastructure; never directly write another owner's tables.

Specify foreign/unique constraints, immutable verified/released revisions, schema validation
and result/Patient linkage checks in a later approved plan. No SQL, migration, reserved
number or edits to V099/V100/V101. In particular, V102 is not reserved by this document.

## S. Future test matrix — not executed/implemented in this task

| Area | Required future cases |
| --- | --- |
| Doctor ordering | Responsible active Doctor succeeds; wrong/inactive Doctor denied; CLOSED/ineligible Visit denied; Patient/Doctor spoof impossible; inactive/non-Laboratory service rejected; reason/definition validation; multiple orders per Visit |
| Registration | Canonical Clinical order linkage; duplicate replay creates one work item; same key/different payload rejected; registration failure rolls back or retries per approved contract |
| Lab scope | Assigned authorized worker can see correct order; unrelated worker, wrong role and inactive account denied; worklist cannot broaden via supplied Patient/order ID |
| Collection if approved | Valid transition, correct order/specimen/type/collector; duplicate safe; invalid transition/rejection/recollection rules; concurrent cancel/collection |
| Result entry | Correct order/Visit/Patient; multiple lines per panel; allowed indicator IDs; completeness; exact numeric/text/qualified values, units, ranges, deterministic order |
| Interpretation | Boundary values/inclusive rules per approved spec; incompatible unit, missing/demographically inapplicable reference -> UNKNOWN; catalog changes do not rewrite old results; no trusted client abnormal flag |
| Verification/correction | Qualified verifier only; invalid transition denied; expected-version conflict; immutable verified content; new correction revision/reason/visibility if approved; concurrent verify/edit/release |
| Doctor view | Current permitted Doctor sees approved revision; unrelated Doctor denied; canonical linkage; draft visibility policy; historical different-Doctor policy tested separately if approved |
| Patient access | Unreleased hidden; own released revision visible; approved dependent visible; unrelated Patient denied; private/replaced/pending-correction content follows approved rules |
| Completion | Required pending Lab prevents closure if approved; cancellation/optional/partial/review thresholds explicit; no automatic Visit closure, prescription or invoice |
| Billing/queue | Exactly-once approved charge/event behavior; no duplicate per indicator/replay; no unapproved queue/room side effect; several orders follow agreed completion grouping |
| Architecture | Clinical cannot import Lab repositories/entities; Lab cannot import Visit persistence; Patient cannot import Lab persistence; DTOs only at HTTP; production wiring uses real owner implementations |
| Database | Approved migrations, uniqueness/FKs/immutability, concurrent writes and durable replay against guarded test PostgreSQL; compilation alone is not SQL validation |

No new tests, full regression, DB probe or production build is needed for this documentation-
only task. Future tests cannot make undefined requirements implementation-ready.

## T. Decisions required from team

Minimum approval questions before workflow code begins:

1. Is scope internal specimen processing or external verified-result ingestion, and which
   catalog tests/panels, value kinds and completeness rules are supported?
2. Who can order, collect, process, verify, correct and release? How is Lab work assigned;
   may the same worker enter and verify? What happens to reassigned/inactive Doctor care?
3. If internal collection is included, what specimen identity/cardinality/type and
   acceptance/rejection/recollection transitions and cancellation limits apply?
4. How are measurement units, exact values, reference applicability/version and abnormal/
   unknown/manual assessment determined, including demographic gaps and text results?
5. At which milestone does a Doctor see results, is Doctor review required, and who releases
   which revision to Patient/dependents? What are correction/withdrawal/partial-panel rules?
6. Which Lab services are required for Visit closure, at what milestone, and how do optional,
   pending external, cancelled and partial results affect closure? Is any queue action needed?
7. What triggers charges/payment gating/cancellation/refund/recollection charges, and which
   canonical IDs/transaction/retry/idempotency guarantees bind Clinical, Lab, Visit and Billing?

Then approve exact state/DTO/API contracts, schema constraints and a bounded implementation
plan. Do not assign UC/BR numbers from frontend comments or this proposal.

### AI Agent Recommendation

- Status: PENDING HUMAN REVIEW.
- Scope: this Laboratory requirement-gap/design document only.
- Recommendation: resolve the seven decisions and persist approved requirements/contracts
  before Laboratory production code, routes or migrations.
- Evidence: sections B-D; existing role/catalog/generic order support without operational
  Laboratory requirement or result/specimen implementation.
- Risks: implementing from UI fixtures could expose unverified results, mis-scope staff,
  conflate account/profile IDs, use incorrect reference interpretation, block Visit closure
  indefinitely, or create duplicate charges/work. No production behavior changed here.
- Alternative: keep Laboratory capability disabled until scope is approved; external
  documents remain external documents, not fabricated internal lab results.

### Human Final Review

- Status: PENDING.
- Decision: not recorded.
- Reviewer: not recorded.
- Reviewed at: not recorded.
- Required boundary: approved Laboratory scope, workflow/permissions/release/closure/billing
  policy and exact contracts. Agent has not supplied approval or reviewer identity.

## U. Implementation-ready?

NO. LABORATORY IMPLEMENTATION BLOCKED BY REQUIREMENT GAP.

Catalog/reference metadata, Lab account role and generic Clinical order storage are real;
they do not supply the missing operational/public workflow contract. No production code,
frontend, migrations or queue behavior were changed. This is a reviewable proposal only.

Validation: `git diff --check` PASS. `git status -sb` shows the required branch and only
this untracked document; tracked production/frontend/migration diffs and index are empty.
Explicit trailing-whitespace scan of this untracked file has no matches. No build/tests
were run because no production/test source changed, as permitted by this checkpoint.
Exact file eligible for review/staging: `backend/docs/laboratory-blood-test-requirement-gap.md`.
No automatic staging, commit or push.
