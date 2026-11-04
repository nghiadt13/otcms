import { ApiError } from "@/lib/api/apiError";
import type { DoctorExamApi } from "./doctorExamApi";
import * as clinical from "./doctorExamLiveClinical";
import * as orders from "./doctorExamLiveOrders";
import * as visits from "./doctorExamLiveVisits";

// Bản live của DoctorExamApi: ghép các phần đã có endpoint trên BE `product`. Phần BE chưa có
// (xét nghiệm, đơn thuốc, hồ sơ cũ, phiếu kết quả) ném 501 BACKEND_NOT_AVAILABLE; màn dựa vào
// `capabilities` để khóa nút và hiện ghi chú nên không gọi tới các hàm này.

const missing = (): never => {
  throw new ApiError(501, { errorCode: "BACKEND_NOT_AVAILABLE" });
};

export const doctorLiveApi: DoctorExamApi = {
  capabilities: {
    laboratoryOrders: false,
    orderListing: false,
    prescriptions: false,
    pastRecords: false,
    resultSheet: false,
    closeWithEvidence: false,
    startExamination: true,
    findingsReportPdfRequired: true,
  },
  listTodayPatients: visits.listTodayPatients,
  getVisitContext: visits.getVisitContext,
  startExamination: visits.startExamination,
  completeVisit: visits.completeVisit,
  bookFollowUp: visits.bookFollowUp,
  getMedicalHistory: visits.getMedicalHistory,
  saveMedicalHistory: visits.saveMedicalHistory,
  listPatientAppointments: visits.listPatientAppointments,
  getExamination: clinical.getExamination,
  saveExamination: clinical.saveExamination,
  getAdvice: clinical.getAdvice,
  saveAdvice: clinical.saveAdvice,
  listCatalog: orders.listCatalog,
  listOrders: orders.listOrders,
  submitOrders: orders.submitOrders,
  cancelOrder: orders.cancelOrder,
  saveFindings: orders.saveFindings,
  completeFindings: orders.completeFindings,
  listLabResults: async () => [],
  getPrescription: async () => null,
  savePrescription: async () => missing(),
  listPastVisits: async () => [],
  listResultSheets: async () => [],
  amendResult: async () => missing(),
};
