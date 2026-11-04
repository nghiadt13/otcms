# KAN-37 — Admin / Imaging integration

## Record location and applicable instructions

Root `AGENTS.md` and `frontend/AGENTS.md` apply to this frontend change. Neither
prescribes a Shadow Plan / Action Record path or template. `backend/AGENTS.md`
requires scope/file boundary, exact command, checkpoint, result, residual blocker
and sync-back decision; its referenced `.claude/skills/_shared/ai-review-protocol.md`
is absent from this checkout. No backend execution or `/add-execute` is invoked.
This workspace-local document follows the existing `docs/password-recovery-integration.md`
Action Record convention and records those fields without claiming a missing template
or a runtime grant. No existing KAN-37 record was found in the checkout.

## Shadow Plan

- Branch: `feat/hung/KAN-37-my-account-live`.
- READ: applicable AGENTS, local Next client-component guide, Admin account route,
  Imaging profile routes/screen, imaging-shell, preview storage/state, existing
  my-account hook/avatar mutation and HTTP-boundary tests, frontend scripts and UI rules.
- CREATE: this evidence document; imaging-shell account composition and focused tests.
- MODIFY: Imaging profile screen and its two route imports; shell profile data banner.
- Preserve all pre-existing working tree changes. Verify Admin uses live
  `MyAccountProfile` → `useMyAccount` → `GET /api/v1/me`; leave it unchanged.
- Compose `useMyAccount` and existing `AvatarEditor` in imaging-shell. Keep Imaging
  presentation independent of my-account imports. Remove fabricated account fields.
- No preview state/storage changes, patient-shell, shared auth/session/API, backend,
  medical-history, main workflow, dependencies or public contract changes.
- During implementation: `npm test -- src/features/imaging-shell/components/ImagingAccountProfile.test.tsx`.
- Final verification, once in this order, cwd `frontend`: same focused test;
  `npm test -- src/features/my-account`; `npm test -- src/features/patient-profiles`;
  `npx tsc --noEmit`; `npm run lint`; `npm test`; `npm run build`.
  Then cwd repository: `git diff --check`; `git status --short`.
- Keep the user-reported 8 baseline failures and 15 consumer failures separate;
  record observed names/evidence and any additional failures. Their earlier exact
  names/logs are not present in this checkout, so identity cannot be assumed by count.

## Human checkpoint

- Human identity/authority: current user, explicitly self-identified Human owner.
- Decision: APPROVED, supplied directly by Human in the current continuation request.
- Recorded at: 2026-11-03 17:52:21 UTC (2026-11-04 00:52:21 Asia/Bangkok).
- Exact authorization: **CHO PHÉP tiếp tục giai đoạn tích hợp Admin/Imaging đúng phạm vi ticket KAN-37 đã thống nhất. Không mở rộng phạm vi.**
- Boundary: the three account routes and existing account mutations described in
  the request. No commit, push, rebase or merge. This persists the Human instruction;
  it does not represent agent self-approval or approval of later scope changes.

## Action Record / Execution Record

- Approved scope/file boundary: Shadow Plan above; existing KAN-37 changes are inputs.
- Execution route: current agent, direct frontend work; no delegation or host-level
  execution-grant enforcement claimed.
- Checkpoint: Human checkpoint above, persisted before production edits.
- Exact commands: Shadow Plan above; read-only inspection used PowerShell
  `Get-Content`, `rg`, `git branch --show-current`, `git status --short`.
- Result: scoped implementation complete; final verification below.
- Residual blocker: none for scoped implementation. Backend review protocol file is
  unavailable and is not an applicable frontend execution prerequisite.
- Sync-back decision: update this record with final verification; no backend SDD or
  architecture synchronization because feature boundaries and contracts are unchanged.

## Execution result — 2026-11-04 Asia/Bangkok

