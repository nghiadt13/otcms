"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import { listServiceCatalog } from "../api/serviceCatalogApi";
import type { ServiceCatalogItem, ServiceStatus } from "../types/serviceCatalog";

type Filters = {
  q: string;
  type: ServiceCategory | "";
  status: ServiceStatus | "";
};

const INITIAL_FILTERS: Filters = { q: "", type: "", status: "" };

/** Danh sách dịch vụ: ô tìm được debounce 250ms; đổi bộ lọc về trang 0 (do usePagedList). */
export function useServiceCatalogList() {
  const list = usePagedList<ServiceCatalogItem, Filters>(
    (query, signal) =>
      listServiceCatalog(
        {
          page: query.page,
          size: query.size,
          q: query.q.trim() || undefined,
          type: query.type || undefined,
          status: query.status || undefined,
        },
        signal,
      ),
    INITIAL_FILTERS,
  );

  const [searchText, setSearchText] = useState("");
  const debounced = useDebouncedValue(searchText);
  const { setFilters, filters } = list;
  useEffect(() => {
    if (debounced !== filters.q) setFilters({ q: debounced });
  }, [debounced, filters.q, setFilters]);

  const hasFilters =
    filters.q.trim() !== "" || filters.type !== "" || filters.status !== "";

  /** Bỏ mọi bộ lọc (sau khi tạo dịch vụ mới để thấy ngay bản ghi). */
  const resetFilters = () => {
    setSearchText("");
    setFilters(INITIAL_FILTERS);
  };

  return { ...list, searchText, setSearchText, hasFilters, resetFilters };
}
