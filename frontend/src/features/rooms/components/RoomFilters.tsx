"use client";

import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import {
  ROOM_CATEGORY_LABELS,
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import { useRoomServiceOptions } from "../hooks/useRoomServiceOptions";
import {
  ROOM_STATUS_LABELS,
  type RoomListFilters,
  type RoomStatus,
} from "../types/room";

type RoomFiltersProps = {
  searchText: string;
  onSearchChange: (value: string) => void;
  filters: RoomListFilters;
  onFilterChange: (patch: Partial<RoomListFilters>) => void;
};

const selectClass = "w-full sm:w-auto";

export function RoomFilters({
  searchText,
  onSearchChange,
  filters,
  onFilterChange,
}: RoomFiltersProps) {
  const services = useRoomServiceOptions();
  return (
    <FilterBar>
      <SearchField
        value={searchText}
        onChange={onSearchChange}
        placeholder="Tìm theo tên hoặc mã"
        label="Tìm phòng theo tên hoặc mã"
      />
      <NativeSelect
        className={selectClass}
        aria-label="Lọc theo loại phòng"
        value={filters.type}
        onChange={(e) =>
          onFilterChange({ type: e.target.value as ServiceCategory | "" })
        }
      >
        <NativeSelectOption value="">Mọi loại phòng</NativeSelectOption>
        {SERVICE_CATEGORIES.map((c) => (
          <NativeSelectOption key={c} value={c}>
            {ROOM_CATEGORY_LABELS[c]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      <NativeSelect
        className={selectClass}
        aria-label="Lọc theo trạng thái"
        value={filters.status}
        onChange={(e) =>
          onFilterChange({ status: e.target.value as RoomStatus | "" })
        }
      >
        <NativeSelectOption value="">Mọi trạng thái</NativeSelectOption>
        {(Object.keys(ROOM_STATUS_LABELS) as RoomStatus[]).map((s) => (
          <NativeSelectOption key={s} value={s}>
            {ROOM_STATUS_LABELS[s]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      <NativeSelect
        className={selectClass}
        aria-label="Lọc theo dịch vụ hỗ trợ"
        value={filters.serviceId}
        disabled={services.loading || Boolean(services.error)}
        onChange={(e) => onFilterChange({ serviceId: e.target.value })}
      >
        <NativeSelectOption value="">Mọi dịch vụ</NativeSelectOption>
        {services.options.map((o) => (
          <NativeSelectOption key={o.id} value={o.id}>
            {o.name}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </FilterBar>
  );
}
