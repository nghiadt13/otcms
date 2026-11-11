import { describe, expect, it } from "vitest";
import {
  ICD10_CATALOG,
  ICD_CODE_PATTERN,
  icdLabel,
  searchIcd,
} from "./icd10Catalog";
import {
  addSecondary,
  removeDiagnosis,
  setPrimary,
  validateForCompletion,
} from "./examinationRules";
import {
  draftItems,
  draftTotal,
  toggleService,
  validateDraft,
} from "./orderDraft";
import {
  buildClosingChecklist,
  isClinicalChecklistReady,
  ordersStep,
} from "./visitProgress";
import {
  canOpenVisit,
  countByStatus,
  filterPatients,
} from "./todayPatientsModel";
import { mergeWorkspaceOrders } from "../components/VisitWorkspaceProvider";
import type {
  CatalogService,
  Examination,
  ExaminationInput,
  ParaclinicalOrder,
  TodayPatient,
} from "../types/doctorExam";

const FULL: ExaminationInput = {
  reasonForVisit: "Đau gối",
  symptoms: "Sưng",
  examinationFindings: "Tràn dịch",
  injuryBodySite: "",
  injurySide: "",
  injuryMechanism: "",
  diagnoses: [{ code: "M17.1", type: "PRIMARY" }],
};

describe("ICD-10 M/S (BR-39)", () => {
  it("mọi mã trong danh mục khớp mẫu mã mà BE chấp nhận và chỉ thuộc nhóm M hoặc S", () => {
    for (const entry of ICD10_CATALOG) {
      expect(entry.code).toMatch(ICD_CODE_PATTERN);
      expect(entry.code[0]).toBe(entry.group);
    }
    expect(new Set(ICD10_CATALOG.map((e) => e.code)).size).toBe(
      ICD10_CATALOG.length,
    );
  });

  it("tìm không phân biệt dấu và hoa thường, theo mã hoặc tên", () => {
    expect(searchIcd("thoat vi").map((e) => e.code)).toContain("M51.2");
    expect(searchIcd("s83.5")[0].code).toBe("S83.5");
    expect(searchIcd("ĐAU THẮT LƯNG").length).toBeGreaterThan(0);
    expect(searchIcd("khong co benh nay")).toEqual([]);
  });

  it("nhãn mã ngoài danh mục chỉ hiện mã", () => {
    expect(icdLabel("m17.1")).toBe("M17.1 · Thoái hóa khớp gối nguyên phát");
    expect(icdLabel("m99.9")).toBe("M99.9");
  });
});

describe("luật hoàn thành ghi nhận khám (UC37)", () => {
  it("đủ trường và đúng một chẩn đoán chính thì hợp lệ", () => {
    expect(validateForCompletion(FULL)).toEqual({});
  });

  it("thiếu trường hoặc không có chẩn đoán chính bị báo từng trường", () => {
    const errors = validateForCompletion({
      ...FULL,
      symptoms: "  ",
      diagnoses: [{ code: "M17.1", type: "SECONDARY" }],
    });
    expect(Object.keys(errors).sort()).toEqual(["diagnoses", "symptoms"]);
  });

  it("hai chẩn đoán chính hoặc mã sai dạng bị từ chối", () => {
    expect(
      validateForCompletion({
        ...FULL,
        diagnoses: [
          { code: "M17.1", type: "PRIMARY" },
          { code: "M54.5", type: "PRIMARY" },
        ],
      }).diagnoses,
    ).toBeDefined();
    expect(
      validateForCompletion({
        ...FULL,
        diagnoses: [{ code: "A00", type: "PRIMARY" }],
      }).diagnoses,
    ).toBeDefined();
  });

  it("đổi chẩn đoán chính đẩy chẩn đoán chính cũ xuống phụ, không trùng mã", () => {
    let list = setPrimary([], "M17.1");
    list = addSecondary(list, "M54.5");
    list = addSecondary(list, "M54.5");
    expect(list).toHaveLength(2);
    list = setPrimary(list, "M54.5");
    expect(list).toEqual([
      { code: "M54.5", type: "PRIMARY" },
      { code: "M17.1", type: "SECONDARY" },
    ]);
    expect(removeDiagnosis(list, "M54.5")).toEqual([
      { code: "M17.1", type: "SECONDARY" },
    ]);
  });
});

const CATALOG: CatalogService[] = [
  {
    id: "xq",
    name: "X-quang gối",
    kind: "IMAGING",
    price: 150000,
    modality: "X-quang",
    description: "",
  },
  {
    id: "crp",
    name: "CRP",
    kind: "LABORATORY",
    price: 90000,
    modality: null,
    description: "",
  },
];

