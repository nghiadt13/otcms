import type { PrescriptionDocument } from "../types/prescription";

// Dữ liệu giả đơn thuốc. `patientId`, mã BN và ngày sinh trùng kho hồ sơ đã gắn ở
// lib/patient-scope (bộ `default`); `patientId` chỉ dùng để lọc danh sách.

/** Đơn của hồ sơ không được gắn: trả 403 MSG-40. */
export const PRESCRIPTION_FORBIDDEN_ID = "pres-khac-benh-nhan";
/** Đơn chưa phát hành: trả 409 MSG-42. */
export const PRESCRIPTION_NOT_ISSUED_ID = "pres-chua-phat-hanh";
/** Danh sách lọc theo mã này trả 500 MSG-04 (để thử trạng thái lỗi). */
export const PRESCRIPTION_LIST_ERROR_PATIENT_ID = "ERR-500";

const CLINIC_NAME = "Phòng khám Chấn thương Chỉnh hình OTCMS";

export interface MockPrescription {
  patientId: string;
  document: PrescriptionDocument;
}

export const MOCK_PRESCRIPTIONS: MockPrescription[] = [
  {
    patientId: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
    document: {
      id: "pres-an-1",
      issuedAt: "2026-10-23T10:40:00+07:00",
      doctorName: "BS. Lê Thu Hà",
      clinicName: CLINIC_NAME,
      patient: {
        fullName: "Nguyễn Văn An",
        dateOfBirth: "1992-07-21",
        patientCode: "BN-000123",
        relationship: "SELF",
      },
      diagnosis: "M17.1 – Thoái hóa khớp gối nguyên phát",
      items: [
        {
          medicine: "Diclofenac 75 mg",
          dose: "1 viên",
          frequency: "2 lần/ngày",
          usage: "Uống sau ăn sáng và ăn tối",
        },
        {
          medicine: "Glucosamin 500 mg",
          dose: "1 viên",
          frequency: "2 lần/ngày",
          usage: "Uống sau ăn",
        },
        {
          medicine: "Allopurinol 100 mg",
          dose: "1 viên",
          frequency: "1 lần/ngày",
          usage: "Uống sau ăn sáng",
        },
      ],
    },
  },
  {
    patientId: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
    document: {
      id: "pres-lan-1",
      issuedAt: "2026-07-03T09:30:00+07:00",
      doctorName: "BS. Đỗ Minh Tuấn",
      clinicName: CLINIC_NAME,
      patient: {
        fullName: "Nguyễn Thị Lan",
        dateOfBirth: "1936-03-14",
        patientCode: "BN-000456",
        relationship: "PARENT",
      },
      diagnosis: "M54.5 – Đau thắt lưng",
      items: [
        {
          medicine: "Paracetamol 500 mg",
          dose: "2 viên",
          frequency: "3 lần/ngày",
          usage: "Uống khi đau, cách nhau 6 giờ",
        },
        {
          medicine: "Thiocolchicosid 4 mg",
          dose: "1 viên",
          frequency: "2 lần/ngày",
          usage: "Uống sau ăn",
        },
      ],
    },
  },
];
