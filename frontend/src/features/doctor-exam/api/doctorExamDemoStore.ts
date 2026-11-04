import type {
  CatalogService,
  Examination,
  LabResultLine,
  ParaclinicalOrder,
  PastVisit,
  Prescription,
  ResultSheet,
  TodayPatient,
  VisitContext,
  Advice,
} from "../types/doctorExam";
import type { MedicalHistory } from "./doctorExamApi";
import { todayIsoDate } from "@/lib/format/formatDate";

// Dữ liệu mẫu cho chế độ demo của khu bác sĩ (xem doctorDemoMode.ts). Chỉ nằm trong bộ nhớ
// trình duyệt, mất khi tải lại trang. Không phải dữ liệu thật của bệnh nhân nào.

export type DemoOrder = ParaclinicalOrder & { createdAt: number };

export type DemoVisit = {
  context: VisitContext;
  examination: Examination;
  advice: Advice | null;
  orders: DemoOrder[];
  prescription: Prescription | null;
  sheets: ResultSheet[];
};

const at = (time: string) =>
  new Date(`${todayIsoDate()}T${time}:00+07:00`).toISOString();

const emptyExam = (): Examination => ({
  reasonForVisit: "",
  symptoms: "",
  examinationFindings: "",
  injuryBodySite: "",
  injurySide: "",
  injuryMechanism: "",
  diagnoses: [],
  status: null,
  version: null,
});

function context(
  n: number,
  patientName: string,
  age: number,
  sex: "Nam" | "Nữ",
  status: VisitContext["status"],
  time: string,
  extra: Partial<VisitContext> = {},
): VisitContext {
  return {
    visitId: `demo-visit-${n}`,
    appointmentId: `demo-appt-${n}`,
    patientProfileId: `demo-patient-${n}`,
    doctorProfileId: "demo-doctor",
    patientName,
    age,
    sex,
    patientCode: `BN-0001${20 + n}`,
    allergies: "",
    allergiesLoaded: true,
    medicalHistory: "",
    status,
    serviceName: "Khám cơ xương khớp",
    scheduledAt: at(time),
    source: "BOOKED",
    ...extra,
  };
}

const IMAGING_DONE: DemoOrder = {
  id: "demo-order-1",
  code: "CD-0001",
  round: 1,
  kind: "IMAGING",
  serviceName: "X-quang cột sống thắt lưng",
  bodyArea: "Cột sống thắt lưng",
  clinicalReason: "Đau thắt lưng lan xuống chân trái, nghi thoát vị đĩa đệm.",
  price: 180000,
  status: "RESULTS_AVAILABLE",
  statusNote: "Đã có ảnh, chờ bác sĩ xem",
  cancellable: false,
  imaging: {
    requestId: "demo-order-1",
    version: 3,
    studyRef: "demo-study-1",
    reviewed: false,
    findingsText: "",
    findingsVersion: null,
    findingsCompleted: false,
    reportId: null,
  },
  createdAt: 0,
};

