import { apiRequest } from "@/lib/api/client";
import type { AdminDoctor, MutationImpact } from "@/lib/domain/adminDoctor";
const root = "/api/v1/admin/clinic-services" as const;
export type DoctorAssignment = {
  id: string;
  doctorProfileId: string;
  serviceId: string;
};
export async function getServiceAssignments(id: string, signal?: AbortSignal) {
  const [assignments, doctors] = await Promise.all([
    apiRequest<{ items: DoctorAssignment[] }>(
      `${root}/${encodeURIComponent(id)}/doctor-assignments`,
      { signal },
    ),
    apiRequest<{ items: AdminDoctor[] }>("/api/v1/admin/doctor-profiles", {
      signal,
    }),
  ]);
  return { assignments: assignments.items, doctors: doctors.items };
}
export function assignServiceDoctor(
  serviceId: string,
  doctorProfileId: string,
) {
  return apiRequest<DoctorAssignment>(
    `${root}/${encodeURIComponent(serviceId)}/doctor-assignments`,
    { method: "POST", body: JSON.stringify({ doctorProfileId }) },
  );
}
export function assessAssignmentRemoval(
  serviceId: string,
  assignmentId: string,
) {
  return apiRequest<MutationImpact>(
    `${root}/${encodeURIComponent(serviceId)}/doctor-assignments/${encodeURIComponent(assignmentId)}/impact`,
  );
}
export function removeServiceDoctor(serviceId: string, assignmentId: string) {
  return apiRequest<void>(
    `${root}/${encodeURIComponent(serviceId)}/doctor-assignments/${encodeURIComponent(assignmentId)}`,
    { method: "DELETE" },
  );
}
