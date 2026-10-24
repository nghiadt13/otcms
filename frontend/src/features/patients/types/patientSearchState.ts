import type { PatientLookupResponse } from "./patient";

// Kiểu trạng thái UI dùng chung giữa hook tìm, khối kết quả và dialog tạo hồ sơ. Không phải
// hình API nên tách khỏi `patient.ts`.

/** Giá trị điền sẵn cho dialog tạo hồ sơ. Ngày sinh dạng dd/mm/yyyy để khớp ô nhập. */
export interface WalkInInitialValues {
  phone: string;
  dateOfBirth: string;
  fullName: string;
}

/** Tiêu chí của lần tìm lại sau khi tạo bị trùng hồ sơ (ngày sinh YYYY-MM-DD). */
export interface DuplicateCriteria {
  phone: string;
  dateOfBirth: string;
}

/** Trạng thái khối kết quả: chưa tìm, đang tìm, hoặc đã có kết quả. */
export type SearchState =
  | { kind: "idle" }
  | { kind: "loading" }
  | {
      kind: "ready";
      response: PatientLookupResponse;
      /** Chỉ có khi tìm theo SĐT + ngày sinh: điền sẵn nếu cần tạo hồ sơ. */
      prefill: WalkInInitialValues | null;
    };
