# Requirement trace — feat-001-auth-login T010

## Status

`TRACED / VERIFICATION PASSED / APPROVED`

| Requirement area | Test evidence |
|---|---|
| REQ-AUTH-001..020 | `PatientRegistrationT004Test`, `T004PostgresIntegrationTest` |
| REQ-AUTH-021..036 | `T005CredentialLoginServiceTest`, `T005RealLoginIntegrationTest`, `T005ContractTest` |
| REQ-AUTH-037..047 | `T006SecurityContractTest`, `T006HttpSecurityEvidenceTest`, `T006PostgresIntegrationTest`, `T006A12EvidenceTest` |
| REQ-AUTH-048..052 | `T010CommonSecurityContractTest`, `T005ContractTest` |
| REQ-AUTH-053..066 | `T008PasswordRecoveryServiceTest`, `T008PasswordRecoveryPostgresIntegrationTest`, `T010PasswordRecoveryControllerTest` |
| NFR-AUTH-006..015 | PostgreSQL/Testcontainers integration suite, security contract tests, redaction tests and `T009SmtpEmailDeliveryAdapterTest` |

## Verification

- Targeted T010 tests passed.
- `\\.\\mvnw.cmd clean verify` passed with 62 tests and no failures/errors/skips.

## Trace decision

T010 test evidence is traced across the auth requirements. Human approval: Long, `APPROVED`, 2026-09-30.
