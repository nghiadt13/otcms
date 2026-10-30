import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type {
  AdminDoctor,
  DoctorInterval,
  MutationImpact,
} from "@/lib/domain/adminDoctor";
import { addDays } from "@/lib/format/formatDate";

export type ScheduleKind = "shifts" | "time-offs";
export type IntervalInput = Pick<DoctorInterval, "startsAt" | "endsAt">;
const root = (doctorId: string) =>
  `/api/v1/admin/doctors/${encodeURIComponent(doctorId)}` as const;
export async function listScheduleDoctors(signal?: AbortSignal) {
  return (
    await apiRequest<{ items: AdminDoctor[] }>(
      "/api/v1/admin/doctor-profiles",
      { signal },
    )
  ).items;
}
/** Date filters are inclusive VN dates; the backend range end is exclusive. */
export function scheduleRange(from: string, to: string) {
  return {
    from: new Date(`${from}T00:00:00+07:00`).toISOString(),
    to: new Date(`${addDays(to, 1)}T00:00:00+07:00`).toISOString(),
  };
}
export async function listDoctorSchedule(
  doctorId: string,
  kind: ScheduleKind,
  from: string,
  to: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams(scheduleRange(from, to));
  const path: `/api/v1/${string}` =
    kind === "shifts"
      ? `${root(doctorId)}/shifts`
      : `/api/v1/admin/doctor-profiles/${encodeURIComponent(doctorId)}/time-offs`;
  return (
    await apiRequest<{ items: DoctorInterval[] }>(`${path}?${query}`, {
      signal,
    })
  ).items;
}
export function saveDoctorInterval(
  doctorId: string,
  kind: ScheduleKind,
  id: string | undefined,
  input: IntervalInput,
) {
  return apiRequest<DoctorInterval>(
    `${root(doctorId)}/${kind}${id ? `/${encodeURIComponent(id)}` : ""}`,
    { method: id ? "PUT" : "POST", body: JSON.stringify(input) },
  );
}
export async function deleteDoctorInterval(
  doctorId: string,
  kind: ScheduleKind,
  id: string,
) {
  if (kind === "time-offs") {
    const impact = await apiRequest<MutationImpact>(
      `${root(doctorId)}/time-offs/${encodeURIComponent(id)}/mutation-impact`,
    );
    if (impact.blocked)
      throw new ApiError(409, {
        code: "MSG-36",
        errorCode: "RESOURCE_MUTATION_BLOCKED",
      });
  }
  return apiRequest<void>(
    `${root(doctorId)}/${kind}/${encodeURIComponent(id)}`,
    { method: "DELETE" },
  );
}
export function assessDoctorInterval(
  doctorId: string,
  kind: ScheduleKind,
  id: string | undefined,
  input: IntervalInput,
) {
  return apiRequest<MutationImpact>(`${root(doctorId)}/mutation-impact`, {
    method: "POST",
    body: JSON.stringify({
      resourceType: kind === "shifts" ? "SHIFT" : "TIME_OFF",
      resourceId: id ?? null,
      ...input,
    }),
  });
}

export function vietnamDateTime(value: string) {
  const time = new Date(`${value}:00+07:00`);
  if (!Number.isFinite(time.getTime()))
    throw new Error("Thời gian không hợp lệ.");
  return time.toISOString();
}