- Admin verified unchanged: `/admin/account` renders `MyAccountProfile`, which calls
  live `useMyAccount` / `getMyAccount` (`GET /api/v1/me`). Its password adapter remains
  outside this integration stage, as already documented in existing my-account code.
- Both Imaging routes now compose live account data via `ImagingAccountProfile`
  in imaging-shell. `ProfileScreen` accepts presentation props without importing
  my-account. `/edit` reuses `AvatarEditor` and its PUT multipart `photo`, DELETE,
  account events and authoritative refetch. No new mutation implementation.
- Removed fabricated employee code/work contact and preview-profile reads/writes
  from these screens. Role, username, display name, status and allowed actions come
  from `/me`; protected images use the existing authenticated blob lifecycle.
- Loading, error/retry, avatar error and permission states are supported. Editing
  saves through the existing editor's “Lưu ảnh” / “Gỡ ảnh” buttons; return navigation
  does not save preview data. Profile banner labels the account data as live.
- Modified production files only: the two Imaging profile route files,
  `imaging/components/ProfileScreen.tsx`, `imaging-shell/components/ImagingShell.tsx`;
  added `imaging-shell/components/ImagingAccountProfile.tsx` and adjacent tests.
- All earlier dirty my-account/patient-profiles/message files were preserved as inputs.
  No preview storage/state, patient-shell, auth/session, API client, backend,
  medical-history, main workflow, dependency or contract edits in this continuation.

### Exact verification evidence

Initial focused run hit sandbox `esbuild` directory access denial before tests.
The same command rerun with approved escalation passed 9/9. This is an environment
startup failure, not a product failure. Scoped Prettier command before the tests:
`npx prettier --write src/features/imaging/components/ProfileScreen.tsx src/features/imaging-shell/components/ImagingAccountProfile.tsx src/features/imaging-shell/components/ImagingAccountProfile.test.tsx`.

The final round ran once, sequentially, cwd `frontend`:

| Step | Exact command | Result |
| --- | --- | --- |
| 1 | `npm test -- src/features/imaging-shell/components/ImagingAccountProfile.test.tsx` | PASS: 9 tests, 1 file |
| 2 | `npm test -- src/features/my-account` | PASS: 207 tests, 15 files |
| 3 | `npm test -- src/features/patient-profiles` | PASS: 148 tests, 15 files |
| 4 | `npx tsc --noEmit` | PASS, exit 0 |
| 5 | `npm run lint` | PASS, exit 0 |
| 6 | `npm test` | FAIL: 23 failed, 1456 passed; 5 failed / 207 passed files; 144.59s |
| 7 | `npm run build` | PASS, exit 0; both Imaging profile routes and Admin account generated |

Full-test output was captured without rerunning the suite using:

```powershell
$kan37TestLog = Join-Path $env:TEMP 'kan37-full-npm-test.log'
npm test *> $kan37TestLog
$kan37TestExit = $LASTEXITCODE
Get-Content -Encoding UTF8 $kan37TestLog -Tail 120
exit $kan37TestExit
```

### Failure groups kept separate

Observed distribution matches the Human-reported 8 baseline + 15 consumer failures.
Earlier exact baseline logs were unavailable; the classification uses the observed
suite boundaries below rather than claiming an independent before/after baseline run.

| Group | Test file | Failures | Observed cause |
| --- | --- | --- | --- |
| Baseline (8 total) | `features/patient-reception/components/ReceptionScreen.test.tsx` | 7 | Missing expected patients, patient combobox and invalid-context alerts |
| Baseline (8 total) | `features/accounts/components/AccountManagement.test.tsx` | 1 | Missing patient “Vô hiệu hóa” menu item |
| Consumer (15 total) | `features/admin-shell/components/AdminAccountMenu.test.tsx` | 4 | Existing authSession mock lacks `peekSession` used by live account hook |
| Consumer (15 total) | `app/(patient)/portal/account/_personal/PersonalInfoScreen.test.tsx` | 10 | Old consumer setup reaches account-load error rather than expected profile form/email/empty state |
| Consumer (15 total) | `app/(patient)/portal/_area/PortalAreaFrame.test.tsx` | 1 | Missing expected profile demographic text |

