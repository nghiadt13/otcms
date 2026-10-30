# Post-code review — FEAT-002 T021 A1

- Status: APPROVED — Owner Long approved T021 implementation and trace and accepted the recorded missing task-grant evidence as a process exception on 2026-10-14 (Asia/Bangkok).
- Requirement/task: SPEC v0.8.0 REQ-BOOK-044 and AC16; TASKS v0.3.0 T021; approved PLAN v0.4.0 Phase 3.
- Scope: trusted proxy client-IP resolution and independent process-local fixed-window limits for the public clinic-service catalog and public appointment availability. No database/schema change or distributed limiter.

## Implementation

`TrustedProxyIpResolver` uses the peer address unless that peer matches configured trusted proxy CIDRs. It then parses `Forwarded` or `X-Forwarded-For` and walks the chain from the proxy side to the nearest untrusted client. Invalid header values fall back to the peer; invalid configured CIDRs fail startup. IPv4, IPv6, CIDR and IPv4-mapped IPv6 inputs are normalized before comparison.

`PublicRequestRateLimiter` keys independent catalog and availability buckets by client IP and fixed window. It uses the injected `Clock`, atomically admits no more than the configured request count, and computes `Retry-After` with ceiling seconds and a minimum of one. Cleanup runs every 16 requests and inspects at most 64 map entries per pass, so scan progress exceeds the maximum one new IP bucket per request. It uses no separate per-request expiry queue, avoiding unbounded cleanup metadata under distinct-IP churn.

`PublicRequestRateLimitInterceptor` limits only GET/HEAD on `/api/v1/clinic-services` and `/api/v1/appointments/availability`. A 429 response uses the shared error shape, correlation ID, generic message and no IP/count details. Security and CORS permit anonymous HEAD on those same two endpoints so requests reach the limiter.

## Verification and review

- Focused `PublicRequestRateLimiterTest`, `TrustedProxyIpResolverTest`, and `AppointmentBookingPropertiesTest` passed 12/12 after the final limiter and HEAD assertion edits (`2026-10-14 18:58:47 +07:00`).
- `PublicRequestRateLimitT021PostgresIntegrationTest` passed 2/2 on the user's local PostgreSQL suite at 2026-10-14 19:05 +07:00, confirmed by its Surefire report. It covers independent buckets, exact `Retry-After`, error/correlation, IP isolation, and anonymous HEAD behavior for both public routes.
- User-provided full local suite output reports `BUILD SUCCESS`, 166 tests, 0 failures, 0 errors, 0 skipped, and `VisitT012PostgresIntegrationTest` passed 3/3 (2026-10-14 19:06:02 +07:00). The earlier 155-test result predates T021.
- Initial independent review found that the expiry queue could grow without bound under sustained distinct-IP traffic and that anonymous HEAD was rejected by Spring Security before reaching the interceptor. The queue was removed and HEAD was permitted through Security/CORS with integration coverage added.
- Post-fix independent review (`/root/review_t021`, 2026-10-14) found no remaining concrete correctness, security, or regression finding. Reviewer confirmed bounded/progressing cleanup and anonymous HEAD coverage. The active-window bucket map still scales with distinct client IPs seen during that window, which is intrinsic to per-IP tracking; the additional unbounded expiry queue has been removed.
- Independent debugger review (`/root/t021_debugger`) found no residual cleanup, HEAD/security, or test-isolation regression. Independent test evidence review (`/root/verify_t020`) confirmed the T021 2/2 Surefire report and 166 passing tests across the report set. The XML reports do not prove all suites came from one invocation; the user's console output independently reports `BUILD SUCCESS`, 166 tests, and 0 failures/errors/skips.
- No production or Supabase database was accessed.

## Pending closeout

Owner Long's approval is recorded. The missing persisted grant evidence remains explicitly documented as an accepted process exception; no retroactive grant-consumption claim is made. T021 is complete.