export function createDemoVisits(): Record<string, DemoVisit> {
  const base = (
    c: VisitContext,
    extra: Partial<DemoVisit> = {},
  ): DemoVisit => ({
    context: c,
    examination: emptyExam(),
    advice: null,
    orders: [],
    prescription: null,
    sheets: [],
    ...extra,
  });
  const v1 = context(
    1,
    "Nguyễn Văn An",
    54,
    "Nam",
    "WAITING_FOR_EXAMINATION",
    "08:30",
    {
      allergies: "Dị ứng penicillin",
      medicalHistory: "Thoái hóa khớp gối hai bên; tăng huyết áp.",
    },
  );
  const v2 = context(2, "Trần Thị Bích", 38, "Nữ", "IN_EXAMINATION", "09:00", {
    medicalHistory: "Đau thắt lưng tái phát 2 năm.",
  });
  const v3 = context(
    3,
    "Lê Quang Huy",
    27,
    "Nam",
    "AWAITING_CONCLUSION",
    "07:45",
    {
      serviceName: "Khám chấn thương thể thao",
    },
  );
  const v4 = context(4, "Phạm Thu Hà", 61, "Nữ", "COMPLETED", "08:00", {
    allergies: "Không",
    medicalHistory: "Loãng xương sau mãn kinh.",
  });
  const v6 = context(
    6,
    "Đỗ Thanh Bình",
    45,
    "Nam",
    "WAITING_FOR_EXAMINATION",
    "09:20",
    {
      appointmentId: null,
      serviceName: "Khám vãng lai",
      source: "WALK_IN",
    },
  );
  const done = (code: string): Examination["diagnoses"] => [
    { code, type: "PRIMARY" },
  ];
  return {
    [v1.visitId]: base(v1),
    [v2.visitId]: base(v2, {
      examination: {
        ...emptyExam(),
        reasonForVisit: "Đau thắt lưng lan xuống chân trái 2 tuần.",
        symptoms: "Đau tăng khi cúi, tê bì mặt ngoài cẳng chân.",
        status: "DRAFT",
        version: 1,
      },
      orders: [IMAGING_DONE],
    }),
    [v3.visitId]: base(v3, {
      examination: {
        ...emptyExam(),
        reasonForVisit: "Đau gối phải sau chấn thương khi đá bóng.",
        symptoms: "Sưng gối, đi lại đau, cảm giác lỏng gối.",
        examinationFindings: "Dấu ngăn kéo trước (+), tràn dịch khớp gối nhẹ.",
        injuryBodySite: "Gối",
        injurySide: "Phải",
        injuryMechanism: "Xoay gối khi chạy",
        diagnoses: done("S83.5"),
        status: "COMPLETED",
        version: 2,
      },
      orders: [
        {
          ...IMAGING_DONE,
          id: "demo-order-3",
          code: "CD-0003",
          serviceName: "MRI khớp gối",
          bodyArea: "Gối phải",
          clinicalReason: "Nghi đứt dây chằng chéo trước.",
          price: 1800000,
          status: "PAID",
          statusNote: "Chờ chụp",
          cancellable: true,
          imaging: null,
        },
      ],
    }),
    [v4.visitId]: base(v4, {
      examination: {
        ...emptyExam(),
        reasonForVisit: "Đau lưng, tái khám loãng xương.",
        symptoms: "Đau âm ỉ vùng thắt lưng.",
        examinationFindings:
          "Co cứng cơ cạnh sống, không dấu thần kinh khu trú.",
        diagnoses: done("M81.0"),
        status: "COMPLETED",
        version: 2,
      },
      advice: {
        homeCareAdvice: "Bổ sung canxi, vitamin D; tắm nắng buổi sáng.",
        activityAdvice: "Tránh mang vác nặng.",
        rehabilitationAdvice: "Bài tập tăng cường cơ lưng.",
        warningSigns: "Đau tăng đột ngột, tê yếu chân.",
        followUpRecommendation: "Tái khám sau 3 tháng.",
        examinationStatus: "COMPLETED",
        updatedAt: at("08:40"),
        version: 1,
      },
      prescription: {
        status: "ISSUED",
        note: "Uống sau ăn.",
        issuedAt: at("08:45"),
        lines: [
          {
            medicine: "Calci + Vitamin D3",
            strength: "500 mg/200 IU",
            quantity: "60 viên",
            usage: "Ngày uống 2 lần, mỗi lần 1 viên",
          },
        ],
      },
      sheets: [{ version: 1, status: "CURRENT", issuedAt: at("08:50") }],
    }),
    [v6.visitId]: base(v6),
  };
}

