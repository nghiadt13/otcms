# Requirement trace — FEAT-002 T021 A1

| Requirement | Implementation evidence | Verification/status |
|---|---|---|
| REQ-BOOK-044: independent fixed-window buckets for public catalog and availability | `PublicRequestRateLimiter.Bucket`; `PublicRequestRateLimitInterceptor` exact route mapping | Unit tests and PostgreSQL HTTP assertions pass for bucket isolation and reset. |
| REQ-BOOK-044: only configured trusted proxies may supply forwarding information | `TrustedProxyIpResolver` CIDR matching; untrusted peers use `getRemoteAddr()` | Unit coverage passes for untrusted forwarding, trusted XFF chain, Forwarded IPv6/CIDR, malformed fallback and invalid configuration. |
| REQ-BOOK-044: 429 with Retry-After rounded up and minimum one second | `PublicRequestRateLimiter.acquire`; interceptor response header | Unit tests pass for fractional-second ceiling/minimum; PostgreSQL HTTP assertions pass for exact `Retry-After: 10`. |
| REQ-BOOK-044: bounded stale cleanup | `PublicRequestRateLimiter.cleanup` scans no more than 64 map entries per pass every 16 requests; no auxiliary expiry queue | Unit tests pass for bounded pass/eventual removal. Independent review found the fixed scan progresses faster than one new key per request. |
| AC16: safe error and correlation; no client IP leakage | `PublicRequestRateLimitInterceptor` generic `RATE_LIMIT_EXCEEDED` envelope | Unit and PostgreSQL HTTP assertions pass for status, error code, correlation ID, and generic message; unit verifies no IP leakage. |
| Public scope only | Interceptor limits exact catalog/availability paths and GET/HEAD; Security permits anonymous HEAD for the two routes | Unit and PostgreSQL HTTP tests pass for method/path selection and anonymous HEAD limiter behavior. |

## Disposition

Focused unit/config tests passed 12/12. `PublicRequestRateLimitT021PostgresIntegrationTest` passed 2/2; the user-provided local suite reports `BUILD SUCCESS`, 166/166 with zero failures/errors/skips. Independent post-fix code review and debugger review found no remaining concrete findings. Owner Long approved the implementation and trace and accepted the missing persisted task-grant evidence as a process exception on 2026-10-14 (Asia/Bangkok). T021 is complete; the record makes no retroactive grant-consumption claim.
