# FEAT-002 API contract addendum — T049 Reception reads

| Method and route | Role | Request | Result |
|---|---|---|---|
| `GET /api/v1/reception-queue` | Receptionist | Optional clinic-local `date`; `page` defaults to 0; `size` defaults to 50 and is capped at 100. | Active WALK_IN entries only, FIFO by server `arrivedAt` then stable queue-entry ID. Returns queue/visit IDs, WAITING status, 1-based position, current Visit stage, and current room when assigned. |
| `GET /api/v1/visits/{visitId}/progress` | Patient | Authenticated Patient and Visit ID. | Current own Visit stage/status and `arrivedAt`; `queuePosition` is present only for an active WALK_IN entry; current room is nullable. Other-profile and accountless Visits are denied. |

Both routes are read-only Visit-owned APIs, use response DTOs and enforce role/record authorization in the backend. The separate scheduled-arrival worklist remains `GET /api/v1/appointment-arrivals` (T053).