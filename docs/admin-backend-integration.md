# KAN-12 / KAN-13 — Admin backend integration

The Admin routes now call the Spring backend through the shared authenticated `apiRequest` client. They do not fall back to an in-memory store when the backend is unavailable. Service prototypes remain in the repository for reference/tests. Staff/room prototypes are available only through the explicitly labelled prototype mode; the default mode uses the live doctor APIs.

| Route | Live behavior |
| --- | --- |
| `/admin/services` | Search/filter and paginate the complete admin catalog, create/edit price and scheduling parameters, deactivate/reactivate services, assign/remove doctors. |
| `/admin/staff-profiles` | List all doctor profiles (including non-public profiles), choose an active unprofiled doctor account, create/edit professional information, change public visibility and acceptance of new bookings. |
| `/admin/staff-shifts?doctorId=…` | Read a doctor's shifts/time-offs over an inclusive date range, create/update future intervals, delete future intervals with confirmation. |

## Actual API contract

The integration uses the backend's existing data model rather than mapping unrelated mock fields onto it:

- Service write: `{name, description, price, durationMinutes, bufferMinutes, onlineBookable}`. Mutations do not send fake category, modality, lab-indicator or patient-note fields.
- Doctor profile creation links an existing doctor `accountId`. Editing cannot replace that account or change directory flags. Newly created profiles are internal, active in the directory and not accepting new bookings, as defined by the existing provisioning service.
- Doctor photo is a URL; there is no upload endpoint in the current backend.
- Shifts and operational time-offs use `{startsAt, endsAt}` UTC instants. The form interprets local input as UTC+7 regardless of the computer's timezone. Date filters include the entire final day.
- Backend schedules currently belong to doctors and do not include a room assignment or other staff roles. The UI exposes this supported scope explicitly. Operational time-offs are not employee leave applications.
- Service category/modality/lab-reference configuration, other staff profiles, room-linked staff shifts and file upload still require separate backend contracts. The old mock fields are not presented as persisted production data.
- The existing `/admin/appointments` page still uses its prototype store. These live screens do not link a blocking impact message to that mock as if it could resolve real bookings. Coordinate the actual bookings through the team's backend-connected workflow before retrying.

