# Requirement Traceability — FEAT-002 T011

## TASKS v0.3.0 Foundation Closeout Addendum — 2026-10-14

- Disposition: T011 foundation exit COMPLETED under Owner Long-approved TASKS v0.3.0; protected mutation integration remains assigned to T032.
- Approval basis: `staff-doctor-directory` v1.0.0 and original implementation/trace were approved by Owner Long on 2026-10-13. TASKS v0.3.0, which separates foundation completion from booking-impacting mutation enablement, was approved by Owner Long on 2026-10-14. No implementation/schema changes were made during this closeout.
- Fresh independent alignment review found no concrete defect. Profile provisioning creates only a new profile defaulting to unpublished/not accepting bookings and does not create assignment or appointment links; V006/V008 constraints require those owner relationships before appointment linkage.
- Current verification evidence: prior local suite 93/93 and T011 PostgreSQL integration 3/3; subsequent user-provided suite output 124/124, 0 failures/errors/skips, BUILD SUCCESS at 2026-10-14 01:14:11 +07. No additional tests were run for this documentation-only closeout.

- Date: 2026-10-13 (Asia/Bangkok)
- Task: T011 — Staff-owned Doctor profile and schedule foundation.
- Sources: `CONTEXT.md` D02, D04, D06, D07; Report 3 SRS UC17–UC20 and UC26–UC29, BR-13, BR-22–BR-30; T011 record in `TASKS.md`; approved `staff-doctor-directory` v1.0.0.
- Verification: user-run local `scripts/test-local.ps1`; 93/93 passed. T011 PostgreSQL integration 3/3 passed.

| Requirement | Implementation / evidence | Result | Limitation |
|---|---|---|---|
| Doctor profile is distinct from and linked to an existing Doctor account | V005 `doctor_profiles.account_id` unique FK; provisioning handler validates application account type DOCTOR; integration test | PASS locally | Staff credential invitation/account creation remains outside this slice |
| Public Doctor directory only returns published profiles | Public list/detail filter on `publicly_visible` and `in_active_directory`; DTO excludes account/work-contact/eligibility fields; integration test verifies internal profile is not listed | PASS locally | Publication write command awaits T014 guard |
| Account access, directory visibility and booking acceptance remain separate | Independent profile flags; `StaffDirectoryPort` does not collapse account status into acceptance; locked-account fixture retains its acceptance projection | PASS locally | Authentication behavior remains owned by Auth |
| Doctor and Admin can read permitted shift schedule | Own Doctor endpoint and Admin profile-scoped endpoint with a 90-day bound | PASS locally | PostgreSQL fixture covers persistence schema; full role/range endpoint matrix can expand with later schedule work |
| Shifts do not overlap for one Doctor | V005 half-open interval exclusion constraint; clean V001→V006 migration test | PASS locally | Requires the verified `btree_gist` extension on the target PostgreSQL server |
| Operational time off affects booking eligibility | V005 time-off schema and overlap projection through `StaffDirectoryPort` | PASS locally | Admin write commands await T014/T032 guard and protected impact handling |
| Existing Doctor-service assignment points to an owned Doctor profile | V006 restrictive FK; clean migration constraint test | PASS locally | Existing orphan assignment rows cause migration to fail closed |
| Shift/time-off/profile eligibility mutations preserve protected bookings | Explicitly deferred to shared guard and protected-impact integration | DEFERRED | T014 and T032 required before mutation endpoints are exposed |

## TASKS v0.3.0 exit mapping

| T011 foundation exit criterion | Evidence | Result |
|---|---|---|
| Doctor profile schema/provisioning and valid Doctor identity | V005 `doctor_profiles` account FK/unique; Admin provisioning validates DOCTOR account; integration test | PASS |
| Public directory and privacy boundary | Public query filters published/in-directory; response excludes account/work contact/eligibility; test covers unpublished profile | PASS |
| Separate booking eligibility projections | `StaffDirectoryPort` exposes independent visibility, acceptance, directory, shift coverage and time-off overlap; no conflation with account status | PASS |
| Shift/time-off schema and schedule reads | V005 interval schema/shift exclusion; Doctor-owned and Admin bounded read endpoints | PASS |
| Protected booking-impacting mutations unavailable/fail-closed pending T014/T032 | No publication/acceptance/directory/shift/time-off/account-status write endpoints; T014 guard exists, T032 impact workflow remains downstream | PASS for withholding boundary |

## Human Review

- Status: APPROVED — implementation and requirement trace.
- Decision: `APPROVE implementation and trace T011`.
- Reviewer: Owner Long (as identified by the current user).
- Reviewed at: 2026-10-13 (Asia/Bangkok; decision received in conversation).
