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
