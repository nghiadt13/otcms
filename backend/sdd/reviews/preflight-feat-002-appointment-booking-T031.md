# FEAT-002 T031 Preflight — Scope and Decision Checkpoint

Date: 2026-10-19 (Asia/Bangkok)
Status: APPROVED — Owner Long approved all eight policy groups, Visit contract/API/V015 material checkpoint A1/A2, and all eight Room material-state decisions on 2026-10-19 (Asia/Bangkok). Visit/Doctor/Room source work is authorized within these checkpoints. PostgreSQL/concurrency verification, operational room provisioning disposition, and final T031 closure remain required.

## Authority and dependency audit

- Governing repository rules: root/backend `AGENTS.md`, `backend/CONSTITUTION.md`, and `docs/architecture.md`; FEAT-002 baseline is Report 3 at `docs/requirements/baselines/report3-2026-10-12/BASELINE.md`.
- Approved feature sources: CONTEXT v0.16.0 (D12), SPEC v0.9.0 (REQ-BOOK-051/057 and §5.5), PLAN v0.7.0 (Phase 6), TASKS v0.6.1 (T031 / AC15), Architecture Profile v1.4.0, Visit contract v1.2.0 plus T029 scheduled-care amendment.
- TASKS dependency row requires T012, T014, T029 and T043. All four are `[x]`: Visit foundation/read projections; shared CoordinationGuard; scheduled care start/complete and immediate capacity; WALK_IN API. Their closure evidence appears in TASKS execution records and T029 review/trace artifacts.
- T031 is not a prerequisite for T029 immediate start-time validation: T031 adds expected-interval reservations and overrun handling. T033 and T032 remain downstream.

## Baseline behavior already fixed

D12 fixes ownership and exclusions: appointment reserves Doctor time only; Visit/Reception/Clinical coordinates Doctor and suitable-room capacity during care; no synthetic appointment for WALK_IN; the visit's own linked appointment is an exact self-link exception; existing confirmed appointment occupancy and completed buffer remain protected; no queue, lab/external-result workflow, or booking-time room/device reservation is added. Doctor/room double assignment must be prevented.

D12 deliberately does not set a numeric expected duration or threshold. It requires the owning Visit SPEC/PLAN and integration contract to define duration estimation, overrun response, and concurrency/synchronization before implementation. No conclusion should assume that “Doctor is free now” is sufficient.

## Contract and persistence findings

- Existing `AppointmentCapacityPort.checkVisitReservation` checks a proposed Doctor interval against Appointment-owned occupancy and validates the linked-appointment exception. `VisitCareReservationQueryPort` exposes current RESERVED/IN_PROGRESS Doctor-care intervals. Both are bounded/read-oriented contracts; neither commits a time reservation.
- T029's `VisitScheduledCarePort` starts/completes scheduled care with a Visit-owned assignment using immediate Doctor capacity checking. It explicitly leaves duration/overrun reservations and rooms to T031.
- Existing slot projections protect future Doctor appointments and care intervals, but there is no suitable-room reservation contract/entity/API identified in the current repository and no public capacity reservation operation that atomically reserves both Doctor and room for arbitrary expected-duration Visit care.
- Any missing owner contract, schema, resource inventory/suitability rule, or cross-owner transaction capability is a stop condition. Prepare a material-state/contract checkpoint before changing schema or frozen contracts; never access another feature's repository/entity/table directly.

## Approved policy decisions

Owner Long approved the following policy choices on 2026-10-19:

