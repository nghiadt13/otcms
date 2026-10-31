import type { MedicalHistory } from "../types/patientProfile";
import { PROFILE_MOCK_IDS } from "./patientProfileMockData";

// Dữ liệu giả tiền sử bệnh và tiện ích thời gian của API. Hai hồ sơ có sẵn tiền sử để thử
// lưu/STALE; các hồ sơ còn lại trống (version 0).

export const DEFAULT_MEDICAL_HISTORIES: Record<string, MedicalHistory> = {
  [PROFILE_MOCK_IDS.an]: {
    conditions: "Thoái hóa cột sống thắt lưng (2023).",
    allergies: "Penicillin: nổi mẩn đỏ.",
    procedures: "Nội soi khớp gối phải (2019).",
    injuries: "Bong gân cổ chân trái (2021).",
    orthopedicHistory: "Đau thần kinh tọa, đang theo dõi.",
    updatedAt: "2026-10-20T09:30:00+07:00",
    updatedBy: "BS. Phạm Thu Hà",
    version: 3,
  },
  [PROFILE_MOCK_IDS.lan]: {
    conditions: "Đái tháo đường type 2, tăng huyết áp.",
    allergies: "",
    procedures: "",
    injuries: "Gãy cổ xương đùi phải (2018).",
    orthopedicHistory: "Thay khớp háng phải (2019).",
    updatedAt: "2026-09-02T14:10:00+07:00",
    updatedBy: "BS. Lê Quang Minh",
    version: 2,
  },
};

export function emptyMedicalHistory(): MedicalHistory {
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

/** Thời điểm hiện tại dạng ISO có offset +07:00 (quy ước của API). */
export function vietnamIsoNow(now: Date = new Date()): string {
  const shifted = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  return `${shifted.toISOString().slice(0, 19)}+07:00`;
}
