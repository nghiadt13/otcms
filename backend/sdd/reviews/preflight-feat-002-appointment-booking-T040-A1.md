# T040 Preflight — A1

Date: 2026-10-14 (Asia/Bangkok)  
Status: EXECUTION AUTHORIZED by Owner Long's direct request to create and implement the FEAT-002 layered migration.

## Authority and inputs

- `backend/CONSTITUTION.md` v2.0.0, ARCH-01 requires `controller -> service -> repository -> entity` and RFC-approved exceptions.
- RFC-001 and Architecture Profile v1.3.0 already approve Java/Spring layered architecture; this task aligns Appointment with that authority and does not modify the Constitution.
- Existing FEAT-002 PLAN v0.4.0 incorrectly selected Clean Architecture. This migration revises the Plan to v0.5.0 and TASKS to v0.4.0 under the user's direct implementation authorization.

## Shadow Plan and boundaries

1. Inventory Appointment source/test packages, consumers and frozen integration contracts.
2. Move HTTP/persistence/application classes to layered feature packages while preserving route, security, SQL, transaction and behavior contracts.
3. Introduce service abstraction for query controller and place API response mapping in mapper boundary.
4. Update Plan, TASKS, AGENTS and architecture docs; add T040 review/trace evidence.
5. Statically inspect package declarations/paths and stale package references. Runtime verification remains a T040 exit gate.

File boundary: Appointment Java packages/tests, FEAT-002 PLAN/TASKS and T040 review/trace, root AGENTS, `docs/architecture.md`, and the relevant Architecture Profile clarification. No other feature package refactor, Constitution edit, migration, database access, route/DTO contract change, or production/Supabase access.

## Risks and acceptance

- Risk: package moves can break Spring discovery/imports or transaction behavior. Mitigation: preserve stereotypes and method bodies, introduce no schema/API change, and require approved compile/test commands plus independent review before T040 completion.
- Existing request payloads continue using strict `JsonNode` parsing to preserve exact unknown-field/type errors; typed Jakarta-validated request DTO conversion is outside this migration and is recorded as a follow-up.
- Acceptance: canonical package layout, no remaining Appointment `application`/`adapter` package declarations/imports, controller depends on service interface, DTO mapping belongs to mapper, persistence remains owner-local, and tests/build pass before task closure.
