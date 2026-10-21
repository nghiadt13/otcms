"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { listWebsiteContents } from "../api/websiteContentAdminApi";
import type {
  WebsiteContentListItem,
  WebsiteContentType,
} from "../types/websiteContentAdmin";

export type WebsiteContentFilters = { q: string; type: WebsiteContentType | "" };

const INITIAL_FILTERS: WebsiteContentFilters = { q: "", type: "" };

/** Danh sách nội dung + bộ lọc. Ô tìm debounce 250 ms; bộ lọc rỗng thì không gửi lên API. */
export function useWebsiteContentList() {
  const list = usePagedList<WebsiteContentListItem, WebsiteContentFilters>(
    ({ q, type, page, size }, signal) =>
      listWebsiteContents(
        { q: q || undefined, type: type || undefined, page, size },
        signal,
      ),
    INITIAL_FILTERS,
  );
  const { setFilters } = list;
  const appliedQ = list.filters.q;

  const [searchInput, setSearchInput] = useState("");
  const debouncedQ = useDebouncedValue(searchInput.trim(), 250);
  useEffect(() => {
    if (debouncedQ !== appliedQ) setFilters({ q: debouncedQ });
  }, [debouncedQ, appliedQ, setFilters]);

  return {
    ...list,
    searchInput,
    setSearchInput,
    setType: (type: WebsiteContentType | "") => setFilters({ type }),
    isFiltered: Boolean(list.filters.q || list.filters.type),
  };
}
