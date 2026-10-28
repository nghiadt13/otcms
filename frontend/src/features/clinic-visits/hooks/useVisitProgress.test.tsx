import { act, renderHook } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { getVisitProgress } from "../api/visitApi";
import { resetVisitMock } from "../api/visitMockStore";
import {
  useVisitProgress,
  VISIT_PROGRESS_REFRESH_MS,
} from "./useVisitProgress";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Lớp API giả chạy thật; chỉ bọc bằng vi.fn để đếm lần gọi và ép một lần lỗi.
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
vi.mock("../api/visitApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/visitApi")>();
  return { ...original, getVisitProgress: vi.fn(original.getVisitProgress) };
});

const NOW = new Date("2026-10-24T08:00:00+07:00");

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
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
  setVisibility("visible");
  vi.mocked(getVisitProgress).mockClear();
  vi.mocked(toast.error).mockClear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useVisitProgress", () => {
  it("tự tải lại sau 30 s và cập nhật số người phía trước", async () => {
    const { result } = renderHook(() => useVisitProgress("vis-an-today"));
    await advance(0);
    expect(result.current.loading).toBe(false);
    expect(result.current.progress?.queues[0].peopleAhead).toBe(3);

    await advance(VISIT_PROGRESS_REFRESH_MS);
    expect(result.current.progress?.queues[0].peopleAhead).toBe(2);
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

    act(() => result.current.reload());
    await advance(0);
    expect(result.current.error).toBeNull();
    expect(result.current.progress?.id).toBe("vis-an-today");
  });
});
