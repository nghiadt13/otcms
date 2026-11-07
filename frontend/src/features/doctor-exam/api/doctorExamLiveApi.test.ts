import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { doctorLiveApi } from "./doctorExamLiveApi";

// Khóa hợp đồng với BE `product` (đọc từ source): đường dẫn, header, thân request và cách suy
// danh sách từ /appointments. Mock ở ranh giới apiRequest, không chạm mạng.
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

// Dùng thân hàm có ngoặc: beforeEach trả về một hàm thì Vitest coi đó là hàm dọn dẹp và gọi nó.
beforeEach(() => {
  request.mockReset();
});

/** Trả theo tiền tố đường dẫn dài nhất khớp; giá trị là ApiError thì ném. Không khớp: 404. */
function route(table: Record<string, unknown>) {
  request.mockImplementation(async (path: string) => {
    const key = Object.keys(table)
      .filter((prefix) => path.startsWith(prefix))
      .sort((x, y) => y.length - x.length)[0];
    if (!key) throw new ApiError(404, {});
    const value = table[key];
    if (value instanceof Error) throw value;
    return value;
  });
}

const active = (patch: Record<string, unknown> = {}) => ({
  visitId: "visit-1",
  patientProfileId: "pat-1",
  patient: {
    fullName: "Nguyễn Văn An",
    dateOfBirth: "1980-05-20",
    gender: "MALE",
  },
  visitSource: "SCHEDULED",
  linkedAppointmentId: "appt-1",
  assignmentId: "as-1",
  stepCode: "SCHEDULED_APPOINTMENT",
  startedAt: "2026-11-04T01:30:00Z",
  expectedEnd: "2026-11-04T02:00:00Z",
  ...patch,
});

const appointment = (patch: Record<string, unknown> = {}) => ({
  id: "appt-1",
  patientProfileId: "pat-1",
  patientName: "Nguyễn Văn An",
  serviceName: "Khám cơ xương khớp",
  doctorId: "doc-1",
  status: "CHECKED_IN",
  startTime: "2026-11-04T01:30:00Z",
  visitId: "visit-1",
  ...patch,
});

