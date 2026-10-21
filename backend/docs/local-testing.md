# Local backend tests

Backend integration tests use a dedicated PostgreSQL database on this machine. The test profile accepts only `localhost`/`127.0.0.1`/`::1` and the database name `otcms_test_db`; application context startup fails before datasource creation if the effective URL points anywhere else or if the `supabase` profile is also active. The base `application.yml` continues to load `.env` for the normal application setup. When tests activate `test`, `application-test.yml` supplies its own datasource values, and the guard checks the effective URL before Spring creates a datasource; the Supabase datasource values are therefore not used by tests.

## One-time database setup

Start the local PostgreSQL service, then create an empty database named `otcms_test_db` using a local PostgreSQL administrator account (for example, with pgAdmin or `createdb`). Never use the Supabase URL or the application database for tests. Flyway applies the checked-in migrations when the test context starts; JPA validates the resulting schema.

For PowerShell, set the local test account for the current terminal session. These variables are separate from the Supabase variables used by the application profile:

```powershell
$env:OTCMS_TEST_DB_USERNAME = 'postgres'
$secureTestDbPassword = Read-Host 'Local otcms_test_db database password' -AsSecureString
$env:OTCMS_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secureTestDbPassword).Password
```

The test URL is fixed at `jdbc:postgresql://127.0.0.1:5432/otcms_test_db`; it cannot be overridden through `.env` or a process variable.

## Check PostgreSQL and run tests

Create the empty `otcms_test_db` database once. From `backend/`, run the helper below. It prompts for the local database account without printing its password, checks PostgreSQL/`btree_gist`, installs `btree_gist` only in `otcms_test_db` when missing and permitted, then runs the Maven suite. It removes its credential variables when it exits.

```powershell
.\scripts\test-local.ps1
```

The helper never connects to Supabase. If it reports that `btree_gist` cannot be installed, have a local PostgreSQL administrator install it in `otcms_test_db`, then rerun the helper.

All Spring integration tests use the shared `@LocalPostgresTest` annotation. Unit tests that do not start a Spring context need no database. The test database is intentionally persistent between runs; integration tests must create unique records and clean up only their own data.

Clear the password from the terminal after testing:

```powershell
Remove-Item Env:OTCMS_TEST_DB_PASSWORD
Remove-Item Env:OTCMS_TEST_DB_USERNAME -ErrorAction SilentlyContinue
```

Do not enable the `supabase` profile for tests. The test datasource guard rejects remote hosts even if another property source attempts to override the local URL.
