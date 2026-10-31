# Trace — FEAT-002 T017 A2

- Status: COMPLETE for the TASKS v0.3.0 foundation checkpoint. Owner Long approved implementation and trace on 2026-10-14. Owner Long approved persistent `btree_gist` in the dedicated test DB.
- Requirement/task: `backend/sdd/features/feat-002-appointment-booking/TASKS.md` T017 v0.3.0 foundation checkpoint.
- Implementation: `src/test/java/fu/fa26/sep490/g58/otcms_be/persistence/T017PersistenceCheckpointPostgresIntegrationTest.java`.
- Review: `backend/sdd/reviews/post-code-feat-002-appointment-booking-T017-A2.md`.

| T017 exit evidence | Artifact/check | Current state |
| --- | --- | --- |
| Fresh empty-schema V001→V013 install | `cleanSchemaMigratesV001ThroughV013WithFinalOwnerConstraints` | Passed in final user-run suite; all owner table/FK/unique/exclusion assertions passed |
| Upgrade from applied V001 with existing data | `upgradeFromV001PreservesAuthAndPatientDataAndTheOriginalChecksum` | Passed in final user-run suite; Flyway history and final appointment constraints verified |
| V001 immutable checksum | SHA-256 assertion against approved historical value and before/after Flyway checksum comparison | Passed in final user-run suite |
| Data/profile mapping preservation | Account, patient profile, external identity, consent assertions | Passed in final user-run suite |
| Owner FK/exclusion constraints | Schema-qualified assertions check table, constraint type, and definition fragments | Passed in final user-run suite |
| Stable T010–T012 owner contracts | Approved decisions recorded in `TASKS.md` and `shared_context.md` | Prerequisites complete |
| Atomicity and ordered lock plan | Post-code review artifact, PLAN §3.1 source inventory | Written; independent review passed |
| Runtime race and failure rollback | T033/T035 | Deliberately deferred |

Exact approved command was `backend/.\scripts/test-local.ps1`, which prompts for local DB credentials and runs `mvnw.cmd test`. Final user-provided result: Tests run 127, Failures 0, Errors 0, Skipped 0; BUILD SUCCESS; total time 01:08 min. V013 may install database-scoped `btree_gist` in `otcms_test_db`; this persistent effect was approved and was not removed. No migration file was modified by this task.