No additional failing tests beyond those 23 were observed; new Imaging tests pass
both focused and in the full suite. The focused scenarios cover live projection,
multipart upload/delete/refetch, protected avatar/revocation, failed load/retry,
forced-password/missing action restrictions, upload failure, access denial and stale
unmounted requests. Each asserts the entire preview-storage string stays unchanged.
HTTP is mocked at `apiRequest` / `fetchBlob`; hooks and mutation implementations are
real. This is not evidence of a live backend/browser end-to-end run.

Residual blocker for a fully green repository suite: the separated baseline and
consumer failures above. No scope expansion to repair them; no commit/push/rebase/merge.
Final repository check: `git diff --check` PASS, exit 0. Git reports an existing
CRLF-to-LF normalization warning for `frontend/src/lib/messages/appMessages.ts`;
no whitespace errors. `git status --short` is the final read-only operation;
the working tree intentionally retains the earlier KAN-37 edits and this continuation.

## Continuation — consumer regressions and header acceptance, 2026-11-04

This section supersedes the earlier residual-blocker assessment. No commit, push,
reset, restore, rebase, merge or pull was performed in this continuation.

### Entry checkpoint and scope

- Confirmed branch `feat/hung/KAN-37-my-account-live`; initial `git status --short`
  was empty. HEAD was `fa9f3459a3635007656b4de5f897d58acacad460`
  (`feat(my-account): integrate live account and patient profile APIs`).
- `git log --oneline --left-right origin/product...HEAD` showed only `> fa9f345`:
  one commit ahead, no divergent origin/product commits in the local remote ref.
  No fetch/pull was performed, so this describes the locally available ref.
- Read root/frontend AGENTS and the existing implementation, tests and this record;
  no deeper AGENTS files apply. Read the installed Next client-component guide.
- Current user authorizes consumer test setup repairs and runtime repairs in the
  owning KAN-37 feature. Patient-shell, shared account cache, backend contracts and
  unconfirmed administrative validation remain outside this continuation's scope.

### Changes and consumer evidence

- Updated only HTTP/session fixtures in `AdminAccountMenu.test.tsx`,
  `PortalAreaFrame.test.tsx` and `PersonalInfoScreen.test.tsx`: real hooks and
  mutations now receive `/api/v1/me` and own-profile DTOs through `apiRequest`.
  Unexpected HTTP paths throw; no production fallback or additional cache was added.
- Kept the password dialog, logout, demographic, profile, missing-profile and
  ownership assertions. Corrected the obsolete OTP expectation MSG-08 to MSG-82
  as explicitly required by this continuation. Asserted that the official email
  stays unchanged after requesting OTP; pending email text in the dialog is not
  treated as the official email.
- The rejected-email scenario explicitly simulates a backend 400/MSG-08; it does
  not assert that the live backend reveals whether an address exists. Existing
  my-account tests retain coverage for suppressed requests accepted with 202.
- Found and fixed one runtime regression in the owning feature:
  `patient-profiles/components/PersonalInfoForm.tsx` used MSG-90 after updating
  personal account information. Restored MSG-05, as specified by the account-screen
  document `docs/fe-be-api-patient-personal-info.md` section 2.4 and the retained
  consumer assertion. No validation, write-version or retry behavior changed.
- Initial focused result: **15 FAIL / 1 PASS** (Admin 4, Portal 1, Personal 10).
  After repair: **1 FAIL / 15 PASS** (Admin 4/4, Portal 2/2, Personal 9/10).
  Therefore **14 of the original 15 regressions resolved; 1 remains blocked**.

### Remaining profile blocker — retained failing assertion

