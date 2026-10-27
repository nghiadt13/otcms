# Task Contract Review — FEAT-002 TASKS v0.3.0

- Date: 2026-10-14 (Asia/Bangkok).
- Request: Owner Long asked to revise `TASKS.md` to resolve reciprocal dependency/gating between T010 and T032 and leave the revision pending human approval.
- Status: APPROVED by Owner Long on 2026-10-14.
- Supersedes: TASKS v0.2.0 for future task preflights. v0.2.0 approval and execution records remain historical evidence.
- Changed artifacts: `backend/sdd/features/feat-002-appointment-booking/TASKS.md` and this review. No source, schema, API, contract registry, plan, or task checkbox was changed.

## Proposed resolution

| Task | Foundation exit proposed | Deferred downstream work |
|---|---|---|
| T010 Service/catalog | Catalog schema/read and snapshot/eligibility contract, Admin authorization/history policy, approved stable owner contract; protected write paths fail closed. | Booking-impacting service deactivation and assignment removal are enabled only by T032 impact checks. |
| T011 Staff/account | Doctor profile/directory/acceptance/schedule foundation, owner read contracts and schema; protected writes fail closed. | Booking-impacting publication/acceptance/account/shift/time-off changes are enabled only with the T014 guard and T032 impact integration. |
| T012 Visit/reception/clinical | Visit/reception/clinical evidence schema and owner read contracts, arrival and link invariants. | Atomic arrival/check-in remains T027; active-care reservation/overrun behavior remains T031. |
| T017 persistence checkpoint | Clean install and upgrade from applied V001 with data preservation, checksum/mapping/FK/constraint evidence, stable owner foundations, transaction/atomicity boundary review, current guard participant inventory and ordered-lock plan. | Runtime cross-module races and future participating writers remain T033/T035 verification; T017 no longer waits for T027/T031/T032. |
| T032 protected-mutation integration | Consume complete T010/T011 foundations, then enforce minimal impact projection and safe resolution behavior after appointment/care capabilities exist. | Remains downstream of T010/T011 and T024–T026/T031. |

This removes the apparent cycle: T010/T011 foundation → T017 → T018→availability and booking/care tasks → T032. T032 has no edge back into T010/T011 foundation completion or T017. T010/T011 must not return success for a withheld protected mutation; until T032 is approved and implemented, affected operations stay unavailable or fail closed.

## Approval boundary and verification

- Current T010/T011/T012/T017 checkboxes remain unchanged. Historical partial statuses and previous blocked preflights are not retroactively rewritten.
- Owner Long approved with `APPROVE TASKS v0.3.0 dependency and exit-criteria revision` on 2026-10-14 (Asia/Bangkok).
- Re-preflight the foundation tasks individually; approval does not itself complete a task or authorize any specific migration/production action.
- TASKS Human Final Review v0.2.0 is retained as historical; TASKS v0.3.0 is now the approved contract for future preflights.
- Reviewed dependency references T010–T018 and T024–T035 for the revised sequence. No automated code/test command was run because this is a task-contract-only change.
- `git diff --check` was run on the changed TASKS file and review artifact and reported no whitespace errors.
- No production/Supabase access, credential inspection, test execution, migration, or endpoint behavior change occurred.

## AI Agent Recommendation

- Status: APPROVED.
- Recommendation: the foundation/deferred-work split is approved for future task preflights.
- Human decision received: `APPROVE TASKS v0.3.0 dependency and exit-criteria revision`.

## Human Final Review

- Status: APPROVED.
- Decision: APPROVE TASKS v0.3.0 dependency and exit-criteria revision.
- Reviewer: Owner Long.
- Reviewed at: 2026-10-14 (Asia/Bangkok; decision received in conversation).
- Follow-up: re-preflight named foundation tasks; approval does not complete tasks or auto-resume blocked attempts.
