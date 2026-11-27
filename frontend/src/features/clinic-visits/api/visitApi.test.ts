import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import type { VisitListItem, VisitProgress } from "../types/visitProgress";
import { getVisitProgress, listVisits } from "./visitApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

// Hợp đồng lớp API của lượt khám: danh sách đọc GET /api/v1/me/visits rồi lọc và chia trang ở máy; tiến độ
// là API thật, khóa đường dẫn và lỗi đi nguyên từ BE.

const item = (
  patch: Pick<VisitListItem, "visitId" | "status"> & Partial<VisitListItem>,
): VisitListItem => ({
  patientProfileId: "p-self",
  patientName: "Nguyễn Văn An",
  relationship: "SELF",
  visitDate: "2026-10-24",
  checkedInAt: "2026-10-24T01:00:00Z",
  queueNumber: null,
  roomName: null,
  ...patch,
});

/** Sáu dòng như BE trả, mới trước. */
const rows = (): VisitListItem[] => [
  item({
    visitId: "vis-1",
    status: "WAITING_FOR_EXAMINATION",
    queueNumber: 12,
    roomName: "Phòng khám 2",
  }),
  item({
    visitId: "vis-2",
    status: "IN_EXAMINATION",
    patientProfileId: "p-child",
    patientName: "Nguyễn Gia Huy",
    relationship: "CHILD",
    visitDate: "2026-10-23",
  }),
  item({ visitId: "vis-3", status: "CANCELLED_LEFT", visitDate: "2026-10-22" }),
  item({
    visitId: "vis-4",
    status: "AWAITING_CONCLUSION",
    patientProfileId: "p-child",
    patientName: "Nguyễn Gia Huy",
    relationship: "CHILD",
    visitDate: "2026-10-21",
  }),
  item({ visitId: "vis-5", status: "COMPLETED", visitDate: "2026-10-20" }),
  item({
    visitId: "vis-6",
    status: "INCOMPLETE",
    patientProfileId: "p-legacy",
    patientName: "Trần Văn Bình",
    relationship: null,
    visitDate: "2026-10-18",
  }),
];

const ids = (page: { items: { id: string }[] }) =>
  page.items.map((visit) => visit.id);

beforeEach(() => {
  request.mockReset();
  request.mockResolvedValue(rows());
});

describe("listVisits (API thật)", () => {
  it("gọi GET /api/v1/me/visits và giữ thứ tự BE", async () => {
    const page = await listVisits({ when: "recent", page: 0, size: 10 });
    expect(request).toHaveBeenCalledWith("/api/v1/me/visits", {
      signal: undefined,
    });
    expect(ids(page)).toEqual([
      "vis-1",
      "vis-2",
      "vis-3",
      "vis-4",
      "vis-5",
      "vis-6",
    ]);
    expect(page).toMatchObject({
      page: 0,
      size: 10,
      totalItems: 6,
      totalPages: 1,
    });
  });

  it("đang diễn ra chỉ gồm Chờ khám, Đang khám, Chờ kết luận", async () => {
    const page = await listVisits({ when: "active", page: 0, size: 10 });
    expect(ids(page)).toEqual(["vis-1", "vis-2", "vis-4"]);
  });

  it("lọc theo hồ sơ đã gắn qua patientId", async () => {
    const page = await listVisits({
      when: "recent",
      patientId: "p-child",
      page: 0,
      size: 10,
    });
    expect(ids(page)).toEqual(["vis-2", "vis-4"]);
    expect(
      page.items.every((visit) => visit.patient.relationship === "CHILD"),
    ).toBe(true);
  });

  it("đổi dòng BE thành lượt; quan hệ cũ là null thì giữ null", async () => {
    const page = await listVisits({ when: "recent", page: 0, size: 10 });
    expect(page.items[0]).toEqual({
      id: "vis-1",
      visitDate: "2026-10-24",
      status: "WAITING_FOR_EXAMINATION",
      patient: {
        id: "p-self",
        fullName: "Nguyễn Văn An",
        relationship: "SELF",
      },
    });
    expect(page.items[5].patient).toEqual({
      id: "p-legacy",
      fullName: "Trần Văn Bình",
      relationship: null,
    });
  });

  it("chia trang: đúng cỡ, đúng số trang và tổng; trang vượt quá thì rỗng", async () => {
    const second = await listVisits({ when: "recent", page: 1, size: 4 });
    expect(ids(second)).toEqual(["vis-5", "vis-6"]);
    expect(second).toMatchObject({
      page: 1,
      size: 4,
      totalItems: 6,
      totalPages: 2,
    });

    const beyond = await listVisits({ when: "recent", page: 5, size: 4 });
    expect(beyond.items).toEqual([]);
  });

  it("không có lượt nào thì trang rỗng, không lỗi", async () => {
    request.mockResolvedValueOnce([]);
    const page = await listVisits({ when: "active", page: 0, size: 10 });
    expect(page).toMatchObject({ items: [], totalItems: 0, totalPages: 0 });
  });

  it("chuyển tín hiệu hủy, và lỗi của BE đi nguyên", async () => {
    const controller = new AbortController();
    await listVisits({ when: "recent", page: 0, size: 10 }, controller.signal);
    expect(request).toHaveBeenCalledWith("/api/v1/me/visits", {
      signal: controller.signal,
    });

    request.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
    await expect(
      listVisits({ when: "recent", page: 0, size: 10 }),
    ).rejects.toMatchObject({ status: 500, code: "MSG-04" });
  });
});

const progressBody = (patch: Partial<VisitProgress> = {}): VisitProgress => ({
  visitId: "visit-1",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: "CONSULTATION",
  arrivedAt: "2026-10-24T01:00:00Z",
  queuePosition: null,
  currentRoom: "Phòng khám 2",
  queueNumber: 12,
  queueStage: "EXAM",
  examQueue: null,
  peopleAhead: 3,
  beingCalled: false,
  serviceOrders: [],
  ...patch,
});

describe("getVisitProgress (API thật)", () => {
  it("gọi đúng đường dẫn progress và trả nguyên dữ liệu BE", async () => {
    const body = progressBody();
    request.mockResolvedValueOnce(body);

    await expect(getVisitProgress("visit-1")).resolves.toEqual(body);
    expect(request).toHaveBeenCalledWith("/api/v1/visits/visit-1/progress", {
      signal: undefined,
    });
  });

  it("mã hóa mã lượt trong đường dẫn và chuyển tín hiệu hủy", async () => {
    request.mockResolvedValueOnce(progressBody());
    const controller = new AbortController();

    await getVisitProgress("a/b", controller.signal);
    expect(request).toHaveBeenCalledWith("/api/v1/visits/a%2Fb/progress", {
      signal: controller.signal,
    });
  });

  it("lỗi của BE đi nguyên: 404 MSG-41 và 403 MSG-40", async () => {
    request.mockRejectedValueOnce(new ApiError(404, { code: "MSG-41" }));
    await expect(getVisitProgress("khong-co")).rejects.toMatchObject({
      status: 404,
      code: "MSG-41",
    });

    request.mockRejectedValueOnce(new ApiError(403, { code: "MSG-40" }));
    await expect(getVisitProgress("cua-nguoi-khac")).rejects.toMatchObject({
      status: 403,
      code: "MSG-40",
    });
  });
});
