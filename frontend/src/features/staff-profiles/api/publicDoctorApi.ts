import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import { publicApiRequest } from "@/lib/api/client";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { todayIsoDate } from "@/lib/format/formatDate";
import type { DoctorInfo } from "../types/staffProfile";
import type { PublicDoctor, PublicDoctorDetail } from "../types/publicDoctor";
import { buildDoctorAvailabilityMock } from "./publicDoctorAvailabilityMock";
import { mockStore, type MockStaffRecord } from "./staffProfileMockData";

// LỚP API GIẢ cho /api/v1/public/doctors (không cần đăng nhập). Đọc cùng kho hồ sơ với màn
// Admin nên bật/tắt "hiển thị" ở Admin đổi ngay danh bạ công khai (UC18 AF1).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 200;
const USE_MOCK = process.env.NODE_ENV === "test";
type DoctorBackend = {
  doctorProfileId: string;
  fullName: string;
  photoUrl: string | null;
  specialties: string;
  qualifications: string;
  biography: string;
  consultationInformation: string;
};
const fromBackend = (doctor: DoctorBackend): PublicDoctor => ({
  id: doctor.doctorProfileId,
  fullName: doctor.fullName,
  photoUrl: doctor.photoUrl?.startsWith("/")
    ? `${apiBaseUrl}${doctor.photoUrl}`
    : doctor.photoUrl,
  specialties: doctor.specialties ? doctor.specialties.split(",") : [],
  qualifications: doctor.qualifications,
  biography: doctor.biography,
  consultationInfo: doctor.consultationInformation,
});

type PublicDoctorRecord = MockStaffRecord & { doctor: DoctorInfo };

/** Bác sĩ công khai: đang hoạt động, đúng vai trò bác sĩ và đã bật hiển thị. */
const isPublicDoctor = (
  record: MockStaffRecord,
): record is PublicDoctorRecord =>
  record.status === "ACTIVE" &&
  record.role === "ORTHOPEDIC_DOCTOR" &&
  record.doctor?.isPublic === true;

function toPublicDoctor(record: PublicDoctorRecord): PublicDoctor {
  return {
    id: record.id,
    fullName: record.displayName,
    photoUrl: record.photoUrl,
    specialties: [...record.doctor.specialties],
    qualifications: record.doctor.qualifications,
    biography: record.doctor.biography,
    consultationInfo: record.doctor.consultationInfo,
  };
}

/** GET /api/v1/public/doctors — không có `availability`. */
export async function listPublicDoctors(
  signal?: AbortSignal,
): Promise<{ items: PublicDoctor[] }> {
  if (!USE_MOCK) {
    const response = await publicApiRequest<{ items: DoctorBackend[] }>(
      "/api/v1/doctors",
      { signal },
    );
    return { items: response.items.map(fromBackend) };
  }
  await mockDelay(LATENCY_MS, signal);
  const items = mockStore.profiles.filter(isPublicDoctor).map(toPublicDoctor);
  return clone({ items });
}

/** GET /api/v1/public/doctors/{id} — không còn công khai: 404 MSG-24. */
export async function getPublicDoctor(
  id: string,
  signal?: AbortSignal,
): Promise<PublicDoctorDetail> {
  if (!USE_MOCK) {
    const doctor = await publicApiRequest<DoctorBackend>(
      `/api/v1/doctors/${encodeURIComponent(id)}`,
      { signal },
    );
    return { ...fromBackend(doctor), availability: [] };
  }
  await mockDelay(LATENCY_MS, signal);
  const record = mockStore.profiles.find((item) => item.id === id);
  if (!record || !isPublicDoctor(record)) mockFail(404, "MSG-24");
  return clone({
    ...toPublicDoctor(record),
    availability: buildDoctorAvailabilityMock(record.username, todayIsoDate()),
  });
}

/** Bác sĩ nổi bật trên trang chủ: `limit` mục đầu của danh bạ công khai. */
export async function listFeaturedDoctors(limit = 4): Promise<PublicDoctor[]> {
  const { items } = await listPublicDoctors();
  return items.slice(0, limit);
}