Read APIs added to complete the existing mutation contracts (all Admin-only):

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/v1/admin/clinic-services` | `{items: ServiceResponse[]}`, including inactive and non-online services |
| GET | `/api/v1/admin/clinic-services/{id}/doctor-assignments` | `{items: DoctorAssignmentResponse[]}` |
| GET | `/api/v1/admin/doctor-profiles` | `{items: DoctorAdminResponse[]}`, including internal profiles |
| GET | `/api/v1/admin/doctor-profiles/eligible-accounts` | `{items: [{accountId, username, fullName}]}`; active doctor accounts not yet profiled |
| PUT | `/api/v1/admin/doctor-profiles/{id}` | Updated profile; accepts professional details only |
| GET | `/api/v1/admin/doctor-profiles/{id}/time-offs?from=&to=` | `{items: DoctorIntervalResponse[]}` overlapping the requested interval |

Existing create/edit/delete, directory-state and mutation-impact endpoints remain authoritative. No database migration is introduced.

## Workflow protections

- Session authorization and CSRF remain in the shared API client. Internal read APIs and mutations require Admin authority at the backend.
- Backend serializes resource mutations using the existing coordination guard. Editing a profile preserves identity and all directory flags.
- Creating, moving or deleting a historical shift/time-off is rejected at the backend; the UI also renders past/ongoing entries as read-only.
- Backend rejects overlapping shifts and overlapping time-offs. Time-offs may intersect work shifts intentionally: they make that time unavailable for booking.
- Before saving an interval or removing a service-doctor assignment, the UI checks mutation impact. Protected bookings/active care block the write. The actual mutation repeats the authoritative backend check, so a concurrent booking cannot bypass the guard between preview and save.
- A failed save leaves the form and entered values intact. Submitting is disabled while requests are pending. Successful mutations re-fetch backend data.
- Profile detail and directory-state changes use two backend requests. If the second fails, the form explicitly reports that details were saved but flags were not, allowing a retry.
- Changing doctor/date/type aborts previous reads; late responses cannot overwrite the currently selected doctor's data.

## Manual verification

Start the backend with Java 21 and the team's normal development environment; start the frontend using `npm run dev`. Log in with a real Admin account at `/staff-login` before opening the routes above. Use a development database for mutation testing.

1. Open Services. Verify that services with `active=false` and `onlineBookable=false` are still visible to Admin. Search by name and change the status filter.
2. Create a service with a name, price, duration and buffer. Refresh the page to verify persistence. Change the price and verify it is retained after refresh. Verify that historical appointment price snapshots are unchanged.
3. Deactivate/reactivate a service. Check the public catalog: only active online-bookable services are returned there.
4. Open the service's Doctors dialog. Assign an existing doctor. Check that already assigned doctors cannot be added again through the picker. Try removing an assignment with protected bookings: the action must be blocked.
5. Create a doctor profile from an eligible active doctor account. Verify that the selected account disappears from the eligible list after creation. Edit the professional fields, turn on public visibility and new bookings, then refresh.
6. Configure the doctor's service assignment and future shift. Check appointment availability: a doctor needs an active profile accepting bookings, a matching service assignment and working time, with no overlapping time-off or occupied slot.
7. Add a duplicate shift and an overlapping time-off: verify rejection. Use a past date: verify the frontend rejects it and the API also rejects a direct request.
8. Change a shift or add a time-off overlapping an existing booking/active visit. Check that the impact message appears and the interval is not saved. Coordinate the affected booking first, then retry.
9. Verify cancellation/closing dialogs, loading/empty/network-error states, pagination with more than 10 rows, and that rapidly changing doctors never shows the previous doctor's schedule.
10. Inspect at desktop and 375px mobile width. Tables may scroll inside their container; the page itself must not overflow horizontally.

## Automated verification

Frontend: `npm run lint`, `npm test`, `npm run build`, `npx tsc --noEmit`.

Backend (Java 21):

```powershell
.\mvnw.cmd -B "-Dtest=AdminDoctorProfileServiceTest,AdminDoctorProfileControllerTest,AdminClinicServiceControllerTest,ManageDoctorResourcesT032Test,ProvisionDoctorProfileServiceImplTest,ClinicServiceHandlerTest,ManageDoctorServiceAssignmentsT032Test" test
```

API/component tests use controlled fixtures and backend tests use mocked persistence, so they do not mutate the team's Supabase database. Browser QA similarly intercepts network requests in an isolated browser context. A real Admin session and development database are still needed for final end-to-end acceptance against deployed services.


## Jira conformance update

- `serviceCatalogApi.ts` is now the canonical live adapter. The existing function names and argument lists are preserved; data types are aligned with the actual ServiceRequest. Legacy categories/indicators remain only in `serviceCatalogPrototype*`, never in the live form or payload.
- List/detail use the Admin catalog when available. If that endpoint is absent (404 only), list uses the Jira-specified staff catalog. That fallback does not contain discontinued services; the current BE also excludes ADMIN from that staff endpoint, so an Admin may receive a visible permission error until KAN-9 is deployed. Authorization/network errors never cause a silent fallback to fake data.
- `publicDoctorApi.ts` now reads `/api/v1/doctors` and `/{id}`. It does not access the prototype store. Detail maps unavailable/hidden doctors to MSG-24. No invented availability is displayed; the user is directed to booking to select a service and inspect actual slots.
- Time-off deletion now calls the existing removal mutation-impact endpoint first. A blocked impact prevents DELETE. Creating/editing intervals and removing service assignments retain their preflight checks. There is no service-deactivation impact endpoint in the current controller; the UI confirms the action and displays BE errors without inventing an impact result.
- Backend domain errors use existing FE MSG entries; scheduling-specific messages retain actionable descriptions. No new MSG number is invented.
- Doctor-only JSON/Instant flows are under the backend mode. The retained staff/room prototypes are accessible through an explicitly labelled prototype mode and do not write to the database.
- Additional backend support for KAN-8/KAN-9 is retained locally in separate backend commits for review by the BE owner. That backend support is not an upstream dependency assumed to exist; deployments without those APIs still require the KAN-8/KAN-9 work. Profile editing, hidden admin lists and time-off reads depend on those additions. Frontend and backend changes are grouped separately in Git for review.
- Browser/component fixtures do not certify persistence in the team's real DB. Final DB acceptance still requires the team's development backend and a real Admin account.
