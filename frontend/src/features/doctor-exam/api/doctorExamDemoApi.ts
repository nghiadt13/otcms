import { prescriptionItems } from "../schema/prescription";
import { mockDelay, mockFail, clone } from "@/lib/api/mockServer";
import type {
  Advice,
  AdviceInput,
  CancelCause,
  CatalogService,
  Examination,
  ExaminationInput,
  ImagingResult,
  LabResult,
  OrderDraftItem,
  ParaclinicalOrder,
  Prescription,
  ResultSheet,
  VisitContext,
} from "../types/doctorExam";
import type { DoctorExamApi, MedicalHistory } from "./doctorExamApi";
import {
  DEMO_CATALOG,
  DEMO_HISTORY,
  DEMO_LAB_LINES,
  DEMO_PAST_VISITS,
  createDemoVisits,
  demoTodayPatients,
  type DemoOrder,
  type DemoVisit,
} from "./doctorExamDemoStore";

// Bản demo của DoctorExamApi: chạy hoàn toàn trong bộ nhớ, mô phỏng các luật chính của BE
// (một chẩn đoán chính, hoàn tất khám cần đủ điều kiện BR-33, chỉ định tự "thanh toán" rồi "có
// kết quả" sau vài giây). Chỉ dùng khi bật chế độ demo.

const PAID_AFTER_MS = 5_000;
const RESULT_AFTER_MS = 12_000;

let visits = createDemoVisits();
let history: MedicalHistory = { ...DEMO_HISTORY };

const wait = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 250, signal);

const visitOf = (id: string): DemoVisit =>
  visits[id] ?? mockFail(404, "MSG-41");

/** Chỉ dùng cho test: đưa kho dữ liệu mẫu về ban đầu. */
export function resetDoctorDemo() {
  visits = createDemoVisits();
  history = { ...DEMO_HISTORY };
}

function imagingNote(imaging: ImagingResult) {
  if (imaging.findingsCompleted) return "Đã có kết luận";
  return imaging.reviewed ? "Đã xem ảnh" : "Đã có ảnh, chờ bác sĩ xem";
}

/** Đẩy chỉ định vừa tạo qua các trạng thái theo thời gian để thấy được cả luồng. */
function progressed(order: DemoOrder, now: number): DemoOrder {
  if (!order.createdAt || order.status === "CANCELLED") return order;
  const elapsed = now - order.createdAt;
  if (elapsed < PAID_AFTER_MS)
    return {
      ...order,
      status: "PENDING_PAYMENT",
      statusNote: null,
      cancellable: true,
    };
  if (elapsed < RESULT_AFTER_MS)
    return {
      ...order,
      status: "PAID",
      statusNote: order.kind === "IMAGING" ? "Chờ chụp" : "Chờ lấy mẫu",
      cancellable: true,
    };
  if (order.kind === "LABORATORY")
    return {
      ...order,
      status: "RESULTS_AVAILABLE",
      statusNote: null,
      cancellable: false,
    };
  const imaging = order.imaging ?? {
    requestId: order.id,
    version: 3,
    studyRef: `demo-study-${order.id}`,
    reviewed: false,
    findingsText: "",
    findingsVersion: null,
    findingsCompleted: false,
    reportId: null,
  };
  return {
    ...order,
    status: "RESULTS_AVAILABLE",
    cancellable: false,
    imaging,
    statusNote: imagingNote(imaging),
  };
}

const strip = (order: DemoOrder): ParaclinicalOrder => {
  const { createdAt, ...rest } = order;
  void createdAt;
  return clone(rest);
};

function updateOrder(
  visitId: string,
  orderId: string,
  change: Partial<DemoOrder>,
) {
  const visit = visitOf(visitId);
  visit.orders = visit.orders.map((o) =>
    o.id === orderId ? { ...o, ...change } : o,
  );
}

function findVisitOfOrder(orderId: string): string {
  const entry = Object.entries(visits).find(([, v]) =>
    v.orders.some((o) => o.id === orderId),
  );
  return entry ? entry[0] : mockFail(404, "MSG-41");
}

