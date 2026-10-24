import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import type { PatientSex } from "../types/patientProfile";

// DỮ LIỆU GIẢ của phòng khám cho lớp API hồ sơ. Mỗi người là một hồ sơ tại phòng khám, gồm
// cả hồ sơ chưa gắn. Việc gắn với tài khoản nằm ở kho dùng chung `linkedProfilesMockStore`.
// Ba hồ sơ đã gắn mặc định (An, Lan, Huy) khớp id, mã và tên với kho đó; test kiểm tra điều này.

const account = getMockCurrentActor("patient");
export const MOCK_ACCOUNT_ID = account.accountId;
export const MOCK_ACCOUNT_EMAIL = account.email ?? "";
export const MOCK_ACCOUNT_NAME = account.displayName;

/** Tài khoản khác giữ hồ sơ này làm Bản thân; dùng để thử 409 MSG-21. */
const OTHER_ACCOUNT_ID = "acc-binh";

export interface ClinicPatientRecord {
  id: string;
  patientCode: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: PatientSex;
  phone: string;
  address: string | null;
  citizenId: string | null;
  email: string | null;
  hasVisits: boolean;
  /** Tài khoản đang giữ hồ sơ này làm Bản thân; null nếu chưa có. */
  selfAccountId: string | null;
}

export const PROFILE_MOCK_IDS = {
  an: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
  lan: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
  huy: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f03",
  binh: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f11",
  bao: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f12",
  hoa: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f13",
  duc: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f14",
} as const;

const AN: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.an,
  patientCode: "BN-000123",
  fullName: "Nguyễn Văn An",
  dateOfBirth: "1992-07-21",
  sex: "MALE",
  phone: "0912345678",
  address: "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
  citizenId: null,
  email: MOCK_ACCOUNT_EMAIL,
  hasVisits: true,
  selfAccountId: MOCK_ACCOUNT_ID,
};

const LAN: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.lan,
  patientCode: "BN-000456",
  fullName: "Nguyễn Thị Lan",
  dateOfBirth: "1936-03-14",
  sex: "FEMALE",
  phone: "0987654321",
  address: "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
  citizenId: "079136001234",
  email: null,
  hasVisits: true,
  selfAccountId: null,
};

const HUY: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.huy,
  patientCode: "BN-000789",
  fullName: "Nguyễn Gia Huy",
  dateOfBirth: "2020-05-09",
  sex: "MALE",
  phone: "0912345678",
  address: "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
  citizenId: null,
  email: null,
  hasVisits: false,
  selfAccountId: null,
};

// Bố và con dùng chung email an@example.com: test gợi ý hai hồ sơ. Bố đã có tài khoản riêng.
const BINH: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.binh,
  patientCode: "BN-000130",
  fullName: "Nguyễn Văn Bình",
  dateOfBirth: "1962-11-02",
  sex: "MALE",
  phone: "0933445566",
  address: "45 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
  citizenId: "079062003456",
  email: MOCK_ACCOUNT_EMAIL,
  hasVisits: true,
  selfAccountId: OTHER_ACCOUNT_ID,
};

const BAO: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.bao,
  patientCode: "BN-000131",
  fullName: "Nguyễn Gia Bảo",
  dateOfBirth: "2018-02-14",
  sex: "MALE",
  phone: "0933445566",
  address: "45 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
  citizenId: null,
  email: MOCK_ACCOUNT_EMAIL,
  hasVisits: false,
  selfAccountId: null,
};

// Hồ sơ vãng lai không email: không bao giờ được gợi ý, chỉ hướng dẫn ra quầy.
const HOA: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.hoa,
  patientCode: "BN-000140",
  fullName: "Lê Thị Hoa",
  dateOfBirth: "1980-09-30",
  sex: "FEMALE",
  phone: "0977112233",
  address: "7 Trần Hưng Đạo, Quận 5, TP. Hồ Chí Minh",
  citizenId: null,
  email: null,
  hasVisits: true,
  selfAccountId: null,
};

// Trùng SĐT + ngày sinh với lần tạo mẫu: thử nhánh 409 DUPLICATE_PROFILE.
const DUC: ClinicPatientRecord = {
  id: PROFILE_MOCK_IDS.duc,
  patientCode: "BN-000150",
  fullName: "Phạm Văn Đức",
  dateOfBirth: "1990-01-01",
  sex: "MALE",
  phone: "0900000000",
  address: "3 Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh",
  citizenId: null,
  email: null,
  hasVisits: true,
  selfAccountId: null,
};

export const DEFAULT_CLINIC_RECORDS: ClinicPatientRecord[] = [
  AN,
  LAN,
  HUY,
  BINH,
  BAO,
  HOA,
  DUC,
];

/** Biến thể "bố chỉ đưa con đi khám": không có Bản thân, chỉ một hồ sơ con được gợi ý. */
export const NO_SELF_CLINIC_RECORDS: ClinicPatientRecord[] = [
  LAN,
  HUY,
  BAO,
  HOA,
  DUC,
];

