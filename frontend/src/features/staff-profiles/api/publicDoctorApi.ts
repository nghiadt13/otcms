import { publicApiRequest } from "@/lib/api/client";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { ApiError } from "@/lib/api/apiError";
import type { PublicDoctor } from "../types/publicDoctor";

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
  const photo = d.photoUrl?.startsWith("/")
    ? `${apiBaseUrl}${d.photoUrl}`
    : d.photoUrl;
  const specList = d.specialties
    ? d.specialties
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : [];
  return {
    id: d.doctorProfileId,
    fullName: d.fullName,
    photoUrl: photo,
    specialties: specList,
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

// The profile endpoint also returns a per-day summary; the page does not use it. Free times come
// from the booking availability API so that the list, the profile and booking agree.
export async function getPublicDoctor(
  id: string,
  signal?: AbortSignal,
): Promise<PublicDoctor> {
  try {
    const data = await publicApiRequest<DoctorResponse>(
      `/api/v1/doctors/${encodeURIComponent(id)}`,
      { signal },
    );
    return mapDoctor(data);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404)
      throw new ApiError(404, { code: "MSG-24" });
    throw error;
  }
}

export async function listFeaturedDoctors(limit = 4): Promise<PublicDoctor[]> {
  return (await listPublicDoctors()).items.slice(0, Math.max(0, limit));
}