1. **Duration estimate:** Use a Visit-owner-configured expected duration per service/step and capture its expected end before assignment. No implicit numeric default; missing configuration fails closed and prevents care start. The estimate source is configuration, not a client-provided estimate.
2. **Overrun response:** Never move/cancel/shorten an appointment or admit overlapping care. Care may continue beyond its estimate only while the extension remains conflict-free. If it would overlap protected capacity, fail the extension/start request and flag the Visit for Receptionist coordination; downstream impact requires explicit authorized resolution. No numeric warning/grace threshold is introduced.
3. **Reservation interval and state:** Expected care occupies `[actualStart, expectedEnd)` while RESERVED or IN_PROGRESS. Completed care retains its committed interval as history but does not block elapsed future time. Every active Visit service step, including same-visit result-reading, uses Doctor capacity; room capacity applies where the owning service requires a room.
4. **Self-link and protection:** Exclude only the exact linked appointment for the same Visit and Patient. Preserve booked start/end+buffer; no bypass for another active care assignment, appointment, or completed buffer. WALK_IN has no synthetic appointment or self-link exemption.
5. **Room ownership and scope:** Visit/Reception/Clinical owns suitable-room selection/reservation; Appointment owns Doctor booking occupancy. No room contract/catalog was present at preflight; its separate material-state checkpoint was approved later and authorizes Visit-owned V017 and server-side allocation. No room CRUD API or caller-selected room is authorized.
6. **Atomicity/concurrency:** Care start/extend/reserve/complete and appointment create/change affecting the same Doctor serialize under T014 shared guard and transaction. Room conflicts serialize under the Room owner boundary. Any required multi-owner Doctor/room/Visit write must be one datasource transaction; otherwise stop for architecture/contract checkpoint, never compensating partial success.
7. **Failure behavior and authority:** Capacity conflict rejects new/extended care with a typed conflict and no partial assignment; unavailable evidence fails closed. Backend authorization uses the authenticated actor, not client-supplied Doctor/Visit IDs. Exact endpoints/error codes must be specified in the owning contract before source implementation.
8. **Schema/migration:** Reuse V001–V014 if sufficient. Any required expected-end, room reservation, audit, or exclusion schema needs a separate material-state checkpoint before edits; verify local test DB migration head and use an additive migration. Never edit applied migrations or access production/Supabase.

Approval record: Owner Long approved all eight proposed groups (“Duyệt toàn bộ phương án đề xuất”) on 2026-10-19 (Asia/Bangkok). The approved policy does not itself approve new endpoints, schema, room contracts, or any other material-state change.

## Scope and file boundary after approval

- In scope: Visit-owned expected duration/overrun policy and active care reservations; Doctor-capacity coordination through owner public contracts; suitable-room reservation only if an approved owner contract/persistence capability exists; protection of upcoming appointments and completed buffer; exact own-link handling; focused unit/PostgreSQL tests; owner SPEC/PLAN/contract synchronization and AC15 trace/TASKS evidence.
- Out of scope: appointment-time room/device booking, synthetic appointments for walk-ins, queue implementation, laboratory/external-result ingestion, clinical decision logic, unrelated Admin protected mutations (T032), integrated readiness races (T033), frontend, production/Supabase access.
- Expected source boundary: Visit/Reception/Clinical owning service and repository/entity; shared neutral integration contracts only by approved additive contract; Appointment consumer only through public service/port. Do not touch files owned by another feature as a shortcut.
- Exact focused tests/command and migration allocation will be written to the T031 Action Record only after checkpoint decisions and any prerequisite contract checkpoint are approved.

## Verification and stop gates

- Verify expected-interval conflict with active Doctor care, upcoming appointment, completed appointment buffer, own linked appointment, unrelated care, adjacency, and walk-in; reject conflicts with immutable state.
- Verify concurrent booking vs care-start/extension with real PostgreSQL transactions/barriers under the shared guard; only one incompatible operation may commit. Verify room concurrency if an approved room contract is available.
- Verify overrun at boundaries, conflict/no-conflict behavior, warning/resolution authority, and no implicit appointment transitions or occupancy release.
- Verify fail-closed dependency outage, transaction rollback, privacy-safe conflict projection and no clinical payload leakage.
- Stop if numeric policy, contract ownership, room catalog, schema, transaction manager/datasource, or cross-owner atomicity differs from the approved decisions. Raise the specific separate checkpoint before source edits.

## Approval disposition

Checkpoint disposition: APPROVED for the policy and bounded Visit-owned API/persistence scopes in `preflight-material-feat-002-T031-visit-capacity-contract-A1.md` (A1/A2) and `preflight-material-feat-002-T031-room-capacity.md` (Room decisions 1–8). T031 remains incomplete and production booking remains gated pending PostgreSQL/concurrency evidence, operational room provisioning disposition, review, trace and closure.

## Approved decision sync-back

No numeric estimate, grace duration, warning threshold, or fallback duration is implied. Missing Visit-owner service/step estimate rejects care start. A care extension validates its newly added prospective segment `[max(now,currentExpectedEnd),newExpectedEnd)`; a conflict fails without partial writes and routes the Visit for Receptionist coordination. Existing appointment status/interval/buffer remains unchanged.
