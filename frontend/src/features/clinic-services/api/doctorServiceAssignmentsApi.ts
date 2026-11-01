import { apiRequest } from "@/lib/api/client";

export type DoctorServiceAssignment = {
  id: string;
  serviceId: string;
  doctorProfileId: string;
};

export type ActiveDoctorOption = {
  id: string;
  displayName: string;
};

type StaffProfilesPage = {
  items: Array<{
    id: string;
    role: string;
    status: string;
    displayName: string;
  }>;
  page: number;
  totalPages: number;
};

export async function listActiveDoctorOptions(
  signal?: AbortSignal,
): Promise<ActiveDoctorOption[]> {
  const doctors: ActiveDoctorOption[] = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const params = new URLSearchParams({
      role: "ORTHOPEDIC_DOCTOR",
      status: "ACTIVE",
      page: String(page),
      size: "100",
    });
    const result = await apiRequest<StaffProfilesPage>(
      `/api/v1/admin/staff-profiles?${params.toString()}`,
      { signal },
    );
    doctors.push(
      ...result.items.map((doctor) => ({
        id: doctor.id,
        displayName: doctor.displayName,
      })),
    );
    totalPages = result.totalPages;
    page += 1;
  }
  return doctors;
}

export function listDoctorServiceAssignments(
  serviceId: string,
  signal?: AbortSignal,
): Promise<DoctorServiceAssignment[]> {
  return apiRequest<DoctorServiceAssignment[]>(
    `/api/v1/admin/clinic-services/${encodeURIComponent(serviceId)}/doctor-assignments`,
    { signal },
  );
}

export function assignDoctorToService(
  serviceId: string,
  doctorProfileId: string,
) {
  return apiRequest<DoctorServiceAssignment>(
    `/api/v1/admin/clinic-services/${encodeURIComponent(serviceId)}/doctor-assignments`,
    {
      method: "POST",
      body: JSON.stringify({ doctorProfileId }),
    },
  );
}
