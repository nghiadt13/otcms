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

/** Dòng hàng chờ (A2 và A6) với giá trị mặc định của một lượt theo lịch hẹn đang chờ. */
const queueRow = (patch: Record<string, unknown> = {}) => ({
  visitId: "visit-1",
  queueNumber: 1,
  examStatus: "WAITING",
  source: "SCHEDULED",
  appointmentId: "appt-1",
  patientProfileId: "pat-1",
  patientName: "Nguyễn Văn An",
  doctorProfileId: "doc-1",
  doctorName: "BS. Minh",
  roomId: null,
  roomLabel: null,
  serviceId: null,
  queuedAt: "2026-11-04T01:00:00Z",
  startedAt: null,
  finishedAt: null,
  dateOfBirth: "1980-05-20",
  gender: "MALE",
  ...patch,
});

describe("lượt khám và danh sách hôm nay", () => {
  it("danh sách hôm nay lấy từ hàng chờ (A2), thêm lịch đã xác nhận chưa đến, không đọc /doctor/visits/active", async () => {
    route({
      "/api/v1/doctor/exam-queue": {
        items: [
          queueRow({
            visitId: "v-walk",
            queueNumber: 2,
            source: "WALK_IN",
            appointmentId: null,
          }),
          queueRow({
            visitId: "visit-1",
            examStatus: "IN_EXAMINATION",
            appointmentId: "a1",
          }),
          queueRow({
            visitId: "v-old",
            examStatus: "AWAITING_CONCLUSION",
            appointmentId: "a9",
            queuedAt: "2026-11-10T01:00:00Z",
          }),
        ],
      },
      "/api/v1/appointments?": {
        items: [
          appointment({ id: "a1", status: "IN_PROGRESS", visitId: "visit-1" }),
          appointment({ id: "a2", status: "CONFIRMED", visitId: null }),
          appointment({ id: "a4", status: "CANCELLED" }),
          appointment({ id: "a5", status: "NO_SHOW" }),
        ],
      },
    });
    const rows = await doctorLiveApi.listTodayPatients();
    expect(request.mock.calls[0][0]).toBe("/api/v1/doctor/exam-queue");
    expect(request.mock.calls[1][0]).toMatch(
      /^\/api\/v1\/appointments\?from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}&size=100&page=0$/,
    );
    expect(
      request.mock.calls.some((call) =>
        String(call[0]).startsWith("/api/v1/doctor/visits/active"),
      ),
    ).toBe(false);
    expect(rows.map((r) => r.status)).toEqual([
      "WAITING_FOR_EXAMINATION",
      "IN_EXAMINATION",
      "AWAITING_CONCLUSION",
      "NOT_ARRIVED",
    ]);
    expect(rows[1]).toMatchObject({
      visitId: "visit-1",
      serviceName: "Khám cơ xương khớp",
      queueNumber: 1,
    });
    expect(rows[3]).toMatchObject({
      appointmentId: "a2",
      visitId: null,
      queueNumber: null,
    });
  });

  it("walk-in đang chờ có mã lượt, số thứ tự, tuổi và giới lấy từ hàng chờ", async () => {
    route({
      "/api/v1/doctor/exam-queue": {
        items: [
          queueRow({
            visitId: "walk-1",
            queueNumber: 4,
            examStatus: "WAITING",
            source: "WALK_IN",
            appointmentId: null,
            patientName: "Đỗ Thanh Bình",
            dateOfBirth: "1990-01-01",
            gender: "FEMALE",
          }),
        ],
      },
      "/api/v1/appointments?": { items: [] },
    });
    const [walkIn] = await doctorLiveApi.listTodayPatients();
    expect(walkIn).toMatchObject({
      visitId: "walk-1",
      queueNumber: 4,
      status: "WAITING_FOR_EXAMINATION",
      source: "WALK_IN",
      appointmentId: null,
      patientName: "Đỗ Thanh Bình",
      sex: "Nữ",
      serviceName: "Khám bệnh",
      scheduledAt: "2026-11-04T01:00:00Z",
    });
    expect(walkIn.age).toBeGreaterThan(20);
  });

  it("hàng chờ lỗi thì báo lỗi, không dựng danh sách từ lịch hẹn", async () => {
    route({
      "/api/v1/doctor/exam-queue": new ApiError(500, {}),
      "/api/v1/appointments?": {
        items: [appointment({ status: "CONFIRMED", visitId: null })],
      },
    });
    await expect(doctorLiveApi.listTodayPatients()).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  it("lịch đã có dòng hàng chờ không lặp ở nhóm Chưa đến; lịch đã xác nhận khác vẫn hiện", async () => {
    route({
      "/api/v1/doctor/exam-queue": {
        items: [queueRow({ appointmentId: "appt-1", visitId: "visit-1" })],
      },
      "/api/v1/appointments?": {
        items: [
          appointment({
            id: "appt-1",
            status: "CONFIRMED",
            visitId: "visit-1",
          }),
          appointment({ id: "appt-2", status: "CONFIRMED", visitId: null }),
        ],
      },
    });
    const rows = await doctorLiveApi.listTodayPatients();
    expect(rows.map((r) => r.appointmentId)).toEqual(["appt-1", "appt-2"]);
    expect(rows.map((r) => r.status)).toEqual([
      "WAITING_FOR_EXAMINATION",
      "NOT_ARRIVED",
    ]);
  });

  it("ngữ cảnh lượt: trạng thái từ hàng chờ (sau chỉ định là chờ kết luận), tuổi/giới từ /doctor/visits/active, chi tiết từ hẹn liên kết", async () => {
    route({
      "/api/v1/visits/visit-1/exam-queue-entry": queueRow({
        examStatus: "AWAITING_CONCLUSION",
        doctorProfileId: "doc-9",
      }),
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
      status: "AWAITING_CONCLUSION",
      source: "BOOKED",
      serviceName: "Tái khám",
      allergies: "Penicillin",
      allergiesLoaded: true,
      medicalHistory: "Gout; Mổ gối 2020",
    });
    expect(context.age).toBeGreaterThan(20);
  });

  it("walk-in đang chờ (không có hẹn, chưa có bước khám) có trạng thái chờ khám và tên từ hàng chờ; thiếu tiền sử không làm hỏng", async () => {
    route({
      "/api/v1/visits/walk-1/exam-queue-entry": queueRow({
        visitId: "walk-1",
        examStatus: "WAITING",
        source: "WALK_IN",
        appointmentId: null,
        patientName: "Đỗ Thanh Bình",
        queueNumber: 4,
      }),
      "/api/v1/doctor/visits/active": { items: [] },
      "/api/v1/patient-profiles/": new ApiError(403, { code: "MSG-19" }),
    });
    const context = await doctorLiveApi.getVisitContext("walk-1");
    expect(context).toMatchObject({
      visitId: "walk-1",
      appointmentId: null,
      source: "WALK_IN",
      status: "WAITING_FOR_EXAMINATION",
      patientName: "Đỗ Thanh Bình",
      allergies: "",
      allergiesLoaded: false,
      age: null,
    });
    expect(context.scheduledAt).toBe("2026-11-04T01:00:00Z");
  });

  it("lượt không có trong hàng chờ (404) thì ngữ cảnh rỗng và không quét lịch hẹn", async () => {
    route({ "/api/v1/doctor/visits/active": { items: [] } });
    expect(await doctorLiveApi.getVisitContext("unknown")).toMatchObject({
      visitId: "unknown",
      status: null,
      patientName: null,
      patientProfileId: null,
    });
    expect(
      request.mock.calls.some((call) =>
        String(call[0]).startsWith("/api/v1/appointments"),
      ),
    ).toBe(false);
  });

  it("lỗi khác khi đọc hàng chờ thì ném ra, không coi như chưa có trạng thái", async () => {
    route({
      "/api/v1/visits/visit-1/exam-queue-entry": new ApiError(403, {}),
    });
    await expect(
      doctorLiveApi.getVisitContext("visit-1"),
    ).rejects.toBeInstanceOf(ApiError);
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
  });

  it("kết thúc lượt: lịch hẹn đóng qua PATCH COMPLETED, walk-in qua POST completion", async () => {
    request.mockResolvedValue({
      visitId: "v",
      examStatus: "COMPLETED",
      closedAt: "2026-11-11T02:00:00Z",
    });
    await doctorLiveApi.finishVisit({
      visitId: "v",
      appointmentId: "appt-1",
    } as never);
    expect(request).toHaveBeenLastCalledWith(
      "/api/v1/appointments/appt-1/status",
      {
        method: "PATCH",
        body: JSON.stringify({ targetStatus: "COMPLETED" }),
      },
    );
    await doctorLiveApi.finishVisit({
      visitId: "v",
      appointmentId: null,
    } as never);
    expect(request).toHaveBeenLastCalledWith("/api/v1/visits/v/completion", {
      method: "POST",
    });
  });

  it("kết thúc bị từ chối vì thiếu bằng chứng giữ mã MSG-42 để màn hiện đúng", async () => {
    request.mockRejectedValueOnce(
      new ApiError(409, { errorCode: "VISIT_COMPLETION_NOT_ALLOWED" }),
    );
    await expect(
      doctorLiveApi.finishVisit({ visitId: "v", appointmentId: null } as never),
    ).rejects.toMatchObject({ code: "MSG-42" });
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
  it("không quét các trang lịch hẹn: tên lấy từ hàng chờ, chi tiết hẹn lấy theo mã hẹn của hàng chờ", async () => {
    request.mockImplementation(async (path: string) => {
      if (path.startsWith("/api/v1/doctor/visits/active")) return { items: [] };
      if (path === "/api/v1/visits/target/exam-queue-entry")
        return queueRow({
          visitId: "target",
          appointmentId: "appt-7",
          patientName: "Trang hai",
        });
      if (path === "/api/v1/appointments/appt-7")
        return appointment({
          id: "appt-7",
          visitId: "target",
          serviceName: "Tái khám",
        });
      if (path.includes("medical-history"))
        return {
          allergies: "",
          conditions: "",
          orthopedicHistory: "",
          version: 1,
        };
      throw new ApiError(404, {});
    });
    const context = await doctorLiveApi.getVisitContext("target");
    expect(context).toMatchObject({
      patientName: "Trang hai",
      appointmentId: "appt-7",
      serviceName: "Tái khám",
      allergiesLoaded: true,
    });
    expect(
      request.mock.calls.some((call) =>
        String(call[0]).startsWith("/api/v1/appointments?"),
      ),
    ).toBe(false);
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
