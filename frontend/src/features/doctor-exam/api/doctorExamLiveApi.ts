import { ApiError } from "@/lib/api/apiError";
import type { DoctorExamApi } from "./doctorExamApi";
import * as clinical from "./doctorExamLiveClinical";
import * as orders from "./doctorExamLiveOrders";
import * as visits from "./doctorExamLiveVisits";
import * as prescription from "./doctorExamLivePrescription";
import * as records from "./doctorExamLivePastRecords";

// Live Clinical/Diagnostic integration. Lab and result sheets remain unavailable.

const missing = (): never => {
  throw new ApiError(501, { errorCode: "BACKEND_NOT_AVAILABLE" });
};

export const doctorLiveApi: DoctorExamApi = {
  capabilities: {
    laboratoryOrders: false,
    orderListing: true,
    prescriptions: true,
    pastRecords: true,
    resultSheet: false,
    startExamination: true,
    findingsReportPdfRequired: true,
  },
  listTodayPatients: visits.listTodayPatients,
  getVisitContext: visits.getVisitContext,
  startExamination: visits.startExamination,
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
  getPrescription: prescription.getPrescription,
  savePrescription: prescription.savePrescription,
  listPastVisits: records.listPastVisits,
  listResultSheets: async () => [],
  amendResult: async () => missing(),
};