describe("lượt khám và danh sách hôm nay", () => {
  it("danh sách hôm nay suy từ /appointments, bỏ hẹn hủy/không đến và đổi trạng thái sang lượt khám", async () => {
    route({
      "/api/v1/appointments?": {
        items: [
          appointment(),
          appointment({ id: "a2", status: "CONFIRMED", visitId: null }),
          appointment({ id: "a3", status: "IN_PROGRESS", visitId: "v3" }),
          appointment({ id: "a4", status: "CANCELLED" }),
          appointment({ id: "a5", status: "NO_SHOW" }),
        ],
      },
      "/api/v1/doctor/visits/active": { items: [] },
    });
    const rows = await doctorLiveApi.listTodayPatients();
    expect(request.mock.calls[0][0]).toMatch(
      /^\/api\/v1\/appointments\?from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}&size=100&page=0$/,
    );
    expect(rows.map((r) => r.status)).toEqual([
      "WAITING_FOR_EXAMINATION",
      "NOT_ARRIVED",
      "IN_EXAMINATION",
    ]);
    expect(rows[1].visitId).toBeNull();
  });

  it("lượt đang khám ghép tuổi/giới vào hẹn tương ứng và thêm bệnh nhân vãng lai", async () => {
    route({
      "/api/v1/appointments?": {
        items: [
          appointment({
            id: "appt-1",
            visitId: "visit-1",
            status: "CHECKED_IN",
          }),
        ],
      },
      "/api/v1/doctor/visits/active": {
        items: [
          active({
            visitId: "visit-1",
            linkedAppointmentId: "appt-1",
            visitSource: "SCHEDULED",
          }),
          active({
            visitId: "walk-1",
            linkedAppointmentId: null,
            visitSource: "WALK_IN",
            stepCode: "CONSULTATION",
            patient: {
              fullName: "Đỗ Thanh Bình",
              dateOfBirth: "1990-01-01",
              gender: "FEMALE",
            },
          }),
        ],
      },
    });
    const rows = await doctorLiveApi.listTodayPatients();
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      visitId: "visit-1",
      status: "IN_EXAMINATION",
      sex: "Nam",
      source: "BOOKED",
    });
    expect(rows[0].age).toBeGreaterThan(20);
    expect(rows[1]).toMatchObject({
      visitId: "walk-1",
      patientName: "Đỗ Thanh Bình",
      source: "WALK_IN",
      sex: "Nữ",
      status: "IN_EXAMINATION",
      appointmentId: null,
    });
  });

  it("endpoint lượt đang khám lỗi (BE cũ) thì danh sách vẫn dựng từ lịch hẹn", async () => {
    route({
      "/api/v1/appointments?": { items: [appointment()] },
      "/api/v1/doctor/visits/active": new ApiError(404, {}),
    });
    const rows = await doctorLiveApi.listTodayPatients();
    expect(rows).toHaveLength(1);
    expect(rows[0].age).toBeNull();
  });

  it("ngữ cảnh lượt đang khám lấy họ tên, tuổi, giới từ /doctor/visits/active và chi tiết từ hẹn liên kết", async () => {
    route({
      "/api/v1/doctor/visits/active": { items: [active()] },
      "/api/v1/appointments/appt-1": appointment({
        doctorId: "doc-9",
        serviceName: "Tái khám",
      }),
      "/api/v1/patient-profiles/pat-1/medical-history": {
        conditions: "Gout",
        allergies: "Penicillin",
        orthopedicHistory: "Mổ gối 2020",
        version: 2,
      },
    });
    const context = await doctorLiveApi.getVisitContext("visit-1");
    expect(context).toMatchObject({
      patientName: "Nguyễn Văn An",
      patientProfileId: "pat-1",
      doctorProfileId: "doc-9",
      appointmentId: "appt-1",
      sex: "Nam",
      status: "IN_EXAMINATION",
      source: "BOOKED",
      serviceName: "Tái khám",
      allergies: "Penicillin",
      allergiesLoaded: true,
      medicalHistory: "Gout; Mổ gối 2020",
    });
    expect(context.age).toBeGreaterThan(20);
  });

  it("lượt đang khám của vãng lai không có hẹn: không có mã hẹn để bắt đầu/hoàn tất qua lịch hẹn", async () => {
    route({
      "/api/v1/doctor/visits/active": {
        items: [active({ linkedAppointmentId: null, visitSource: "WALK_IN" })],
      },
      "/api/v1/patient-profiles/": new ApiError(403, {}),
    });
    const context = await doctorLiveApi.getVisitContext("visit-1");
    expect(context).toMatchObject({
      appointmentId: null,
      source: "WALK_IN",
      allergiesLoaded: false,
    });
  });

  it("lượt chưa đang khám (chờ, đã hoàn tất) tìm qua lịch hẹn, thiếu tuổi/giới; thiếu tiền sử không làm hỏng", async () => {
    route({
      "/api/v1/doctor/visits/active": { items: [] },
      "/api/v1/appointments?": { items: [appointment()] },
      "/api/v1/patient-profiles/": new ApiError(403, { code: "MSG-19" }),
    });
    const context = await doctorLiveApi.getVisitContext("visit-1");
    expect(context).toMatchObject({
      patientName: "Nguyễn Văn An",
      patientProfileId: "pat-1",
      doctorProfileId: "doc-1",
      status: "WAITING_FOR_EXAMINATION",
      allergies: "",
      allergiesLoaded: false,
      age: null,
    });

    route({
      "/api/v1/doctor/visits/active": { items: [] },
      "/api/v1/appointments?": { items: [] },
    });
    expect(await doctorLiveApi.getVisitContext("unknown")).toMatchObject({
      visitId: "unknown",
      patientName: null,
      patientProfileId: null,
    });
  });

  it("bắt đầu khám qua lịch hẹn hoặc bước chăm sóc; không có lệnh Doctor đóng lượt", async () => {
    request.mockResolvedValue(undefined);
    const booked = { visitId: "v", appointmentId: "appt-1" } as never;
    const walkIn = { visitId: "v", appointmentId: null } as never;

    await doctorLiveApi.startExamination(booked);
    expect(request).toHaveBeenLastCalledWith(
      "/api/v1/appointments/appt-1/status",
      {
        method: "PATCH",
        body: JSON.stringify({ targetStatus: "IN_PROGRESS" }),
      },
    );
    await doctorLiveApi.startExamination(walkIn);
    expect(request.mock.lastCall![0]).toBe("/api/v1/visits/v/care-assignments");
    expect(JSON.parse(request.mock.lastCall![1].body)).toEqual({
      stepCode: "CONSULTATION",
    });
    expect(doctorLiveApi).not.toHaveProperty("completeVisit");
  });
});

