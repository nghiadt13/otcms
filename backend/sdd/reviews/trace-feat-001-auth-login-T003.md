# Requirement Traceability — FEAT-001 T003 Common Auth Foundation

- **Feature:** `feat-001-auth-login`
- **Task:** `T003 — Implement common API error, validation and observability foundation`
- **Spec:** v1.1.0 `APPROVED & LOCKED`
- **Trace scope:** `REQ-AUTH-048..052`, `NFR-AUTH-011`
- **Execution evidence:** `E-feat-001-auth-login-T003-A5`; `.\\mvnw.cmd test` `BUILD SUCCESS`; 1 test, 0 failures, 0 errors, 0 skipped
- **Post-code review:** `.sdd/reviews/post-code-feat-001-auth-login-T003.md` — Human `APPROVED`

## Requirement Traceability Matrix

| Requirement | Plan component | Task | Code evidence | Test status | Coverage/consistency |
|---|---|---|---|---|---|
| `REQ-AUTH-048` | Authentication audit/correlation primitives | T003 | `ErrorResponse`, `CorrelationIdFilter` | `contextLoads` only | `COVERED` foundation; audit emission downstream |
| `REQ-AUTH-049` | Safe identifier/HMAC audit | T003/T006 | No HMAC implementation in T003 boundary | No direct test | `DEFERRED` to owning audit task; not orphaned |
| `REQ-AUTH-050` | Redaction and safe error boundary | T003 | `SensitiveDataRedactor`, nested `ErrorResponse`, `ValidationError` | `contextLoads` only | `COVERED` utility review; `MISSING TEST` for variants |
| `REQ-AUTH-051` | Typed rate-limit configuration | T003 | `AuthProperties` bounded rate-limit records | `contextLoads` only | `COVERED` configuration foundation; enforcement downstream |
| `REQ-AUTH-052` | Abuse-control policy bindings | T003 | login/register/resend/refresh/google and credential-failure defaults | `contextLoads` only | `COVERED` policy foundation; enforcement/concurrency downstream |
| `NFR-AUTH-011` | Structured observability boundary | T003 | correlation response/request propagation and safe error primitives | `contextLoads` only | `COVERED` foundation; `MISSING TEST` for serialization/propagation |

## Impact and Consistency

- T003 introduces a shared/public error envelope and correlation primitive consumed by downstream auth controllers/services; contract sync is required.
- T003 introduces typed auth configuration under `app.auth`; endpoint enforcement remains in downstream tasks.
- The approved post-code review confirms no scope drift and no remaining implementation blocker after A5 Bearer-redaction remediation.
- Dedicated tests for nested error serialization, redaction variants, correlation fallback, validation safety and anti-enumeration are intentionally deferred to T010.
- No Plan/Spec version drift was found. No existing API implementation was present before T003, so no compatibility break was observed.

## Verification and Gaps

- **Exact command:** `.\\mvnw.cmd test`
- **Result:** `BUILD SUCCESS`; 1 test, 0 failures, 0 errors, 0 skipped.
- **MISSING TEST:** Only `contextLoads` executes; dedicated contract/security tests remain required in T010.
- **Trace status:** foundation coverage is present; delivery still depends on T010 follow-up and shared-contract sync.

## AI Agent Recommendation

- **Status:** `PENDING HUMAN REVIEW`
- **Scope:** T003 REQ/NFR traceability and shared error/config contract impact
- **Recommendation:** Accept foundation-level trace coverage, retain T010 contract/redaction/anti-enumeration tests as mandatory follow-up, and sync the new shared error/correlation/config contract before starting downstream consumers.
- **Evidence:** A5 execution record, A5 review PASS, approved post-code review, exact Maven result and current source boundary.
- **Risks and assumptions:** Runtime enforcement of rate limits, structured audit/HMAC and duration logging remains downstream; current test suite does not directly exercise T003 contracts.
- **Alternatives considered:** Marking T003 fully tested was rejected because dedicated tests are absent; moving audit/enforcement behavior into T003 was rejected because it exceeds the approved boundary.
- **Required human decision:** Authorized Human reviewer must approve/revise/reject this T003 trace and its T010 test follow-up.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T003 trace with foundation-level coverage; retain contract and anti-enumeration tests as T010 follow-up.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30T00:33:10.0796830+07:00
- **Follow-up:** Continue T004 and rerun trace if the shared contract changes.
