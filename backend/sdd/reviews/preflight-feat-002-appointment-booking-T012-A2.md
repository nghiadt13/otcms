# Preflight — FEAT-002 T012 A2

- Status: BLOCKED — shared port ownership/shape decision required
- Date: 2026-10-14 (Asia/Bangkok)
- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T012`
- Governance: `team` / `direct`, from `backend/sdd/shared_context.md` v3.3.2; runtime identity/enforcement UNVERIFIED.

## Outcome and approved boundary

Close T012 against the approved TASKS v0.3.0 foundation exit: Visit/reception/clinical evidence schema and owner read bindings; WALK_IN/recorded-arrival/link/uniqueness invariants. Atomic arrival/check-in writers remain T027, and active-care reservation/overrun behavior remains T031. Existing implementation, V007 and earlier approvals are reused where they satisfy this exit.

## Dependency evidence

- T002, T007 and T009 are complete; T010, T011 and T013 are complete. V007 is present and V008 (T013) now supplies appointment relationships and same-patient prior-appointment FKs.
- Contract `visit-reception-clinical` v1.0.0 is frozen and previously approved. T014 shared guard is complete.
- Earlier user-run local suite passed 95/95 and `VisitT012PostgresIntegrationTest` 2/2. The later user-provided suite excerpt reports 124/124 with the T012 PostgreSQL class 2/2; no new tests were run during this preflight.

## Scout summary

- Backend: Java 21, Spring Boot, modular monolith/Clean Architecture.
- Relevant persistence: `V007__visit_reception_and_clinical_evidence.sql`; appointment linkage was later added in V008.
- Relevant adapter: `VisitWorkflowPersistenceAdapter` implements `VisitWorkflowPort` and binds follow-up evidence only.
- Shared ports: `ReceptionWorkflowPort` currently combines the `requireRecordedArrival(...)` read with `checkInAtomically(...)` write.
- Tests: `VisitT012PostgresIntegrationTest` covers V007 schema/link/replay constraints and follow-up evidence but does not test a recorded-arrival read adapter.
- No additional docs or in-flight plan alter the approved T012 v0.3.0 exit; TASKS v0.3.0 is the accepted execution plan.

## Independent review finding

Fresh read-only review found no defect in V007 or the `VisitWorkflowPort` binding, but identified a T012 exit gap: `ReceptionWorkflowPort.requireRecordedArrival` has no implementation. T027 owns atomic arrival/check-in writers; that does not automatically supply the declared read port. Do not add a no-op/default throwing adapter, and do not implement the T027 writer early.

## Decision required

1. **Recommended — split read/write contract:** approve `RecordedArrivalQueryPort` (read-only owner port) for T012, move `requireRecordedArrival` there, keep `ReceptionWorkflowPort.checkInAtomically` for the T027 writer, update frozen contract to v1.1.0, implement the read-only persistence adapter and focused regression test, then complete T012. This changes a shared frozen contract and needs explicit approval before implementation.
2. **Defer arrival read binding:** amend the T012 exit so recorded-arrival table/schema and logical contract declaration are sufficient, and explicitly assign the executable arrival reader to T027; then close T012 without adding a premature adapter. This changes the approved task exit and requires explicit approval.

## Execution state

- No source/schema changes and no test commands were run. No grant was issued or consumed.
- T012 remains unchecked and unclosed. No production/Supabase access or credential inspection occurred.
- Waiting for Owner Long's contract/exit decision before issuing any T012 implementation grant.

## Resolution — 2026-10-14

- Owner Long selected “Split ports; T012 implements reader”. This resolved the shared-contract decision and authorized the bounded T012 A3 implementation.
- `visit-reception-clinical` was updated and frozen at v1.1.0; see `shared_context.md` and the T012 A3 execution record.
- A3 implementation and independent review are recorded separately. The previously blocked A2 preflight remains an immutable record of the state before this decision.