`PersonalInfoScreen.test.tsx` → “hồ sơ đã có lượt khám: họ tên và ngày sinh khóa,
nói rõ một lần; trường khác sửa được” still fails: fullName has no `readonly`.
The live `OwnPatientProfile` DTO and `ownPatientProfileApi.mapProfile` carry neither
`hasVisits` nor `editableFields`. Passing `hasVisits: true` in the HTTP fixture is
intentionally insufficient: the live mapper drops it, proving the old mock store
cannot supply this rule anymore. The unlocked/no-visits case passes but does not
prove that the application distinguishes visit history.

The retained requirement is described in `docs/fe-be-api-patient-personal-info.md`
sections 1/2.3. UC03/UC04 files are absent from `docs/requirements`; this continuation
cannot confirm R3 or infer a new backend permission contract. Resolution requires
the team to confirm the field-editability rule and the backend to provide the
authoritative permission/visit-history projection (and enforce it on writes).
Then update `patient-profiles/types/ownPatientProfile.ts`,
`patient-profiles/api/ownPatientProfileApi.ts` and
`patient-profiles/components/PersonalInfoForm.tsx`, with their tests, to preserve
and render those permissions. No guessed locking rule, validation change, skipped
test or relaxed assertion was introduced. This is **not** one of the 8 baseline failures.

### Avatar header acceptance — BLOCKED

The patient header has not been synchronized indirectly:

- `frontend/src/features/patient-shell/components/PatientAccountMenu.tsx` only
  renders `AvatarFallback`; it accepts no avatar source.
- `frontend/src/features/patient-shell/components/PatientPortalHeader.tsx` uses
  `useCurrentAccount` and passes only display name/email/role/links.
- `frontend/src/hooks/useCurrentAccount.ts` does not subscribe to account-change
  events. `frontend/src/lib/auth/currentAccountApi.ts` has an existing account
  cache whose projection lacks avatarUrl and whose invalidation is not wired to
  KAN-37 mutations.

Minimum proposed change along the existing header data path: extend the existing
`CurrentAccount` projection/cache to retain avatarUrl and invalidate/refetch on
successful account changes with session-safe handling; update `useCurrentAccount`
to react to those changes; obtain an authenticated avatar object URL with proper
abort/revoke/session handling in `PatientPortalHeader`; pass it into an
`AvatarImage` in `PatientAccountMenu`. These four production files and their
adjacent tests require expanded authorization. The lifecycle helper can be shared
only under an explicitly approved dependency change; do not import my-account
directly into patient-shell (it is not a COMPOSITION_FEATURES entry).
No files on this header path were edited, and no localStorage/base64 or second
account cache workaround was introduced. The header acceptance criterion is **BLOCKED**.

### Verification commands and results

All commands below run from `frontend` unless otherwise stated. The initial
consumer command first encountered the same esbuild sandbox directory denial;
the identical command was rerun with escalation, then showed the 15 actual test
failures. Development used only focused consumer reruns and scoped Prettier.
Full test, TypeScript, lint and build are each run once in the final round.

Consumer command (initial, development and final):
`npm test -- src/features/admin-shell/components/AdminAccountMenu.test.tsx 'src/app/(patient)/portal/_area/PortalAreaFrame.test.tsx' 'src/app/(patient)/portal/account/_personal/PersonalInfoScreen.test.tsx'`.

The final round proceeds despite the proven consumer blocker, to establish the
remaining PR gate results without skipping or weakening that test.

| Step | Command | Result |
| --- | --- | --- |
| 1 | Consumer command above | 15 PASS / 1 FAIL; 2 passed / 1 failed files |
| 2 | `npm test -- src/features/my-account` | 207 PASS, 15 files |
| 3 | `npm test -- src/features/patient-profiles` | 148 PASS, 15 files; includes Medical History KAN-36 |
| 4 | `npm test -- src/features/imaging-shell src/features/imaging` | 24 PASS, 5 files |
| 5 | `npx tsc --noEmit` | PASS, exit 0 |
| 6 | `npm run lint` | PASS, exit 0 |
| 7 | `npm test` | 1468 PASS / 11 FAIL; 208 passed / 4 failed files; 137.36s |
| 8–10 | Build, repository diff/status | Results recorded below |

