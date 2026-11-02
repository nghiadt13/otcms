# Local backend tests

Backend integration tests use a dedicated PostgreSQL database on this machine. The test profile accepts only `localhost`/`127.0.0.1`/`::1` and the database name `otcms_test_db`; application context startup fails before datasource creation if the effective URL points anywhere else or if the `supabase` profile is also active. The base `application.yml` continues to load `.env` for the normal application setup. When tests activate `test`, `application-test.yml` supplies its own datasource values, and the guard checks the effective URL before Spring creates a datasource; the Supabase datasource values are therefore not used by tests.

## One-time database setup

Start the local PostgreSQL service, then create an empty database named `otcms_test_db` using a local PostgreSQL administrator account (for example, with pgAdmin or `createdb`). Never use the Supabase URL or the application database for tests. Flyway applies the checked-in migrations when the test context starts; JPA validates the resulting schema.

The current `application-test.yml` declares the test username and password directly. It does not bind
`OTCMS_TEST_DB_USERNAME` or `OTCMS_TEST_DB_PASSWORD`. Do not print those configuration values or
copy production credentials. The test datasource guard accepts only loopback hosts and `otcms_test_db`.

The verified container setup uses PostgreSQL 18.6. The test configuration itself does not pin a server
version; the image digest below records the image verified on 2026-10-23. From `backend/`, with Docker
Desktop running, create the dedicated container once:

```powershell
$testDbConfig = Get-Content -Raw src/test/resources/application-test.yml
$env:POSTGRES_USER = [regex]::Match($testDbConfig, '(?m)^\s+username:\s*([^\r\n]+)').Groups[1].Value.Trim().Trim('"', "'")
$env:POSTGRES_PASSWORD = [regex]::Match($testDbConfig, '(?m)^\s+password:\s*([^\r\n]+)').Groups[1].Value.Trim().Trim('"', "'")
$env:POSTGRES_DB = 'otcms_test_db'
try {
    if (!$env:POSTGRES_USER -or !$env:POSTGRES_PASSWORD -or $env:POSTGRES_PASSWORD.StartsWith('${')) {
        throw 'Test credential binding is missing or unresolved'
    }
    docker run --detach --name otcms-postgres-test `
        --publish 127.0.0.1:5432:5432 `
        --env POSTGRES_USER --env POSTGRES_PASSWORD --env POSTGRES_DB `
        --volume otcms-postgres-test-data:/var/lib/postgresql `
        --health-cmd 'pg_isready -U postgres -d otcms_test_db' `
        --health-interval 2s --health-timeout 3s --health-retries 20 `
        postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6
} finally {
    Remove-Item Env:POSTGRES_PASSWORD, Env:POSTGRES_USER, Env:POSTGRES_DB -ErrorAction SilentlyContinue
}
```

If the container already exists, reuse it with `docker start otcms-postgres-test`. Do not delete its
volume or overwrite its credentials. The configured test URL is
`jdbc:postgresql://127.0.0.1:5432/otcms_test_db`; port 5432 must be available before initial creation.

## Check PostgreSQL and run tests

The previously documented `scripts/test-local.ps1` helper is absent from this checkout. Check container
health, then run the targeted Maven suite. Flyway creates the schema and installs `btree_gist` through
V004_1; the local test database user must have permission to install that extension.

```powershell
docker inspect --format '{{.State.Health.Status}}' otcms-postgres-test
docker exec otcms-postgres-test pg_isready -U postgres -d otcms_test_db
.\mvnw.cmd '-Dtest=UC07AdministratorAccountPostgresIntegrationTest,UC08AdministratorAccountPostgresIntegrationTest,UC09AdministratorAccountPostgresIntegrationTest,AdministratorAccountDatabaseVerificationTest' test
```

Use JDK 21, as selected by the current `pom.xml`. If the Windows Wrapper fails while resolving the
`.m2` directory, the exact cached Maven 3.9.16 command used successfully on this machine is recorded
in `docs/uc08-uc09-administrator-accounts.md`; do not change project dependencies to work around it.

All Spring integration tests use the shared `@LocalPostgresTest` annotation. Unit tests that do not
start a Spring context need no database. The test database is intentionally persistent between runs.
AdministratorAccountDatabaseVerificationTest uses unique records and isolated upgrade schemas,
preserving them for inspection; it never cleans the shared schema or deletes existing data.

Do not enable the `supabase` profile for tests. The test datasource guard rejects remote hosts even if another property source attempts to override the local URL.
