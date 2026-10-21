"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { listAnnouncements } from "../api/announcementApi";
import type {
  AnnouncementAudience,
  AnnouncementFilters,
  AnnouncementListItem,
} from "../types/announcement";

const INITIAL_FILTERS: AnnouncementFilters = {
  q: "",
  audience: "",
  from: "",
  to: "",
};

/**
 * Danh sách thông báo + bộ lọc. Ô tìm debounce 250ms; khoảng ngày sai (từ > đến)
 * thì không gọi API và giữ kết quả cũ.
 */
export function useAnnouncementList() {
  const list = usePagedList<AnnouncementListItem, AnnouncementFilters>(
    (query, signal) => listAnnouncements(query, signal),
    INITIAL_FILTERS,
  );
  const { setFilters } = list;
  const appliedQ = list.filters.q;

  const [searchInput, setSearchInput] = useState("");
  const debouncedQ = useDebouncedValue(searchInput.trim(), 250);
  const [range, setRange] = useState({ from: "", to: "" });

  const dateError =
    range.from && range.to && range.from > range.to
      ? APP_MESSAGES["MSG-54"]
      : undefined;

  useEffect(() => {
    if (debouncedQ !== appliedQ) setFilters({ q: debouncedQ });
  }, [debouncedQ, appliedQ, setFilters]);

  function changeRange(next: { from: string; to: string }) {
    setRange(next);
    if (!(next.from && next.to && next.from > next.to)) setFilters(next);
  }

  function changeAudience(audience: AnnouncementAudience | "") {
    setFilters({ audience });
  }

  const { q, audience, from, to } = list.filters;
  const hasFilters = Boolean(q || audience || from || to);

  return {
    list,
    searchInput,
    setSearchInput,
    range,
    changeRange,
    dateError,
    changeAudience,
    hasFilters,
  };
}
