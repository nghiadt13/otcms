"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { listRooms } from "../api/roomApi";
import type { Room, RoomListFilters } from "../types/room";

const INITIAL_FILTERS: RoomListFilters = {
  q: "",
  type: "",
  status: "",
  serviceId: "",
};

/** Danh sách phòng phân trang; ô tìm được debounce 250ms trước khi gọi API. */
export function useRoomList() {
  const list = usePagedList<Room, RoomListFilters>(
    (query, signal) => listRooms(query, signal),
    INITIAL_FILTERS,
  );
  const [searchText, setSearchText] = useState("");
  const debouncedSearch = useDebouncedValue(searchText);
  const { setFilters } = list;

  useEffect(() => {
    setFilters({ q: debouncedSearch.trim() });
  }, [debouncedSearch, setFilters]);

  return { ...list, searchText, setSearchText };
}
