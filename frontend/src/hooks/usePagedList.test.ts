import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { paginate } from "@/lib/api/mockServer";
import type { PageQuery } from "@/lib/api/pagination";
import { usePagedList } from "./usePagedList";
import { formatAppMessage } from "@/lib/messages/appMessages";
type Filters = { q: string };
const rows = Array.from({ length: 45 }, (_, i) => ({ id: i, name: `Dòng ${i}` }));

const fetcher = async (query: Filters & PageQuery) =>
  paginate(
    rows.filter((row) => row.name.includes(query.q)),
    query,
    { summary: { total: rows.length } },
  );

describe("usePagedList", () => {
  it("tải trang đầu và giữ dữ liệu kèm trang", async () => {
    const { result } = renderHook(() => usePagedList(fetcher, { q: "" }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data?.items).toHaveLength(20);
    expect(result.current.data?.summary.total).toBe(45);
  });

  it("đổi bộ lọc thì về trang 0", async () => {
    const { result } = renderHook(() => usePagedList(fetcher, { q: "" }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.setPage(2));
    await waitFor(() => expect(result.current.data?.page).toBe(2));
    act(() => result.current.setFilters({ q: "Dòng 1" }));
    await waitFor(() => expect(result.current.page).toBe(0));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data?.page).toBe(0);
  });

  it("báo lỗi bằng câu MSG và cho tải lại", async () => {
    let fail = true;
    const { result } = renderHook(() =>
      usePagedList(async (query: Filters & PageQuery) => {
        if (fail) throw new ApiError(500, { code: "MSG-04" });
        return paginate(rows, query);
      }, { q: "" }),
    );
    await waitFor(() => expect(result.current.error).toBe(formatAppMessage("MSG-04")));
    fail = false;
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data?.totalItems).toBe(45));
    expect(result.current.error).toBeNull();
  });
});
