# Backend completion phase 3 - Action Record

## Scope and Shadow Plan

Explicit user continuation authorizes the smallest Diagnostic-owned completion query, UC28 composition, compatible UC44 completion projection, focused/compatibility/broad non-DB verification, audit update and local commits. Existing branch retained. No frontend edit, new branch, push, dependency/schema change or blocked owner implementation. No Human reviewer identity or approval impersonated; referenced review protocol is absent as recorded in previous checkpoints.

Plan: audit Diagnostic canonical registration, release/history/corrections and immutability; add owner service with bounded batched persistence reads; compose in Visit; test linkage, completion, cancellation, duplicate legacy requests, version selection and race reasoning; audit UC44 compatibility and add a separate metadata projection if supported; run Java 21 compile/test-compile/focused/compatibility/broad non-DB; update audit and commit locally after green diff checks.

Boundary: Diagnostic public completion service/implementation and two repository batch queries; Visit lifecycle plus tests; Diagnostic status/model/DTO/mapper/controller and affected tests if UC44 remains compatible; architecture/production wiring/DB fixture coverage; this Action Record and backend-completion-audit.md. No new migration.

Exact commands: .\mvnw.cmd -DskipTests compile; .\mvnw.cmd test-compile; .\mvnw.cmd -Dtest=<focused selector> test; prior broad non-DB selector; git diff --check; scoped local git add/commit; git status -sb. No push.

## Source decisions

V097 partial unique index and registration use one non-CANCELLED Diagnostic request per Clinical service order. Query globally by order IDs, reject missing/ambiguous/wrong-Visit/wrong-Patient requests rather than picking an arbitrary/latest row. Canceled Clinical orders remain excluded by the existing Clinical evidence service; canceled Diagnostic request history is not a fulfillment.

Existing Diagnostic history selects the latest COMPLETED version, not latest private draft. A new DRAFT correction does not revoke the already published effective result. New completed versions supersede older ones; mismatched, unvalidated or unreleased newer completed evidence fails closed. Completion also verifies current persisted attempt/study, canonical request/Visit/report IDs, matching version, completedAt, validatedAt and releasedAt. No RESULT_AVAILABLE/REVIEWED-only shortcut and no external Imaging call.

Four batch queries maximum: live requests; current attempts; completed findings versions (effective latest selected deterministically); referenced reports. Both UC28 and UC44 reuse the same owner predicate. No N+1 per-order loop, Visit authorization or foreign repository in this owner query. The empty order set returns complete empty evidence without persistence.

## Concurrency audit

Clinical new-order creation and examination writes retain their existing shared guard before first Visit reads; UC28 holds guard then Visit row lock through owner evidence reads and commit. Diagnostic findings/release does not acquire that guard. Do not claim shared lock atomicity for Diagnostic. DiagnosticTransitionPolicy allows cancellation/relink only before results and cannot reset REVIEWED. Completed findings are immutable in entity methods and V053 trigger; V056 protects released/used reports. Dispatch writes report.releasedAt and findings COMPLETED together in its existing transaction. A newer DRAFT correction leaves the published result effective. A supported new completion preserves complete -> complete; incomplete -> complete can cause conservative rejection/retry while batched reads overlap. No new lock or migration needed. PostgreSQL race/trigger execution remains separately unverified if DB unavailable.

## Verification

PASS: Java 21 compile/test-compile; latest Doctor integration checkpoint has 232 focused and 1295 broad non-database tests, zero failures/errors/skips. PostgreSQL remains BLOCKED. See doctor-flow-completion-action-record.md for exact commands and current evidence; no broader UC44 completion is claimed.


## Merge repair follow-up

Restored during product/UC28 merge repair. Java 21 compile and test-compile PASS; 180 focused and 1295 broad non-database tests PASS, zero failures/errors/skips. Cancellation fixture corrected to cancel an ORDERED order. Local database execution remains unavailable; no UC44 projection or broader phase completion claimed. See product-uc28-merge-repair-action-record.md for scope, exact commands, logs, product contracts and residual database limitation.