Preserved regression tests cover old email until verification, wrong/expired OTP
MSG-82, protected avatar object-URL revocation, stale account/session responses,
own-profile version and STALE_VERSION without automatic write retry. All existing
my-account and patient-profiles tests passed. Imaging tests assert the entire
preview-storage string remains unchanged; no Imaging preview state was edited.

### Manual checklist before PR (not executed against live backend)

- [ ] Sign in as Admin, patient and Imaging technician; verify own `/me` data and
  actor restrictions on account screens, including forced-password state.
- [ ] Change/remove avatar, reload, navigate between routes and sign out/in;
  verify protected image requests and image replacement. Header acceptance must
  be implemented and verified after scope approval.
- [ ] Request new email; old email remains authoritative. Wrong/expired OTP shows
  MSG-82; correct verification changes email; suppressed requests do not disclose
  address existence. Check account/session changes during pending requests.
- [ ] Save own profile with its server version; provoke concurrent modification,
  preserve draft on STALE_VERSION, reload explicitly and avoid automatic overwrite.
  Resolve and verify visit-based field editability with the team/backend first.
- [ ] Confirm MSG-05 on successful personal-account save; check unchanged local
  administrative validation, missing-profile state and staff access rejection.
- [ ] Check KAN-36 Medical History read/edit and verify Imaging workflow preview
  data survives profile navigation, avatar mutations and reload.

PR status: **NOT READY** until the profile-editability blocker and avatar-header
acceptance blocker are resolved. Baseline failures remain separately tracked.

### Full-suite reconciliation and targeted follow-up

The single full run contains these failures; none of the consumer or newly
observed timing failures are reclassified as baseline:

| Classification | Suite / scenario | Failures |
| --- | --- | --- |
| Previously verified baseline | `ReceptionScreen.test.tsx` | 7 |
| Previously verified baseline | `AccountManagement.test.tsx` | 1 |
| Retained KAN-37 blocker | `PersonalInfoScreen.test.tsx`: visit-based field locking | 1 |
| Additional timing failure | `PersonalInfoScreen.test.tsx`: “các trường theo đặc tả” | 1 |
| Additional timing failure | `PatientProfilesScreen.test.tsx`: UUID history entry with administrative list failure | 1 |

The PersonalInfo failure was at `getByText("an@example.com")`: own-profile form
readiness does not imply the independently loaded AccountEmailField is ready.
Changed that assertion to `await findByText` without changing its expected content.
The PatientProfilesScreen failure was at `getByText("Không tải được hồ sơ.")`
while its administrative list still rendered “Đang tải hồ sơ”; the independently
loaded history link already had the correct UUID. No KAN-36 source/test was edited.
This is evidence of an asynchronous assertion race, not proof of a new runtime
regression or an independently verified baseline failure.

After the full-run evidence, ran one diagnostic focused command:
`npm test -- src/features/admin-shell/components/AdminAccountMenu.test.tsx 'src/app/(patient)/portal/_area/PortalAreaFrame.test.tsx' 'src/app/(patient)/portal/account/_personal/PersonalInfoScreen.test.tsx' src/features/patient-profiles/components/PatientProfilesScreen.test.tsx`.
Result: **21 PASS / 1 FAIL**, 3 passed / 1 failed files. The only remaining failure
is the retained visit-lock assertion; PatientProfilesScreen passed 6/6 unchanged.
Consumer result remains **15 PASS / 1 FAIL**, or **14/15 original regressions
resolved**. The full suite was deliberately not repeated, honoring the user's
single-full-run limit. Thus a clean full-suite result outside the 8 baseline
failures is **not established**; the additional KAN-36 timing failure remains a
verification blocker. TypeScript/lint passed before the final assertion-only
change from getByText to awaited findByText; neither was repeated.