function applyImaging(
  order: ParaclinicalOrder,
  next: ImagingResult,
): ImagingResult {
  const visitId = findVisitOfOrder(order.id);
  updateOrder(visitId, order.id, {
    imaging: next,
    statusNote: imagingNote(next),
  });
  return clone(next);
}

export const doctorDemoApi: DoctorExamApi = {
  capabilities: {
    laboratoryOrders: false,
    orderListing: true,
    prescriptions: true,
    pastRecords: true,
    resultSheet: true,
    startExamination: true,
    findingsReportPdfRequired: false,
  },
  async listTodayPatients(signal) {
    await wait(signal);
    return clone(demoTodayPatients(visits));
  },
  async getVisitContext(visitId, signal) {
    await wait(signal);
    return clone(visitOf(visitId).context);
  },
  async startExamination(context: VisitContext) {
    await wait();
    visitOf(context.visitId).context.status = "IN_EXAMINATION";
  },
  async getExamination(visitId, signal) {
    await wait(signal);
    return clone(visitOf(visitId).examination);
  },
  async saveExamination(visitId, input: ExaminationInput, mode) {
    await wait();
    const visit = visitOf(visitId);
    if (
      mode === "complete" &&
      input.diagnoses.filter((d) => d.type === "PRIMARY").length !== 1
    )
      mockFail(400, "MSG-08");
    const next: Examination = {
      ...input,
      status: mode === "draft" ? "DRAFT" : "COMPLETED",
      version: (visit.examination.version ?? 0) + 1,
    };
    visit.examination = next;
    if (visit.context.status === "WAITING_FOR_EXAMINATION")
      visit.context.status = "IN_EXAMINATION";
    return clone(next);
  },
  async getAdvice(visitId, signal) {
    await wait(signal);
    return clone(visitOf(visitId).advice);
  },
  async saveAdvice(visitId, input: AdviceInput) {
    await wait();
    const visit = visitOf(visitId);
    if (!visit.examination.status) mockFail(404, "MSG-41");
    const next: Advice = {
      ...input,
      examinationStatus: visit.examination.status,
      updatedAt: new Date().toISOString(),
      version: (visit.advice?.version ?? 0) + 1,
    };
    visit.advice = next;
    return clone(next);
  },
  async bookFollowUp() {
    await wait();
  },
  async listCatalog(signal) {
    await wait(signal);
    return clone(DEMO_CATALOG) as CatalogService[];
  },
  async listOrders(visitId, signal) {
    await wait(signal);
    const visit = visitOf(visitId);
    visit.orders = visit.orders.map((o) => progressed(o, Date.now()));
    return visit.orders.map(strip);
  },
  async submitOrders(visitId, items: OrderDraftItem[], catalog) {
    await wait();
    const visit = visitOf(visitId);
    const round = Math.max(0, ...visit.orders.map((o) => o.round ?? 0)) + 1;
    const created = items.map((item, index): DemoOrder => {
      const service =
        catalog.find((s) => s.id === item.serviceId) ?? mockFail(404, "MSG-41");
      return {
        id: `demo-order-${visitId}-${round}-${index}`,
        code: `CD-${String(Date.now()).slice(-5)}${index}`,
        round,
        kind: service.kind === "LABORATORY" ? "LABORATORY" : "IMAGING",
        serviceName: service.name,
        bodyArea: item.bodyArea,
        clinicalReason: item.clinicalReason,
        price: service.price,
        status: "PENDING_PAYMENT",
        statusNote: null,
        cancellable: true,
        imaging: null,
        createdAt: Date.now(),
      };
    });
    visit.orders = [...visit.orders, ...created];
    if (visit.context.status === "IN_EXAMINATION")
      visit.context.status = "AWAITING_CONCLUSION";
    return created.map(strip);
  },
  async cancelOrder(
    order: ParaclinicalOrder,
    _cause: CancelCause,
    reason: string,
  ) {
    await wait();
    void _cause;
    if (!reason.trim()) mockFail(400, "MSG-39");
    const visitId = findVisitOfOrder(order.id);
    if (order.imaging?.studyRef) mockFail(409, "MSG-42");
    updateOrder(visitId, order.id, {
      status: "CANCELLED",
      statusNote: null,
      cancellable: false,
    });
  },
  async saveFindings(order, content) {
    await wait();
    const imaging = order.imaging ?? mockFail(404, "MSG-41");
    return applyImaging(order, {
      ...imaging,
      reviewed: true,
      findingsText: content,
      findingsVersion: (imaging.findingsVersion ?? 0) + 1,
    });
  },
  async completeFindings(order, content) {
    await wait();
    const imaging = order.imaging ?? mockFail(404, "MSG-41");
    if (!content.trim()) mockFail(400, "MSG-39");
    return applyImaging(order, {
      ...imaging,
      reviewed: true,
      findingsText: content,
      findingsCompleted: true,
      findingsVersion: (imaging.findingsVersion ?? 0) + 1,
      reportId: "demo-report",
    });
  },
  async listLabResults(visitId, signal): Promise<LabResult[]> {
    await wait(signal);
    const visit = visitOf(visitId);
    return visit.orders
      .map((o) => progressed(o, Date.now()))
      .filter(
        (o) => o.kind === "LABORATORY" && o.status === "RESULTS_AVAILABLE",
      )
      .map((o) => ({
        orderId: o.id,
        serviceName: o.serviceName,
        lines: clone(DEMO_LAB_LINES),
      }));
  },
  async getPrescription(visitId, signal) {
    await wait(signal);
    return clone(visitOf(visitId).prescription);
  },
  async savePrescription(visitId, value: Prescription, issue) {
    await wait();
    const current = visitOf(visitId).prescription;
    if (current?.status === "ISSUED" || value.status === "ISSUED")
      mockFail(409, "MSG-42");
    if ((current?.version ?? 0) !== value.version) mockFail(409, "MSG-42");
    const next: Prescription = {
      id: current?.id ?? crypto.randomUUID(),
      version: (current?.version ?? 0) + (issue ? 2 : 1),
      lines: prescriptionItems(value.lines),
      status: issue ? "ISSUED" : "DRAFT",
      issuedAt: issue ? new Date().toISOString() : null,
    };
    visitOf(visitId).prescription = next;
    return clone(next);
  },
  async getMedicalHistory(_id, signal) {
    await wait(signal);
    void _id;
    return { ...history };
  },
  async saveMedicalHistory(_id, value) {
    await wait();
    void _id;
    history = { ...value, version: value.version + 1 };
    return { ...history };
  },
  async listPatientAppointments(_id, signal) {
    await wait(signal);
    void _id;
    return [
      {
        id: "a1",
        serviceName: "Khám cơ xương khớp",
        startTime: "2026-08-12T09:00:00+07:00",
        status: "COMPLETED",
      },
      {
        id: "a2",
        serviceName: "Tái khám cơ xương khớp",
        startTime: "2026-11-20T09:30:00+07:00",
        status: "CONFIRMED",
      },
    ];
  },
  async listPastVisits(currentVisitId, signal) {
    await wait(signal);
    visitOf(currentVisitId);
    return clone(DEMO_PAST_VISITS);
  },
  async listResultSheets(visitId, signal) {
    await wait(signal);
    return clone(visitOf(visitId).sheets);
  },
  async amendResult(visitId, reason): Promise<ResultSheet[]> {
    await wait();
    const visit = visitOf(visitId);
    if (!reason.trim()) mockFail(400, "MSG-39");
    if (visit.context.status !== "COMPLETED") mockFail(409, "MSG-42");
    const latest = Math.max(0, ...visit.sheets.map((s) => s.version));
    visit.sheets = [
      ...visit.sheets.map((s): ResultSheet => ({ ...s, status: "REPLACED" })),
      {
        version: latest + 1,
        status: "CURRENT",
        issuedAt: new Date().toISOString(),
      },
    ];
    return clone(visit.sheets);
  },
};
