import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { resetShiftMockStore } from "../api/shiftApi";
import { shiftMockConfig } from "../api/shiftMockData";
import { useShiftWeek } from "./useShiftWeek";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Giả lập thanh địa chỉ: useSearchParams đọc `url.search`, router.replace ghi lại và báo component vẽ lại.
const url = vi.hoisted(() => ({
  search: "",
  listeners: new Set<() => void>(),
}));

vi.mock("next/navigation", async () => {
  const React = await import("react");
  return {
    usePathname: () => "/admin/staff-shifts",
    useRouter: () => ({
      replace: (next: string) => {
        url.search = next.split("?")[1] ?? "";
        url.listeners.forEach((listener) => listener());
      },
    }),
    useSearchParams: () => {
      const search = React.useSyncExternalStore(
        (listener) => {
          url.listeners.add(listener);
          return () => url.listeners.delete(listener);
        },
        () => url.search,
        () => url.search,
      );
      return new URLSearchParams(search);
    },
  };
});

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

function mockViewport(wide: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: wide,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

describe("useShiftWeek", () => {
  beforeEach(() => {
    shiftMockConfig.delayMs = 0;
    resetShiftMockStore();
    url.search = "";
  });
  afterEach(() => vi.clearAllMocks());

  it("từ 640px mặc định là Lưới + Tuần và tải ca của kỳ", async () => {
    mockViewport(true);
    const { result } = renderHook(() => useShiftWeek());
    expect(result.current.period).toBe("week");
    expect(result.current.view).toBe("grid");
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.range.days).toHaveLength(7);
    expect(result.current.error).toBeNull();
  });

  it("dưới 640px mặc định Danh sách + Ngày khi URL chưa chọn", async () => {
    mockViewport(false);
    const { result } = renderHook(() => useShiftWeek());
    expect(result.current.period).toBe("day");
    expect(result.current.view).toBe("list");
    expect(result.current.range.days).toEqual([todayIsoDate()]);
    await waitFor(() => expect(result.current.loading).toBe(false));
  });

  it("‹ › đổi ngày neo trên URL, Hôm nay quay về ngày hiện tại", async () => {
    mockViewport(true);
    const { result } = renderHook(() => useShiftWeek());
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.goNext());
    await waitFor(() =>
      expect(result.current.anchor).toBe(addDays(todayIsoDate(), 7)),
    );
    expect(url.search).toContain("date=");
    act(() => result.current.goToday());
    await waitFor(() => expect(result.current.anchor).toBe(todayIsoDate()));
    expect(url.search).toBe("");
  });

  it("lọc nhân viên không còn hợp lệ (MSG-33): bỏ lọc rồi tải lại", async () => {
    mockViewport(true);
    url.search = "staffId=st-off";
    const { result } = renderHook(() => useShiftWeek());
    await waitFor(() => expect(url.search).not.toContain("staffId"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.filters.staffId).toBeUndefined();
    expect(result.current.error).toBeNull();
  });

  it("lỗi dịch vụ (MSG-04) đưa vào error và cho tải lại", async () => {
    mockViewport(true);
    url.search = "date=2030-01-07";
    const { result } = renderHook(() => useShiftWeek());
    await waitFor(() =>
      expect(result.current.error).toBe(formatAppMessage("MSG-04")),
    );
    expect(result.current.forbidden).toBe(false);
  });
});
