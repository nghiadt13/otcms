# SDD Sync — FEAT-001 T007 Google OIDC Contract

- **Feature:** `feat-001-auth-login`
- **Reason:** Sync the Google OIDC exchange, verified-identity linkage and onboarding transaction contract after T007 implementation.
- **Sync type:** external authentication API, identity persistence and onboarding transaction
- **Producer:** `GoogleOidcAdapter`, `GoogleAuthenticationService`, `GoogleExchangeController`
- **Consumers:** Patient Portal and authentication/session boundary
- **Compatibility:** Google exchange returns the existing session token response; refresh remains cookie-only; provider failures remain generic and no provider token is returned.
- **Schema sync:** existing V001 `external_identities`, `accounts`, `patient_profiles` and `consent_evidence` schema is authoritative; no applied migration was edited.
- **Configuration sync:** `GOOGLE_OIDC_ISSUER`, `GOOGLE_OIDC_CLIENT_ID`, `GOOGLE_OIDC_CLIENT_SECRET`, token/JWKS URI and timeout settings are environment-bound.
- **Evidence:** T007 post-code review, trace report and `.\mvnw.cmd clean verify` with 47 passing tests.
- **Residual risk:** production redirect URI, client credentials and outbound JWKS/token endpoint policy require deployment review.

## Sync disposition

- **Status:** `SYNCED — PENDING HUMAN SYNC REVIEW`
- **Recommendation:** Accept the additive Google OIDC and onboarding contract while preserving fail-closed conflict and rollback behavior.

## Human Final Review

- **Status:** `APPROVED`
- **Decision:** Approve T007 implementation, requirement traceability and SDD sync.
- **Reviewer:** Long
- **Reviewed at:** 2026-09-30