describe("ghi nhận khám và lời dặn", () => {
  const input = {
    reasonForVisit: "Đau",
    symptoms: "Sưng",
    examinationFindings: "Tràn dịch",
    injuryBodySite: "",
    injurySide: "",
    injuryMechanism: "",
    diagnoses: [{ code: "M17.1", type: "PRIMARY" as const }],
  };
  const dto = { ...input, status: "DRAFT", injuryBodySite: null, version: 1 };

  it("chưa có bản ghi khám (404) trả khung trống, lỗi khác vẫn ném", async () => {
    request.mockRejectedValueOnce(new ApiError(404, { code: "MSG-41" }));
    expect(await doctorLiveApi.getExamination("v")).toMatchObject({
      status: null,
      version: null,
      diagnoses: [],
    });
    request.mockRejectedValueOnce(new ApiError(403, { code: "MSG-40" }));
    await expect(doctorLiveApi.getExamination("v")).rejects.toMatchObject({
      status: 403,
    });
  });

  it("nháp dùng PUT /examination, hoàn thành dùng POST /examination/completion; null thành chuỗi rỗng", async () => {
    request.mockResolvedValue(dto);
    const draft = await doctorLiveApi.saveExamination("v", input, "draft");
    expect(request).toHaveBeenLastCalledWith("/api/v1/visits/v/examination", {
      method: "PUT",
      body: JSON.stringify(input),
    });
    expect(draft.injuryBodySite).toBe("");
    await doctorLiveApi.saveExamination("v", input, "complete");
    expect(request.mock.lastCall![0]).toBe(
      "/api/v1/visits/v/examination/completion",
    );
    expect(request.mock.lastCall![1].method).toBe("POST");
  });

  it("lời dặn: GET 404 → null, PUT gửi 5 trường", async () => {
    request.mockRejectedValueOnce(new ApiError(404, { code: "MSG-41" }));
    expect(await doctorLiveApi.getAdvice("v")).toBeNull();
    request.mockResolvedValue({
      homeCareAdvice: "Nghỉ",
      examinationStatus: "DRAFT",
      version: 1,
    });
    const saved = await doctorLiveApi.saveAdvice("v", {
      homeCareAdvice: "Nghỉ",
      activityAdvice: "",
      rehabilitationAdvice: "",
      warningSigns: "",
      followUpRecommendation: "",
    });
    expect(request.mock.lastCall![0]).toBe(
      "/api/v1/visits/v/after-visit-advice",
    );
    expect(saved.warningSigns).toBe("");
  });
});

