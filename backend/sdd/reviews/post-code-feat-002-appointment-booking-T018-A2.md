# Post-code review — FEAT-002 T018 A2

- Status: complete; independent review passed after fixture corrections and follow-up constructor fix; post-fix local suite passed 135/135.
- Scope: reusable slot engine, bounded owner read projections, no HTTP endpoint/schema migration/production DB change.
- Requirements: TASKS.md T018 v0.3.0; SPEC REQ-BOOK-007–015; CONTEXT §4.3–4.4; approved PLAN Phase 3.
- Implementation: `appointment/domain`, `appointment/application`, `appointment/adapter/out/persistence`, additive `StaffScheduleAvailabilityPort` and `VisitCareReservationQueryPort`, Staff and Visit persistence adapters.
- Tests: initial run had 135 tests, 0 failures, 52 errors because Spring could not select `GenerateAvailableSlotsHandler`'s constructor. The production constructor now has `@Autowired`. Post-fix local suite passed 135 tests, 0 failures, 0 errors, 0 skipped in 1:12; `VisitT012PostgresIntegrationTest` passed 3/3.
- Shared contract: `appointment-slot-availability-read-projections` v1.0.0 in `shared_context.md`; Owner Long approved it for T019/T020 consumption.

## Implementation plan and boundary

The domain engine resolves inclusive clinic-local dates into half-open UTC bounds, limits the query to `maxHorizonDays + 1` dates, validates the configured grid without rounding, and generates local wall-clock starts using valid zone offsets. It checks consultation plus buffer against one shift and against half-open time-off, occupied appointment, and active-care intervals. Current-service context carries current duration/buffer; appointment-snapshot context carries persisted values and excludes only that appointment. A self-linked care reservation is excluded only when both linked appointment and patient IDs match.

Staff owns bounded shift/time-off reads; Visit owns bounded RESERVED/IN_PROGRESS care projections; Appointment owns bounded occupied appointment reads. Query ranges and Doctor IDs are explicit. No adapter reads another feature's repository/entity/table. The public availability endpoints, notice/horizon eligibility, actor authorization, reschedule/reassignment policies, and command-time atomic rechecks remain T019/T020/T022 work.

## Review checklist

- Grid anchoring, offset equivalence, seconds/fraction rejection, inclusive dates, DST transitions, and horizon-bounded date queries.
- Half-open interval edges, exact shift fit, time-off and care conflicts, current service vs stored snapshot behavior, and self-exclusion.
- Same-owner persistence boundaries, active statuses, query range/Doctor bounds, and integration-contract dependency direction.
- Test fixtures use local `otcms_test_db` only and remove their own rows.

## Findings

- Blocking findings resolved before verification: PostgreSQL fixture now inserts the required `APPOINTMENT_CHECK_IN` evidence and groups visit creation, reception evidence, and appointment linking in one transaction so V008/V010 deferred constraints observe a consistent final state. Cleanup resets the appointment link/status before deleting care, reception, and visit rows.
- Independent review: no remaining blocking findings. Reviewer confirmed the fixture FK/trigger semantics and cleanup order; domain interval filtering, DST handling, self-exclusion, bounded owner queries, and feature ownership boundaries were also reviewed.
- Follow-up from user run: Spring reported `BeanCreationException` for `GenerateAvailableSlotsHandler` (`No default constructor found`) because it has a production constructor and a test constructor. Added `@Autowired` to select the production constructor. Follow-up review passed; post-fix suite then passed 135/135.
- Follow-up review: passed. Reviewer confirmed Spring will use the annotated public production constructor, while the package-private engine-injecting constructor remains available to same-package unit tests.
