import type {
  ImagingVisitStatus,
  PatientImagingStudySummary,
} from "../types/imagingStudy";

// Dữ liệu giả ảnh chụp. Mã study trùng `studyId` trong kết quả khám (feature medical-records).

/** Study của hồ sơ không được gắn: trả 403 MSG-40. */
export const IMAGING_FORBIDDEN_STUDY_ID = "std-khac-benh-nhan";
/** Danh sách lọc theo mã này trả 500 MSG-04 (để thử trạng thái lỗi). */
export const IMAGING_LIST_ERROR_PATIENT_ID = "ERR-500";

export interface MockStudy extends PatientImagingStudySummary {
  /** UID DICOM dùng làm khóa để cấu hình viewer trong fixture. */
  studyUid: string;
  visitStatus: ImagingVisitStatus;
}

export const MOCK_STUDIES: MockStudy[] = [
  {
    id: "std-an-xray-1",
    visitId: "vis-an-done",
    visitStatus: "COMPLETED",
    serviceName: "X-quang khớp gối 2 tư thế",
    modality: "XRAY",
    studyDate: "2026-10-23",
    seriesCount: 2,
    patient: {
      id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
      fullName: "Nguyễn Văn An",
      relationship: "SELF",
    },
    finding: "Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày.",
    studyUid: "1.2.826.0.1.3680043.8.498.10230001",
  },
  {
    id: "std-lan-us-1",
    visitId: "vis-lan-incomplete",
    visitStatus: "INCOMPLETE",
    serviceName: "Siêu âm khớp gối phải",
    modality: "ULTRASOUND",
    studyDate: "2026-09-15",
    seriesCount: 3,
    patient: {
      id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
      fullName: "Nguyễn Thị Lan",
      relationship: "PARENT",
    },
    // Kết luận đã có trong kho nhưng API giả ẩn đi vì lượt chưa hoàn tất (xem imagingStudyApi).
    finding: "Tràn dịch khớp gối phải, lượng ít.",
    studyUid: "1.2.826.0.1.3680043.8.498.10230002",
  },
];
