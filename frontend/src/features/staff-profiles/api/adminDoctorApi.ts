import { apiRequest } from "@/lib/api/client";
import type { AdminDoctor } from "@/lib/domain/adminDoctor";

export type DoctorProfileInput = Pick<
  AdminDoctor,
  | "fullName"
  | "photoUrl"
  | "workContact"
  | "specialties"
  | "qualifications"
  | "biography"
  | "consultationInformation"
>;
export type DoctorAccountOption = {
  accountId: string;
  username: string;
  fullName: string | null;
};
export async function listAdminDoctors(signal?: AbortSignal) {
  return (
    await apiRequest<{ items: AdminDoctor[] }>(
      "/api/v1/admin/doctor-profiles",
      { signal },
    )
  ).items;
}
export async function listDoctorAccounts(signal?: AbortSignal) {
  return (
    await apiRequest<{ items: DoctorAccountOption[] }>(
      "/api/v1/admin/doctor-profiles/eligible-accounts",
      { signal },
    )
  ).items;
}
export function saveDoctorProfile(
  id: string | undefined,
  accountId: string,
  input: DoctorProfileInput,
) {
  return apiRequest<AdminDoctor>(
    id
      ? `/api/v1/admin/doctor-profiles/${encodeURIComponent(id)}`
      : "/api/v1/admin/doctor-profiles",
    {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(id ? input : { ...input, accountId }),
    },
  );
}
export function saveDoctorDirectory(
  doctor: AdminDoctor,
  changes: Partial<
    Pick<AdminDoctor, "publiclyVisible" | "acceptingNewBookings">
  >,
) {
  return apiRequest<AdminDoctor>(
    `/api/v1/admin/doctors/${encodeURIComponent(doctor.doctorProfileId)}/directory-state`,
    {
      method: "PATCH",
      body: JSON.stringify({
        publiclyVisible: doctor.publiclyVisible,
        acceptingNewBookings: doctor.acceptingNewBookings,
        inActiveDirectory: doctor.inActiveDirectory,
        ...changes,
      }),
    },
  );
}
