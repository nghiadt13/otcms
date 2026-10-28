import type { LinkedProfile } from "@/lib/patient-scope/types";

// Đơn thuốc điện tử của khu bệnh nhân (UC44). Chỉ đơn đã phát hành, thuộc lượt Đã hoàn tất.

export interface PrescriptionItem {
  medicine: string;
  /** Ví dụ "1 viên" */
  dose: string;
  /** Ví dụ "2 lần/ngày" */
  frequency: string;
  /** Ví dụ "Uống sau ăn" */
  usage: string;
}

/** Thông tin người bệnh in trên đơn; kèm quan hệ để giao diện nói rõ "của ai". */
export type PrescriptionPatient = Pick<
  LinkedProfile,
  "fullName" | "dateOfBirth" | "patientCode" | "relationship"
>;

export interface PrescriptionDocument {
  id: string;
  /** ISO có offset +07:00 */
  issuedAt: string;
  doctorName: string;
  clinicName: string;
  patient: PrescriptionPatient;
  diagnosis: string;
  items: PrescriptionItem[];
}

/** Dòng danh sách `/portal/prescriptions`. */
export interface PrescriptionListItem {
  id: string;
  issuedAt: string;
  itemCount: number;
  doctorName: string;
  patient: Pick<LinkedProfile, "id" | "fullName" | "relationship">;
}

export interface PrescriptionListQuery {
  /** Chỉ một hồ sơ; bỏ trống = mọi hồ sơ đã gắn với tài khoản. */
  patientId?: string;
}
