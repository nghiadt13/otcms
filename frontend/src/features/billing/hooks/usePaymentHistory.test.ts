import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as paymentApi from "../api/paymentApi";
import {
  defaultPaymentFilters,
  paymentDateRangeError,
  usePaymentHistory,
} from "./usePaymentHistory";

describe("defaultPaymentFilters", () => {
  it("2026-10-20 → từ 2026-09-20 đến 2026-10-20, không lọc phương thức", () => {
    expect(defaultPaymentFilters(new Date("2026-10-20T03:00:00Z"))).toEqual({
      from: "2026-09-20",
      to: "2026-10-20",
      q: "",
      method: "",
    });
  });

  it("tính theo giờ Việt Nam: 18:00 UTC ngày 19 đã là ngày 20", () => {
    expect(defaultPaymentFilters(new Date("2026-10-19T18:00:00Z")).to).toBe("2026-10-20");
  });
});

describe("paymentDateRangeError", () => {
  it("từ > đến → MSG-54; bằng nhau → null", () => {
    expect(paymentDateRangeError("2026-10-20", "2026-10-01")).toBe("MSG-54");
    expect(paymentDateRangeError("2026-10-20", "2026-10-20")).toBeNull();
  });
});

describe("usePaymentHistory", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-20T03:00:00Z"));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("từ > đến: có rangeError và không gọi thêm listPayments", async () => {
    const spy = vi.spyOn(paymentApi, "listPayments");
    const { result } = renderHook(() => usePaymentHistory());
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 4000 });
    const calls = spy.mock.calls.length;

    act(() => result.current.setDateRange({ from: "2026-10-20", to: "2026-10-01" }));
    expect(result.current.rangeError).toBe("MSG-54");
    expect(spy.mock.calls.length).toBe(calls);

    act(() => result.current.setDateRange({ from: "", to: "2026-10-01" }));
    expect(result.current.range.from).toBe("2026-10-20");
  });

  it("đổi method về trang 0 và gửi method; q được debounce rồi trim", async () => {
    const spy = vi.spyOn(paymentApi, "listPayments");
    const { result } = renderHook(() => usePaymentHistory());
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 4000 });
    act(() => result.current.setPage(1));
    await waitFor(() => expect(result.current.page).toBe(1));

    act(() => result.current.setMethod("CASH"));
    expect(result.current.page).toBe(0);
    expect(result.current.filters.method).toBe("CASH");

    act(() => result.current.setSearchText("  Bích "));
    expect(result.current.filters.q).toBe(""); // chưa qua 250 ms
    await waitFor(() => expect(result.current.filters.q).toBe("Bích"), { timeout: 2000 });
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 4000 });
    expect(spy).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "Bích", method: "CASH", page: 0 }),
      expect.anything(),
    );
  });
});