export function demoTodayPatients(
  visits: Record<string, DemoVisit>,
): TodayPatient[] {
  const rows = Object.values(visits).map(({ context: c }): TodayPatient => ({
    appointmentId: c.appointmentId,
    visitId: c.visitId,
    patientProfileId: c.patientProfileId,
    patientName: c.patientName ?? "",
    age: c.age,
    sex: c.sex,
    patientCode: c.patientCode,
    serviceName: c.serviceName ?? "",
    scheduledAt: c.scheduledAt ?? at("08:00"),
    source: c.source ?? "BOOKED",
    status: c.status ?? "WAITING_FOR_EXAMINATION",
  }));
  rows.push({
    appointmentId: "demo-appt-5",
    visitId: null,
    patientProfileId: "demo-patient-5",
    patientName: "Hoàng Minh Tuấn",
    age: 33,
    sex: "Nam",
    patientCode: "BN-000125",
    serviceName: "Khám cơ xương khớp",
    scheduledAt: at("10:30"),
    source: "BOOKED",
    status: "NOT_ARRIVED",
  });
  return rows.sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
}

export const DEMO_CATALOG: CatalogService[] = [
  {
    id: "svc-xq-gối",
    name: "X-quang khớp gối",
    kind: "IMAGING",
    price: 150000,
    modality: "X-quang",
    description: "Chụp thẳng và nghiêng.",
  },
  {
    id: "svc-xq-cs",
    name: "X-quang cột sống thắt lưng",
    kind: "IMAGING",
    price: 180000,
    modality: "X-quang",
    description: "",
  },
  {
    id: "svc-us-vai",
    name: "Siêu âm khớp vai",
    kind: "IMAGING",
    price: 250000,
    modality: "Siêu âm",
    description: "",
  },
  {
    id: "svc-ct-cs",
    name: "CT cột sống thắt lưng",
    kind: "IMAGING",
    price: 1200000,
    modality: "CT",
    description: "",
  },
  {
    id: "svc-mri-gối",
    name: "MRI khớp gối",
    kind: "IMAGING",
    price: 1800000,
    modality: "MRI",
    description: "",
  },
  {
    id: "svc-crp",
    name: "Xét nghiệm CRP",
    kind: "LABORATORY",
    price: 90000,
    modality: null,
    description: "Protein phản ứng C.",
  },
  {
    id: "svc-uric",
    name: "Xét nghiệm acid uric máu",
    kind: "LABORATORY",
    price: 70000,
    modality: null,
    description: "",
  },
  {
    id: "svc-ctm",
    name: "Tổng phân tích tế bào máu",
    kind: "LABORATORY",
    price: 110000,
    modality: null,
    description: "",
  },
  {
    id: "svc-tai-kham",
    name: "Tái khám cơ xương khớp",
    kind: "EXAMINATION",
    price: 150000,
    modality: null,
    description: "",
  },
];

export const DEMO_LAB_LINES: LabResultLine[] = [
  {
    indicator: "CRP",
    value: "18.4",
    unit: "mg/L",
    reference: "0 – 5",
    abnormal: true,
  },
  {
    indicator: "WBC",
    value: "8.2",
    unit: "G/L",
    reference: "4.0 – 10.0",
    abnormal: false,
  },
  {
    indicator: "Acid uric",
    value: "412",
    unit: "µmol/L",
    reference: "202 – 416",
    abnormal: false,
  },
];

export const DEMO_HISTORY: MedicalHistory = {
  conditions: "Đau thắt lưng tái phát.",
  allergies: "",
  procedures: "",
  injuries: "",
  orthopedicHistory: "",
  version: 1,
};

export const DEMO_PAST_VISITS: PastVisit[] = [
  {
    visitId: "past-1",
    visitDate: "2026-08-12",
    status: "COMPLETED",
    primaryDiagnosis: "M54.5 · Đau thắt lưng",
    advice: "Nghỉ ngơi, tập giãn cơ lưng.",
    prescriptions: 1,
    imaging: ["X-quang cột sống thắt lưng: thoái hóa nhẹ L4–L5"],
    laboratory: ["CRP 3.1 mg/L (bình thường)"],
  },
  {
    visitId: "past-2",
    visitDate: "2026-05-03",
    status: "INCOMPLETE",
    primaryDiagnosis: null,
    advice: null,
    prescriptions: 0,
    imaging: ["Siêu âm khớp vai: chưa kết luận"],
    laboratory: [],
  },
];
