import { apiRequest } from "@/lib/api/client";
import type {
  AdminDoctor,
  DoctorInterval,
  MutationImpact,
} from "@/lib/domain/adminDoctor";
import { addDays } from "@/lib/format/formatDate";

export type ScheduleKind = "shifts";
export type IntervalInput = Pick<DoctorInterval, "startsAt" | "endsAt"> & {
  roomId?: string;
};
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
  return (
    await apiRequest<{ items: DoctorInterval[] }>(
      `${root(doctorId)}/${kind}?${query}`,
      { signal },
    )
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
      resourceType: "SHIFT",
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

export type DoctorRoomOption = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: string;
  active: boolean;
};
export async function listDoctorRooms(signal?: AbortSignal) {
  const result = await apiRequest<{ items: DoctorRoomOption[] }>(
    "/api/v1/admin/visit-rooms",
    { signal },
  );
  return result.items.filter(
    (room) => room.active && room.type === "EXAMINATION",
  );
}
