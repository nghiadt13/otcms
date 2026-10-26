import type {
  AdministrativeInfo,
  ResultPatientRef,
} from "../types/examinationResult";

// Người bệnh và đường dẫn phiếu mẫu trong dữ liệu giả kết quả khám. Mã hồ sơ, mã BN và
// ngày sinh trùng kho hồ sơ đã gắn (lib/patient-scope, bộ default).

export const SHEET_SAMPLE_URL = "/mock/result-sheet-sample.pdf";

export const AN: ResultPatientRef = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
  fullName: "Nguyễn Văn An",
  relationship: "SELF",
};
export const LAN: ResultPatientRef = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
  fullName: "Nguyễn Thị Lan",
  relationship: "PARENT",
};

export const AN_ADMIN: AdministrativeInfo = {
  fullName: "Nguyễn Văn An",
  dateOfBirth: "1992-07-21",
  sex: "MALE",
  patientCode: "BN-000123",
  phone: "0901234567",
  address: "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
};
export const LAN_ADMIN: AdministrativeInfo = {
  fullName: "Nguyễn Thị Lan",
  dateOfBirth: "1936-03-14",
  sex: "FEMALE",
  patientCode: "BN-000456",
  phone: "0912345678",
  address: null,
};
