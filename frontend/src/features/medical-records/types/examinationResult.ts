import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { VisitStatus } from "@/lib/domain/clinicVisit";

// Kết quả khám của khu bệnh nhân (UC41). BE chỉ trả dữ liệu của các khối trong
// `visibleBlocks`; FE dựng đúng các khối đó và không tự suy từ trạng thái lượt.

/** Khối nội dung của một lượt khám. Thứ tự hiển thị cố định ở màn chi tiết. */
export type ResultBlock =
  | "EXAMINATION"
  | "IMAGING"
  | "LABORATORY"
  | "PRESCRIPTIONS"
  | "ADMINISTRATIVE"
  | "RESULT_SHEET";

/** Chỉ hai trạng thái này được xem ở khu bệnh nhân; các giai đoạn khác ở màn lượt khám. */
export type ResultVisitStatus = Extract<
  VisitStatus,
  "COMPLETED" | "INCOMPLETE"
>;

/** Người khám, kèm quan hệ để giao diện luôn nói rõ "của ai". */
export type ResultPatientRef = Pick<
  LinkedProfile,
  "id" | "fullName" | "relationship"
>;

export interface Icd10Ref {
  code: string;
  title: string;
}

export interface ExaminationSection {
  reason: string;
  symptoms: string;
  findings: string;
  primaryDiagnoses: Icd10Ref[];
  secondaryDiagnoses: Icd10Ref[];
  treatmentDirection: string;
  advice: string;
  /** YYYY-MM-DD, null khi không hẹn tái khám. */
  followUpDate: string | null;
}

export interface ImagingResultItem {
  /** null khi chỉ định chưa gắn study DICOM. */
  studyId: string | null;
  serviceName: string;
  /** Kết luận của bác sĩ chỉ có khi lượt Đã hoàn tất. */
  finding: string | null;
}

export type LabFlag = "HIGH" | "LOW";

export interface LabLine {
  indicator: string;
  value: string;
  unit: string;
  referenceRange: string;
  /** null = trong khoảng tham chiếu. */
  flag: LabFlag | null;
}

export interface LabGroup {
  serviceName: string;
  lines: LabLine[];
}

export interface PrescriptionRef {
  id: string;
  issuedAt: string;
  itemCount: number;
}

export interface AdministrativeInfo {
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: "MALE" | "FEMALE";
  patientCode: string;
  phone: string;
  address: string | null;
}

export interface ResultSheetVersion {
  version: number;
  /** Bản REPLACED là bản cũ đã bị thay thế. */
  status: "CURRENT" | "REPLACED";
  /** ISO có offset +07:00 */
  issuedAt: string;
  /** URL có chữ ký và hạn do BE cấp; FE không lưu lại. */
  fileUrl: string;
}

export interface ExaminationResult {
  visitId: string;
  /** YYYY-MM-DD */
  visitDate: string;
  status: ResultVisitStatus;
  patient: ResultPatientRef;
  previousVisitId: string | null;
  nextVisitId: string | null;
  visibleBlocks: ResultBlock[];
  examination?: ExaminationSection;
  imaging?: ImagingResultItem[];
  laboratory?: LabGroup[];
  prescriptions?: PrescriptionRef[];
  administrative?: AdministrativeInfo;
  resultSheets?: ResultSheetVersion[];
}

/** Dòng danh sách `/portal/results`. */
export interface ResultVisitSummary {
  visitId: string;
  visitDate: string;
  status: ResultVisitStatus;
  serviceName: string;
  patient: ResultPatientRef;
}

export interface ResultVisitListQuery {
  /** Chỉ một hồ sơ; bỏ trống = mọi hồ sơ đã gắn với tài khoản. */
  patientId?: string;
}
