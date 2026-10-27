# Post-code review — FEAT-002 T017 A2

- Status: COMPLETE. Independent review passed, user-run verification passed 127/127, and Owner Long approved implementation and trace on 2026-10-14.
- Scope: PostgreSQL migration checkpoint only; no production migration or runtime workflow changes.
- Source: `src/test/java/fu/fa26/sep490/g58/otcms_be/persistence/T017PersistenceCheckpointPostgresIntegrationTest.java`.
- Verification status: user reports the final full-suite run passed 127 tests, 0 failures/errors/skips, `BUILD SUCCESS` (01:08 min). Earlier runs exposed Flyway's zero-padded version and schema-qualified FK deparser output; both assertion fixes were independently reviewed. The successful full suite verifies the fresh V001→V013 install, V001-with-data upgrade, preserved data/checksum, and final owner constraint assertions. Execution was limited to `127.0.0.1:5432/otcms_test_db` with unique temporary schemas dropped in `finally`. Owner Long approved the possible persistent database-scoped `btree_gist` installation in that DB on 2026-10-14; the test does not drop it.

## Planned evidence

1. A fresh empty isolated schema receives a clean Flyway install of all 13 migrations (the test does not call Flyway `clean`), then checks Flyway history/current version, owner tables, and final FK/exclusion constraints.
2. Another isolated schema is migrated only through V001, populated with representative account, patient profile, external identity, and consent rows, then upgraded through V013. It checks retained data, generated independent patient-profile identity, V001 Flyway checksum, history, and final appointment constraints.
3. The classpath SHA-256 of V001 is compared with the persisted historical SHA-256 `9a616dcb1d8ffbf007d6af3e8fd1fa3f1eb97c60294e1d5d5fe7ba37802d35a6`.

## Boundary and lock-order review

The approved PLAN §3.1 order is: one shared READ_COMMITTED transaction; acquire the neutral coordination guard first; then read current policy/owner state and acquire owning resource/appointment locks; then related visit/reception/replay rows ordered by UUID ascending; finally write state and audit. Capture `Clock` after acquiring the guard. Do not perform network calls while holding it. Guarded mutations use transaction-local `lock_timeout=2s` and `statement_timeout=5s`; timeout/deadlock rolls back the whole command without automatic retry.

Current booking-boundary writer handlers visible in source are service create/update/activate/deactivate, Doctor profile provisioning, patient-profile creation, and T008 care-relationship request/confirm. Each is transactional and acquires the guard before its owner/relationship lookup and writes. `CareRelationshipController` deliberately lacks a Spring MVC controller stereotype under approved D03, so those guarded handlers are not exposed as HTTP workflows. Authentication/session/recovery writes remain outside the current booking consistency boundary unless a future operation changes booking authority or patient-profile linkage.

The singleton guard row is the current shared serialization lock. The remaining owner-row order below is a mandatory admission rule for future workflows, not evidence that appointment row locks already exist. Per-row lock ordering and cross-workflow atomicity remain T033/T035 evidence.

Appointment lifecycle, reschedule/cancel/start/no-show, reception arrival/check-in, shift/time-off/assignment mutation, account lock/hide/deactivation, and impact-aware service deactivation are not current guarded writers; their future implementations must join the same boundary before reading policy or taking owner locks. Public availability/catalog reads are not serialized by the guard. Protected service deactivation/assignment removal stays withheld or fails closed until T032 supplies impact checking. Race/rollback proof remains T033/T035, not this foundation checkpoint.

## Review questions

- Does the per-schema Flyway setup isolate schema objects and safely clean up only the generated schema?
- Do the schema-qualified table/type/definition assertions adequately verify the FK, uniqueness, and exclusion mappings?
- Are the asserted migration count, source SHA-256, mapping, preserved fields, FKs, and exclusions sufficient for T017's v0.3.0 exit?
- Does the source inventory accurately distinguish exposed guard participants from dormant/future handlers?
- Are there hidden transaction/lock ordering gaps that should be recorded before T017 approval?

## Findings

Independent review passed. Reviewer found and resolved: (1) clarify the possible persistent database-scoped `btree_gist` installation; (2) include T008 guarded care-relationship handlers while noting the controller is not exposed; (3) validate constraint table/type/definition; (4) describe V001 SHA-256 separately from the Flyway checksum; and (5) call the clean install a fresh-schema `migrate()` flow rather than a Flyway `clean()` operation. Reviewer confirmed both the `013` version correction and schema-qualified FK normalization. Final user-run PostgreSQL suite passed 127/127, completing the database checkpoint. No remaining source-based blocker is known; T017 awaits Human final approval before checkbox closure.
