# SDD sync — feat-001-auth-login T009

## Status

`SYNCED / APPROVED`

## Synchronized artifacts

- Added T009 execution evidence to `.sdd/features/feat-001-auth-login/TASKS.md`.
- Added mail dependency approved by the Human Director: `spring-boot-starter-mail`, managed by the Spring Boot BOM.
- Added `app.email.*` configuration keys backed by environment variables.
- Preserved `EmailDeliveryPort` as the application boundary; T008 callers remain compatible.
- No database migration or persistence schema change was introduced.

## Residual handoff

Production must provide `APP_EMAIL_USERNAME`, `APP_EMAIL_PASSWORD` and `APP_EMAIL_FROM`, with `APP_EMAIL_ENABLED=true`. Human approval: Long, `APPROVED`, 2026-09-30.