describe("phiếu chỉ định (UC45, BR-41)", () => {
  it("CĐHA cần lý do và vùng chụp, xét nghiệm chỉ cần lý do", () => {
    let draft = toggleService({}, "xq");
    draft = toggleService(draft, "crp");
    expect(validateDraft(draft, CATALOG)).toEqual({
      xq: {
        clinicalReason: "Nhập lý do lâm sàng.",
        bodyArea: "Nhập vùng cần chụp.",
      },
      crp: { clinicalReason: "Nhập lý do lâm sàng." },
    });
    draft.xq = { ...draft.xq, clinicalReason: "Đau", bodyArea: "Gối phải" };
    draft.crp = { ...draft.crp, clinicalReason: "Viêm?" };
    expect(validateDraft(draft, CATALOG)).toEqual({});
  });

  it("bỏ chọn xóa dòng, tổng tiền và danh sách gửi theo danh mục", () => {
    let draft = toggleService({}, "crp");
    draft = toggleService(draft, "xq");
    expect(draftTotal(draft, CATALOG)).toBe(240000);
    draft.xq = { ...draft.xq, clinicalReason: " Đau ", bodyArea: " Gối " };
    expect(draftItems(draft, CATALOG).map((i) => i.serviceId)).toEqual([
      "xq",
      "crp",
    ]);
    expect(draftItems(draft, CATALOG)[0]).toMatchObject({
      clinicalReason: "Đau",
      bodyArea: "Gối",
    });
    expect(Object.keys(toggleService(draft, "crp"))).toEqual(["xq"]);
  });
});

function order(patch: Partial<ParaclinicalOrder>): ParaclinicalOrder {
  return {
    id: "o",
    code: null,
    round: 1,
    kind: "LABORATORY",
    serviceName: "CRP",
    bodyArea: "",
    clinicalReason: "",
    price: null,
    status: "RESULTS_AVAILABLE",
    statusNote: null,
    cancellable: false,
    imaging: null,
    ...patch,
  };
}
const imagingResult = (completed: boolean) => ({
  requestId: "r",
  version: 1,
  studyRef: "s",
  reviewed: true,
  findingsText: "x",
  findingsVersion: 1,
  findingsCompleted: completed,
  reportId: null,
});

describe("điều kiện hoàn tất lượt khám (UC36, BR-33)", () => {
  const completed: Examination = { ...FULL, status: "COMPLETED", version: 1 };

  it("chưa hoàn tất khi khám còn nháp hoặc chưa có chẩn đoán chính", () => {
    const draft = { ...completed, status: "DRAFT" as const };
    expect(isClinicalChecklistReady(buildClosingChecklist(draft, []))).toBe(
      false,
    );
    expect(isClinicalChecklistReady(buildClosingChecklist(undefined, []))).toBe(
      false,
    );
  });

  it("chỉ định chưa có kết quả hoặc CĐHA chưa có kết luận chặn hoàn tất; chỉ định hủy thì không", () => {
    const waiting = order({ id: "a", status: "PAID" });
    const noFindings = order({
      id: "b",
      kind: "IMAGING",
      imaging: imagingResult(false),
    });
    const cancelled = order({ id: "c", status: "CANCELLED" });
    expect(
      isClinicalChecklistReady(buildClosingChecklist(completed, [waiting])),
    ).toBe(false);
    expect(
      isClinicalChecklistReady(buildClosingChecklist(completed, [noFindings])),
    ).toBe(false);
    expect(
      isClinicalChecklistReady(buildClosingChecklist(completed, [cancelled])),
    ).toBe(true);
    const done = order({
      id: "d",
      kind: "IMAGING",
      imaging: imagingResult(true),
    });
    expect(
      isClinicalChecklistReady(
        buildClosingChecklist(completed, [done, cancelled]),
      ),
    ).toBe(true);
  });

  it("nguồn dữ liệu tải lỗi thì mục đó chưa đạt, không được coi như không có gì", () => {
    const items = buildClosingChecklist(completed, [], {
      examination: true,
      orders: true,
    });
    expect(isClinicalChecklistReady(items)).toBe(false);
    expect(items.map((i) => i.ok)).toEqual([false, false]);
    expect(items[1].detail).toContain("Chưa kiểm tra được");
  });

  it("tóm tắt chỉ định đếm đúng số đã có kết quả", () => {
    expect(ordersStep([]).state).toBe("TODO");
    const step = ordersStep([
      order({ id: "a" }),
      order({ id: "b", status: "PAID" }),
    ]);
    expect(step).toEqual({
      state: "IN_PROGRESS",
      summary: "2 chỉ định, 1 đã có kết quả.",
    });
  });
});

