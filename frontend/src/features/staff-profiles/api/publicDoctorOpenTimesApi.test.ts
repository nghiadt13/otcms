import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import {
  groupOpenTimesByDoctor,
  listPublicDoctorOpenTimes,
  OPEN_TIME_WINDOW_DAYS,
} from "./publicDoctorOpenTimesApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));

beforeEach(() => {
  request.mockReset();
});

describe("groupOpenTimesByDoctor", () => {
  it("gom theo bác sĩ và sắp theo thời gian", () => {
    const grouped = groupOpenTimesByDoctor([
      {
        serviceId: "svc-a",
        doctorId: "doc-1",
        startTime: "2026-11-19T02:30:00Z",
      },
      {
        serviceId: "svc-a",
        doctorId: "doc-1",
        startTime: "2026-11-19T01:30:00Z",
      },
      {
        serviceId: "svc-a",
        doctorId: "doc-2",
        startTime: "2026-11-19T01:30:00Z",
      },
    ]);
    expect(grouped.get("doc-1")).toEqual([
      { serviceId: "svc-a", startAt: "2026-11-19T01:30:00Z" },
      { serviceId: "svc-a", startAt: "2026-11-19T02:30:00Z" },
    ]);
    expect(grouped.get("doc-2")).toEqual([
      { serviceId: "svc-a", startAt: "2026-11-19T01:30:00Z" },
    ]);
  });

  it("cùng bác sĩ và cùng thời điểm ở nhiều dịch vụ thì chỉ giữ dịch vụ đầu tiên", () => {
    const grouped = groupOpenTimesByDoctor([
      {
        serviceId: "svc-a",
        doctorId: "doc-1",
        startTime: "2026-11-19T01:30:00Z",
      },
      {
        serviceId: "svc-b",
        doctorId: "doc-1",
        startTime: "2026-11-19T01:30:00.000Z",
      },
    ]);
    expect(grouped.get("doc-1")).toEqual([
      { serviceId: "svc-a", startAt: "2026-11-19T01:30:00Z" },
    ]);
  });
});

describe("listPublicDoctorOpenTimes", () => {
  it("đọc danh mục dịch vụ rồi gọi giờ đặt của từng dịch vụ trong 7 ngày tới", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === "/api/v1/clinic-services")
        return { items: [{ id: "svc-a" }, { id: "svc-b" }] };
      if (path.includes("serviceId=svc-a"))
        return {
          slots: [
            {
              serviceId: "svc-a",
              doctorId: "doc-1",
              startTime: "2026-11-19T01:30:00Z",
            },
          ],
        };
      return {
        slots: [
          {
            serviceId: "svc-b",
            doctorId: "doc-2",
            startTime: "2026-11-19T03:00:00Z",
          },
        ],
      };
    });

    const grouped = await listPublicDoctorOpenTimes();

    const from = todayIsoDate();
    const to = addDays(from, OPEN_TIME_WINDOW_DAYS - 1);
    expect(OPEN_TIME_WINDOW_DAYS).toBe(7);
    expect(request.mock.calls.map(([path]) => path)).toEqual([
      "/api/v1/clinic-services",
      `/api/v1/appointments/availability?serviceId=svc-a&from=${from}&to=${to}`,
      `/api/v1/appointments/availability?serviceId=svc-b&from=${from}&to=${to}`,
    ]);
    expect(grouped.get("doc-1")).toEqual([
      { serviceId: "svc-a", startAt: "2026-11-19T01:30:00Z" },
    ]);
    expect(grouped.get("doc-2")).toEqual([
      { serviceId: "svc-b", startAt: "2026-11-19T03:00:00Z" },
    ]);
  });

  it("một dịch vụ lỗi thì cả lần tải lỗi, không trả kết quả thiếu", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === "/api/v1/clinic-services")
        return { items: [{ id: "svc-a" }, { id: "svc-b" }] };
      if (path.includes("serviceId=svc-b")) throw new ApiError(503);
      return { slots: [] };
    });
    await expect(listPublicDoctorOpenTimes()).rejects.toMatchObject({
      status: 503,
    });
  });

  it("chưa có dịch vụ nào đặt trực tuyến được thì không có giờ và không gọi giờ đặt", async () => {
    request.mockResolvedValueOnce({ items: [] });
    expect((await listPublicDoctorOpenTimes()).size).toBe(0);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
