import type { ServiceOrderStatus, VisitStatus } from "@/lib/domain/clinicVisit";

// Kiểu dữ liệu của luồng khám bệnh bác sĩ (R3: UC35–UC45, UC49, UC50). Hình dạng bám theo
// hợp đồng BE `product` (clinical, diagnostic, appointment); trường BE chưa trả thì để null.

/** Trạng thái hiển thị ở "Bệnh nhân hôm nay": lượt khám, hoặc lịch hẹn chưa đến. */
export type TodayStatus = VisitStatus | "NOT_ARRIVED";

export type TodayPatient = {
  /** Mã lịch hẹn; null với bệnh nhân vãng lai. */
  appointmentId: string | null;
  /** Mã lượt khám; null khi lịch hẹn chưa check-in nên chưa mở được. */
  visitId: string | null;
  patientProfileId: string | null;
  patientName: string;
  /** BE chưa trả tuổi, giới, mã bệnh nhân cho danh sách của bác sĩ: null cho tới khi có. */
  age: number | null;
  sex: "Nam" | "Nữ" | "Khác" | null;
  patientCode: string | null;
  serviceName: string;
  /** Giờ hẹn (ISO) hoặc giờ tiếp nhận với vãng lai; null khi chưa có. */
  scheduledAt: string | null;
  /** Số thứ tự trong hàng chờ của bác sĩ; null khi chưa đến (chưa có lượt). */
  queueNumber: number | null;
  source: "BOOKED" | "WALK_IN";
  status: TodayStatus;
};

/** Thông tin tối thiểu của bệnh nhân trong workspace khám (BR-13). */
export type VisitContext = {
  visitId: string;
  appointmentId: string | null;
  patientProfileId: string | null;
  doctorProfileId: string | null;
  patientName: string | null;
  /** BE chưa trả tuổi, giới tính, mã bệnh nhân cho bác sĩ: null cho tới khi có. */
  age: number | null;
  sex: "Nam" | "Nữ" | "Khác" | null;
  patientCode: string | null;
  allergies: string;
  /** false: chưa đọc được tiền sử (lỗi mạng/quyền); "Chưa ghi nhận" khi đó là sai, phải cảnh báo. */
  allergiesLoaded: boolean;
  medicalHistory: string;
  status: VisitStatus | null;
  serviceName: string | null;
  scheduledAt: string | null;
  source: "BOOKED" | "WALK_IN" | null;
};

export type ExaminationStatus = "DRAFT" | "COMPLETED";
export type DiagnosisType = "PRIMARY" | "SECONDARY";
export type Diagnosis = { code: string; type: DiagnosisType };

/** Nội dung bác sĩ nhập ở UC29. */
export type ExaminationInput = {
  reasonForVisit: string;
  symptoms: string;
  examinationFindings: string;
  injuryBodySite: string;
  injurySide: string;
  injuryMechanism: string;
  diagnoses: Diagnosis[];
};

export type Examination = ExaminationInput & {
  /** null: lượt khám chưa có bản ghi khám nào. */
  status: ExaminationStatus | null;
  version: number | null;
};

/** Lời dặn sau khám (UC35). BE chưa có trường "hướng điều trị" (BR-40). */
export type AdviceInput = {
  homeCareAdvice: string;
  activityAdvice: string;
  rehabilitationAdvice: string;
  warningSigns: string;
  followUpRecommendation: string;
};
export type Advice = AdviceInput & {
  examinationStatus: ExaminationStatus | null;
  updatedAt: string | null;
  version: number | null;
};

export type ServiceKind = "IMAGING" | "LABORATORY";
export type CatalogKind = "EXAMINATION" | ServiceKind;

/** Dịch vụ trong danh mục phòng khám: khám (cho tái khám) hoặc cận lâm sàng (chỉ định). */
export type CatalogService = {
  id: string;
  name: string;
  kind: CatalogKind;
  price: number;
  /** X-quang, siêu âm, CT, MRI (chỉ CĐHA). */
  modality: string | null;
  description: string;
};

/** Một dòng trong phiếu chỉ định đang soạn. */
export type OrderDraftItem = {
  serviceId: string;
  clinicalReason: string;
  bodyArea: string;
  instructions: string;
};

/** Kết quả đọc phim gắn với một chỉ định CĐHA (UC42). */
export type ImagingResult = {
  requestId: string;
  /** Phiên bản dùng cho header X-Expected-Version của BE. */
  version: number;
  studyRef: string | null;
  /** Bác sĩ đã mở xem ảnh (diagnostic: REVIEWED); chỉ khi đó mới ghi được kết luận. */
  reviewed: boolean;
  findingsText: string;
  findingsVersion: number | null;
  findingsCompleted: boolean;
  reportId: string | null;
};

export type ParaclinicalOrder = {
  id: string;
  code: string | null;
  round: number | null;
  kind: ServiceKind;
  serviceName: string;
  bodyArea: string;
  clinicalReason: string;
  price: number | null;
  status: ServiceOrderStatus;
  /** Chữ phụ cho trạng thái, ví dụ "Đã chụp, chờ bác sĩ đọc". */
  statusNote: string | null;
  /** BE cho hủy được ngay bây giờ (diagnostic: yêu cầu còn PENDING). */
  cancellable: boolean;
  imaging: ImagingResult | null;
};

export type LabResultLine = {
  indicator: string;
  value: string;
  unit: string;
  reference: string;
  abnormal: boolean;
};
export type LabResult = {
  orderId: string;
  serviceName: string;
  lines: LabResultLine[];
};

export type PrescriptionLine = {
  medicine: string;
  dose: string;
  frequency: string;
  usage: string;
};
export type Prescription = {
  status: "DRAFT" | "ISSUED";
  id: string | null;
  version: number;
  lines: PrescriptionLine[];
  issuedAt: string | null;
};

export type PastVisit = {
  visitId: string;
  visitDate: string;
  status: VisitStatus;
  primaryDiagnosis: string | null;
  advice: string | null;
  prescriptions: number;
  imaging: string[];
  laboratory: string[];
};

export type ResultSheet = {
  version: number;
  status: "CURRENT" | "REPLACED";
  issuedAt: string;
};

/** Việc nào đã có API thật. false: màn vẫn dựng, nút lưu bị khóa và có ghi chú chờ BE. */
export type DoctorCapabilities = {
  laboratoryOrders: boolean;
  orderListing: boolean;
  prescriptions: boolean;
  pastRecords: boolean;
  resultSheet: boolean;
  startExamination: boolean;
  /** BE hiện đòi tệp PDF báo cáo khi hoàn tất kết luận hình ảnh (R3 không đòi). */
  findingsReportPdfRequired: boolean;
};

export type CancelCause = "PATIENT_DECLINED" | "CLINIC_UNABLE";
