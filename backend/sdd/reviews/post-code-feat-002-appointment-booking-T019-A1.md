# Post-code review — FEAT-002 T019 A1

- Status: implementation and verification complete; independent review passed; final Owner Long approval pending.
- Requirement/task: approved SPEC v0.8.0 §4.1–4.2 and REQ-BOOK-001–004/043/049; TASKS v0.3.0 T019; approved PLAN v0.4.0 Phase 3.
- Scope: public/staff availability GET endpoints, actor authorization, eligibility and date/time filters, reuse T010 catalog and T018 slot engine; additive read-only Staff and Service/Catalog ports. Rate limiting stays in T021; reschedule/reassignment stays in T020.
- New cross-owner contract: draft `appointment-availability-provider-queries` v1.0.0 in `shared_context.md`; approval is required before downstream freeze.
- Tests added: `GetAppointmentAvailabilityHandlerTest` and `AppointmentAvailabilityT019PostgresIntegrationTest`; first local suite run reported failures described below; fixes have not yet been rerun.

## Implementation summary

The Appointment use case obtains the active service through `ServiceCatalogPort`, intersects Staff's active-directory/accepting provider projection with Service/Catalog active-assignment IDs, and applies public visibility only for public requests. Public requests enforce clinic-local `today..today+maxHorizonDays` and minimum notice; staff requests enforce the same horizon and non-past starts without the public notice. Explicit ineligible provider/service requests fail with stable errors; empty results return `{slots:[]}`. Public route is permitted without authentication; staff route is restricted to Receptionist, Doctor and Admin and is read-only. Slot DTO exposes only service/provider identifiers, Doctor display name and UTC times.

## Acceptance checklist

- Public catalog response remains the existing T010 `{items:[]}` contract; staff catalog remains restricted to Receptionist/Doctor.
- Public availability uses active online-bookable service, active assignment, active-directory accepting Doctor, public visibility and online notice.
- Staff availability uses active service, active assignment, active-directory accepting Doctor and non-past start; hidden-but-active Doctors are visible to staff; Admin has GET-only inspect access.
- Missing/malformed/reversed/oversized dates map to `VALIDATION_FAILED`; requested dates outside clinic horizon map to `BOOKING_WINDOW_VIOLATION`.
- Empty availability is 200; explicit ineligible Doctor is `DOCTOR_NOT_ELIGIBLE`; no PII/contact/unavailability reasons are returned.
- No cross-feature repository or entity imports; owner adapters read only owned tables.

## Independent review

Independent review passed. Reviewer confirmed T010 already provides the required public/staff catalog endpoints and active/online filtering; the previous concern that those endpoints were absent was a false positive. Reviewer also confirmed T010 tests cover filtering; the empty list envelope is implemented by the existing `{items:[]}` response wrapper.

## First local run and corrective changes

The user-provided run reported 146 tests, with 1 failure and 7 errors. The T019-specific causes were:

- The unit test's mocked slot engine returned a hidden Doctor slot for a public query. The handler trusted the engine to honor its input and dereferenced a missing provider. The handler now filters generated slots against its eligible-provider projection before constructing responses.
- The unit test labeled `REQUEST_DATE.minusDays(1)` outside the horizon, but that date was clinic-local today. The test now uses two days before `REQUEST_DATE`.
- The PostgreSQL integration test registered a fixed `Clock` alongside the production `businessClock`, leaving constructor injection ambiguous. Its fixed test clock is now `@Primary`.

The repeated integration context errors were Spring's failure-threshold cascade after the ambiguous `Clock` prevented the first context load. The fixed test clock is now `@Primary`.

The next Owner-provided run confirmed the `Clock` fix resolved context startup and reported 146 tests with 3 failures and 0 errors. Those failures were test expectation issues: two integration assertions used Hamcrest `contains` for multi-slot results, and the unit test's fixed instant made both mocked slots more than the configured 120-minute notice away. The assertions were changed to membership checks (and explicitly exclude ineligible Doctors); the unit clock moved to `2026-10-15T05:00Z` so the 06:45 slot is inside the notice and 07:00 is exactly at the inclusive boundary.

The latest Owner-provided local run passed: 146 tests, 0 failures, 0 errors, 0 skipped, total 1:13, finished 2026-10-14 16:33:23 +07:00. `VisitT012PostgresIntegrationTest` passed 3/3. This is user-provided test evidence; the agent did not launch that run.

## AI Agent Recommendation

- Status: APPROVED BY OWNER LONG
- Recommendation: Owner Long approved T019 implementation, trace, and additive provider-query contract; contract is frozen.
- Evidence: independent code review; SPEC/PLAN trace; latest Owner-provided local suite passed 146/146 (0 failures/errors/skips), including the 3/3 Visit T012 integration tests.
- Risks: new shared query contract remains draft until Owner Long approves it; rate limiting is separately assigned to T021.
- Required human decision: None; implementation and trace are approved and the additive provider-query contract is frozen.

## Human Final Review

- Status: APPROVED
- Decision: `APPROVE implementation and trace T019; approve and freeze the provider-query contract`.
- Reviewer: Owner Long
- Reviewed at: 2026-10-14 (Asia/Bangkok)
- Follow-up: None.
