import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { VisitStatus } from "@/lib/domain/clinicVisit";

// Ảnh chụp chẩn đoán của khu bệnh nhân (UC51). Chỉ study thuộc lượt Đã hoàn tất hoặc
// Chưa hoàn tất; kết luận chỉ có khi lượt Đã hoàn tất.

export type ImagingModality = "XRAY" | "ULTRASOUND" | "CT" | "MRI";

/** Nhãn hiển thị của loại ảnh (không phải mã trạng thái nên không có tone). */
export const IMAGING_MODALITY_LABELS: Record<ImagingModality, string> = {
  XRAY: "X-quang",
  ULTRASOUND: "Siêu âm",
  CT: "CT",
  MRI: "MRI",
};

export type ImagingVisitStatus = Extract<VisitStatus, "COMPLETED" | "INCOMPLETE">;

export type ImagingPatientRef = Pick<LinkedProfile, "id" | "fullName" | "relationship">;

/** Dòng danh sách `/portal/images`: không có `viewerUrl`. */
export interface PatientImagingStudySummary {
  id: string;
  visitId: string;
  visitStatus: ImagingVisitStatus;
  serviceName: string;
  modality: ImagingModality;
  /** YYYY-MM-DD */
  studyDate: string;
  seriesCount: number;
  patient: ImagingPatientRef;
  finding: string | null;
}

/** Chi tiết study. `viewerUrl` có hạn, do BE cấp; null khi chưa mở được trình xem. */
export interface PatientImagingStudy extends PatientImagingStudySummary {
  viewerUrl: string | null;
}

export interface ImagingStudyListQuery {
  /** Chỉ một hồ sơ; bỏ trống = mọi hồ sơ đã gắn với tài khoản. */
  patientId?: string;
}
