import { publicApiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { PublicDoctor, PublicDoctorDetail } from "../types/publicDoctor";
type DoctorResponse = {
  doctorProfileId: string;
  fullName: string;
  photoUrl: string | null;
  specialties: string | null;
  qualifications: string | null;
  biography: string | null;
  consultationInformation: string | null;
};
function mapDoctor(d: DoctorResponse): PublicDoctor {
  return {
    id: d.doctorProfileId,
    fullName: d.fullName,
    photoUrl: d.photoUrl,
    specialties: d.specialties ? [d.specialties] : [],
    qualifications: d.qualifications ?? "",
    biography: d.biography ?? "",
    consultationInfo: d.consultationInformation ?? "",
  };
}
export async function listPublicDoctors(
  signal?: AbortSignal,
): Promise<{ items: PublicDoctor[] }> {
  const data = await publicApiRequest<{ items: DoctorResponse[] }>(
    "/api/v1/doctors",
    { signal },
  );
  return { items: data.items.map(mapDoctor) };
}
export async function getPublicDoctor(
  id: string,
  signal?: AbortSignal,
): Promise<PublicDoctorDetail> {
  try {
    const data = await publicApiRequest<DoctorResponse>(
      `/api/v1/doctors/${encodeURIComponent(id)}`,
      { signal },
    );
    return { ...mapDoctor(data), availability: [] };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404)
      throw new ApiError(404, { code: "MSG-24" });
    throw error;
  }
}
export async function listFeaturedDoctors(limit = 4): Promise<PublicDoctor[]> {
  return (await listPublicDoctors()).items.slice(0, Math.max(0, limit));
}