describe("chỉ định cận lâm sàng và kết luận hình ảnh", () => {
  const catalog = [
    {
      id: "xq",
      name: "X-quang",
      kind: "IMAGING",
      price: 1,
      modality: null,
      description: "",
    },
    {
      id: "crp",
      name: "CRP",
      kind: "LABORATORY",
      price: 1,
      modality: null,
      description: "",
    },
  ] as never;
  const item = (serviceId: string) => ({
    serviceId,
    clinicalReason: "Đau",
    bodyArea: "Gối",
    instructions: "",
  });

  it("gửi đợt chỉ định CĐHA kèm Idempotency-Key và đổi trạng thái thanh toán", async () => {
    request.mockResolvedValue({
      roundNumber: 2,
      orders: [
        {
          orderId: "o1",
          orderCode: "CD-1",
          serviceId: "xq",
          serviceName: "X-quang",
          clinicalReason: "Đau",
          bodyArea: "Gối",
          priceSnapshot: 150000,
          orderStatus: "ORDERED",
          paymentStatus: "PENDING_PAYMENT",
        },
      ],
    });
    const created = await doctorLiveApi.submitOrders(
      "v",
      [item("xq")],
      catalog,
      "key-1",
      true,
    );
    const [path, init] = request.mock.lastCall!;
    expect(path).toBe("/api/v1/visits/v/imaging-requests");
    expect(init.headers).toEqual({ "Idempotency-Key": "key-1" });
    expect(JSON.parse(init.body)).toEqual({
      patientConsentConfirmed: true,
      orders: [item("xq")],
    });
    expect(created[0]).toMatchObject({
      round: 2,
      status: "PENDING_PAYMENT",
      price: 150000,
    });
  });

  it("chưa gửi chỉ định xét nghiệm lên BE (chưa hỗ trợ)", async () => {
    await expect(
      doctorLiveApi.submitOrders("v", [item("crp")], catalog, "key", true),
    ).rejects.toMatchObject({ status: 400, code: "MSG-39" });
    expect(request).not.toHaveBeenCalled();
  });

  it("danh sách chỉ định ghép trạng thái yêu cầu ảnh với lịch sử kết luận", async () => {
    request.mockImplementation(async (path: string) =>
      path.startsWith("/api/v1/imaging-history")
        ? {
            items: [
              {
                requestId: "r1",
                studyRef: "uid-1",
                findingsText: "Rách",
                findingsVersion: 1,
                reportId: "rep",
              },
            ],
          }
        : {
            items: [
              {
                orderId: "o1",
                orderCode: "CD1",
                serviceName: "MRI gối",
                orderStatus: "RESULTS_AVAILABLE",
                paymentStatus: "PAID",
                diagnostic: { requestId: "r1", status: "REVIEWED", version: 5 },
              },
              {
                orderId: "o2",
                orderCode: "CD2",
                serviceName: "X-quang",
                orderStatus: "ORDERED",
                paymentStatus: "PAID",
                diagnostic: { requestId: "r2", status: "PENDING", version: 1 },
              },
              {
                orderId: "o3",
                orderCode: "CD3",
                serviceName: "CT",
                orderStatus: "CANCELLED",
                paymentStatus: "PAID",
                diagnostic: {
                  requestId: "r3",
                  status: "CANCELLED",
                  version: 2,
                },
              },
            ],
          },
    );
    const orders = await doctorLiveApi.listOrders("v");
    expect(orders.map((o) => o.status)).toEqual([
      "RESULTS_AVAILABLE",
      "PAID",
      "CANCELLED",
    ]);
    expect(orders[0].imaging).toMatchObject({
      reviewed: true,
      findingsCompleted: true,
      studyRef: "uid-1",
      version: 5,
    });
    expect(orders[1].imaging?.findingsCompleted).toBe(false);
  });

  it("hủy chỉ định gửi X-Expected-Version lấy từ BE", async () => {
    request.mockImplementation(async (path: string) =>
      path === "/api/v1/imaging-requests/r1" ? { version: 7 } : undefined,
    );
    await doctorLiveApi.cancelOrder(
      { imaging: { requestId: "r1" } } as never,
      "PATIENT_DECLINED",
      "Từ chối",
    );
    const [path, init] = request.mock.lastCall!;
    expect(path).toBe("/api/v1/imaging-requests/r1/cancellations");
    expect(init.headers["X-Expected-Version"]).toBe("7");
    expect(init.headers["Idempotency-Key"]).toBeTruthy();
    expect(JSON.parse(init.body)).toEqual({
      cause: "PATIENT_DECLINED",
      reason: "Từ chối",
    });
  });

  it("hoàn tất kết luận: xem ảnh → lưu nháp → tải PDF → hoàn tất, và đòi PDF", async () => {
    const calls: string[] = [];
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      calls.push(`${init?.method ?? "GET"} ${path.split("?")[0]}`);
      if (path.endsWith("/findings")) return { version: 1, reportId: null };
      if (path.includes("/reports")) return { reportId: "rep-1" };
      if (path === "/api/v1/imaging-requests/r1")
        return { version: 4, status: "RESULT_AVAILABLE" };
      return undefined;
    });
    const order = {
      imaging: {
        requestId: "r1",
        studyRef: "uid-1",
        reviewed: false,
        version: 3,
      },
    } as never;
    await expect(
      doctorLiveApi.completeFindings(order, "Rách", null),
    ).rejects.toMatchObject({
      code: "MSG-47",
    });
    const file = new File(["%PDF"], "kq.pdf", { type: "application/pdf" });
    const result = await doctorLiveApi.completeFindings(order, "Rách", file);
    const steps = calls.filter((c) => !c.startsWith("GET"));
    expect(steps).toEqual([
      "POST /api/v1/imaging-requests/r1/reviews",
      "POST /api/v1/imaging-requests/r1/findings",
      "POST /api/v1/imaging-requests/r1/reports",
      "POST /api/v1/imaging-requests/r1/findings/completions",
    ]);
    expect(result).toMatchObject({
      findingsCompleted: true,
      reportId: "rep-1",
    });
    const completion = request.mock.calls.find((c) =>
      String(c[0]).endsWith("/completions"),
    )!;
    expect(JSON.parse(completion[1].body)).toEqual({
      findingsVersion: 1,
      reportId: "rep-1",
      studyRef: "uid-1",
    });
  });

  it("chỉ yêu cầu ảnh còn PENDING mới hủy được", async () => {
    request.mockImplementation(async (path: string) =>
      path.startsWith("/api/v1/imaging-history")
        ? { items: [] }
        : {
            items: ["PENDING", "IN_PROGRESS", "RESULT_AVAILABLE"].map(
              (status, i) => ({
                orderId: "o" + i,
                orderCode: "CD" + i,
                serviceName: "X-quang",
                paymentStatus: "PAID",
                orderStatus: "ORDERED",
                diagnostic: { requestId: "r" + i, status, version: 1 },
              }),
            ),
          },
    );
    expect(
      (await doctorLiveApi.listOrders("v")).map((o) => o.cancellable),
    ).toEqual([true, false, false]);
  });

  it("thử lại kết luận sau khi đã xem ảnh không gọi /reviews lần hai (BE trả 409)", async () => {
    const posts: string[] = [];
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (init?.method === "POST") posts.push(path);
      if (path.endsWith("/findings")) return { version: 2, reportId: null };
      if (path === "/api/v1/imaging-requests/r1")
        return { version: 6, status: "REVIEWED" };
      return undefined;
    });
    // prop cũ báo chưa xem ảnh, nhưng BE đã ở REVIEWED sau một lần lưu hỏng giữa chừng
    const stale = {
      imaging: {
        requestId: "r1",
        studyRef: "uid",
        reviewed: false,
        version: 3,
      },
    } as never;
    await doctorLiveApi.saveFindings(stale, "Rách");
    expect(posts).toEqual(["/api/v1/imaging-requests/r1/findings"]);
  });
});

