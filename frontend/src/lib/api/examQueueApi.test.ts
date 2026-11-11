import { beforeEach, describe, expect, it, vi } from "vitest";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

import {
  completeWalkInVisit,
  type ExamQueueItem,
  getExamQueueEntry,
  listDoctorExamQueue,
  listOnShiftDoctors,
  listReceptionExamQueue,
} from "./examQueueApi";

const queued: ExamQueueItem = {
  visitId: "visit-1",
  queueNumber: 3,
  examStatus: "WAITING",
  source: "WALK_IN",
  appointmentId: null,
  patientProfileId: "patient-1",
  patientName: "Nguyễn Văn A",
  doctorProfileId: "doctor-1",
  doctorName: "BS. Minh",
  roomId: "room-1",
  roomLabel: "Phòng 2",
  serviceId: "service-1",
  queuedAt: "2026-11-11T02:00:00Z",
  startedAt: null,
  finishedAt: null,
};

beforeEach(() => {
  request.mockReset();
});

describe("listReceptionExamQueue", () => {
  it("không truyền ngày thì gọi đường dẫn gốc (BE mặc định hôm nay)", async () => {
    request.mockResolvedValue({ items: [queued] });
    await expect(listReceptionExamQueue()).resolves.toEqual({
      items: [queued],
    });
    expect(request).toHaveBeenCalledWith("/api/v1/exam-queue");
  });

  it("truyền ngày thì gửi query date=YYYY-MM-DD", async () => {
    request.mockResolvedValue({ items: [] });
    await listReceptionExamQueue("2026-11-12");
    expect(request).toHaveBeenCalledWith("/api/v1/exam-queue?date=2026-11-12");
  });
});

describe("hàng chờ của bác sĩ và ca trực", () => {
  it("listDoctorExamQueue gọi đường dẫn của bác sĩ đăng nhập", async () => {
    request.mockResolvedValue({ items: [] });
    await listDoctorExamQueue();
    expect(request).toHaveBeenCalledWith("/api/v1/doctor/exam-queue");
  });

  it("listOnShiftDoctors trả danh sách bác sĩ đang trong ca", async () => {
    const shift = {
      doctorProfileId: "doctor-1",
      doctorName: "BS. Minh",
      roomId: null,
      roomLabel: null,
      waitingCount: 0,
    };
    request.mockResolvedValue({ items: [shift] });
    await expect(listOnShiftDoctors()).resolves.toEqual({ items: [shift] });
    expect(request).toHaveBeenCalledWith("/api/v1/reception/on-shift-doctors");
  });
});

describe("theo mã lượt khám", () => {
  it("getExamQueueEntry mã hoá mã lượt trong đường dẫn", async () => {
    request.mockResolvedValue(queued);
    await expect(getExamQueueEntry("visit/1")).resolves.toEqual(queued);
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/visit%2F1/exam-queue-entry",
    );
  });

  it("completeWalkInVisit gửi POST không có thân tới đường dẫn completion", async () => {
    const closed = {
      visitId: "visit-1",
      examStatus: "COMPLETED",
      closedAt: "2026-11-11T03:00:00Z",
    };
    request.mockResolvedValue(closed);
    await expect(completeWalkInVisit("visit-1")).resolves.toEqual(closed);
    expect(request).toHaveBeenCalledWith("/api/v1/visits/visit-1/completion", {
      method: "POST",
    });
  });

  it("lỗi từ apiRequest được ném nguyên, không bọc lại", async () => {
    const failure = new Error("network");
    request.mockRejectedValue(failure);
    await expect(getExamQueueEntry("visit-1")).rejects.toBe(failure);
  });
});