Logs retained under the system temporary directory:
`kan37-consumers-initial.log`, `kan37-consumers-focused.log`,
`kan37-final-consumers.log`, `kan37-final-my-account.log`,
`kan37-final-patient-profiles.log`, `kan37-final-imaging.log`,
`kan37-final-typescript.log`, `kan37-final-lint.log`, `kan37-final-full.log`,
`kan37-post-full-focused.log`, `kan37-final-build.log`.

Final gates: `npm run build` **PASS**, exit 0 (one run, after the final fixture
timing correction). Repository `git diff --check` **PASS**, exit 0. Final
`git status --short` contains only the five intentionally modified files:
this record, the three consumer test suites and `PersonalInfoForm.tsx`; no new
files. No runtime regression introduced by this continuation was identified,
but the full-suite goal is not met: the profile blocker and additional timing
failures cannot be counted among the 8 verified baseline failures.

## Human decision update — editable-field rule and patient avatar header

The Human's latest decision supersedes the previous tentative blocker on profile
editability: KAN-37/UC03/UC04/BR-12 and KAN-22 do not establish the old
`hasVisits => lock fullName/dateOfBirth` rule. No `editableFields`, `hasVisits`,
backend changes, appointment/visit imports or inferred field permissions are
being added. Updated the consumer test to assert fields remain editable according
to the current own-profile contract and retained the other form validations.

For avatar composition, the app layer owns the join: new
`src/app/(patient)/portal/PatientPortalHeaderWithAccount.tsx` reads account state
through `useMyAccount`, reuses `useAccountAvatar` for authenticated fetch,
session guard and object URL cleanup, then passes `avatarSrc` to
`patient-shell/PatientPortalHeader` and `PatientAccountMenu`. The layout now
renders this app composition component. Patient-shell only renders the passed
image with initials fallback; it does not import my-account. This introduces no
new cache, browser storage or shared API/auth changes.

Added a patient-header test for passed image and delete-to-initials behavior.
Existing `useAccountAvatar` tests cover authenticated upload projection refresh,
delete projection removal, reload from account data, account/session switching,
late responses, abort and URL cleanup. Focused and final results for this decision
update will be appended after verification.

### Verification — latest Human decision

- Patient header + account avatar/session focused: PASS, 70/70 tests across
  `PatientPortalHeader.test.tsx`, `useAccountAvatar.test.tsx`,
  `useMyAccount.test.tsx`, `AvatarEditor.test.tsx` and the personal info consumer.
- Consumer suites: PASS, 16/16 tests across the three previously failing suites.
- My-account: PASS, 207/207; patient-profiles: PASS, 148/148;
  Imaging: PASS, 24/24.
- TypeScript: PASS. Lint: PASS with warnings only in the test's mocked avatar
  image element. Full test: 1443 passed, 8 failed (1451 total; 200 passed / 2
  failed files). The exact 8 are the previously verified baseline:
  `ReceptionScreen.test.tsx` 7 and `AccountManagement.test.tsx` 1. Vitest also
  logged a worker termination timeout while running the existing long suite;
  no new KAN-37 consumer failures appeared.
- Build: PASS. `git diff --check`: PASS. Final status lists the prior continuation
  files plus the patient app composition, portal layout, PatientPortalHeader,
  PatientAccountMenu and header test. Backend was unchanged, so `./mvnw verify`
  was not run.
- Manual checklist before PR: exercise upload, delete and reload as a patient;
  verify header image changes immediately, initials return after delete, and
  signing out/in as a second account never shows the prior account's avatar.
  Also manually confirm the protected avatar request uses the active session.

KAN-37 status after this update: consumer regression is resolved, no
`editableFields` contract was added, avatar is composed through app/patient
layout, and the full frontend suite has only the 8 known baseline failures.
