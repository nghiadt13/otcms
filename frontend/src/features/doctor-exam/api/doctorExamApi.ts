import type {
  Advice,
  AdviceInput,
  CancelCause,
  CatalogService,
  DoctorCapabilities,
  Examination,
  ExaminationInput,
  ImagingResult,
  LabResult,
  OrderDraftItem,
  ParaclinicalOrder,
  PastVisit,
  Prescription,
  ResultSheet,
  TodayPatient,
  VisitContext,
} from "../types/doctorExam";
import { doctorDemoApi } from "./doctorExamDemoApi";
import { isDoctorDemoMode } from "./doctorDemoMode";
import { doctorLiveApi } from "./doctorExamLiveApi";

/** Tiền sử bệnh của bệnh nhân (UC12): 5 ô văn bản và phiên bản để chống ghi đè. */
export type MedicalHistory = {
  conditions: string;
  allergies: string;
  procedures: string;
  injuries: string;
  orthopedicHistory: string;
  version: number;
};

/** Lịch hẹn của bệnh nhân với bác sĩ này (UC24). */
export type PatientAppointment = {
  id: string;
  serviceName: string;
  startTime: string;
  status: string;
};

export type FollowUpInput = {
  serviceId: string;
  /** ISO có múi giờ. */
  startTime: string;
  purpose: string;
  reason: string;
};

/**
 * Cổng duy nhất mà màn hình bác sĩ dùng để nói chuyện với dữ liệu. Có hai bản:
 *  - live: `apiRequest` tới BE `product` (hợp đồng lấy từ source, xem plans/reports);
 *  - demo: dữ liệu trong bộ nhớ để xem giao diện/chạy thử khi BE còn thiếu.
 * Hàm nào BE chưa có thì bản live ném lỗi và `capabilities` báo false để màn khóa nút.
 */
export interface DoctorExamApi {
  capabilities: DoctorCapabilities;
  listTodayPatients(signal?: AbortSignal): Promise<TodayPatient[]>;
  getVisitContext(visitId: string, signal?: AbortSignal): Promise<VisitContext>;
  /** Bác sĩ bắt đầu khám: hẹn đặt trước → IN_PROGRESS, vãng lai → bước chăm sóc. */
  startExamination(context: VisitContext): Promise<void>;
  getExamination(visitId: string, signal?: AbortSignal): Promise<Examination>;
  saveExamination(
    visitId: string,
    input: ExaminationInput,
    mode: "draft" | "complete",
  ): Promise<Examination>;
  getAdvice(visitId: string, signal?: AbortSignal): Promise<Advice | null>;
  saveAdvice(visitId: string, input: AdviceInput): Promise<Advice>;
  bookFollowUp(context: VisitContext, input: FollowUpInput): Promise<void>;
  listCatalog(signal?: AbortSignal): Promise<CatalogService[]>;
  listOrders(
    visitId: string,
    signal?: AbortSignal,
  ): Promise<ParaclinicalOrder[]>;
  submitOrders(
    visitId: string,
    items: OrderDraftItem[],
    catalog: CatalogService[],
    idempotencyKey: string,
    patientConsentConfirmed: boolean,
  ): Promise<ParaclinicalOrder[]>;
  cancelOrder(
    order: ParaclinicalOrder,
    cause: CancelCause,
    reason: string,
  ): Promise<void>;
  saveFindings(
    order: ParaclinicalOrder,
    content: string,
  ): Promise<ImagingResult>;
  completeFindings(
    order: ParaclinicalOrder,
    content: string,
    report: File | null,
  ): Promise<ImagingResult>;
  listLabResults(visitId: string, signal?: AbortSignal): Promise<LabResult[]>;
  getPrescription(
    visitId: string,
    signal?: AbortSignal,
  ): Promise<Prescription | null>;
  savePrescription(
    visitId: string,
    value: Prescription,
    issue: boolean,
  ): Promise<Prescription>;
  getMedicalHistory(
    patientProfileId: string,
    signal?: AbortSignal,
  ): Promise<MedicalHistory>;
  saveMedicalHistory(
    patientProfileId: string,
    value: MedicalHistory,
  ): Promise<MedicalHistory>;
  listPatientAppointments(
    patientProfileId: string,
    signal?: AbortSignal,
  ): Promise<PatientAppointment[]>;
  listPastVisits(
    currentVisitId: string,
    signal?: AbortSignal,
  ): Promise<PastVisit[]>;
  listResultSheets(
    visitId: string,
    signal?: AbortSignal,
  ): Promise<ResultSheet[]>;
  amendResult(visitId: string, reason: string): Promise<ResultSheet[]>;
}

/** Chọn bản theo từng lần gọi để công tắc demo có hiệu lực ngay, không cần tải lại module. */
export function getDoctorExamApi(): DoctorExamApi {
  return isDoctorDemoMode() ? doctorDemoApi : doctorLiveApi;
}
