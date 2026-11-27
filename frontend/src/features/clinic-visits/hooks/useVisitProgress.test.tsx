import { act, renderHook } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { getVisitProgress } from "../api/visitApi";
import type { VisitProgress } from "../types/visitProgress";
import {
  useVisitProgress,
  VISIT_PROGRESS_REFRESH_MS,
} from "./useVisitProgress";

// Hook chạy thật; chỉ thay getVisitProgress (ranh giới API) bằng vi.fn để đếm lần gọi và ép lỗi.
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
vi.mock("../api/visitApi", () => ({ getVisitProgress: vi.fn() }));

const progressAt = (peopleAhead: number): VisitProgress => ({
  visitId: "vis-an-today",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: "CONSULTATION",
  arrivedAt: "2026-10-24T01:00:00Z",
  queuePosition: null,
  currentRoom: "Phòng khám 2",
  queueNumber: 12,
  queueStage: "EXAM",
  examQueue: null,
  peopleAhead,
  beingCalled: false,
  serviceOrders: [],
});

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
  document.dispatchEvent(new Event("visibilitychange"));
}

/**
 * Chạy hẹn giờ trong `ms` (chu kỳ 30 s và độ trễ giả). Lệnh gọi lúc đang chạy đồng hồ lên lịch
 * độ trễ 0 thành 1 ms (fake timers), nên thêm một nhịp 1 ms để việc đó chạy trước khi kiểm tra.
 */
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
    await vi.advanceTimersByTimeAsync(1);
  });
}

const callCount = () => vi.mocked(getVisitProgress).mock.calls.length;

beforeEach(() => {
  vi.useFakeTimers();
  setVisibility("visible");
  vi.mocked(getVisitProgress).mockReset();
  vi.mocked(getVisitProgress).mockResolvedValue(progressAt(3));
  vi.mocked(toast.error).mockClear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useVisitProgress", () => {
  it("tự tải lại sau 30 s và cập nhật số người phía trước; ghi thời điểm cập nhật", async () => {
    const { result } = renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);
    expect(result.current.loading).toBe(false);
    expect(result.current.progress?.peopleAhead).toBe(3);
    expect(result.current.updatedAt).toEqual(expect.any(String));

    vi.mocked(getVisitProgress).mockResolvedValueOnce(progressAt(2));
    await advance(VISIT_PROGRESS_REFRESH_MS);
    expect(result.current.progress?.peopleAhead).toBe(2);
  });

  it("tab ẩn thì không gọi API; hiện lại thì tải ngay", async () => {
    renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);
    const before = callCount();

    setVisibility("hidden");
    await advance(VISIT_PROGRESS_REFRESH_MS * 3);
    expect(callCount()).toBe(before);

    setVisibility("visible");
    await advance(0);
    expect(callCount()).toBe(before + 1);
  });

  it("tải lại tự động lỗi: giữ dữ liệu cũ, đánh dấu thời điểm lỗi, không toast", async () => {
    const { result } = renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);
    const kept = result.current.progress;

    vi.mocked(getVisitProgress).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    await advance(VISIT_PROGRESS_REFRESH_MS);
    expect(result.current.progress).toBe(kept);
    expect(result.current.refreshFailedAt).toBeInstanceOf(Date);
    expect(result.current.error).toBeNull();
    expect(toast.error).not.toHaveBeenCalled();

    await advance(VISIT_PROGRESS_REFRESH_MS);
    expect(result.current.refreshFailedAt).toBeNull();
  });

  it("bấm Làm mới lỗi thì báo toast và vẫn giữ dữ liệu cũ", async () => {
    const { result } = renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);

    vi.mocked(getVisitProgress).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    act(() => result.current.reload());
    await advance(0);

    expect(toast.error).toHaveBeenCalledTimes(1);
    expect(vi.mocked(toast.error).mock.calls[0][0]).toBe(
      formatAppMessage("MSG-04"),
    );
    expect(result.current.progress).not.toBeNull();
    expect(result.current.refreshing).toBe(false);
  });

  it("lỗi tải lần đầu đi vào trạng thái lỗi; Thử lại lấy được dữ liệu", async () => {
    vi.mocked(getVisitProgress).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    const { result } = renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.progress).toBeNull();
    expect(result.current.updatedAt).toBeNull();

    act(() => result.current.reload());
    await advance(0);
    expect(result.current.error).toBeNull();
    expect(result.current.progress?.visitId).toBe("vis-an-today");
  });
});