describe("ngữ cảnh lượt khám nhiều trang và tiền sử", () => {
  it("duyệt các trang lịch hẹn cho tới khi gặp lượt khám", async () => {
    request.mockImplementation(async (path: string) => {
      if (path.startsWith("/api/v1/doctor/visits/active")) return { items: [] };
      if (path.includes("medical-history"))
        return {
          allergies: "",
          conditions: "",
          orthopedicHistory: "",
          version: 1,
        };
      return path.includes("page=0")
        ? { items: [appointment({ visitId: "other" })], page: 0, totalPages: 2 }
        : {
            items: [
              appointment({ visitId: "target", patientName: "Trang hai" }),
            ],
            page: 1,
            totalPages: 2,
          };
    });
    const context = await doctorLiveApi.getVisitContext("target");
    expect(context.patientName).toBe("Trang hai");
    expect(context.allergiesLoaded).toBe(true);
  });

  it("đọc tiền sử lỗi thì báo chưa đọc được dị ứng thay vì coi như không có", async () => {
    request.mockImplementation(async (path: string) => {
      if (path.includes("medical-history")) throw new ApiError(503, {});
      if (path.startsWith("/api/v1/doctor/visits/active")) return { items: [] };
      return { items: [appointment()] };
    });
    expect(await doctorLiveApi.getVisitContext("visit-1")).toMatchObject({
      allergies: "",
      allergiesLoaded: false,
    });
  });

  it("lưu tiền sử chỉ gửi đúng 6 trường, bỏ trường chỉ đọc", async () => {
    request.mockResolvedValue({});
    await doctorLiveApi.saveMedicalHistory("p", {
      conditions: "a",
      allergies: "b",
      procedures: "c",
      injuries: "d",
      orthopedicHistory: "e",
      version: 3,
      updatedAt: "x",
      updatedBy: "y",
    } as never);
    expect(
      Object.keys(JSON.parse(request.mock.lastCall![1].body)).sort(),
    ).toEqual([
      "allergies",
      "conditions",
      "injuries",
      "orthopedicHistory",
      "procedures",
      "version",
    ]);
  });
});
