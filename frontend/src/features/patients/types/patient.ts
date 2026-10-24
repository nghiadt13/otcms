// Hình khớp JSON hợp đồng API của khu Lễ tân (phase-02 §6–§7). Không có tiền sử, dị ứng
// ở bất kỳ kiểu nào của feature này (BR-12).

/** Giới tính theo hợp đồng API (UPPER_SNAKE). */
export type Sex = "MALE" | "FEMALE";

export const SEX_LABELS: Record<Sex, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
};

/**
 * Hồ sơ trả về khi tìm và sau khi tạo. Không kèm SĐT, địa chỉ, email, CCCD: các trường
 * đó chỉ dùng để tìm và nhập, không đưa ngược ra màn tìm kiếm.
 */
export interface PatientMatch {
  id: string;
  patientCode: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: Sex;
  /** Đã gắn tài khoản: chỉ hiện nhãn. Không có thao tác tạo tài khoản ở khu Lễ tân. */
  hasAccount: boolean;
}

/** Cách tìm trên form: theo SĐT + ngày sinh, hoặc theo mã bệnh nhân. */
export type PatientSearchMode = "phone" | "code";

/**
 * Tiêu chí tìm. `fullName` chỉ gửi khi lần tìm trước trả MULTIPLE (BR-14).
 * Query string tương ứng: `patientCode`, hoặc `phone`, `dateOfBirth`, `fullName`.
 */
export type PatientLookupQuery =
  | { patientCode: string }
  | { phone: string; dateOfBirth: string; fullName?: string };

export interface PatientLookupResponse {
  result: "SINGLE" | "NONE" | "MULTIPLE";
  /** MULTIPLE luôn trả `[]` để không lộ hồ sơ khác cùng SĐT và ngày sinh. */
  matches: PatientMatch[];
}

/** Body của POST /api/v1/patients (UC10). */
export interface CreateWalkInPatientRequest {
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: Sex;
  /** 10 chữ số, đã bỏ khoảng trắng. */
  phone: string;
  address: string;
  /** Mã định danh hoặc số hộ chiếu (trẻ em); không có thì null. */
  citizenId: string | null;
  /** Không bắt buộc. Gửi khi lễ tân đã hỏi và bệnh nhân đồng ý ghi email. */
  email?: string;
}
