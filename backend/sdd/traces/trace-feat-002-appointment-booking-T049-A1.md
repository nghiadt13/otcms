# FEAT-002 T049 Requirement Trace — A1

- Status: COMPLETE — scoped requirements, security review and local PostgreSQL verification pass.
- Date: 2026-10-28 (Asia/Bangkok)
- Scope: T049; REQ-RECEPTION-006/007; AC29/AC30. T053 owns the pending scheduled-arrival worklist reused by this task. T052 retains consolidated UC-to-AC and full API parity review.
- Inputs: FEAT-002 SPEC, PLAN, TASKS, API-CONTRACT and OpenAPI; Visit/Patient public service boundaries; changed Visit read API and PostgreSQL integration tests; independent T049 code review; user-provided local test log.

## Requirement Traceability Matrix

| Requirement / acceptance | Implementation | Verification | Result |
|---|---|---|---|
| REQ-RECEPTION-006 / AC29 — Receptionist reads active walk-ins FIFO with server pagination and clinic-day scope; scheduled arrivals excluded | `GET /api/v1/reception-queue` in `VisitReceptionReadController`; Visit-owned query in `VisitReceptionReadRepository.findQueue`; sort `(arrived_at,id)`, bounded page size, Visit `source='WALK_IN'`; active assignment/room reservation projection. Pending scheduled arrivals continue through T053 `GET /api/v1/appointment-arrivals`. | `VisitReceptionReadT049PostgresIntegrationTest` verifies Receptionist authorization, invalid page size, FIFO/page metadata and room projection; local PostgreSQL full suite includes 3/3 T049 tests. | PASS |
| REQ-RECEPTION-007 / AC30 — Patient reads only an authorized Visit's current stage/status; queue position only for active WALK_IN; other-profile and accountless access denied | `GET /api/v1/visits/{visitId}/progress`; Patient authorization delegates to Patient-owned `AuthorizePatientProfileService`; Visit-owned read projection. Position lookup requires the current entry and preceding entries to be active WAITING WALK_IN records in the clinic-day bounds. | `VisitReceptionReadT049PostgresIntegrationTest` verifies own progress, cross-profile/accountless denial and queue position removal after leaving reception. Latest local PostgreSQL run includes T049 3/3. | PASS |
| Current room and stage | Both reads use active Visit assignment and room-reservation projections; room code is absent without an active reservation. | T049 integration test checks active room projection and current progress stage. | PASS |
| API contract | API-CONTRACT and OpenAPI define both T049 endpoints, role scope, inputs and response DTOs. No schema or migration change. | Static review against controller/DTO behavior; consolidated UC-to-AC and static/runtime parity remain T052 ownership. | PASS for T049 scope |

## Verification evidence

- User-provided `backend/scripts/test-local.ps1` output finished 2026-10-28 11:33:49 +07 on local `otcms_test_db`: BUILD SUCCESS; 314 tests, 0 failures, 0 errors, 0 skipped. `VisitReceptionReadT049PostgresIntegrationTest` passed 3/3.
- The 11:33:49 run occurred after the final defensive `WALK_IN` predicate was added to `findPosition`; it supersedes the earlier 11:27:23 run.
- `backend/./mvnw.cmd clean package -DskipTests` also passed after the query correction, compiling 423 production and 90 test sources. This is compile evidence only; the test-local run above supplies runtime evidence.
- No production or shared/Supabase database was accessed.

## Scope limits

- T053's scheduled-arrival pending list is reused, not reimplemented by T049.
- T052 still owns complete feature UC-to-AC trace, OpenAPI/runtime parity across T046–T051, and final feature-level QA/review. T049 completion does not close T052.

## Recommendation

Approve T049 completion. The scoped requirements are mapped to implementation and passing PostgreSQL integration evidence; independent review findings were corrected and re-reviewed without remaining blockers.
