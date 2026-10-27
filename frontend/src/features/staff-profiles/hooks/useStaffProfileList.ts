"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { listStaffProfiles } from "../api/staffProfileApi";
import type {
  StaffProfileFilters,
  StaffProfileListItem,
} from "../types/staffProfile";

const INITIAL_FILTERS: StaffProfileFilters = {
  q: "",
  role: "",
  status: "ACTIVE", // mặc định chỉ hồ sơ đang hoạt động (đề xuất)
  isPublic: "",
};

/** Danh sách hồ sơ: ô tìm kiếm đợi 250 ms rồi mới gọi API; đổi lọc thì về trang đầu. */
export function useStaffProfileList() {
  const list = usePagedList<StaffProfileListItem, StaffProfileFilters>(
    listStaffProfiles,
    INITIAL_FILTERS,
  );
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const { filters, setFilters } = list;

  useEffect(() => {
    if (debounced !== filters.q) setFilters({ q: debounced });
  }, [debounced, filters.q, setFilters]);

  // Công khai chỉ áp cho bác sĩ: chọn lọc này thì vai trò khóa ở bác sĩ.
  const setFilter = <K extends keyof StaffProfileFilters>(
    key: K,
    value: StaffProfileFilters[K],
  ) => {
    if (key === "isPublic" && value !== "")
      setFilters({
        isPublic: value as StaffProfileFilters["isPublic"],
        role: "ORTHOPEDIC_DOCTOR",
      });
    else setFilters({ [key]: value } as Partial<StaffProfileFilters>);
  };

  return { ...list, search, setSearch, setFilter };
}
