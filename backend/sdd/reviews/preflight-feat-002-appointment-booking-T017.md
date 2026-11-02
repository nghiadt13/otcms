# Preflight — FEAT-002 T017

- Date: 2026-10-14 (Asia/Bangkok).
- Invocation: `/add-execute --feature=feat-002-appointment-booking --task=T017`.
- Result: BLOCKED before grant issuance, migration/test command, or implementation action.
- Governance: canonical `Project Ownership: team` and `Agent Execution: direct`, from `backend/sdd/shared_context.md` v3.3.2. No invocation overrides. Runtime identity/enforcement remains UNVERIFIED; no host-controlled execution claim was observed.
- Feature gate: TASKS v0.2.0 Human Final Review is APPROVED. T017 is unchecked and depends on T006–T016.

## Dependency and contract evidence

- T006–T009 and T013–T016 are marked complete. T010, T011 and T012 remain unchecked in the dependency table; T017 cannot pass its declared dependency gate while these task markers are incomplete.
- T010 post-code implementation/trace review is approved, but T010 is explicitly PARTIAL in its execution record and remains unchecked. `clinic-service-catalog` v1.0.0 remains DRAFT pending contract-shape approval; Doctor assignment writes/protected removal are deferred to T032.
- T011 post-code implementation/trace and `staff-doctor-directory` v1.0.0 contract are approved/frozen, but T011 remains unchecked because publication/acceptance and shift/time-off mutations are deferred to the T014/T032 integration gates. Its approved evidence covers the existing V005/V006 slice and clean V001→V006 migration, not completion of the entire T011 task contract.
- T012 remains unchecked in TASKS. Frozen `visit-reception-clinical` v1.0.0 and prior local V001→V007 evidence exist, but the task exit marker has not been approved/closed in the dependency table.
- T017 additionally requires real owner contracts ready, a clean database and upgrade from applied V001 with existing data, checksum/mapping/FK/constraint evidence, and cross-module atomicity/lock-order checkpoint. Prior records show additive migrations through V013 and local PostgreSQL suite evidence through 123/123, but no T017-specific clean-V001-to-current upgrade/checkpoint record was found.
- Architecture Profile approves `backend/.\mvnw.cmd test` and `backend/.\mvnw.cmd clean verify`; local DB helper evidence is user-provided. No command was run during this blocked preflight.
- Production/Supabase was not accessed. No credentials or `.env` values were inspected.

## Required resolution

1. Close T010–T012 in their owning task records with required contract decisions, implementation/trace reviews and deferred-scope dispositions, or update the approved dependency/exit contract through the required human review process.
2. Persist T017's clean/upgrade target and exact local-only checkpoint, including V001 checksum/data-preservation evidence and cross-module transaction/lock-order evidence.
3. Re-run `/add-execute --feature=feat-002-appointment-booking --task=T017` after revalidating prerequisites. No grant was issued or consumed in this attempt.

## AI Agent Recommendation

- Status: PENDING HUMAN REVIEW.
- Scope: T017 eligibility only; no code or database state change.
- Recommendation: resolve the named dependency/task-contract gates before dispatching T017.
- Required human decision: resolve T010–T012 task closure/dependency state and approve the T017 local verification checkpoint if it is not already persisted.

## Human Final Review

- Status: PENDING.
- Decision:
- Reviewer:
- Reviewed at:
- Follow-up: re-run T017 preflight after dependency and checkpoint evidence is persisted.
