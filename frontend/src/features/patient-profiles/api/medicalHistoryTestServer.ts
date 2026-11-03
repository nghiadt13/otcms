import { ApiError } from "@/lib/api/apiError";
import type { MedicalHistoryProfile } from "./medicalHistoryProfilesApi";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

/** Fixture chỉ dùng trong test, mô phỏng contract KAN-28 tại ranh giới apiRequest. */
export const HISTORY_PROFILE_ID = "f0360000-0000-4000-8000-000000000001";
export const HISTORY_DEPENDENT_ID = "f0360000-0000-4000-8000-000000000002";
export const HISTORY_PATH = `/api/v1/patient-profiles/${HISTORY_PROFILE_ID}/medical-history`;

export function emptyHistory(): MedicalHistory {
  return {
    conditions: "",
    allergies: "",
    procedures: "",
    injuries: "",
    orthopedicHistory: "",
    updatedAt: null,
    updatedBy: null,
    version: 0,
  };
}

export function historyFixture(): MedicalHistory {
  return {
    ...emptyHistory(),
    conditions: "Đang theo dõi bệnh nền.",
    allergies: "Penicillin.",
    version: 3,
    updatedAt: "2026-10-20T09:30:00+07:00",
    updatedBy: "Bác sĩ điều trị",
  };
}

export class MedicalHistoryTestServer {
  profiles: MedicalHistoryProfile[] = [
    {
      patientProfileId: HISTORY_PROFILE_ID,
      fullName: "Hồ sơ thật",
      relationship: "SELF",
      linkStatus: "CONFIRMED",
    },
  ];
  history = historyFixture();
  getError: unknown = null;
  putError: unknown = null;
  listError: unknown = null;

  request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
    if (path === "/api/v1/me/patient-profiles") {
      if (this.listError) throw this.listError;
      return structuredClone({ items: this.profiles }) as T;
    }
    if (!path.endsWith("/medical-history"))
      throw new Error("Unexpected API path");
    if (init.method !== "PUT") {
      if (this.getError) throw this.getError;
      return structuredClone(this.history) as T;
    }
    if (this.putError) throw this.putError;
    const body = JSON.parse(String(init.body)) as MedicalHistoryUpdate;
    if (body.version !== this.history.version)
      throw new ApiError(409, {
        errorCode: "STALE_VERSION",
        detail: "Localized backend text",
      });
    this.history = {
      ...body,
      updatedAt: "2026-11-03T21:00:00+07:00",
      updatedBy: "Người cập nhật",
      version: body.version + 1,
    };
    return structuredClone(this.history) as T;
  };
}