function patient(patch: Partial<TodayPatient>): TodayPatient {
  return {
    appointmentId: "a",
    visitId: "v",
    patientProfileId: "p",
    patientName: "Nguyễn Văn An",
    age: null,
    sex: null,
    patientCode: null,
    serviceName: "Khám",
    scheduledAt: "2026-11-04T01:00:00Z",
    queueNumber: null,
    source: "BOOKED",
    status: "WAITING_FOR_EXAMINATION",
    ...patch,
  };
}

describe("Bệnh nhân hôm nay (UC35)", () => {
  const list = [
    patient({ visitId: "1", scheduledAt: "2026-11-04T03:00:00Z" }),
    patient({
      visitId: "2",
      patientName: "Trần Thị Bích",
      status: "IN_EXAMINATION",
    }),
    patient({ visitId: null, patientName: "Lê Văn C", status: "NOT_ARRIVED" }),
  ];

  it("đếm theo trạng thái và lọc theo tên không phân biệt dấu, sắp theo nhóm trạng thái", () => {
    const counts = countByStatus(list);
    expect(counts).toMatchObject({
      ALL: 3,
      WAITING_FOR_EXAMINATION: 1,
      IN_EXAMINATION: 1,
      NOT_ARRIVED: 1,
    });
    expect(
      filterPatients(list, "ALL", "tran thi").map((p) => p.visitId),
    ).toEqual(["2"]);
    // Đang khám trước, rồi chờ khám, rồi chưa đến (dù giờ hẹn của chờ khám muộn hơn).
    expect(filterPatients(list, "ALL", "").map((p) => p.visitId)).toEqual([
      "2",
      "1",
      null,
    ]);
    expect(filterPatients(list, "IN_EXAMINATION", "").length).toBe(1);
  });

  it("chờ khám sắp theo số thứ tự chứ không theo giờ; đã xong xếp cuối", () => {
    const queue = [
      patient({
        visitId: "w2",
        queueNumber: 2,
        scheduledAt: "2026-11-04T01:00:00Z",
      }),
      patient({
        visitId: "w1",
        queueNumber: 1,
        scheduledAt: "2026-11-04T05:00:00Z",
      }),
      patient({ visitId: "done", status: "COMPLETED", queueNumber: 0 }),
      patient({
        visitId: "await",
        status: "AWAITING_CONCLUSION",
        queueNumber: 3,
      }),
    ];
    expect(filterPatients(queue, "ALL", "").map((p) => p.visitId)).toEqual([
      "await",
      "w1",
      "w2",
      "done",
    ]);
  });

  it("chỉ mở được lượt khám đã có mã (đã check-in)", () => {
    expect(canOpenVisit(list[0])).toBe(true);
    expect(canOpenVisit(list[2])).toBe(false);
  });
});

describe("gộp chỉ định của workspace", () => {
  const imaging = (completed: boolean) => ({
    requestId: "r1",
    version: 1,
    studyRef: "s",
    reviewed: true,
    findingsText: "x",
    findingsVersion: 1,
    findingsCompleted: completed,
    reportId: null,
  });

  it("chỉ định vừa gửi hiện cùng danh sách BE, không trùng mã", () => {
    const loaded = [order({ id: "a" })];
    const submitted = [
      order({ id: "a" }),
      order({ id: "b", status: "PENDING_PAYMENT" }),
    ];
    expect(
      mergeWorkspaceOrders(loaded, submitted, new Set()).map((o) => o.id),
    ).toEqual(["a", "b"]);
  });

  it("kết luận đã gửi nhưng chưa phát hành vẫn tính là xong để không kẹt hoàn tất khám", () => {
    const pending = order({
      id: "i",
      kind: "IMAGING",
      imaging: imaging(false),
    });
    const [merged] = mergeWorkspaceOrders([pending], [], new Set(["r1"]));
    expect(merged.imaging?.findingsCompleted).toBe(true);
    expect(merged.statusNote).toBe("Đã gửi kết luận, chờ phát hành");
    expect(
      mergeWorkspaceOrders([pending], [], new Set())[0].imaging
        ?.findingsCompleted,
    ).toBe(false);
  });
});
